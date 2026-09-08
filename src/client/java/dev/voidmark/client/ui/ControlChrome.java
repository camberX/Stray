package dev.voidmark.client.ui;

import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.render.GuiDraw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Apple Control Center / visionOS glass tokens for the optional click-GUI design.
 */
public final class ControlChrome {
	public static final int BLUE = 0xFF0A84FF;
	public static final int TEXT = 0xFF1C1C1E;
	public static final int MUTED = 0x991C1C1E;
	public static final int LIGHT_TEXT = 0xFFF5F5F7;
	public static final int LIGHT_MUTED = 0x99EBEBF0;
	public static final int TRACK = 0x59FFFFFF;
	public static final int KNOB = 0xFFFFFFFF;
	public static final float WINDOW_R = 24f;
	public static final float CARD_R = 18f;
	public static final float RAIL_R = 22f;

	private ControlChrome() {
	}

	public static int paneRgb() {
		int rgb = VoidmarkConfig.get().controlPaneRgb & 0xFFFFFF;
		return rgb == 0 ? 0xFFFFFF : rgb;
	}

	public static float paneOpacity() {
		return VoidmarkConfig.clamp(VoidmarkConfig.get().controlPaneOpacity, 0.12f, 0.78f);
	}

	public static boolean darkText() {
		int rgb = paneRgb();
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		return (r * 0.30f + g * 0.59f + b * 0.11f) > 140f;
	}

	public static int text() {
		return darkText() ? TEXT : LIGHT_TEXT;
	}

	public static int muted() {
		return darkText() ? MUTED : LIGHT_MUTED;
	}

	public static int windowFill() {
		return Theme.withAlpha(paneRgb(), Math.round(paneOpacity() * 255f));
	}

	public static int cardFill() {
		return Theme.withAlpha(Theme.mix(paneRgb(), 0xFFFFFF, 0.16f), Math.round(Math.min(0.46f, paneOpacity() + 0.06f) * 255f));
	}

	public static int railFill() {
		return Theme.withAlpha(Theme.mix(paneRgb(), 0xFFFFFF, 0.12f), Math.round(Math.min(0.42f, paneOpacity() + 0.05f) * 255f));
	}

	public static int pillFill() {
		return Theme.withAlpha(0xFFFFFF, darkText() ? 48 : 36);
	}

	public static int searchFill() {
		return Theme.withAlpha(Theme.mix(paneRgb(), 0xFFFFFF, 0.22f), Math.round(Math.min(0.48f, paneOpacity() + 0.06f) * 255f));
	}

	public static int clipFill() {
		return Theme.withAlpha(paneRgb(), Math.min(255, Math.round(paneOpacity() * 255f) + 48));
	}

	public static int frostVeil() {
		return Theme.withAlpha(Theme.mix(paneRgb(), 0xFFFFFF, 0.72f), 168);
	}

	public static void window(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		GuiDraw.rounded(graphics, x, y, w, h, WINDOW_R, windowFill());
		float frost = VoidmarkConfig.clamp(VoidmarkConfig.get().controlFrost, 0f, 1f);
		if (frost > 0.01f) {
			GuiDraw.rounded(
				graphics,
				x,
				y,
				w,
				h,
				WINDOW_R,
				Theme.withAlpha(Theme.mix(paneRgb(), 0xFFFFFF, 0.28f), Math.round(frost * 58f))
			);
		}
	}

	public static void card(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		GuiDraw.rounded(graphics, x, y, w, h, CARD_R, cardFill());
	}

	public static void rail(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		GuiDraw.rounded(graphics, x, y, w, h, RAIL_R, railFill());
	}

	public static void search(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		GuiDraw.rounded(graphics, x, y, w, h, h * 0.5f, searchFill());
	}

	public static void sheet(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		GuiDraw.rounded(graphics, x, y, w, h, 16f, Theme.withAlpha(paneRgb(), 210));
	}

	public static void face(GuiGraphicsExtractor graphics, float x, float y, float size, PlayerSkin skin) {
		Identifier id = faceTexture(skin);
		if (id == null) {
			GuiDraw.circle(graphics, x + size * 0.5f, y + size * 0.5f, size * 0.5f, clipFill());
			return;
		}
		GuiDraw.blit(graphics, id, x, y, size, size, 8f, 8f, 8, 8, 64, 64);
		GuiDraw.blit(graphics, id, x, y, size, size, 40f, 8f, 8, 8, 64, 64);
		GuiDraw.circleClip(graphics, x, y, size, windowFill());
	}

	private static Identifier faceTexture(PlayerSkin skin) {
		if (skin == null || skin.body() == null) {
			return null;
		}
		ClientAsset.Texture body = skin.body();
		return body.texturePath();
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
