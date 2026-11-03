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
		shadow(graphics, x, y, w, h, r, Math.min(w, h) < 40f ? 6 : 12, 0x50);
		GuiDraw.roundedFine(graphics, x, y, w, h, r, fill);
		GuiDraw.hairline(graphics, x, y, w, h, r, LINE);
	}

	/** Only the soft shadow, for panels that draw their own fill. */
	public static void shadow(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float r, int steps, int strength) {
		for (int i = steps; i >= 1; i--) {
			float t = 1f - (i - 0.5f) / steps;
			int a = Math.round(strength * t * t);
			if (a <= 0) {
				continue;
			}
			GuiDraw.roundedOutline(graphics, x - i, y - i + 1.5f, w + i * 2f, h + i * 2f, r + i, a << 24, 1f);
		}
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
