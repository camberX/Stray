package dev.stray.client.render.overlay.window;

import com.sun.jna.Callback;
import com.sun.jna.CallbackReference;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * Wayland: the overlay is a layer-shell surface on the "overlay" layer instead of a window,
 * so the compositor never tiles, focuses, borders, blurs or animates it like a window, it
 * sits above fullscreen apps, and window capture of the game never includes it. Input passes
 * straight through (empty input region). It gets its own EGL context, so the game can run on
 * OpenGL or Vulkan. On Hyprland it is kept exactly over the game window; elsewhere it covers
 * the whole output (right for a fullscreen game).
 */
final class LayerOverlay implements OverlayWindow {
	private static final int ANCHOR_TOP = 1;
	private static final int ANCHOR_BOTTOM = 2;
	private static final int ANCHOR_LEFT = 4;
	private static final int ANCHOR_RIGHT = 8;
	private static final int LAYER_OVERLAY = 3;
	private static final boolean ANY_FOCUS = Boolean.getBoolean("stray.overlay.anyFocus");

	interface Egl extends Library {
		Pointer eglGetPlatformDisplay(int platform, Pointer nativeDisplay, Pointer attribs);

		Pointer eglGetDisplay(Pointer nativeDisplay);

		boolean eglInitialize(Pointer dpy, int[] major, int[] minor);

		boolean eglBindAPI(int api);

		int eglQueryAPI();

		boolean eglChooseConfig(Pointer dpy, int[] attribs, Pointer[] configs, int size, int[] count);

		Pointer eglCreateContext(Pointer dpy, Pointer config, Pointer share, int[] attribs);

		Pointer eglCreateWindowSurface(Pointer dpy, Pointer config, Pointer window, int[] attribs);

		boolean eglDestroySurface(Pointer dpy, Pointer surface);

		boolean eglDestroyContext(Pointer dpy, Pointer ctx);

		boolean eglMakeCurrent(Pointer dpy, Pointer draw, Pointer read, Pointer ctx);

		boolean eglSwapBuffers(Pointer dpy, Pointer surface);

		boolean eglSwapInterval(Pointer dpy, int interval);

		Pointer eglGetCurrentContext();

		Pointer eglGetCurrentDisplay();

		Pointer eglGetCurrentSurface(int which);

		Pointer eglGetProcAddress(String name);

		int eglGetError();
	}

	public interface Global extends Callback {
		void invoke(Pointer data, Pointer registry, int name, String iface, int version);
	}

	public interface Remove extends Callback {
		void invoke(Pointer data, Pointer registry, int name);
	}

	public interface Configure extends Callback {
		void invoke(Pointer data, Pointer surface, int serial, int w, int h);
	}

	public interface Closed extends Callback {
		void invoke(Pointer data, Pointer surface);
	}

	public interface OutGeometry extends Callback {
		void invoke(Pointer data, Pointer out, int x, int y, int pw, int ph, int subpixel, String make, String model, int transform);
	}

	public interface OutMode extends Callback {
		void invoke(Pointer data, Pointer out, int flags, int w, int h, int refresh);
	}

	public interface OutDone extends Callback {
		void invoke(Pointer data, Pointer out);
	}

	public interface OutScale extends Callback {
		void invoke(Pointer data, Pointer out, int factor);
	}

	public interface OutText extends Callback {
		void invoke(Pointer data, Pointer out, String text);
	}

	// Callbacks and listener tables must stay reachable while Wayland may call them.
	private Global global;
	private Remove remove;
	private Configure configure;
	private Closed closedCb;
	private OutGeometry outGeometry;
	private OutMode outMode;
	private OutDone outDone;
	private OutScale outScale;
	private OutText outName;
	private OutText outDescription;
	private Memory registryListener;
	private Memory layerListener;
	private Memory outputListener;

	private Egl egl;
	private NativeLibrary eglLib;
	private Pointer display;
	private Pointer queue;
	private Pointer wrapper;
	private Pointer compositor;
	private Pointer layerShell;
	private Pointer viewporter;
	private final Map<Pointer, String> outputNames = new HashMap<>();
	private final Map<Pointer, Integer> outputScales = new HashMap<>();

	private Pointer surface;
	private Pointer layer;
	private Pointer viewport;
	private Pointer eglWindow;
	private Pointer eglDisplay;
	private Pointer eglConfig;
	private Pointer eglContext;
	private Pointer eglSurface;
	private GLCapabilities caps;

	private boolean closed;
	private int configuredW;
	private int configuredH;
	private String monitor;
	private int placeX = Integer.MIN_VALUE;
	private int placeY;
	private int placeW;
	private int placeH;
	private int bufW = 1;
	private int bufH = 1;
	private boolean shown;

	private Pointer savedDisplay;
	private Pointer savedDraw;
	private Pointer savedRead;
	private Pointer savedContext;
	private int savedApi;
	private GLCapabilities savedCaps;

	@Override
	public String create() {
		long wl = GameWindow.waylandDisplay();
		if (wl == 0L) {
			return "no Wayland connection found";
		}
		display = new Pointer(wl);
		queue = (Pointer) Wayland.call("wl_display_create_queue", Pointer.class, display);
		wrapper = (Pointer) Wayland.call("wl_proxy_create_wrapper", Pointer.class, display);
		Wayland.call("wl_proxy_set_queue", Void.class, wrapper, queue);
		Pointer registry = Wayland.request(wrapper, 1, Wayland.REGISTRY, 0, (Object) null);
		global = (data, reg, name, iface, version) -> {
			switch (iface) {
				case "wl_compositor" -> compositor = Wayland.bind(reg, name, Wayland.COMPOSITOR, iface, Math.min(version, 4));
				case "zwlr_layer_shell_v1" -> layerShell = Wayland.bind(reg, name, Wayland.LAYER_SHELL, iface, Math.min(version, 4));
				case "wp_viewporter" -> viewporter = Wayland.bind(reg, name, Wayland.VIEWPORTER, iface, 1);
				case "wl_output" -> {
					if (version >= 2) {
						Pointer out = Wayland.bind(reg, name, Wayland.OUTPUT, iface, Math.min(version, 4));
						Wayland.call("wl_proxy_add_listener", Integer.class, out, outputListener(), null);
					}
				}
				default -> {
				}
			}
		};
		remove = (data, reg, name) -> {
		};
		registryListener = table(global, remove);
		Wayland.call("wl_proxy_add_listener", Integer.class, registry, registryListener, null);
		roundtrip();
		roundtrip();
		if (compositor == null) {
			return "no wl_compositor";
		}
		if (layerShell == null) {
			return "your compositor has no layer-shell (GNOME doesn't; Hyprland, KDE, Sway do)";
		}
		HyprlandIpc.follow();
		try {
			eglLib = NativeLibrary.getInstance("EGL");
			egl = Native.load("EGL", Egl.class);
		} catch (Throwable t) {
			return "libEGL not found";
		}
		eglDisplay = null;
		try {
			eglDisplay = egl.eglGetPlatformDisplay(0x31D8, display, null);
		} catch (Throwable ignored) {
		}
		if (eglDisplay == null) {
			eglDisplay = egl.eglGetDisplay(display);
		}
		if (eglDisplay == null || !egl.eglInitialize(eglDisplay, new int[1], new int[1])) {
			return "EGL would not start (" + Integer.toHexString(egl.eglGetError()) + ")";
		}
		int savedApiNow = egl.eglQueryAPI();
		egl.eglBindAPI(0x30A2);
		int[] attribs = {
			0x3033, 0x0004, // SURFACE_TYPE: WINDOW
			0x3040, 0x0008, // RENDERABLE_TYPE: OPENGL
			0x3024, 8, 0x3023, 8, 0x3022, 8, 0x3021, 8,
			0x3038
		};
		Pointer[] configs = new Pointer[1];
		int[] count = new int[1];
		if (!egl.eglChooseConfig(eglDisplay, attribs, configs, 1, count) || count[0] < 1) {
			egl.eglBindAPI(savedApiNow);
			return "no EGL config with alpha";
		}
		eglConfig = configs[0];
		eglContext = egl.eglCreateContext(eglDisplay, eglConfig, null, new int[]{0x3098, 3, 0x30FB, 3, 0x30FD, 1, 0x3038});
		egl.eglBindAPI(savedApiNow);
		if (eglContext == null) {
			return "no GL 3.3 context (" + Integer.toHexString(egl.eglGetError()) + ")";
		}
		String error = makeSurface();
		if (error != null) {
			return error;
		}
		begin();
		try {
			egl.eglSwapInterval(eglDisplay, 0);
			GameWindow.ensureGl(name -> {
				Pointer p = egl.eglGetProcAddress(MemoryUtil.memASCII(MemoryUtil.memAddress(name)));
				return p == null ? 0L : Pointer.nativeValue(p);
			});
			caps = GL.createCapabilities();
		} finally {
			restore();
		}
		return null;
	}

	private void roundtrip() {
		Wayland.call("wl_display_roundtrip_queue", Integer.class, display, queue);
	}

	private static Memory table(Callback... callbacks) {
		Memory m = new Memory((long) Native.POINTER_SIZE * callbacks.length);
		for (int i = 0; i < callbacks.length; i++) {
			m.setPointer((long) Native.POINTER_SIZE * i, CallbackReference.getFunctionPointer(callbacks[i]));
		}
		return m;
	}

	private Memory outputListener() {
		if (outputListener == null) {
			outGeometry = (data, out, x, y, pw, ph, sub, make, model, transform) -> {
			};
			outMode = (data, out, flags, w, h, refresh) -> {
			};
			outDone = (data, out) -> {
			};
			outScale = (data, out, factor) -> outputScales.put(out, factor);
			outName = (data, out, text) -> outputNames.put(out, text);
			outDescription = (data, out, text) -> {
			};
			outputListener = table(outGeometry, outMode, outDone, outScale, outName, outDescription);
		}
		return outputListener;
	}

	private Pointer outputNamed(String name) {
		if (name == null) {
			return null;
		}
		for (Map.Entry<Pointer, String> e : outputNames.entrySet()) {
			if (name.equals(e.getValue())) {
				return e.getKey();
			}
		}
		return null;
	}

	/** wl_surface + layer surface + EGL window for the current monitor. */
	private String makeSurface() {
		HyprlandIpc.Place place = HyprlandIpc.place();
		monitor = place == null ? null : place.monitor();
		Pointer output = outputNamed(monitor);
		surface = Wayland.request(compositor, 0, Wayland.SURFACE, 0, (Object) null);
		// Empty input region: the pointer passes straight through to the game.
		Pointer region = Wayland.request(compositor, 1, Wayland.REGION, 0, (Object) null);
		Wayland.request(surface, 5, null, 0, region);
		Wayland.request(region, 0, null, Wayland.MARSHAL_DESTROY);
		viewport = viewporter == null ? null : Wayland.request(viewporter, 1, Wayland.VIEWPORT, 0, null, surface);
		layer = Wayland.request(layerShell, 0, Wayland.LAYER_SURFACE, 0, null, surface, output, LAYER_OVERLAY, "stray-overlay");
		closed = false;
		configuredW = 0;
		configuredH = 0;
		configure = (data, ls, serial, w, h) -> {
			configuredW = w;
			configuredH = h;
			Wayland.request(ls, 6, null, 0, serial);
		};
		closedCb = (data, ls) -> closed = true;
		layerListener = table(configure, closedCb);
		Wayland.call("wl_proxy_add_listener", Integer.class, layer, layerListener, null);
		Wayland.request(layer, 2, null, 0, -1);
		Wayland.request(layer, 4, null, 0, 0);
		placeX = Integer.MIN_VALUE;
		applyPlace(place);
		Wayland.request(surface, 6, null, 0);
		roundtrip();
		if (closed) {
			return "the compositor refused the overlay surface";
		}
		int[] buf = bufferSize(place);
		bufW = buf[0];
		bufH = buf[1];
		eglWindow = (Pointer) NativeLibrary.getInstance("wayland-egl").getFunction("wl_egl_window_create").invoke(Pointer.class, new Object[]{surface, bufW, bufH});
		if (viewport != null) {
			Wayland.request(viewport, 2, null, 0, logicalW(place), logicalH(place));
		}
		eglSurface = egl.eglCreateWindowSurface(eglDisplay, eglConfig, eglWindow, null);
		if (eglSurface == null) {
			return "no EGL surface (" + Integer.toHexString(egl.eglGetError()) + ")";
		}
		return null;
	}

	private void dropSurface() {
		if (eglSurface != null) {
			egl.eglDestroySurface(eglDisplay, eglSurface);
			eglSurface = null;
		}
		if (eglWindow != null) {
			NativeLibrary.getInstance("wayland-egl").getFunction("wl_egl_window_destroy").invoke(Void.class, new Object[]{eglWindow});
			eglWindow = null;
		}
		if (viewport != null) {
			Wayland.request(viewport, 0, null, Wayland.MARSHAL_DESTROY);
			viewport = null;
		}
		if (layer != null) {
			Wayland.request(layer, 7, null, Wayland.MARSHAL_DESTROY);
			layer = null;
		}
		if (surface != null) {
			Wayland.request(surface, 0, null, Wayland.MARSHAL_DESTROY);
			surface = null;
		}
	}

	private int logicalW(HyprlandIpc.Place place) {
		return place != null ? place.w() : Math.max(1, configuredW);
	}

	private int logicalH(HyprlandIpc.Place place) {
		return place != null ? place.h() : Math.max(1, configuredH);
	}

	private double scale(HyprlandIpc.Place place) {
		if (place != null && place.scale() > 0) {
			return viewport != null ? place.scale() : 1.0;
		}
		Integer s = outputScales.get(outputNamed(monitor));
		return viewport != null && s != null ? s : 1.0;
	}

	private int[] bufferSize(HyprlandIpc.Place place) {
		double s = scale(place);
		return new int[]{Math.max(1, (int) Math.round(logicalW(place) * s)), Math.max(1, (int) Math.round(logicalH(place) * s))};
	}

	/** Size and position the layer surface: over the game window, or the whole output. */
	private void applyPlace(HyprlandIpc.Place place) {
		if (place != null) {
			Wayland.request(layer, 1, null, 0, ANCHOR_TOP | ANCHOR_LEFT);
			Wayland.request(layer, 0, null, 0, Math.max(1, place.w()), Math.max(1, place.h()));
			Wayland.request(layer, 3, null, 0, place.y(), 0, 0, place.x());
			placeX = place.x();
			placeY = place.y();
			placeW = place.w();
			placeH = place.h();
		} else {
			Wayland.request(layer, 1, null, 0, ANCHOR_TOP | ANCHOR_LEFT | ANCHOR_BOTTOM | ANCHOR_RIGHT);
			Wayland.request(layer, 0, null, 0, 0, 0);
			Wayland.request(layer, 3, null, 0, 0, 0, 0, 0);
			placeX = Integer.MIN_VALUE + 1;
		}
	}

	@Override
	public boolean sync(boolean wanted) {
		Wayland.call("wl_display_dispatch_queue_pending", Integer.class, display, queue);
		HyprlandIpc.Place place = HyprlandIpc.place();
		String wantMonitor = place == null ? null : place.monitor();
		boolean moved = place == null ? placeX != Integer.MIN_VALUE + 1
			: place.x() != placeX || place.y() != placeY || place.w() != placeW || place.h() != placeH;
		if (closed || (wantMonitor != null && !wantMonitor.equals(monitor))) {
			dropSurface();
			String error = makeSurface();
			if (error != null) {
				throw new IllegalStateException(error);
			}
		} else if (moved || (place == null && bufW != bufferSize(null)[0])) {
			applyPlace(place);
			int[] buf = bufferSize(place);
			bufW = buf[0];
			bufH = buf[1];
			NativeLibrary.getInstance("wayland-egl").getFunction("wl_egl_window_resize").invoke(Void.class, new Object[]{eglWindow, bufW, bufH, 0, 0});
			if (viewport != null) {
				Wayland.request(viewport, 2, null, 0, logicalW(place), logicalH(place));
			}
			Wayland.request(surface, 6, null, 0);
		}
		Wayland.call("wl_display_flush", Integer.class, display);
		shown = wanted && (ANY_FOCUS || GameWindow.active());
		return shown;
	}

	@Override
	public int pixelWidth() {
		return bufW;
	}

	@Override
	public int pixelHeight() {
		return bufH;
	}

	@Override
	public void begin() {
		savedDisplay = egl.eglGetCurrentDisplay();
		savedDraw = egl.eglGetCurrentSurface(0x3059);
		savedRead = egl.eglGetCurrentSurface(0x305A);
		savedContext = egl.eglGetCurrentContext();
		savedApi = egl.eglQueryAPI();
		savedCaps = GameWindow.caps();
		egl.eglBindAPI(0x30A2);
		egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);
		if (caps != null) {
			GL.setCapabilities(caps);
		}
	}

	private void restore() {
		if (savedContext != null && savedDisplay != null) {
			egl.eglMakeCurrent(savedDisplay, savedDraw, savedRead, savedContext);
		} else {
			egl.eglMakeCurrent(eglDisplay, null, null, null);
		}
		egl.eglBindAPI(savedApi);
		GL.setCapabilities(savedCaps);
	}

	@Override
	public void end() {
		egl.eglSwapBuffers(eglDisplay, eglSurface);
		restore();
	}

	@Override
	public void destroy() {
		HyprlandIpc.stop();
		if (egl != null && eglDisplay != null) {
			dropSurface();
			if (eglContext != null) {
				egl.eglDestroyContext(eglDisplay, eglContext);
				eglContext = null;
			}
		}
		if (display != null) {
			Wayland.call("wl_display_flush", Integer.class, display);
		}
	}

	@Override
	public String describe() {
		HyprlandIpc.Place place = HyprlandIpc.place();
		return "Wayland layer surface " + bufW + "x" + bufH + (place == null ? ", whole output" : " on " + place.monitor() + " at " + place.x() + "," + place.y())
			+ (viewport == null ? "" : ", scaled") + (shown ? ", showing" : ", hidden");
	}
}
