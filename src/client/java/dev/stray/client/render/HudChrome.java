package dev.stray.client.render;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.ui.ControlChrome;
import dev.stray.client.ui.MaterialPalette;
import dev.stray.client.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class HudChrome {
	private static final float STROKE = 0.5f;
	private static final int FILL = 0x99000000;
	private static final int OFF = 0x88000000;
	private static final int ACCENT_ALPHA = 115;

	private static boolean hudPass;
	/** This draw skips accent strokes. Inventory HUD minimal uses it. */
	private static boolean neutralOutline;

	private HudChrome() {
	}

	public static void beginHud() {
		hudPass = true;
		dev.stray.client.ui.Win98.sync();
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

	public static boolean vanilla() {
		return StrayConfig.get().hudStyleVanilla();
	}

	public static boolean click() {
		return StrayConfig.get().hudStyleClick();
	}

	public static boolean glass() {
		return StrayConfig.get().hudStyleGlass();
	}

	public static boolean hypr() {
		return StrayConfig.get().hudStyleHypr();
	}

	public static boolean shadow() {
		return StrayConfig.get().hudStyleShadow();
	}

	public static boolean win98() {
		return StrayConfig.get().hudStyleWin98();
	}

	/** HUD text in the Windows 98 style: re-inked for gray windows, no shadow. */
	public static boolean win98Ink() {
		return hudPass && win98();
	}

	/** HUD text in the Hyprland style: Google Sans Flex, no shadow. */
	public static boolean hyprInk() {
		return hudPass && hypr();
	}

	public static boolean vanillaInk() {
		return hudPass && vanilla();
	}

	/** HUD text in the Shadow style: Inter, no shadow. */
	public static boolean shadowInk() {
		return hudPass && shadow();
	}

	public static boolean clickInk() {
		return hudPass && click();
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
		if (vanilla() || !background()) {
			return;
		}
		if (win98()) {
			dev.stray.client.ui.Win98.raised(graphics, x, y, w, h);
			return;
		}
		if (hypr()) {
			MaterialPalette.refresh();
			float r = Math.min(9f, Math.min(w, h) * 0.5f);
			GuiFrostBlur.blitWindow(graphics, x, y, w, h, r);
			GuiDraw.roundedFine(graphics, x, y, w, h, r, Theme.HUD_WINDOW);
			int ring = StrayConfig.get().accentOutlines && !neutralOutline ? MaterialPalette.primary : MaterialPalette.withAlpha(MaterialPalette.primary, 0x40);
			GuiDraw.hairline(graphics, x, y, w, h, r, ring);
			return;
		}
		if (shadow()) {
			dev.stray.client.ui.ShadowLook.panel(graphics, x, y, w, h, Math.min(Math.max(4f, radius), 8f), Theme.HUD_WINDOW);
			return;
		}
		if (click()) {
			box(graphics, x, y, w, h, FILL);
			return;
		}
		if (glass()) {
			float r = Math.min(Math.max(4f, radius), Math.min(w, h) / 2f);
			float shine = Math.min(w, h) <= 22f ? 0.33f : 1f;
			ControlChrome.glassPane(graphics, x, y, w, h, r, shine, 0x6A000000);
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
		int rail = neutralOutline ? 0 : accent;
		GuiDraw.panel(graphics, x, y, w, h, radius, Theme.HUD_WINDOW, line(), rail, right);
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
		if (win98()) {
			dev.stray.client.ui.Win98.raised(graphics, x, y, w, h);
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
		if (win98()) {
			win98Bar(graphics, x, y, w, h, color);
			return;
		}
		if (click()) {
			if (w > 0f && h > 0f) {
				GuiDraw.fillSmooth(graphics, x, y, w, h, clickPaint(color));
			}
			return;
		}
		GuiDraw.rounded(graphics, x, y, w, h, radius, color);
	}

	public static void slot(GuiGraphicsExtractor graphics, float x, float y, float size, boolean selected) {
		if (win98()) {
			if (selected) {
				dev.stray.client.ui.Win98.pressed(graphics, x, y, size, size);
				GuiDraw.border(graphics, x - 1, y - 1, size + 2, size + 2, dev.stray.client.ui.Win98.NAVY, 1f);
			} else {
				dev.stray.client.ui.Win98.sunken(graphics, x, y, size, size, dev.stray.client.ui.Win98.FACE);
			}
			return;
		}
		if (hypr()) {
			MaterialPalette.refresh();
			GuiDraw.roundedFine(graphics, x, y, size, size, 3f, selected ? MaterialPalette.secondaryContainer : MaterialPalette.surfaceHigh);
			if (selected) {
				GuiDraw.hairline(graphics, x, y, size, size, 3f, MaterialPalette.primary);
			}
			return;
		}
		if (click()) {
			box(graphics, x, y, size, size, selected ? Theme.withAlpha(Theme.ACCENT, ACCENT_ALPHA) : OFF);
			return;
		}
		if (glass()) {
			GuiDraw.fillSmooth(
				graphics,
				x,
				y,
				size,
				size,
				selected ? Theme.withAlpha(Theme.ACCENT, ACCENT_ALPHA) : OFF
			);
			return;
		}
		int fill = selected ? Theme.HUD_CARD_HOVER : Theme.HUD_TRACK;
		int outline = neutralOutline ? line() : (selected ? Theme.ACCENT : Theme.HUD_LINE);
		GuiDraw.well(graphics, x, y, size, fill, outline);
	}

	/**
	 * Progress bars and chips in Windows 98: tracks are sunken white wells, fills are the classic
	 * chunky blocks (navy for the accent, the bar's own color otherwise).
	 */
	private static void win98Bar(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int color) {
		if (w <= 0f || h <= 0f) {
			return;
		}
		boolean neutral = color == Theme.HUD_TRACK || color == Theme.HUD_CARD || color == Theme.HUD_CARD_HOVER
			|| color == Theme.HUD_LINE || color == Theme.HUD_WINDOW || color == Theme.TRACK || color == Theme.CARD || ((color >>> 24) < 0xC0);
		if (neutral) {
			if (h >= 4 && w >= 4) {
				dev.stray.client.ui.Win98.shallow(graphics, x, y, w, h, dev.stray.client.ui.Win98.WINDOW);
			} else {
				GuiDraw.fill(graphics, x, y, w, h, dev.stray.client.ui.Win98.WINDOW);
			}
			return;
		}
		int block = color == Theme.ACCENT ? dev.stray.client.ui.Win98.NAVY : (0xFF000000 | dev.stray.client.ui.Win98.ink(color));
		if (h < 4) {
			GuiDraw.fill(graphics, x, y, w, h, block);
			return;
		}
		float inset = 1;
		float bh = h - inset * 2;
		float bw = Math.max(3, bh * 0.75f);
		for (float bx = x + inset; bx < x + w - inset; bx += bw + 1) {
			GuiDraw.fill(graphics, bx, y + inset, Math.min(bw, x + w - inset - bx), bh, block);
		}
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
