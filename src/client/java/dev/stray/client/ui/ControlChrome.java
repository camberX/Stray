package dev.stray.client.ui;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.GuiFrostBlur;
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
		return false;
	}

	public static int hudFill() {
		float hud = StrayConfig.clamp(StrayConfig.get().hudOpacity, 0.20f, 1f);
		return Theme.withAlpha(paneRgb(), Math.round(paneOpacity() * hud * 255f));
	}

	public static void glass(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, int fill) {
		float r = Math.min(Math.max(6f, radius), Math.min(w, h) * 0.5f);
		GuiDraw.roundedFine(graphics, x, y, w, h, r, fill);
		rim(graphics, x, y, w, h, r);
	}

	public static int paneRgb() {
		int rgb = StrayConfig.get().controlPaneRgb & 0xFFFFFF;
		return rgb == 0 ? 0x181818 : rgb;
	}

	public static int pillRgb() {
		int rgb = StrayConfig.get().controlPillRgb & 0xFFFFFF;
		return rgb == 0 ? 0x808080 : rgb;
	}

	public static float paneOpacity() {
		return StrayConfig.clamp(StrayConfig.get().controlPaneOpacity, 0.12f, 0.78f);
	}

	public static float pillOpacity() {
		return StrayConfig.clamp(StrayConfig.get().controlPillOpacity, 0.12f, 0.78f);
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
		surface(graphics, x, y, w, h, WINDOW_R, windowFill());
	}

	public static void card(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		surface(graphics, x, y, w, h, CARD_R, cardFill());
	}

	public static void rail(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		surface(graphics, x, y, w, h, railRadius(), railFill());
	}

	public static void search(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		surface(graphics, x, y, w, h, h * 0.5f, searchFill());
	}

	/**
	 * Liquid glass for the control menu: refracted frost, a thinner veil than the raw fill,
	 * and a bright top rim. HUD glass stays on the plain frost blit.
	 */
	private static void surface(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, int fill) {
		if (w < 2f || h < 2f) {
			return;
		}
		float r = Math.min(Math.max(4f, radius), Math.min(w, h) * 0.5f);
		GuiFrostBlur.blitLiquid(graphics, x, y, w, h, r);
		GuiDraw.roundedFine(graphics, x, y, w, h, r, liquidVeil(fill));
		liquidRim(graphics, x, y, w, h, r);
	}

	private static int liquidVeil(int fill) {
		int alpha = (fill >>> 24) & 0xFF;
		if (alpha > 148) {
			alpha = Math.round(alpha * 0.70f);
		}
		return (Math.min(255, alpha) << 24) | (fill & 0xFFFFFF);
	}

	public static void rim(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius) {
		int hi;
		int lo;
		if (StrayConfig.get().accentOutlines) {
			int accent = Theme.ACCENT & 0xFFFFFF;
			hi = Theme.withAlpha(accent, darkText() ? 210 : 190);
			lo = Theme.withAlpha(accent, darkText() ? 78 : 58);
		} else {
			hi = Theme.withAlpha(0xFFFFFF, darkText() ? 58 : 40);
			lo = Theme.withAlpha(0xFFFFFF, darkText() ? 16 : 11);
		}
		GuiDraw.gradientRim(graphics, x, y, w, h, radius, hi, lo);
	}

	public static void sheet(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		surface(graphics, x, y, w, h, 16f, Theme.withAlpha(paneRgb(), 210));
	}

	private static void liquidRim(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius) {
		int hi;
		int lo;
		if (StrayConfig.get().accentOutlines) {
			int accent = Theme.ACCENT & 0xFFFFFF;
			hi = Theme.withAlpha(accent, 220);
			lo = Theme.withAlpha(accent, 70);
		} else if (darkText()) {
			hi = Theme.withAlpha(0xFFFFFF, 210);
			lo = Theme.withAlpha(0x000000, 58);
		} else {
			hi = Theme.withAlpha(0xFFFFFF, 176);
			lo = Theme.withAlpha(0xFFFFFF, 34);
		}
		GuiDraw.gradientRim(graphics, x, y, w, h, radius, hi, lo);
	}

	public static void face(GuiGraphicsExtractor graphics, float x, float y, float size, PlayerSkin skin) {
		Identifier id = faceTexture(skin);
		if (id == null) {
			GuiDraw.circle(graphics, x + size * 0.5f, y + size * 0.5f, size * 0.5f, clipFill());
			return;
		}
		if (GuiDraw.circleBlit(graphics, id, x, y, size, 8f, 8f, 8, 8, 64, 64)) {
			GuiDraw.circleBlit(graphics, id, x, y, size, 40f, 8f, 8, 8, 64, 64);
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
