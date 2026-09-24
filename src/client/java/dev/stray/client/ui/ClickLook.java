package dev.stray.client.ui;

import dev.stray.client.render.GuiDraw;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Shared click-GUI chrome: a black hairline around a flat fill.
 * Hover and focus pass the accent as the outline and pick up the accent wash.
 */
public final class ClickLook {
	public static final float STROKE = 0.5f;
	public static final int OUTLINE = 0xFF000000;
	public static final int FILL = 0x88000000;
	public static final int PANEL = 0x99000000;
	public static final int TEXT = 0xFFFFFFFF;
	public static final int DIM = 0xFFAAAAAA;
	private static final int ACCENT_ALPHA = 115;

	private ClickLook() {
	}

	public static void panel(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, int fill, int outline) {
		panel(graphics, x, y, w, h, radius, fill, outline, 0);
	}

	public static void panel(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, int fill, int outline, int accent) {
		panel(graphics, x, y, w, h, radius, fill, outline, accent, false);
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
		int accent,
		boolean accentRight
	) {
		if (w < 2f || h < 2f) {
			return;
		}
		int paint = accentOutline(outline) ? Theme.withAlpha(Theme.ACCENT, ACCENT_ALPHA) : fill;
		frame(graphics, x, y, w, h);
		GuiDraw.fillSmooth(graphics, x + STROKE, y + STROKE, w - STROKE * 2f, h - STROKE * 2f, paint);
		if ((accent & 0xFF000000) != 0) {
			float bar = 2f;
			if (accentRight) {
				GuiDraw.fillSmooth(graphics, x + w - STROKE - bar, y + STROKE, bar, h - STROKE * 2f, accent);
			} else {
				GuiDraw.fillSmooth(graphics, x + STROKE, y + STROKE, bar, h - STROKE * 2f, accent);
			}
		}
	}

	public static void frame(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		GuiDraw.fillSmooth(graphics, x, y, w, STROKE, OUTLINE);
		GuiDraw.fillSmooth(graphics, x, y + h - STROKE, w, STROKE, OUTLINE);
		GuiDraw.fillSmooth(graphics, x, y, STROKE, h, OUTLINE);
		GuiDraw.fillSmooth(graphics, x + w - STROKE, y, STROKE, h, OUTLINE);
	}

	private static boolean accentOutline(int outline) {
		return (outline & 0xFFFFFF) == (Theme.ACCENT & 0xFFFFFF);
	}
}
