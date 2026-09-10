package dev.stray.client.visual.motionblur;

import net.minecraft.client.Minecraft;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

public final class MonitorInfoProvider {
	private static final long CHECK_INTERVAL_NS = 1_000_000_000L;
	private static long lastMonitorHandle;
	private static int lastRefreshRate = 60;
	private static long lastCheckTime;

	private MonitorInfoProvider() {
	}

	public static void updateDisplayInfo() {
		long now = System.nanoTime();
		if (now - lastCheckTime < CHECK_INTERVAL_NS) {
			return;
		}
		lastCheckTime = now;
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.getWindow() == null) {
			return;
		}
		long window = client.getWindow().handle();
		long monitor = GLFW.glfwGetWindowMonitor(window);
		if (monitor == 0) {
			monitor = monitorFromWindow(window, client.getWindow().getScreenWidth(), client.getWindow().getScreenHeight());
		}
		if (monitor != lastMonitorHandle) {
			lastRefreshRate = detectRefreshRate(monitor);
			lastMonitorHandle = monitor;
		}
	}

	public static int getRefreshRate() {
		return lastRefreshRate;
	}

	private static long monitorFromWindow(long window, int windowWidth, int windowHeight) {
		int[] winX = new int[1];
		int[] winY = new int[1];
		GLFW.glfwGetWindowPos(window, winX, winY);
		int centerX = winX[0] + windowWidth / 2;
		int centerY = winY[0] + windowHeight / 2;
		long result = GLFW.glfwGetPrimaryMonitor();
		PointerBuffer monitors = GLFW.glfwGetMonitors();
		if (monitors == null) {
			return result;
		}
		for (int i = 0; i < monitors.limit(); i++) {
			long candidate = monitors.get(i);
			int[] mx = new int[1];
			int[] my = new int[1];
			GLFW.glfwGetMonitorPos(candidate, mx, my);
			GLFWVidMode mode = GLFW.glfwGetVideoMode(candidate);
			if (mode == null) {
				continue;
			}
			if (centerX >= mx[0] && centerX < mx[0] + mode.width() && centerY >= my[0] && centerY < my[0] + mode.height()) {
				return candidate;
			}
		}
		return result;
	}

	private static int detectRefreshRate(long monitor) {
		GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
		return mode != null ? mode.refreshRate() : 60;
	}
}
