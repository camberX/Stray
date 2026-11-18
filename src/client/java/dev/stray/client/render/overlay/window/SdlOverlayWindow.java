package dev.stray.client.render.overlay.window;

//? if >=26.3 {
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.sdl.SDLSurface;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_Surface;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

// 26.3: an SDL3 window with its own GL context, shared with the game's.
final class SdlOverlayWindow implements OverlayWindow {
	private static final long OPENGL = 0x2L;
	private static final long HIDDEN = 0x8L;
	private static final long BORDERLESS = 0x10L;
	private static final long INPUT_FOCUS = 0x200L;
	private static final long MINIMIZED = 0x40L;
	private static final long HIGH_PIXEL_DENSITY = 0x2000L;
	private static final long ALWAYS_ON_TOP = 0x10000L;
	private static final long UTILITY = 0x20000L;
	private static final long TRANSPARENT = 0x40000000L;
	private static final long NOT_FOCUSABLE = 0x80000000L;
	private static final int GL_ALPHA_SIZE = 3;
	private static final int GL_SHARE_WITH_CURRENT_CONTEXT = 21;
	private static final int ARGB8888 = 0x16362004;

	static volatile long handle;
	private long window;
	private long context;
	private GLCapabilities caps;
	private GLCapabilities gameCaps;
	private long gameWindow;
	private long gameContext;
	private String driver = "";
	private boolean shown;
	private boolean captureExcluded;
	private boolean clickThrough;
	private int lastX = Integer.MIN_VALUE;
	private int lastY;
	private int lastW;
	private int lastH;
	// Testing without a window manager (nothing hands focus back): -Dstray.overlay.anyFocus=true.
	private static final boolean ANY_FOCUS = Boolean.getBoolean("stray.overlay.anyFocus");
	private int pixelW = 1;
	private int pixelH = 1;

	@Override
	public String create() {
		Minecraft client = Minecraft.getInstance();
		long game = client.getWindow().handle();
		long curWindow = SDLVideo.SDL_GL_GetCurrentWindow();
		long curContext = SDLVideo.SDL_GL_GetCurrentContext();
		if (curContext == 0L) {
			return "the game isn't using OpenGL (switch the graphics backend to OpenGL)";
		}
		String d = SDLVideo.SDL_GetCurrentVideoDriver();
		driver = d == null ? "" : d;
		boolean wayland = "wayland".equals(driver);
		boolean windows = "windows".equals(driver);
		if (wayland && !HyprlandIpc.running()) {
			return "on Wayland this only works on Hyprland (it has to place the window)";
		}
		GLCapabilities before = GL.getCapabilities();
		int alpha;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer buf = stack.mallocInt(1);
			SDLVideo.SDL_GL_GetAttribute(GL_ALPHA_SIZE, buf);
			alpha = buf.get(0);
		}
		int[] size = gameSize(game);
		if (wayland) {
			HyprlandIpc.addRules();
		}
		long flags = OPENGL | HIDDEN | BORDERLESS | TRANSPARENT | UTILITY | NOT_FOCUSABLE | HIGH_PIXEL_DENSITY;
		if (!windows && !wayland) {
			flags |= ALWAYS_ON_TOP;
		}
		SDLVideo.SDL_GL_SetAttribute(GL_ALPHA_SIZE, 8);
		SDLVideo.SDL_GL_SetAttribute(GL_SHARE_WITH_CURRENT_CONTEXT, 1);
		try {
			window = SDLVideo.SDL_CreateWindow(HyprlandIpc.TITLE, Math.max(1, size[2]), Math.max(1, size[3]), flags);
			if (window == 0L) {
				return "could not open the window (" + org.lwjgl.sdl.SDLError.SDL_GetError() + ")";
			}
			context = SDLVideo.SDL_GL_CreateContext(window);
			if (context == 0L) {
				String error = org.lwjgl.sdl.SDLError.SDL_GetError();
				SDLVideo.SDL_DestroyWindow(window);
				window = 0L;
				return "no GL context for it (" + error + ")";
			}
			SDLVideo.SDL_GL_MakeCurrent(window, context);
			SDLVideo.SDL_GL_SetSwapInterval(0);
			caps = GL.createCapabilities();
		} finally {
			SDLVideo.SDL_GL_SetAttribute(GL_ALPHA_SIZE, alpha);
			SDLVideo.SDL_GL_SetAttribute(GL_SHARE_WITH_CURRENT_CONTEXT, 0);
			SDLVideo.SDL_GL_MakeCurrent(curWindow, curContext);
			GL.setCapabilities(before);
		}
		int props = SDLVideo.SDL_GetWindowProperties(window);
		if (windows) {
			long hwnd = SDLProperties.SDL_GetPointerProperty(props, SDLVideo.SDL_PROP_WINDOW_WIN32_HWND_POINTER, 0L);
			long gameHwnd = SDLProperties.SDL_GetPointerProperty(SDLVideo.SDL_GetWindowProperties(game), SDLVideo.SDL_PROP_WINDOW_WIN32_HWND_POINTER, 0L);
			try {
				captureExcluded = hwnd != 0L && Win32Overlay.setup(hwnd, gameHwnd);
				clickThrough = hwnd != 0L;
			} catch (Throwable t) {
				clickThrough = false;
			}
		} else if (wayland) {
			long display = SDLProperties.SDL_GetPointerProperty(props, SDLVideo.SDL_PROP_WINDOW_WAYLAND_DISPLAY_POINTER, 0L);
			long surface = SDLProperties.SDL_GetPointerProperty(props, SDLVideo.SDL_PROP_WINDOW_WAYLAND_SURFACE_POINTER, 0L);
			clickThrough = display != 0L && surface != 0L && WaylandInput.clickThrough(display, surface);
			HyprlandIpc.follow();
		} else {
			SDLVideo.SDL_SetWindowParent(window, game);
			SDL_Surface empty = SDLSurface.SDL_CreateSurface(1, 1, ARGB8888);
			if (empty != null) {
				clickThrough = SDLVideo.SDL_SetWindowShape(window, empty);
				SDLSurface.SDL_DestroySurface(empty);
			}
		}
		handle = window;
		if (!clickThrough) {
			destroy();
			return "could not make the window click-through on " + driver;
		}
		return null;
	}

	private static int[] gameSize(long game) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer x = stack.mallocInt(1);
			IntBuffer y = stack.mallocInt(1);
			IntBuffer w = stack.mallocInt(1);
			IntBuffer h = stack.mallocInt(1);
			SDLVideo.SDL_GetWindowPosition(game, x, y);
			SDLVideo.SDL_GetWindowSize(game, w, h);
			return new int[]{x.get(0), y.get(0), w.get(0), h.get(0)};
		}
	}

	@Override
	public boolean sync(boolean wanted) {
		long game = Minecraft.getInstance().getWindow().handle();
		long gameFlags = SDLVideo.SDL_GetWindowFlags(game);
		boolean show = wanted && (ANY_FOCUS || (gameFlags & INPUT_FOCUS) != 0L) && (gameFlags & MINIMIZED) == 0L;
		if (!"wayland".equals(driver)) {
			int[] g = gameSize(game);
			if (g[0] != lastX || g[1] != lastY || g[2] != lastW || g[3] != lastH) {
				lastX = g[0];
				lastY = g[1];
				lastW = g[2];
				lastH = g[3];
				SDLVideo.SDL_SetWindowSize(window, Math.max(1, g[2]), Math.max(1, g[3]));
				SDLVideo.SDL_SetWindowPosition(window, g[0], g[1]);
			}
		}
		if (show != shown) {
			shown = show;
			if (show) {
				SDLVideo.SDL_ShowWindow(window);
			} else {
				SDLVideo.SDL_HideWindow(window);
			}
		}
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer w = stack.mallocInt(1);
			IntBuffer h = stack.mallocInt(1);
			SDLVideo.SDL_GetWindowSizeInPixels(window, w, h);
			pixelW = Math.max(1, w.get(0));
			pixelH = Math.max(1, h.get(0));
		}
		return show;
	}

	@Override
	public int pixelWidth() {
		return pixelW;
	}

	@Override
	public int pixelHeight() {
		return pixelH;
	}

	@Override
	public void begin() {
		gameWindow = SDLVideo.SDL_GL_GetCurrentWindow();
		gameContext = SDLVideo.SDL_GL_GetCurrentContext();
		gameCaps = GL.getCapabilities();
		SDLVideo.SDL_GL_MakeCurrent(window, context);
		GL.setCapabilities(caps);
	}

	@Override
	public void end() {
		SDLVideo.SDL_GL_SwapWindow(window);
		SDLVideo.SDL_GL_MakeCurrent(gameWindow, gameContext);
		GL.setCapabilities(gameCaps);
	}

	@Override
	public void destroy() {
		HyprlandIpc.stop();
		if (context != 0L) {
			SDLVideo.SDL_GL_DestroyContext(context);
			context = 0L;
		}
		if (window != 0L) {
			SDLVideo.SDL_DestroyWindow(window);
			window = 0L;
		}
		handle = 0L;
	}

	@Override
	public String describe() {
		return "SDL " + driver + (captureExcluded ? ", excluded from capture" : "") + (shown ? ", showing" : ", hidden");
	}
}
//?}
