package dev.stray.client.visual.motionblur;

import net.minecraft.client.Minecraft;

public final class MonitorInfoProvider {
	private static final long CHECK_INTERVAL_NS = 1_000_000_000L;
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
		if (client == null || client.getWindow() == null || client.getWindow().getActiveVideoMode() == null) {
			return;
		}
		lastRefreshRate = Math.max(1, Math.round(client.getWindow().getActiveVideoMode().getRefreshRate()));
	}

	public static int getRefreshRate() {
		return lastRefreshRate;
	}
}
