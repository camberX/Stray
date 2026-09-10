package dev.stray.client.config;

public final class UnloadState {
	private static boolean unloaded;
	private static boolean worldTint;
	private static boolean skyTint;
	private static boolean fog;
	private static boolean aspect;
	private static boolean motionBlur;
	private static boolean markers;

	private UnloadState() {
	}

	public static boolean isUnloaded() {
		return unloaded;
	}

	public static void toggle() {
		if (unloaded) {
			restore();
		} else {
			unload();
		}
	}

	public static void unload() {
		StrayConfig config = StrayConfig.get();
		worldTint = config.worldTintEnabled;
		skyTint = config.skyTintEnabled;
		fog = config.fogEnabled;
		aspect = config.aspectEnabled;
		motionBlur = config.motionBlurEnabled;
		markers = config.markersEnabled;
		config.worldTintEnabled = false;
		config.skyTintEnabled = false;
		config.fogEnabled = false;
		config.aspectEnabled = false;
		config.motionBlurEnabled = false;
		config.markersEnabled = false;
		unloaded = true;
		config.save();
	}

	public static void restore() {
		StrayConfig config = StrayConfig.get();
		config.worldTintEnabled = worldTint;
		config.skyTintEnabled = skyTint;
		config.fogEnabled = fog;
		config.aspectEnabled = aspect;
		config.motionBlurEnabled = motionBlur;
		config.markersEnabled = markers;
		unloaded = false;
		config.save();
	}

	public static void markDirty() {
		unloaded = false;
	}
}
