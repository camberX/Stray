package dev.stray.client.render;

import dev.stray.Stray;
import dev.stray.client.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Accent-tinted liquid marble for the title screen and out-of-world menus.
 * Motion is applied when the blit is queued, because GUI draws are deferred.
 */
public final class TitleBackdrop {
	private static final Identifier TEXTURE = Stray.id("textures/gui/title_marble.png");
	private static final int TEX_W = 1920;
	private static final int TEX_H = 1080;

	private TitleBackdrop() {
	}

	public static void draw(GuiGraphicsExtractor graphics, int width, int height) {
		if (graphics == null || width <= 0 || height <= 0) {
			return;
		}
		Theme.refresh();
		float t = System.nanoTime() / 1_000_000_000f;
		layer(graphics, width, height, t, 1.18f, 0.09f, 0.07f, 0f, Theme.ACCENT);
		layer(graphics, width, height, t * 0.82f + 1.7f, 1.26f, 0.11f, 0.09f, 1.1f, Theme.withAlpha(Theme.ACCENT, 90));
		GuiDraw.fill(graphics, 0, 0, width, height, Theme.withAlpha(0x000000, 56));
	}

	private static void layer(
		GuiGraphicsExtractor graphics,
		int width,
		int height,
		float t,
		float zoom,
		float xAmp,
		float yAmp,
		float spin,
		int color
	) {
		float scale = Math.max(width / (float) TEX_W, height / (float) TEX_H) * zoom;
		float dw = TEX_W * scale;
		float dh = TEX_H * scale;
		float ox = (float) Math.sin(t * 0.55) * width * xAmp;
		float oy = (float) Math.cos(t * 0.43) * height * yAmp;
		float x = (width - dw) * 0.5f + ox;
		float y = (height - dh) * 0.5f + oy;
		float rot = (float) Math.sin(t * 0.21 + spin) * 0.06f;
		graphics.pose().pushMatrix();
		graphics.pose().translate(width * 0.5f, height * 0.5f);
		graphics.pose().rotate(rot);
		graphics.pose().translate(-width * 0.5f, -height * 0.5f);
		GuiDraw.blit(graphics, TEXTURE, x, y, dw, dh, 0f, 0f, TEX_W, TEX_H, TEX_W, TEX_H, color);
		graphics.pose().popMatrix();
	}
}
