package dev.stray.client.render;

import dev.stray.Stray;
import dev.stray.client.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Accent-tinted liquid marble for the title screen and out-of-world menus.
 * The texture is grayscale so {@link Theme#ACCENT} recolors it.
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
		float scale = Math.max(width / (float) TEX_W, height / (float) TEX_H);
		float dw = TEX_W * scale;
		float dh = TEX_H * scale;
		float x = (width - dw) * 0.5f;
		float y = (height - dh) * 0.5f;
		GuiDraw.blit(graphics, TEXTURE, x, y, dw, dh, 0f, 0f, TEX_W, TEX_H, TEX_W, TEX_H, Theme.ACCENT);
		GuiDraw.fill(graphics, 0, 0, width, height, Theme.withAlpha(0x000000, 72));
	}
}
