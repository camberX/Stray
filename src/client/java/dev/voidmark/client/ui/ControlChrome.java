package dev.voidmark.client.ui;

import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.render.GuiDraw;
import dev.voidmark.client.render.GuiFrostBlur;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Apple Control Center / visionOS glass tokens for the optional click-GUI design.
 */
public final class ControlChrome {
	public static final int TEXT = 0xFF1C1C1E;
	public static final int MUTED = 0x991C1C1E;
	public static final int LIGHT_TEXT = 0xFFF5F5F7;
	public static final int LIGHT_MUTED = 0x99EBEBF0;
	public static final int TRACK = 0x59FFFFFF;
	public static final int KNOB = 0xFFFFFFFF;
	public static final float WINDOW_R = 24f;
	public static final float CARD_R = 18f;
	public static final float RAIL_INSET = 10f;
	public static final float RAIL_W = 56f;

	private ControlChrome() {
	}

	public static boolean on() {
		return VoidmarkConfig.get().guiDesignControl();
	}

	public static int hudFill() {
		float hud = VoidmarkConfig.clamp(VoidmarkConfig.get().hudOpacity, 0.20f, 1f);
		return Theme.withAlpha(paneRgb(), Math.round(paneOpacity() * hud * 255f));
	}

	public static void glass(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, int fill) {
		float r = Math.min(Math.max(6f, radius), Math.min(w, h) * 0.5f);
		GuiDraw.roundedFine(graphics, x, y, w, h, r, fill);
		rim(graphics, x, y, w, h, r);
	}

	public static int paneRgb() {
		int rgb = VoidmarkConfig.get().controlPaneRgb & 0xFFFFFF;
		return rgb == 0 ? 0xFFFFFF : rgb;
	}

	public static int pillRgb() {
		int rgb = VoidmarkConfig.get().controlPillRgb & 0xFFFFFF;
		return rgb == 0 ? 0xFFFFFF : rgb;
	}

	public static float paneOpacity() {
		return VoidmarkConfig.clamp(VoidmarkConfig.get().controlPaneOpacity, 0.12f, 0.78f);
	}

	public static float pillOpacity() {
		return VoidmarkConfig.clamp(VoidmarkConfig.get().controlPillOpacity, 0.12f, 0.78f);
	}

	public static boolean darkText() {
		return darkText(paneRgb());
	}

	public static boolean pillDarkText() {
		return darkText(pillRgb());
	}

	private static boolean darkText(int rgb) {
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

	public static int cardText() {
		return pillDarkText() ? TEXT : LIGHT_TEXT;
	}

	public static int cardMuted() {
		return pillDarkText() ? MUTED : LIGHT_MUTED;
	}

	public static int windowFill() {
		return Theme.withAlpha(paneRgb(), Math.round(paneOpacity() * 255f));
	}

	public static int cardFill() {
		return Theme.withAlpha(pillRgb(), pillAlpha());
	}

	public static int railFill() {
		return cardFill();
	}

	public static float railRadius() {
		return Math.max(8f, WINDOW_R - RAIL_INSET);
	}

	public static int accent() {
		return Theme.ACCENT;
	}

	public static int selectedFill() {
		int rgb = darkText()
			? Theme.mix(paneRgb(), 0x000000, 0.22f)
			: Theme.mix(paneRgb(), 0xFFFFFF, 0.22f);
		return Theme.withAlpha(rgb, Math.round(Math.min(0.56f, paneOpacity() + 0.16f) * 255f));
	}

	public static int pillFill() {
		return Theme.withAlpha(pillRgb(), Math.round(pillAlpha() * 0.78f));
	}

	private static int pillAlpha() {
		return Math.round(pillOpacity() * 255f);
	}

	public static int searchFill() {
		int rgb = darkText()
			? Theme.mix(paneRgb(), 0x000000, 0.16f)
			: Theme.mix(paneRgb(), 0xFFFFFF, 0.16f);
		return Theme.withAlpha(rgb, Math.round(Math.min(0.70f, paneOpacity() + 0.18f) * 255f));
	}

	public static int clipFill() {
		return Theme.withAlpha(paneRgb(), Math.min(255, Math.round(paneOpacity() * 255f) + 48));
	}

	public static int frostVeil() {
		return Theme.withAlpha(Theme.mix(paneRgb(), 0xFFFFFF, 0.72f), 168);
	}

	public static void window(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		GuiFrostBlur.blitWindow(graphics, x, y, w, h, WINDOW_R);
		GuiDraw.roundedFine(graphics, x, y, w, h, WINDOW_R, windowFill());
		rim(graphics, x, y, w, h, WINDOW_R);
	}

	public static void card(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		GuiDraw.roundedFine(graphics, x, y, w, h, CARD_R, cardFill());
		rim(graphics, x, y, w, h, CARD_R);
	}

	public static void rail(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		float r = railRadius();
		GuiDraw.roundedFine(graphics, x, y, w, h, r, railFill());
		rim(graphics, x, y, w, h, r);
	}

	public static void search(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		float r = h * 0.5f;
		GuiDraw.roundedFine(graphics, x, y, w, h, r, searchFill());
		rim(graphics, x, y, w, h, r);
	}

	public static void rim(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius) {
		int hi = Theme.withAlpha(0xFFFFFF, darkText() ? 58 : 40);
		int lo = Theme.withAlpha(0xFFFFFF, darkText() ? 16 : 11);
		GuiDraw.gradientRim(graphics, x, y, w, h, radius, hi, lo);
	}

	public static void sheet(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		GuiDraw.roundedFine(graphics, x, y, w, h, 16f, Theme.withAlpha(paneRgb(), 210));
		rim(graphics, x, y, w, h, 16f);
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
		int fill = t > 0.5f ? accent() : TRACK;
		GuiDraw.pill(graphics, x, y, w, h, fill);
		float knobR = h * 0.42f;
		float knobX = x + knobR + 1.6f + t * (w - knobR * 2f - 3.2f);
		GuiDraw.circle(graphics, knobX, y + h * 0.5f, knobR, KNOB);
	}

	public static void slider(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float t) {
		GuiDraw.pill(graphics, x, y, w, h, TRACK);
		GuiDraw.pill(graphics, x, y, Math.max(h, w * t), h, accent());
		GuiDraw.circle(graphics, x + w * t, y + h * 0.5f, h * 0.72f, KNOB);
	}
}
