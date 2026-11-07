package dev.stray.client.ui;

import dev.stray.client.render.GuiDraw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Shared look of the wardrobe and loadouts "stage" windows: a dark see-through
 * window with a soft shadow falling outward from its edges, quiet labels, a
 * white primary pill and a dock of slot tiles.
 */
final class StageUi {
	static final int TEXT = ShadowLook.TEXT;
	static final int MUTED = ShadowLook.MUTED;
	static final int FAINT = ShadowLook.FAINT;
	static final int DOT = 0xFF3DDC84;
	static final int CHIP = 0x10FFFFFF;
	static final int CHIP_HOVER = 0x22FFFFFF;
	static final int TILE_SEL = 0x1EFFFFFF;
	static final float R = ShadowLook.WINDOW_R;

	private StageUi() {
	}

	/** The window: the same Shadow window as everywhere else (shadow, blur, opacity, edge). */
	static void window(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float appear) {
		panel(graphics, x, y, w, h, R, appear);
	}

	private static void panel(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float r, float appear) {
		if (appear <= 0.01f) {
			return;
		}
		ShadowLook.shadow(graphics, x, y, w, h, r, 12, Math.round(0x60 * appear));
		dev.stray.client.render.GuiFrostBlur.blitWindow(graphics, x, y, w, h, r, appear);
		GuiDraw.roundedFine(graphics, x, y, w, h, r, fade(ShadowLook.FILL, appear));
		GuiDraw.hairline(graphics, x, y, w, h, r, fade(ShadowLook.edge(), appear));
	}

	/** A soft oval glow with a darker contact shadow under the model's feet. */
	static void floor(GuiGraphicsExtractor graphics, float cx, float cy, float radius) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(cx, cy);
		graphics.pose().scale(1f, 0.26f);
		for (int i = 6; i >= 1; i--) {
			float r = radius * i / 6f;
			GuiDraw.circle(graphics, 0, 0, r, (Math.round(0x10 * (7 - i) / 6f) << 24) | 0x78BEFF);
		}
		GuiDraw.circle(graphics, 0, 0, radius * 0.42f, 0x55000000);
		GuiDraw.circle(graphics, 0, 0, radius * 0.28f, 0x40000000);
		graphics.pose().popMatrix();
	}

	static void dot(GuiGraphicsExtractor graphics, float cx, float cy) {
		GuiDraw.circle(graphics, cx, cy, 3.2f, 0x333DDC84);
		GuiDraw.circle(graphics, cx, cy, 1.8f, DOT);
	}

	static void label(GuiGraphicsExtractor graphics, Font font, String text, float x, float y, int color) {
		GuiDraw.small(graphics, font, text, x, y, color);
	}

	static int labelWidth(Font font, String text) {
		return GuiDraw.smallWidth(font, text);
	}

	/** Big set or loadout name. */
	static void title(GuiGraphicsExtractor graphics, Font font, Component text, float x, float y, float maxW) {
		float scale = 1.6f;
		float tw = font.width(text) * scale;
		if (tw > maxW) {
			scale = Math.max(1.25f, maxW / Math.max(1f, font.width(text)));
		}
		GuiDraw.text(graphics, font, text, x, y, scale, TEXT, false);
	}

	/** A pill button; returns its width. */
	static float pill(
		GuiGraphicsExtractor graphics,
		Font font,
		float x,
		float y,
		String label,
		String key,
		boolean primary,
		boolean hover
	) {
		float h = 16f;
		float lw = GuiDraw.menuWidth(font, label);
		float kw = key == null ? 0f : GuiDraw.smallWidth(font, key) + 6f;
		float w = 14f + lw + (key == null ? 0f : kw + 5f) + 14f;
		int fill = primary ? (hover ? 0xFFFFFFFF : TEXT) : (hover ? CHIP_HOVER : CHIP);
		int ink = primary ? 0xFF0B0E13 : 0xFFC9D0D8;
		GuiDraw.roundedFine(graphics, x, y, w, h, h / 2f, fill);
		GuiDraw.menu(graphics, font, label, x + 14f, GuiDraw.middle(y, h), ink);
		if (key != null) {
			float kx = x + 14f + lw + 5f;
			GuiDraw.roundedFine(graphics, kx, y + 3f, kw, 10f, 3f, primary ? 0x1F000000 : 0x14FFFFFF);
			GuiDraw.small(graphics, font, key, kx + 3f, y + 4.5f, ink);
		}
		return w;
	}

	static float pillWidth(Font font, String label, String key) {
		float lw = GuiDraw.menuWidth(font, label);
		float kw = key == null ? 0f : GuiDraw.smallWidth(font, key) + 6f;
		return 14f + lw + (key == null ? 0f : kw + 5f) + 14f;
	}

	static void chip(GuiGraphicsExtractor graphics, float x, float y, float w, float h, boolean hover) {
		GuiDraw.roundedFine(graphics, x, y, w, h, h / 2f, hover ? CHIP_HOVER : CHIP);
	}

	static void well(GuiGraphicsExtractor graphics, float x, float y, float size, boolean hover) {
		GuiDraw.roundedFine(graphics, x, y, size, size, 4f, hover ? CHIP_HOVER : CHIP);
	}

	/** The floating dock bar behind the slot tiles. */
	static void dock(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		panel(graphics, x, y, w, h, ShadowLook.WINDOW_R, 1f);
	}

	static void tile(GuiGraphicsExtractor graphics, float x, float y, float size, boolean selected, boolean hover) {
		if (selected) {
			GuiDraw.roundedFine(graphics, x, y, size, size, 8f, TILE_SEL);
			GuiDraw.hairline(graphics, x, y, size, size, 8f, 0x26FFFFFF);
		} else if (hover) {
			GuiDraw.roundedFine(graphics, x, y, size, size, 8f, 0x10FFFFFF);
		}
	}

	static int fade(int argb, float t) {
		int a = Math.round((argb >>> 24) * Math.max(0f, Math.min(1f, t)));
		return (a << 24) | (argb & 0xFFFFFF);
	}
}
