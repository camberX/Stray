package dev.stray.client.render.overlay.window;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.FunctionProvider;
//? if >=26.3 {
import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.sdl.SDLVideo;
//?} else {
/*import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWayland;*///?}

/** What the overlay needs to know about the game's own window, on SDL (26.3) or GLFW (26.2). */
final class GameWindow {
	private GameWindow() {
	}

	private static long handle() {
		return Minecraft.getInstance().getWindow().handle();
	}

	static boolean wayland() {
		//? if >=26.3 {
		return "wayland".equals(SDLVideo.SDL_GetCurrentVideoDriver());
		//?} else {
		/*return GLFW.glfwGetPlatform() == GLFW.GLFW_PLATFORM_WAYLAND;*///?}
	}

	/** The wl_display the game is connected to (0 when not on Wayland). */
	static long waylandDisplay() {
		//? if >=26.3 {
		return SDLProperties.SDL_GetPointerProperty(SDLVideo.SDL_GetWindowProperties(handle()), SDLVideo.SDL_PROP_WINDOW_WAYLAND_DISPLAY_POINTER, 0L);
		//?} else {
		/*return GLFWNativeWayland.glfwGetWaylandDisplay();*///?}
	}

	/** Focused and not minimized: the overlay only shows then, so it never sits over other apps. */
	static boolean active() {
		//? if >=26.3 {
		long flags = SDLVideo.SDL_GetWindowFlags(handle());
		return (flags & 0x200L) != 0L && (flags & 0x40L) == 0L;
		//?} else {
		/*long h = handle();
		return GLFW.glfwGetWindowAttrib(h, GLFW.GLFW_FOCUSED) != 0 && GLFW.glfwGetWindowAttrib(h, GLFW.GLFW_ICONIFIED) == 0;*///?}
	}

	/**
	 * LWJGL's GL functions. On the OpenGL backend the game has set them up already; on Vulkan
	 * nothing has, so they come from the overlay's own context loader.
	 */
	/** The thread's current GL capabilities, or null (on Vulkan there are none). */
	static org.lwjgl.opengl.GLCapabilities caps() {
		try {
			return GL.getCapabilities();
		} catch (IllegalStateException e) {
			return null;
		}
	}

	static void ensureGl(FunctionProvider fallback) {
		if (GL.getFunctionProvider() == null) {
			GL.create(fallback);
		}
	}
}
