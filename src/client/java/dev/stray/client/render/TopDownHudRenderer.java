package dev.stray.client.render;

import dev.stray.client.farming.TopDownView;
import dev.stray.client.ui.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class TopDownHudRenderer {
	private static final float PAD = 4f;

	private TopDownHudRenderer() {
	}

	public static float drawWidth() {
		return TopDownView.WINDOW + PAD * 2f;
	}

	public static float drawHeight() {
		return TopDownView.WINDOW + PAD * 2f;
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.player == null || client.options.hideGui) {
			return;
		}
		if (!TopDownView.showing()) {
			return;
		}
		HudLayout.apply(graphics, client.font, HudLayout.Id.TOP_DOWN, () -> draw(graphics, client.font, 0, 0));
	}

	public static void draw(GuiGraphicsExtractor graphics, Font font, float x, float y) {
		float w = drawWidth();
		float h = drawHeight();
		HudChrome.panel(graphics, x, y, w, h, 6, Theme.HUD_WINDOW, Theme.HUD_LINE, Theme.ACCENT);
		float view = TopDownView.WINDOW;
		float left = x + PAD;
		float top = y + PAD;
		int[] colors = TopDownView.colors();
		int radius = TopDownView.radius();
		int span = radius * 2 + 1;
		if (colors.length != span * span) {
			HudChrome.rounded(graphics, left, top, view, view, 4, Theme.withAlpha(Theme.HUD_CARD, 180));
			return;
		}
		float cell = view / span;
		int index = 0;
		for (int row = 0; row < span; row++) {
			for (int col = 0; col < span; col++) {
				int color = colors[index++];
				GuiDraw.fill(graphics, left + col * cell, top + row * cell, cell + 0.5f, cell + 0.5f, 0xFF000000 | color);
			}
		}
		float mid = left + view * 0.5f;
		float midY = top + view * 0.5f;
		GuiDraw.fill(graphics, mid - 1.5f, midY - 1.5f, 3f, 3f, Theme.ACCENT);
	}
}
