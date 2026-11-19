package dev.stray.client.ui;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.ui.m3.dynamiccolor.DynamicScheme;
import dev.stray.client.ui.m3.dynamiccolor.MaterialDynamicColors;
import dev.stray.client.ui.m3.hct.Hct;
import dev.stray.client.ui.m3.scheme.SchemeExpressive;
import dev.stray.client.ui.m3.scheme.SchemeFidelity;
import dev.stray.client.ui.m3.scheme.SchemeMonochrome;
import dev.stray.client.ui.m3.scheme.SchemeTonalSpot;
import dev.stray.client.ui.m3.scheme.SchemeVibrant;

import java.util.Map;

/**
 * Expressive's colours: Material 3's dynamic colour roles, built from one seed with Google's
 * colour maths (HCT), in the picked scheme style and contrast. Any role can be pinned to a
 * colour of its own; the text on a pinned role is worked out from its tone so it stays readable.
 * Every value is opaque ARGB.
 */
public final class ExColors {
	public static final String[] SCHEMES = {"Tonal", "Vibrant", "Expressive", "Fidelity", "Mono"};
	public static final String[] CONTRASTS = {"Standard", "Medium", "High"};
	/** Roles that can be pinned, and what the theme page calls them. */
	public static final String[] PIN_ROLES = {"primary", "secondary", "tertiary", "background", "cards", "text", "outline"};
	public static final String[] PIN_LABELS = {"Primary", "Secondary", "Tertiary", "Background", "Cards", "Text", "Outline"};
	public static final String[] PIN_HINTS = {
		"Switches, sliders, picked buttons", "Picked tab, chips", "Highlights, badges", "Window", "Cards and rows", "Labels", "Edges, off switches"
	};
	/** Seeds the theme page offers before "Custom". */
	public static final int[] PRESET_SEEDS = {0x6750A4, 0x9FCA2B, 0x2FB5FF, 0xE0457B, 0xFF7A1A, 0x00A88F};

	public static int primary, onPrimary, primaryContainer, onPrimaryContainer;
	public static int secondary, onSecondary, secondaryContainer, onSecondaryContainer;
	public static int tertiary, onTertiary, tertiaryContainer, onTertiaryContainer;
	public static int error, onError, errorContainer, onErrorContainer;
	public static int surface, surfaceDim, surfaceBright;
	public static int surfaceLowest, surfaceLow, surfaceMid, surfaceHigh, surfaceHighest;
	public static int onSurface, onSurfaceVariant, outline, outlineVariant;
	public static int inverseSurface, inverseOnSurface, inversePrimary;

	private static long key = Long.MIN_VALUE;

	static {
		compute(0x6750A4, 0, 0, Map.of());
	}

	private ExColors() {
	}

	/** Forget the cached scheme: the next {@link #refresh} builds it again. */
	public static void invalidate() {
		key = Long.MIN_VALUE;
	}

	/** Rebuilds the roles when the seed, style, contrast or pins changed. */
	public static void refresh() {
		StrayConfig config = StrayConfig.get();
		if (config == null) {
			return;
		}
		Map<String, Integer> pins = config.exPins == null ? Map.of() : config.exPins;
		long k = ((long) (config.exSeed & 0xFFFFFF) << 32) ^ ((long) config.exScheme << 28) ^ ((long) config.exContrast << 26) ^ (pins.hashCode() & 0x3FFFFFFL) ^ ((long) pins.size() << 40);
		if (k == key) {
			return;
		}
		key = k;
		compute(config.exSeed, config.exScheme, config.exContrast, pins);
	}

	/** A scheme for these settings, without touching the live roles (the picker's previews). */
	public static DynamicScheme scheme(int seed, int style, int contrast) {
		Hct hct = Hct.fromInt(0xFF000000 | (seed & 0xFFFFFF));
		double level = contrast <= 0 ? 0.0 : contrast == 1 ? 0.5 : 1.0;
		return switch (style) {
			case 1 -> new SchemeVibrant(hct, true, level);
			case 2 -> new SchemeExpressive(hct, true, level);
			case 3 -> new SchemeFidelity(hct, true, level);
			case 4 -> new SchemeMonochrome(hct, true, level);
			default -> new SchemeTonalSpot(hct, true, level);
		};
	}

	private static void compute(int seed, int style, int contrast, Map<String, Integer> pins) {
		DynamicScheme s = scheme(seed, style, contrast);
		MaterialDynamicColors m = new MaterialDynamicColors();
		primary = m.primary().getArgb(s);
		onPrimary = m.onPrimary().getArgb(s);
		primaryContainer = m.primaryContainer().getArgb(s);
		onPrimaryContainer = m.onPrimaryContainer().getArgb(s);
		secondary = m.secondary().getArgb(s);
		onSecondary = m.onSecondary().getArgb(s);
		secondaryContainer = m.secondaryContainer().getArgb(s);
		onSecondaryContainer = m.onSecondaryContainer().getArgb(s);
		tertiary = m.tertiary().getArgb(s);
		onTertiary = m.onTertiary().getArgb(s);
		tertiaryContainer = m.tertiaryContainer().getArgb(s);
		onTertiaryContainer = m.onTertiaryContainer().getArgb(s);
		error = m.error().getArgb(s);
		onError = m.onError().getArgb(s);
		errorContainer = m.errorContainer().getArgb(s);
		onErrorContainer = m.onErrorContainer().getArgb(s);
		surface = m.surface().getArgb(s);
		surfaceDim = m.surfaceDim().getArgb(s);
		surfaceBright = m.surfaceBright().getArgb(s);
		surfaceLowest = m.surfaceContainerLowest().getArgb(s);
		surfaceLow = m.surfaceContainerLow().getArgb(s);
		surfaceMid = m.surfaceContainer().getArgb(s);
		surfaceHigh = m.surfaceContainerHigh().getArgb(s);
		surfaceHighest = m.surfaceContainerHighest().getArgb(s);
		onSurface = m.onSurface().getArgb(s);
		onSurfaceVariant = m.onSurfaceVariant().getArgb(s);
		outline = m.outline().getArgb(s);
		outlineVariant = m.outlineVariant().getArgb(s);
		inverseSurface = m.inverseSurface().getArgb(s);
		inverseOnSurface = m.inverseOnSurface().getArgb(s);
		inversePrimary = m.inversePrimary().getArgb(s);
		applyPins(pins);
	}

	private static void applyPins(Map<String, Integer> pins) {
		Integer p;
		if ((p = pins.get("primary")) != null) {
			primary = opaque(p);
			onPrimary = on(primary);
			primaryContainer = tone(primary, 30);
			onPrimaryContainer = tone(primary, 90);
		}
		if ((p = pins.get("secondary")) != null) {
			secondaryContainer = opaque(p);
			onSecondaryContainer = on(secondaryContainer);
			secondary = tone(secondaryContainer, 80);
			onSecondary = on(secondary);
		}
		if ((p = pins.get("tertiary")) != null) {
			tertiaryContainer = opaque(p);
			onTertiaryContainer = on(tertiaryContainer);
			tertiary = tone(tertiaryContainer, 80);
			onTertiary = on(tertiary);
		}
		if ((p = pins.get("background")) != null) {
			surface = opaque(p);
			surfaceDim = shift(surface, -2);
			surfaceLowest = shift(surface, -4);
			surfaceLow = shift(surface, 4);
			surfaceMid = shift(surface, 6);
			surfaceBright = shift(surface, 18);
		}
		if ((p = pins.get("cards")) != null) {
			surfaceHigh = opaque(p);
			surfaceHighest = shift(surfaceHigh, 5);
		}
		if ((p = pins.get("text")) != null) {
			onSurface = opaque(p);
			onSurfaceVariant = Theme.withAlpha(Theme.mix(onSurface & 0xFFFFFF, surface & 0xFFFFFF, 0.22f), 255);
		}
		if ((p = pins.get("outline")) != null) {
			outline = opaque(p);
			outlineVariant = Theme.withAlpha(Theme.mix(outline & 0xFFFFFF, surface & 0xFFFFFF, 0.55f), 255);
		}
	}

	private static int opaque(int rgb) {
		return 0xFF000000 | (rgb & 0xFFFFFF);
	}

	/** The same hue and chroma at another tone. */
	private static int tone(int argb, double tone) {
		Hct h = Hct.fromInt(argb);
		return Hct.from(h.getHue(), h.getChroma(), tone).toInt();
	}

	/** A tone step lighter (positive) or darker, for surfaces next to a pinned one. */
	private static int shift(int argb, double by) {
		Hct h = Hct.fromInt(argb);
		return Hct.from(h.getHue(), h.getChroma(), Math.max(0, Math.min(100, h.getTone() + by))).toInt();
	}

	/** Readable text on this colour: a dark tone of its hue on light colours, a light one on dark. */
	private static int on(int argb) {
		Hct h = Hct.fromInt(argb);
		return Hct.from(h.getHue(), Math.min(h.getChroma(), 24), h.getTone() > 58 ? 14 : 96).toInt();
	}

	public static int alpha(int argb, float a) {
		return Theme.withAlpha(argb, Math.round(Math.max(0f, Math.min(1f, a)) * 255f));
	}

	/** Surface opacity from the setting (0.3 to 1). */
	public static float opacity() {
		StrayConfig config = StrayConfig.get();
		return config == null ? 0.94f : StrayConfig.clamp(config.exOpacity, 0.3f, 1f);
	}
}
