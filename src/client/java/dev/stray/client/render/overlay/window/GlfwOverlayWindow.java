package dev.stray.client.render.overlay.window;

//? if <26.3 {
/*import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

// 26.2: a GLFW window sharing the game's GL context.
final class GlfwOverlayWindow implements OverlayWindow {
	private long window;
	private GLCapabilities caps;
	private GLCapabilities gameCaps;
	private long gameContext;
	private int platform;
	private boolean shown;
	private boolean captureExcluded;
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
		long current = GLFW.glfwGetCurrentContext();
		if (current == 0L) {
			return "the game isn't using OpenGL";
		}
		platform = GLFW.glfwGetPlatform();
		boolean wayland = platform == GLFW.GLFW_PLATFORM_WAYLAND;
		boolean windows = platform == GLFW.GLFW_PLATFORM_WIN32;
		if (wayland && !HyprlandIpc.running()) {
			return "on Wayland this only works on Hyprland (it has to place the window)";
		}
		if (wayland) {
			HyprlandIpc.addRules();
		}
		int[] g = gameRect(game);
		GLCapabilities before = GL.getCapabilities();
		GLFW.glfwDefaultWindowHints();
		GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
		GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
		GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
		GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
		GLFW.glfwWindowHint(GLFW.GLFW_TRANSPARENT_FRAMEBUFFER, GLFW.GLFW_TRUE);
		GLFW.glfwWindowHint(GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
		GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_FALSE);
		GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
		GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
		GLFW.glfwWindowHint(GLFW.GLFW_MOUSE_PASSTHROUGH, GLFW.GLFW_TRUE);
		GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
		GLFW.glfwWindowHint(GLFW.GLFW_ALPHA_BITS, 8);
		if (!windows && !wayland) {
			GLFW.glfwWindowHint(GLFW.GLFW_FLOATING, GLFW.GLFW_TRUE);
		}
		try {
			window = GLFW.glfwCreateWindow(Math.max(1, g[2]), Math.max(1, g[3]), HyprlandIpc.TITLE, 0L, game);
			if (window == 0L) {
				return "could not open the window";
			}
			GLFW.glfwMakeContextCurrent(window);
			GLFW.glfwSwapInterval(0);
			caps = GL.createCapabilities();
		} finally {
			GLFW.glfwDefaultWindowHints();
			GLFW.glfwMakeContextCurrent(current);
			GL.setCapabilities(before);
		}
		if (windows) {
			try {
				captureExcluded = Win32Overlay.setup(GLFWNativeWin32.glfwGetWin32Window(window), GLFWNativeWin32.glfwGetWin32Window(game));
			} catch (Throwable ignored) {
			}
		} else if (wayland) {
			HyprlandIpc.follow();
		}
		return null;
	}

	private static int[] gameRect(long game) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer x = stack.mallocInt(1);
			IntBuffer y = stack.mallocInt(1);
			IntBuffer w = stack.mallocInt(1);
			IntBuffer h = stack.mallocInt(1);
			if (GLFW.glfwGetPlatform() != GLFW.GLFW_PLATFORM_WAYLAND) {
				GLFW.glfwGetWindowPos(game, x, y);
			}
			GLFW.glfwGetWindowSize(game, w, h);
			return new int[]{x.get(0), y.get(0), w.get(0), h.get(0)};
		}
	}

	@Override
	public boolean sync(boolean wanted) {
		long game = Minecraft.getInstance().getWindow().handle();
		boolean show = wanted && (ANY_FOCUS || GLFW.glfwGetWindowAttrib(game, GLFW.GLFW_FOCUSED) != 0) && GLFW.glfwGetWindowAttrib(game, GLFW.GLFW_ICONIFIED) == 0;
		if (platform != GLFW.GLFW_PLATFORM_WAYLAND) {
			int[] g = gameRect(game);
			if (g[0] != lastX || g[1] != lastY || g[2] != lastW || g[3] != lastH) {
				lastX = g[0];
				lastY = g[1];
				lastW = g[2];
				lastH = g[3];
				GLFW.glfwSetWindowSize(window, Math.max(1, g[2]), Math.max(1, g[3]));
				GLFW.glfwSetWindowPos(window, g[0], g[1]);
			}
		}
		if (show != shown) {
			shown = show;
			if (show) {
				GLFW.glfwShowWindow(window);
			} else {
				GLFW.glfwHideWindow(window);
			}
		}
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer w = stack.mallocInt(1);
			IntBuffer h = stack.mallocInt(1);
			GLFW.glfwGetFramebufferSize(window, w, h);
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
		gameContext = GLFW.glfwGetCurrentContext();
		gameCaps = GL.getCapabilities();
		GLFW.glfwMakeContextCurrent(window);
		GL.setCapabilities(caps);
	}

	@Override
	public void end() {
		GLFW.glfwSwapBuffers(window);
		GLFW.glfwMakeContextCurrent(gameContext);
		GL.setCapabilities(gameCaps);
	}

	@Override
	public void destroy() {
		HyprlandIpc.stop();
		if (window != 0L) {
			GLFW.glfwDestroyWindow(window);
			window = 0L;
		}
	}

	@Override
	public String describe() {
		return "GLFW " + platform + (captureExcluded ? ", excluded from capture" : "") + (shown ? ", showing" : ", hidden");
	}
}
*///?}
