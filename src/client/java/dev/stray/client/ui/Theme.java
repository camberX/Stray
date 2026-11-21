package dev.stray.client.ui;

import dev.stray.client.config.StrayConfig;

public final class Theme {
	public static int ACCENT = 0xFF2FB5FF;
	public static int ACCENT_DIM = 0xFF1A6FA8;
	public static int WINDOW = 0xFF0B0E14;
	public static int WINDOW_SOLID = 0xFF0B0E14;
	public static int SHEET = 0xEB0B0E14;
	public static int SIDEBAR = 0xB3142032;
	public static int NAV_PILL = 0xE01E5F8C;
	public static int CARD = 0xFF12151C;
	public static int CARD_HOVER = 0xFF171B24;
	public static int LINE = 0xFF1C2430;
	public static int TRACK = 0xFF1A222C;
	public static int PANEL = 0xFF0B1118;
	public static int HUD_WINDOW = 0xFF0B0E14;
	public static int HUD_LINE = 0xFF1C2430;
	/** HUD line before accent outlines recolor it. */
	public static int HUD_LINE_NEUTRAL = 0xFF1C2430;
	public static int HUD_TRACK = 0xFF1A222C;
	public static int HUD_CARD = 0xFF12151C;
	public static int HUD_CARD_HOVER = 0xFF171B24;
	public static int HUD_PANEL = 0xFF0B1118;
	public static int TEXT = 0xFFF2F4F7;
	public static int MUTED = 0xFF8A9AAB;
	public static int HEADER = 0xFFC4CED8;
	public static int OFF = 0xFF3D4A58;
	public static int DANGER = 0xFFE8B86D;
	public static int WARN = 0xFFF5C16C;
	public static float WINDOW_RADIUS = ShadowLook.WINDOW_R;
	public static float PILL_RADIUS = 8f;

	public static final Swatch[] PRESETS = {
		new Swatch("Cyan", 0x2FB5FF),
		new Swatch("Blue", 0x4D8DFF),
		new Swatch("Purple", 0xA78BFA),
		new Swatch("Pink", 0xF472B6),
		new Swatch("Red", 0xFB7185),
		new Swatch("Orange", 0xFB923C),
		new Swatch("Green", 0x34D399),
		new Swatch("White", 0xE5E7EB)
	};

	public static final Swatch[] PANE_PRESETS = {
		new Swatch("Night", 0x0B0E14),
		new Swatch("Slate", 0x151922),
		new Swatch("Navy", 0x0E1828),
		new Swatch("Steel", 0x1C222C),
		new Swatch("Olive", 0x14180F),
		new Swatch("Wine", 0x1C1418),
		new Swatch("Black", 0x0A0A0C),
		new Swatch("Graphite", 0x22262E)
	};

	private Theme() {
	}

	public static void refresh() {
		if (Looks.ex()) {
			ExColors.refresh();
		}
		ShadowLook.look();
		int accent = StrayConfig.get().themeAccentRgb & 0xFFFFFF;
		int pane = StrayConfig.get().themePaneRgb & 0xFFFFFF;
		if (pane == 0) {
			pane = 0x0B0E14;
		}

		float opacity = StrayConfig.clamp(StrayConfig.get().themePaneOpacity, 0.20f, 1f);
		int paneA = Math.round(opacity * 255);
		float hudOpacity = StrayConfig.clamp(StrayConfig.get().hudOpacity, 0.20f, 1f);
		int hudA = Math.round(hudOpacity * 255);

		ACCENT = 0xFF000000 | accent;
		ACCENT_DIM = 0xFF000000 | mix(accent, pane, 0.42f);
		if (StrayConfig.get().guiDesignControl()) {
			int glass = ControlChrome.paneRgb();
			int pill = ControlChrome.pillRgb();
			WINDOW_SOLID = 0xFF000000 | glass;
			WINDOW = ControlChrome.windowFill();
			SHEET = Theme.withAlpha(glass, 210);
			CARD = ControlChrome.cardFill();
			CARD_HOVER = Theme.withAlpha(pill, Math.min(255, ((ControlChrome.cardFill() >>> 24) & 0xFF) + 28));
			LINE = Theme.withAlpha(0xFFFFFF, ControlChrome.darkText() ? 48 : 32);
			TRACK = ControlChrome.TRACK;
			PANEL = ControlChrome.searchFill();
			HUD_WINDOW = ControlChrome.hudFill();
			HUD_LINE = LINE;
			HUD_LINE_NEUTRAL = HUD_LINE;
			HUD_TRACK = TRACK;
			HUD_CARD = CARD;
			HUD_CARD_HOVER = CARD_HOVER;
			HUD_PANEL = PANEL;
			OFF = Theme.withAlpha(mix(pill, 0x000000, 0.25f), 160);
			TEXT = ControlChrome.text();
			MUTED = ControlChrome.muted();
			HEADER = TEXT;
			NAV_PILL = ControlChrome.selectedFill();
			SIDEBAR = ControlChrome.railFill();
			shadow(hudOpacity, opacity);
			look();
			applyAccentOutlines(ACCENT & 0xFFFFFF);
			return;
		}
		TEXT = 0xFFF2F4F7;
		MUTED = 0xFF8A9AAB;
		WINDOW_SOLID = 0xFF000000 | pane;
		WINDOW = withAlpha(pane, paneA);
		SHEET = withAlpha(pane, Math.max(paneA, 232));
		CARD = withAlpha(mix(pane, 0xFFFFFF, 0.055f), paneA);
		CARD_HOVER = withAlpha(mix(pane, 0xFFFFFF, 0.10f), paneA);
		LINE = withAlpha(mix(pane, 0xFFFFFF, 0.14f), Math.max(paneA, 90));
		TRACK = withAlpha(mix(pane, 0xFFFFFF, 0.08f), paneA);
		PANEL = withAlpha(mix(pane, 0x000000, 0.18f), paneA);
		HUD_WINDOW = withAlpha(pane, hudA);
		HUD_LINE = withAlpha(mix(pane, 0xFFFFFF, 0.14f), Math.max(hudA, 90));
		HUD_LINE_NEUTRAL = HUD_LINE;
		HUD_TRACK = withAlpha(mix(pane, 0xFFFFFF, 0.08f), hudA);
		HUD_CARD = withAlpha(mix(pane, 0xFFFFFF, 0.055f), hudA);
		HUD_CARD_HOVER = withAlpha(mix(pane, 0xFFFFFF, 0.10f), hudA);
		HUD_PANEL = withAlpha(mix(pane, 0x000000, 0.18f), hudA);
		OFF = withAlpha(mix(pane, 0xFFFFFF, 0.22f), paneA);
		HEADER = 0xFF000000 | mix(0xC4CED8, pane, 0.10f);
		NAV_PILL = withAlpha(mix(pane, accent, 0.55f), Math.round(230 * opacity));
		SIDEBAR = withAlpha(mix(0x101820, pane, 0.45f), Math.round(185 * opacity));
		shadow(hudOpacity, opacity);
		look();
		applyAccentOutlines(ACCENT & 0xFFFFFF);
	}

	/** uid? and Expressive repaint every shared colour after Shadow's: the whole UI follows the look. */
	private static void look() {
		WINDOW_RADIUS = ShadowLook.WINDOW_R;
		if (Looks.uid()) {
			int accent = 0xFF000000 | (StrayConfig.get().uidAccent & 0xFFFFFF);
			ACCENT = accent;
			ACCENT_DIM = withAlpha(accent, 0x66);
			WINDOW = 0xF5171717;
			WINDOW_SOLID = 0xFF171717;
			SHEET = 0xFF282828;
			CARD = 0xFF1F1F1F;
			CARD_HOVER = 0xFF292929;
			LINE = UidLook.EDGE;
			TRACK = 0xFF323232;
			PANEL = 0xFF232323;
			HUD_WINDOW = 0xF0171717;
			HUD_LINE = UidLook.EDGE;
			HUD_LINE_NEUTRAL = UidLook.EDGE;
			HUD_TRACK = 0xFF323232;
			HUD_CARD = 0xFF1F1F1F;
			HUD_CARD_HOVER = 0xFF292929;
			HUD_PANEL = 0xFF232323;
			TEXT = UidLook.LABEL;
			MUTED = UidLook.VALUE;
			HEADER = UidLook.LABEL;
			OFF = 0xFF646464;
			NAV_PILL = 0xFF282828;
			SIDEBAR = 0xFF0C0C0C;
			WINDOW_RADIUS = 0f;
		} else if (Looks.ex()) {
			ExColors.refresh();
			float op = ExColors.opacity();
			ACCENT = ExColors.primary;
			ACCENT_DIM = ExColors.primaryContainer;
			WINDOW = ExColors.alpha(ExColors.surface, op);
			WINDOW_SOLID = ExColors.surface;
			SHEET = ExColors.alpha(ExColors.surfaceMid, Math.max(op, 0.96f));
			CARD = ExColors.surfaceHigh;
			CARD_HOVER = ExColors.surfaceHighest;
			LINE = ExColors.outlineVariant;
			TRACK = ExColors.surfaceHighest;
			PANEL = ExColors.surfaceLow;
			HUD_WINDOW = ExColors.alpha(ExColors.surfaceMid, op);
			HUD_LINE = ExColors.outlineVariant;
			HUD_LINE_NEUTRAL = ExColors.outlineVariant;
			HUD_TRACK = ExColors.surfaceHighest;
			HUD_CARD = ExColors.surfaceHigh;
			HUD_CARD_HOVER = ExColors.surfaceHighest;
			HUD_PANEL = ExColors.surfaceLow;
			TEXT = ExColors.onSurface;
			MUTED = ExColors.onSurfaceVariant;
			HEADER = ExColors.onSurface;
			OFF = ExColors.outline;
			NAV_PILL = ExColors.secondaryContainer;
			SIDEBAR = ExColors.surfaceLow;
			DANGER = ExColors.tertiary;
			WINDOW_RADIUS = 20f;
		} else if (Looks.minecraft()) {
			// Minecraft: the chat box's black, square corners, the game's formatting colours.
			ACCENT = MinecraftLook.YELLOW;
			ACCENT_DIM = withAlpha(0xFFFF55, 0x66);
			WINDOW = MinecraftLook.BG;
			WINDOW_SOLID = 0xFF000000;
			SHEET = 0xE0000000;
			CARD = 0x18FFFFFF;
			CARD_HOVER = 0x2CFFFFFF;
			LINE = MinecraftLook.LINE;
			TRACK = 0xFF2B2B2B;
			PANEL = 0x60000000;
			HUD_WINDOW = withAlpha(0x000000, Math.round(StrayConfig.clamp(StrayConfig.get().hudOpacity, 0.20f, 1f) * 0x8C));
			HUD_LINE = MinecraftLook.LINE;
			HUD_LINE_NEUTRAL = MinecraftLook.LINE;
			HUD_TRACK = 0xFF2B2B2B;
			HUD_CARD = 0x18FFFFFF;
			HUD_CARD_HOVER = 0x2CFFFFFF;
			HUD_PANEL = 0x60000000;
			TEXT = MinecraftLook.WHITE;
			MUTED = MinecraftLook.GREY;
			HEADER = MinecraftLook.YELLOW;
			OFF = MinecraftLook.DGREY;
			NAV_PILL = 0x40FFFFFF;
			SIDEBAR = 0x60000000;
			WINDOW_RADIUS = 0f;
		} else if (Looks.panels()) {
			// Panels: pure neutral greys (no tint), white for what is on, the theme accent.
			int accent = PanelLook.accent();
			int hudA = Math.round(StrayConfig.clamp(StrayConfig.get().hudOpacity, 0.20f, 1f) * 255);
			ACCENT = accent;
			ACCENT_DIM = withAlpha(accent, 0x66);
			WINDOW = 0xF51C1C1C;
			WINDOW_SOLID = PanelLook.G1;
			SHEET = PanelLook.G2;
			CARD = PanelLook.G2;
			CARD_HOVER = PanelLook.G3;
			LINE = PanelLook.EDGE;
			TRACK = PanelLook.G3;
			PANEL = PanelLook.G0;
			HUD_WINDOW = withAlpha(0x1C1C1C, hudA);
			HUD_LINE = PanelLook.EDGE;
			HUD_LINE_NEUTRAL = PanelLook.EDGE;
			HUD_TRACK = PanelLook.G3;
			HUD_CARD = PanelLook.G2;
			HUD_CARD_HOVER = PanelLook.G3;
			HUD_PANEL = PanelLook.G0;
			TEXT = PanelLook.WHITE;
			MUTED = PanelLook.GREY;
			HEADER = PanelLook.WHITE;
			OFF = PanelLook.DGREY;
			NAV_PILL = PanelLook.G3;
			SIDEBAR = PanelLook.G0;
			WINDOW_RADIUS = PanelLook.RADIUS;
		}
		ShadowLook.look();
	}

	/** Shadow style: see-through black, faint white lines, white and gray text. */
	private static void shadow(float hudOpacity, float opacity) {
		ShadowLook.refresh();
		HUD_WINDOW = ShadowLook.FILL;
		HUD_LINE = ShadowLook.LINE;
		HUD_LINE_NEUTRAL = HUD_LINE;
		HUD_TRACK = 0x14FFFFFF;
		HUD_CARD = ShadowLook.CARD;
		HUD_CARD_HOVER = ShadowLook.CARD_HOVER;
		HUD_PANEL = 0x10000000;
		WINDOW = ShadowLook.FILL;
		SHEET = ShadowLook.fill(Math.max(opacity, 0.92f));
		CARD = ShadowLook.CARD;
		CARD_HOVER = ShadowLook.CARD_HOVER;
		LINE = ShadowLook.LINE;
		TRACK = 0x14FFFFFF;
		PANEL = 0x10000000;
		OFF = 0xFF3A424C;
		TEXT = ShadowLook.TEXT;
		MUTED = ShadowLook.MUTED;
		HEADER = ShadowLook.TEXT;
		NAV_PILL = ShadowLook.SELECTED;
		SIDEBAR = 0x0AFFFFFF;
	}

	private static void applyAccentOutlines(int accent) {
		if (!StrayConfig.get().accentOutlines) {
			return;
		}
		int lineA = Math.max((LINE >>> 24) & 0xFF, 150);
		int hudA = Math.max((HUD_LINE >>> 24) & 0xFF, 150);
		LINE = withAlpha(accent, lineA);
		HUD_LINE = withAlpha(accent, hudA);
	}

	public static void applyPreset(Swatch swatch) {
		StrayConfig config = StrayConfig.get();
		config.themeAccentRgb = swatch.rgb;
		config.themePreset = swatch.name.toLowerCase();
		refresh();
	}

	public static void applyCustom(int rgb) {
		StrayConfig config = StrayConfig.get();
		config.themeAccentRgb = rgb & 0xFFFFFF;
		config.themePreset = "custom";
		refresh();
	}

	public static void applyPane(int rgb) {
		StrayConfig config = StrayConfig.get();
		config.themePaneRgb = rgb & 0xFFFFFF;
		if (config.themePaneRgb == 0) {
			config.themePaneRgb = 0x0B0E14;
		}
		refresh();
	}

	public static void applyPanePreset(Swatch swatch) {
		applyPane(swatch.rgb);
	}

	public static int withAlpha(int color, int alpha) {
		return (alpha << 24) | (color & 0x00FFFFFF);
	}

	public static int mix(int fromRgb, int toRgb, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int r = lerp((fromRgb >> 16) & 0xFF, (toRgb >> 16) & 0xFF, t);
		int g = lerp((fromRgb >> 8) & 0xFF, (toRgb >> 8) & 0xFF, t);
		int b = lerp(fromRgb & 0xFF, toRgb & 0xFF, t);
		return (r << 16) | (g << 8) | b;
	}

	private static int lerp(int from, int to, float t) {
		return Math.round(from + (to - from) * t);
	}

	public record Swatch(String name, int rgb) {
	}
}
