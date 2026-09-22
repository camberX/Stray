package dev.stray.client.render;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.ui.ClickGui;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Right-aligned list of enabled features. Each name keeps one color.
 */
public final class ArrayListHud {
	private ArrayListHud() {
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui || client.screen != null) {
			return;
		}
		if (!StrayConfig.get().arrayList) {
			return;
		}
		draw(graphics, client.font, graphics.guiWidth());
	}

	public static void draw(GuiGraphicsExtractor graphics, Font font, int guiWidth) {
		List<String> labels = new ArrayList<>(ClickGui.enabledLabels());
		labels.sort(Comparator.comparingInt((String label) -> font.width(label)).reversed());
		float y = 1f;
		int line = Math.max(1, font.lineHeight);
		for (String label : labels) {
			int color = ClickGui.colorOf(label);
			float x = guiWidth - font.width(label) - 1f;
			GuiDraw.text(graphics, font, label, x, y, color, true);
			y += line;
		}
	}
}
