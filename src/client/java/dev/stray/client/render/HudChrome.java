package dev.stray.client.render;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.ui.ControlChrome;
import dev.stray.client.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class HudChrome {
	private static final float STROKE = 0.5f;
	private static final int FILL = 0x99000000;
	private static final int OFF = 0x88000000;
	private static final int ACCENT_ALPHA = 115;

	private static boolean hudPass;

	private HudChrome() {
	}

	public static void beginHud() {
		hudPass = true;
	}

	public static void endHud() {
		hudPass = false;
	}

	public static boolean vanilla() {
		return StrayConfig.get().hudStyleVanilla();
	}

	public static boolean click() {
		return StrayConfig.get().hudStyleClick();
	}

	public static boolean vanillaInk() {
		return hudPass && vanilla();
	}

	public static boolean clickInk() {
		return hudPass && click();
	}

	/** Black, or the accent color when accent outlines are on. */
	public static int outline() {
		return StrayConfig.get().accentOutlines ? Theme.ACCENT : 0xFF000000;
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
		if (vanilla()) {
			return;
		}
		if (click()) {
			box(graphics, x, y, w, h, FILL);
			return;
		}
		if (StrayConfig.get().guiDesignControl()) {
			float r = Math.min(Math.max(6f, radius), Math.min(w, h) / 2f);
			GuiFrostBlur.blitWindow(graphics, x, y, w, h, r);
			ControlChrome.glass(graphics, x, y, w, h, r, ControlChrome.hudFill());
			if (StrayConfig.get().hudStarfield && w >= 72f && h >= 52f) {
				Starfield.drawHud(graphics, x, y, w, h, Math.max(8f, radius));
			}
			return;
		}
		boolean right = accentTowardRight(graphics, x, y, w);
		GuiDraw.panel(graphics, x, y, w, h, radius, Theme.HUD_WINDOW, Theme.HUD_LINE, accent, right);
		if (StrayConfig.get().hudStarfield && w >= 72f && h >= 52f) {
			Starfield.drawHud(graphics, x, y, w, h, radius);
		}
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

	/** Progress and chips. Click style uses a flat fill instead of a rounded pill. */
	public static void rounded(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, int color) {
		if (click()) {
			if (w > 0f && h > 0f) {
				GuiDraw.fillSmooth(graphics, x, y, w, h, clickPaint(color));
			}
			return;
		}
		GuiDraw.rounded(graphics, x, y, w, h, radius, color);
	}

	public static void slot(GuiGraphicsExtractor graphics, float x, float y, float size, boolean selected) {
		if (click()) {
			box(graphics, x, y, size, size, selected ? Theme.withAlpha(Theme.ACCENT, ACCENT_ALPHA) : OFF);
			return;
		}
		int fill = selected ? Theme.HUD_CARD_HOVER : Theme.HUD_TRACK;
		int outline = selected ? Theme.ACCENT : Theme.HUD_LINE;
		GuiDraw.well(graphics, x, y, size, fill, outline);
	}

	private static int clickPaint(int color) {
		if (color == Theme.HUD_TRACK || color == Theme.HUD_CARD || color == Theme.HUD_CARD_HOVER || color == Theme.HUD_LINE || color == Theme.HUD_WINDOW) {
			return OFF;
		}
		if ((color >>> 24) == 0xFF) {
			return Theme.withAlpha(color, ACCENT_ALPHA);
		}
		return color;
	}

	/** Rail sits on the side closer to the screen edge. */
	private static boolean accentTowardRight(GuiGraphicsExtractor graphics, float x, float y, float w) {
		var pose = graphics.pose();
		float left = pose.m00() * x + pose.m10() * y + pose.m20();
		float right = pose.m00() * (x + w) + pose.m10() * y + pose.m20();
		return (left + right) * 0.5f > graphics.guiWidth() * 0.5f;
	}
}
