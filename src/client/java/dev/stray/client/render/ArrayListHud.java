package dev.stray.client.render;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.ui.ClickGui;
import dev.stray.client.ui.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Right-aligned boxes. Each background is only as wide as that name.
 * Longest name is on top.
 */
public final class ArrayListHud {
	private static final int PANEL = 0x99000000;
	private static final int OUTLINE = 0xFF000000;
	private static final int SWATCH = 2;
	private static final int PAD = 1;

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
		StrayConfig config = StrayConfig.get();
		List<Row> rows = new ArrayList<>();
		for (String name : ClickGui.enabledLabels()) {
			String label = ClickGui.display(name);
			int color = config.arrayListAccent ? Theme.ACCENT : ClickGui.colorOf(name);
			rows.add(new Row(label, color, widthOf(font, label)));
		}
		rows.sort(Comparator.comparingInt(Row::width).reversed());
		int line = Math.max(1, font.lineHeight);
		int rowH = line + 2;
		int y = 0;
		for (Row row : rows) {
			drawRow(graphics, font, row, guiWidth, y, rowH);
			y += rowH - 1;
		}
	}

	/** Outline, one pixel of padding, the name, its shadow, and the swatch. */
	private static int widthOf(Font font, String label) {
		return font.width(label) + 1 + PAD + SWATCH + 2;
	}

	private static void drawRow(GuiGraphicsExtractor graphics, Font font, Row row, int right, int y, int h) {
		int w = row.width;
		int x = right - w;
		GuiDraw.fill(graphics, x, y, w, 1, OUTLINE);
		GuiDraw.fill(graphics, x, y + h - 1, w, 1, OUTLINE);
		GuiDraw.fill(graphics, x, y, 1, h, OUTLINE);
		GuiDraw.fill(graphics, x + w - 1, y, 1, h, OUTLINE);
		GuiDraw.fill(graphics, x + 1, y + 1, w - 2, h - 2, PANEL);
		GuiDraw.fill(graphics, x + w - 1 - SWATCH, y + 1, SWATCH, h - 2, row.color);
		graphics.text(font, row.label, x + 1 + PAD, y + 1, row.color, true);
	}

	private record Row(String label, int color, int width) {
	}
}
