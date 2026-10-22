package dev.stray.client.ui;

/**
 * Material You color roles for the Hyprland look (click GUI, themed GUIs,
 * watermark), built from the Stray accent the way matugen builds the
 * illogical-impulse palette from a wallpaper: the accent is primary, and the
 * surfaces are near-black tinted toward it. Changing the accent recolors all of it.
 */
public final class MaterialPalette {
	public static int surface;
	public static int surfaceLow;
	public static int surfaceContainer;
	public static int surfaceHigh;
	public static int surfaceHighest;
	public static int onSurface;
	public static int onSurfaceVariant;
	public static int outline;
	public static int outlineVariant;
	public static int primary;
	public static int onPrimary;
	public static int secondaryContainer;
	public static int onSecondaryContainer;

	private static int seed = -1;

	private MaterialPalette() {
	}

	public static void refresh() {
		int accent = Theme.ACCENT & 0xFFFFFF;
		if (accent == seed) {
			return;
		}
		seed = accent;
		surface = tint(0x121212, accent, 0.05f);
		surfaceLow = tint(0x1A1A1A, accent, 0.06f);
		surfaceContainer = tint(0x1F1F1F, accent, 0.07f);
		surfaceHigh = tint(0x292929, accent, 0.08f);
		surfaceHighest = tint(0x343434, accent, 0.09f);
		onSurface = tint(0xE6E6E6, accent, 0.05f);
		onSurfaceVariant = tint(0xC4C4C4, accent, 0.10f);
		outline = tint(0x8E8E8E, accent, 0.12f);
		outlineVariant = tint(0x464646, accent, 0.10f);
		primary = 0xFF000000 | accent;
		onPrimary = tint(0x0E0E0E, accent, 0.20f);
		secondaryContainer = tint(0x2B2B2B, accent, 0.30f);
		onSecondaryContainer = tint(0xEDEDED, accent, 0.10f);
	}

	public static int withAlpha(int color, int alpha) {
		return (alpha << 24) | (color & 0x00FFFFFF);
	}

	private static int tint(int base, int rgb, float t) {
		return 0xFF000000 | (Theme.mix(base, rgb, t) & 0xFFFFFF);
	}
}
