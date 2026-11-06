package dev.stray.client.ui;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.render.GuiDraw;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The "Shadow" style, after the profile viewer window: see-through black panels with
 * a soft shadow falling outward from their edges, a faint white hairline, and white
 * and gray text. It reuses the Click layouts; only the panels and colours change.
 */
public final class ShadowLook {
	public static final int TEXT = 0xFFEEF1F5;
	public static final int MUTED = 0xFF8A94A0;
	public static final int FAINT = 0xFF5A636E;
	public static final int LINE = 0x1CFFFFFF;
	public static final int CARD = 0x0EFFFFFF;
	public static final int CARD_HOVER = 0x1AFFFFFF;
	public static final int SELECTED = 0x22FFFFFF;
	public static final int SELECTED_LINE = 0x30FFFFFF;
	private static final int BLACK = 0x050709;

	private ShadowLook() {
	}

	public static boolean clickGui() {
		return StrayConfig.get().clickGuiShadow();
	}

	public static boolean gui() {
		return StrayConfig.get().guiStyleShadow();
	}

	public static boolean hud() {
		return StrayConfig.get().hudStyleShadow();
	}

	public static boolean chat() {
		StrayConfig config = StrayConfig.get();
		return config.themedChatEnabled && config.chatStyleShadow();
	}

	/** The one see-through black every Shadow surface uses (HUD, chat, GUIs, menu). */
	public static final int FILL = 0xC8050709;

	/** Panel fill at the given opacity (0..1): black, mostly see-through. */
	public static int fill(float opacity) {
		return Theme.withAlpha(BLACK, Math.round(StrayConfig.clamp(opacity, 0f, 1f) * 0.86f * 255f));
	}

	/** A window or HUD panel: shadow outside the edge, translucent black inside, hairline edge. */
	public static void panel(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, int fill) {
		if (w < 2f || h < 2f) {
			return;
		}
		float r = Math.min(Math.max(2f, radius), Math.min(w, h) * 0.5f);
		shadow(graphics, x, y, w, h, r, Math.min(w, h) < 40f ? 8 : 12, 0x60);
		GuiDraw.roundedFine(graphics, x, y, w, h, r, fill);
		GuiDraw.hairline(graphics, x, y, w, h, r, LINE);
	}

	private static final net.minecraft.resources.Identifier SOFT = dev.stray.Stray.id("textures/gui/shadow_soft.png");
	private static final int TEX = 64;
	private static final float MARGIN = 24f;

	/**
	 * A soft drop shadow: a pre-blurred rounded square drawn as nine pieces, so it is one
	 * smooth gradient at any size. {@code extent} is how far it reaches past the edge,
	 * {@code strength} its darkest alpha (under the panel).
	 */
	public static void shadow(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float r, int extent, int strength) {
		if (strength <= 0 || w <= 0f || h <= 0f) {
			return;
		}
		soft(graphics, x, y + 1.5f, w, h, extent, Math.min(255, strength) << 24);
	}

	/** The same soft falloff in a colour (with its alpha), centred on the box: a glow. */
	public static void glow(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int extent, int argb) {
		if ((argb >>> 24) == 0 || w <= 0f || h <= 0f) {
			return;
		}
		soft(graphics, x, y, w, h, extent, argb);
	}

	private static void soft(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int extent, int color) {
		float e = extent;
		float k = e / MARGIN;
		float X = x - e;
		float Y = y - e;
		float W = w + e * 2f;
		float H = h + e * 2f;
		float cs = Math.min(32f * k, Math.min(W, H) * 0.5f);
		float mw = W - cs * 2f;
		float mh = H - cs * 2f;
		piece(graphics, X, Y, cs, cs, 0, 0, 32, 32, color);
		piece(graphics, X + W - cs, Y, cs, cs, 32, 0, 32, 32, color);
		piece(graphics, X, Y + H - cs, cs, cs, 0, 32, 32, 32, color);
		piece(graphics, X + W - cs, Y + H - cs, cs, cs, 32, 32, 32, 32, color);
		if (mw > 0f) {
			piece(graphics, X + cs, Y, mw, cs, 31, 0, 1, 32, color);
			piece(graphics, X + cs, Y + H - cs, mw, cs, 31, 32, 1, 32, color);
		}
		if (mh > 0f) {
			piece(graphics, X, Y + cs, cs, mh, 0, 31, 32, 1, color);
			piece(graphics, X + W - cs, Y + cs, cs, mh, 32, 31, 32, 1, color);
		}
		if (mw > 0f && mh > 0f) {
			piece(graphics, X + cs, Y + cs, mw, mh, 31, 31, 1, 1, color);
		}
	}

	private static void piece(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int u, int v, int rw, int rh, int color) {
		GuiDraw.blit(graphics, SOFT, x, y, w, h, u, v, rw, rh, TEX, TEX, color);
	}

	/** A row, button or slot inside a panel: no shadow, faint white fill. */
	public static void card(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, boolean hover, boolean selected) {
		if (w < 1f || h < 1f) {
			return;
		}
		float r = Math.min(Math.max(1f, radius), Math.min(w, h) * 0.5f);
		GuiDraw.roundedFine(graphics, x, y, w, h, r, selected ? SELECTED : hover ? CARD_HOVER : CARD);
		if (selected) {
			GuiDraw.hairline(graphics, x, y, w, h, r, SELECTED_LINE);
		}
	}
}
