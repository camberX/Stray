package dev.stray.client.visual.motionblur;

public final class FrameTimer {
	private long lastNano;
	private float currentFps;

	public void beginFrame() {
		long now = System.nanoTime();
		float delta = (now - lastNano) / 1_000_000_000.0f;
		lastNano = now;
		currentFps = delta > 0 && delta < 1.0f ? 1.0f / delta : 0.0f;
		MonitorInfoProvider.updateDisplayInfo();
	}

	public float getFps() {
		return currentFps;
	}

	public int getRefreshRate() {
		return MonitorInfoProvider.getRefreshRate();
	}
}
