package dev.voidmark.client.ui;

import dev.voidmark.client.render.GuiDraw;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Apple Control Center / visionOS glass tokens for the optional click-GUI design.
 */
public final class ControlChrome {
	public static final int BLUE = 0xFF0A84FF;
	public static final int WINDOW = 0x99111114;
	public static final int WINDOW_TOP = 0x3D2A2A2E;
	public static final int CARD = 0x73202024;
	public static final int CARD_HI = 0x8A2A2A30;
	public static final int STROKE = 0x3DFFFFFF;
	public static final int STROKE_SOFT = 0x22FFFFFF;
	public static final int RAIL = 0x8C1A1A1E;
	public static final int RAIL_PILL = 0x66FFFFFF;
	public static final int SEARCH = 0xB3141418;
	public static final int TRACK = 0x4DFFFFFF;
	public static final int KNOB = 0xFFFFFFFF;
	public static final int TEXT = 0xFFF5F5F7;
	public static final int MUTED = 0x99EBEBF0;
	public static final int POWER = 0x66FFFFFF;
	public static final float WINDOW_R = 24f;
	public static final float CARD_R = 18f;
	public static final float RAIL_R = 22f;
	public static final float PILL_R = 16f;

	private ControlChrome() {
	}

	public static void window(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		GuiDraw.rounded(graphics, x, y, w, h, WINDOW_R, WINDOW);
		GuiDraw.rounded(graphics, x, y, w, Math.min(72f, h * 0.28f), WINDOW_R, WINDOW_TOP);
		GuiDraw.roundedOutline(graphics, x, y, w, h, WINDOW_R, STROKE, 1.1f);
		GuiDraw.roundedOutline(graphics, x + 1.2f, y + 1.2f, w - 2.4f, h - 2.4f, WINDOW_R - 1.2f, STROKE_SOFT, 0.6f);
	}

	public static void card(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		GuiDraw.rounded(graphics, x, y, w, h, CARD_R, CARD);
		GuiDraw.roundedOutline(graphics, x, y, w, h, CARD_R, STROKE, 0.9f);
	}

	public static void rail(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		GuiDraw.rounded(graphics, x, y, w, h, RAIL_R, RAIL);
		GuiDraw.roundedOutline(graphics, x, y, w, h, RAIL_R, STROKE, 0.9f);
	}

	public static void search(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		GuiDraw.rounded(graphics, x, y, w, h, h * 0.5f, SEARCH);
		GuiDraw.roundedOutline(graphics, x, y, w, h, h * 0.5f, STROKE_SOFT, 0.7f);
	}

	public static void sheet(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		GuiDraw.rounded(graphics, x, y, w, h, 16f, 0xD116161A);
		GuiDraw.roundedOutline(graphics, x, y, w, h, 16f, STROKE, 0.9f);
	}

	public static void toggle(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float t) {
		int fill = t > 0.5f ? BLUE : TRACK;
		GuiDraw.pill(graphics, x, y, w, h, fill);
		float knobR = h * 0.42f;
		float knobX = x + knobR + 1.6f + t * (w - knobR * 2f - 3.2f);
		GuiDraw.circle(graphics, knobX, y + h * 0.5f, knobR, KNOB);
	}

	public static void slider(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float t) {
		GuiDraw.pill(graphics, x, y, w, h, TRACK);
		GuiDraw.pill(graphics, x, y, Math.max(h, w * t), h, BLUE);
		GuiDraw.circle(graphics, x + w * t, y + h * 0.5f, h * 0.72f, KNOB);
	}
}
