package dev.voidmark.client.ui;

import dev.voidmark.client.render.GuiDraw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Apple Control Center / visionOS glass tokens for the optional click-GUI design.
 */
public final class ControlChrome {
	public static final int BLUE = 0xFF0A84FF;
	public static final int WINDOW = 0xC41C1C20;
	public static final int CARD = 0x8A2A2A30;
	public static final int CARD_HI = 0xA2323238;
	public static final int STROKE = 0x1AFFFFFF;
	public static final int STROKE_SOFT = 0x14FFFFFF;
	public static final int RAIL = 0xB21C1C20;
	public static final int RAIL_PILL = 0x59FFFFFF;
	public static final int SEARCH = 0xC2141418;
	public static final int FACE_CLIP = 0xD41C1C20;
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
		GuiDraw.roundedOutline(graphics, x, y, w, h, WINDOW_R, STROKE, 0.5f);
	}

	public static void card(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		GuiDraw.rounded(graphics, x, y, w, h, CARD_R, CARD);
		GuiDraw.roundedOutline(graphics, x, y, w, h, CARD_R, STROKE_SOFT, 0.5f);
	}

	public static void rail(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		GuiDraw.rounded(graphics, x, y, w, h, RAIL_R, RAIL);
		GuiDraw.roundedOutline(graphics, x, y, w, h, RAIL_R, STROKE_SOFT, 0.5f);
	}

	public static void search(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		GuiDraw.rounded(graphics, x, y, w, h, h * 0.5f, SEARCH);
	}

	public static void sheet(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		GuiDraw.rounded(graphics, x, y, w, h, 16f, 0xE016161A);
		GuiDraw.roundedOutline(graphics, x, y, w, h, 16f, STROKE_SOFT, 0.5f);
	}

	public static void face(GuiGraphicsExtractor graphics, float x, float y, float size, PlayerSkin skin) {
		GuiDraw.circle(graphics, x + size * 0.5f, y + size * 0.5f, size * 0.5f, 0x33FFFFFF);
		if (skin != null && skin.body() != null) {
			PlayerFaceExtractor.extractRenderState(graphics, skin, Math.round(x), Math.round(y), Math.round(size));
			GuiDraw.roundedBlitEars(graphics, x, y, size, size, size * 0.5f, FACE_CLIP);
		}
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
