package dev.stray.client.ui;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.render.GuiDraw;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The Panels look: pure neutral greys with no tint (like Minecraft's own dark screens), small
 * corners, a one-pixel edge, white for what is on and the theme accent for marks and counts.
 */
public final class PanelLook {
	public static final int G0 = 0xFF121212;
	public static final int G1 = 0xFF1C1C1C;
	public static final int G2 = 0xFF262626;
	public static final int G3 = 0xFF333333;
	public static final int EDGE = 0xFF2E2E2E;
	public static final int WHITE = 0xFFFFFFFF;
	/** Minecraft's chat grey and dark grey. */
	public static final int GREY = 0xFFAAAAAA;
	public static final int DGREY = 0xFF555555;
	public static final float RADIUS = 6f;

	private PanelLook() {
	}

	/** The theme accent (the same setting Shadow's accent uses). */
	public static int accent() {
		StrayConfig config = StrayConfig.get();
		return 0xFF000000 | ((config == null ? 0x2FB5FF : config.themeAccentRgb) & 0xFFFFFF);
	}

	/** A window or HUD panel: the given fill, small corners, a one-pixel edge. */
	public static void panel(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int fill) {
		if (w < 2f || h < 2f) {
			return;
		}
		float r = Math.min(RADIUS, Math.min(w, h) * 0.5f);
		int base = (fill >>> 24) == 0 ? 0xF51C1C1C : neutral(fill);
		GuiDraw.roundedFine(graphics, x, y, w, h, r, base);
		GuiDraw.hairline(graphics, x, y, w, h, r, EDGE);
	}

	/** A card, chip or slot: a lighter grey, lighter again on hover; picked ones get an accent edge. */
	public static void control(GuiGraphicsExtractor graphics, float x, float y, float w, float h, boolean hover, boolean selected) {
		if (w < 1f || h < 1f) {
			return;
		}
		float r = Math.min(4f, Math.min(w, h) * 0.5f);
		GuiDraw.roundedFine(graphics, x, y, w, h, r, selected || hover ? G3 : G2);
		if (selected) {
			GuiDraw.hairline(graphics, x, y, w, h, r, accent());
		}
	}

	/** The accent bar down the left of a row that is on. */
	public static void onBar(GuiGraphicsExtractor graphics, float x, float y, float h) {
		GuiDraw.fillSmooth(graphics, x, y + 3f, 1.5f, Math.max(1f, h - 6f), accent());
	}

	/** A small square-ish switch. */
	public static void toggle(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float t) {
		t = Math.max(0f, Math.min(1f, t));
		float r = Math.min(2.5f, h * 0.5f);
		int track = lerp(G2, accent(), t);
		GuiDraw.roundedFine(graphics, x, y, w, h, r, track);
		if (t < 0.99f) {
			GuiDraw.hairline(graphics, x, y, w, h, r, Theme.withAlpha(DGREY, Math.round(255 * (1f - t))));
		}
		float k = h - 4f;
		float kx = x + 2f + (w - 4f - k) * t;
		GuiDraw.roundedFine(graphics, kx, y + 2f, k, k, Math.min(2f, k * 0.5f), lerp(DGREY, G0, t));
	}

	/** Two ARGB colours mixed, alpha included. */
	public static int lerp(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int out = 0;
		for (int shift = 0; shift <= 24; shift += 8) {
			int x = (a >>> shift) & 0xFF;
			int y = (b >>> shift) & 0xFF;
			out |= Math.round(x + (y - x) * t) << shift;
		}
		return out;
	}

	/**
	 * A caller's fill made neutral: Stray's fills are slightly blue blacks; this look keeps their
	 * opacity but uses its own grey.
	 */
	static int neutral(int fill) {
		int a = (fill >>> 24) & 0xFF;
		int r = (fill >> 16) & 0xFF;
		int g = (fill >> 8) & 0xFF;
		int b = fill & 0xFF;
		if (Math.max(r, Math.max(g, b)) < 0x30) {
			return Theme.withAlpha(0x1C1C1C, a);
		}
		return fill;
	}
}
