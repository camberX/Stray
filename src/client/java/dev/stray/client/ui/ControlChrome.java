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
		surface(graphics, x, y, w, h, WINDOW_R);
	}

	public static void card(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		surface(graphics, x, y, w, h, CARD_R);
	}

	public static void rail(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		surface(graphics, x, y, w, h, railRadius());
	}

	public static void search(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		surface(graphics, x, y, w, h, h * 0.5f);
	}

	/**
	 * The control menu's copy of liquid-glass-react: no tint, a 12px drop shadow,
	 * and the lens blit (light blur, scale-70 displacement, specular rim).
	 */
	private static void surface(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius) {
		if (w < 2f || h < 2f) {
			return;
		}
		float r = Math.min(Math.max(4f, radius), Math.min(w, h) * 0.5f);
		GuiDraw.roundedFine(graphics, x, y + 16f, w, h, r, 0x12000000);
		GuiDraw.roundedFine(graphics, x, y + 10f, w, h, r, 0x18000000);
		GuiDraw.roundedFine(graphics, x, y + 6f, w, h, r, 0x24000000);
		GuiFrostBlur.blitLiquid(graphics, x, y, w, h, r);
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
		surface(graphics, x, y, w, h, 16f);
	}

	/** Click GUI column backdrop. Same lens as the control menu. */
	public static void backdrop(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius) {
		surface(graphics, x, y, w, h, radius);
	}

	/**
	 * Liquid-glass pane: a dark fallback, the lens, then a tint. Shine 1 matches
	 * the click GUI columns. Smaller cards should pass a lower shine.
	 */
	public static void glassPane(
		GuiGraphicsExtractor graphics,
		float x,
		float y,
		float w,
		float h,
		float radius,
		float shine,
		int tint
	) {
		if (w < 2f || h < 2f) {
			return;
		}
		float r = Math.min(Math.max(1.5f, radius), Math.min(w, h) * 0.5f);
		GuiDraw.roundedFine(graphics, x, y, w, h, r, 0x66000000);
		lens(graphics, x, y, w, h, r, shine);
		if ((tint & 0xFF000000) != 0) {
			GuiDraw.roundedFine(graphics, x, y, w, h, r, tint);
		}
	}

	/** Liquid-glass lens without the column drop shadow. */
	public static void lens(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius) {
		lens(graphics, x, y, w, h, radius, 1f);
	}

	/** @param shine specular strength, 0 to 1. 1 matches the column and category panes. */
	public static void lens(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, float shine) {
		if (w < 2f || h < 2f) {
			return;
		}
		float r = Math.min(Math.max(1.5f, radius), Math.min(w, h) * 0.5f);
		GuiFrostBlur.blitLiquid(graphics, x, y, w, h, r, shine);
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
