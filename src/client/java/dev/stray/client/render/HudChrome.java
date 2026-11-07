package dev.stray.client.render;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class HudChrome {
	private static final float STROKE = 0.5f;

	private static boolean hudPass;
	/** This draw skips accent strokes. Inventory HUD minimal uses it. */
	private static boolean neutralOutline;

	private HudChrome() {
	}

	public static void beginHud() {
		hudPass = true;
		element = null;
	}

	public static void endHud() {
		hudPass = false;
		element = null;
	}

	/** The HUD element being drawn, so its background toggle applies. */
	private static HudLayout.Id element;

	public static void element(HudLayout.Id id) {
		element = id;
	}

	/** False when the element being drawn has its background turned off. */
	public static boolean background() {
		return element == null || HudLayout.background(element);
	}

	/** HUD text drawn in the Shadow style (Inter, no shadow): true while the HUD draws. */
	public static boolean shadowInk() {
		return hudPass;
	}

	public static void neutralOutline(boolean neutral) {
		neutralOutline = neutral;
	}

	/** Black, or the accent color when accent outlines are on. */
	public static int outline() {
		if (neutralOutline) {
			return 0xFF000000;
		}
		return StrayConfig.get().accentOutlines ? Theme.ACCENT : 0xFF000000;
	}

	private static int line() {
		return neutralOutline ? Theme.HUD_LINE_NEUTRAL : Theme.HUD_LINE;
	}

	public static void panel(
		GuiGraphicsExtractor graphics,
		float x,
		float y,
		float w,
		float h,
		float radius,
		int fill,
		int outline,
		int accent
	) {
		if (!background()) {
			return;
		}
		dev.stray.client.ui.ShadowLook.panel(graphics, x, y, w, h, dev.stray.client.ui.ShadowLook.HUD_R, Theme.HUD_WINDOW);
	}

	public static void panel(
		GuiGraphicsExtractor graphics,
		float x,
		float y,
		float w,
		float h,
		float radius,
		int fill,
		int outline
	) {
		panel(graphics, x, y, w, h, radius, fill, outline, 0);
	}

	/** Sharp transparent-black box with a half-pixel outline, matching the click GUI. */
	public static void box(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int fill) {
		if (w < 1f || h < 1f) {
			return;
		}
		int outline = outline();
		GuiDraw.fillSmooth(graphics, x, y, w, STROKE, outline);
		GuiDraw.fillSmooth(graphics, x, y + h - STROKE, w, STROKE, outline);
		GuiDraw.fillSmooth(graphics, x, y, STROKE, h, outline);
		GuiDraw.fillSmooth(graphics, x + w - STROKE, y, STROKE, h, outline);
		GuiDraw.fillSmooth(graphics, x + STROKE, y + STROKE, w - STROKE * 2f, h - STROKE * 2f, fill);
	}

	/** Progress bars and chips. */
	public static void rounded(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, int color) {
		GuiDraw.rounded(graphics, x, y, w, h, radius, color);
	}

	public static void slot(GuiGraphicsExtractor graphics, float x, float y, float size, boolean selected) {
		int fill = selected ? Theme.HUD_CARD_HOVER : Theme.HUD_TRACK;
		int outline = neutralOutline ? line() : (selected ? Theme.ACCENT : Theme.HUD_LINE);
		GuiDraw.well(graphics, x, y, size, fill, outline);
	}
}
