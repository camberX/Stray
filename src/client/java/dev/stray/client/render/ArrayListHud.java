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
	private static final float STROKE = 0.5f;
	private static final float SCALE = 0.9f;
	private static final float PAD = 1f;
	private static final float SWATCH = 2f;
	private static final int PANEL = 0x77000000;

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
		rows.sort(Comparator.comparingDouble(Row::width).reversed());
		float textH = Math.max(1, font.lineHeight) * SCALE;
		float rowH = textH + STROKE * 2f;
		float y = 0f;
		for (Row row : rows) {
			drawRow(graphics, font, row, guiWidth, y, rowH);
			y += rowH - STROKE;
		}
	}

	/** Half-pixel outline, padding, the scaled name, its shadow, and the swatch. */
	private static float widthOf(Font font, String label) {
		return font.width(label) * SCALE + SCALE + PAD + SWATCH + STROKE * 2f;
	}

	private static void drawRow(GuiGraphicsExtractor graphics, Font font, Row row, int right, float y, float h) {
		float w = row.width;
		float x = right - w;
		int outline = HudChrome.outline();
		GuiDraw.fillSmooth(graphics, x, y, w, STROKE, outline);
		GuiDraw.fillSmooth(graphics, x, y + h - STROKE, w, STROKE, outline);
		GuiDraw.fillSmooth(graphics, x, y, STROKE, h, outline);
		GuiDraw.fillSmooth(graphics, x + w - STROKE, y, STROKE, h, outline);
		GuiDraw.fillSmooth(graphics, x + STROKE, y + STROKE, w - STROKE * 2f, h - STROKE * 2f, PANEL);
		GuiDraw.fillSmooth(graphics, x + w - STROKE - SWATCH, y + STROKE, SWATCH, h - STROKE * 2f, row.color);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + STROKE + PAD, y + STROKE);
		graphics.pose().scale(SCALE, SCALE);
		graphics.text(font, row.label, 0, 0, row.color, true);
		graphics.pose().popMatrix();
	}

	private record Row(String label, int color, float width) {
	}
}
