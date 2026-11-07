package dev.stray.client.ui;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.render.GuiDraw;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Shared click-GUI chrome: a black hairline around a flat fill.
 * Hover and focus pass the accent as the outline and pick up the accent wash.
 */
public final class ClickLook {
	public static final float STROKE = 0.5f;
	/**
	 * A stroke of exactly 0.5 ends on a pixel center. The rasterizer keeps the
	 * right and bottom edges and drops the left and top, so those sides look thinner.
	 */
	private static final float STROKE_COVER = STROKE + 0.01f;
	public static final int OUTLINE = 0xFF000000;
	public static final int FILL = 0x88000000;
	public static final int PANEL = 0x99000000;
	public static final int TEXT = 0xFFFFFFFF;
	public static final int DIM = 0xFFAAAAAA;

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
		shadowBox(graphics, x, y, w, h, accentOutline(outline));
	}

	/**
	 * Black fill with an accent stroke. For screens outside the click GUI.
	 * The click GUI itself keeps the accent fill and black outline.
	 */
	public static void outside(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, int fill, int outline) {
		outside(graphics, x, y, w, h, radius, fill, outline, 0);
	}

	public static void outside(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, int fill, int outline, int accent) {
		int stroke = accentOutline(outline) ? (0xFF000000 | (Theme.ACCENT & 0xFFFFFF)) : OUTLINE;
		solid(graphics, x, y, w, h, fill, stroke);
		if ((accent & 0xFF000000) != 0) {
			float bar = 2f;
			GuiDraw.fillSmooth(graphics, x + STROKE, y + STROKE, bar, h - STROKE * 2f, accent);
		}
	}

	/**
	 * Material You surface: a rounded fill (pill-shaped for short widgets) and,
	 * when {@code ring} is set, a smooth 1px ring drawn as a filled shape under
	 * an inset fill rather than a stroke.
	 */
	public static void material(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int fill, int ring) {
		float radius = h <= 24f ? h * 0.5f : Math.min(9f, Math.min(w, h) * 0.5f);
		GuiDraw.roundedFine(graphics, x, y, w, h, radius, fill);
		if ((ring & 0xFF000000) != 0) {
			GuiDraw.hairline(graphics, x, y, w, h, radius, ring);
		}
	}

	/** Flat fill with a real stroke. Does not turn an accent outline into a fill. */
	public static void solid(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int fill, int stroke) {
		if (w < 2f || h < 2f) {
			return;
		}
		boolean hover = (stroke & 0xFFFFFF) != 0 && (stroke & 0xFFFFFF) != (OUTLINE & 0xFFFFFF);
		shadowBox(graphics, x, y, w, h, hover);
	}

	public static void frame(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		stroke(graphics, x, y, w, h, OUTLINE);
	}

	private static void stroke(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int color) {
		GuiDraw.fillSmooth(graphics, x, y, w, STROKE_COVER, color);
		GuiDraw.fillSmooth(graphics, x, y + h - STROKE, w, STROKE, color);
		GuiDraw.fillSmooth(graphics, x, y, STROKE_COVER, h, color);
		GuiDraw.fillSmooth(graphics, x + w - STROKE, y, STROKE, h, color);
	}

	/** Slots and small controls become cards, anything bigger a frosted window. */
	private static void shadowBox(GuiGraphicsExtractor graphics, float x, float y, float w, float h, boolean hover) {
		float shortSide = Math.min(w, h);
		float longSide = Math.max(w, h);
		boolean slot = shortSide <= 20f && longSide <= shortSide * 1.25f;
		boolean widget = !slot && h <= 24f;
		if (slot || widget) {
			ShadowLook.card(graphics, x, y, w, h, slot ? ShadowLook.SLOT_R : ShadowLook.CONTROL_R, hover, false);
		} else {
			ShadowLook.window(graphics, x, y, w, h, ShadowLook.WINDOW_R, Theme.WINDOW);
		}
	}

	private static boolean accentOutline(int outline) {
		return (outline & 0xFFFFFF) == (Theme.ACCENT & 0xFFFFFF);
	}
}
