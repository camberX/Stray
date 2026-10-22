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
	private static final float PAD = 1f;
	private static final float SWATCH = 2f;
	private static final int PANEL = 0x77000000;
	private static final int OUTLINE = 0xFF000000;

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
		float textH = Math.max(1, font.lineHeight);
		float rowH = textH + STROKE * 2f;
		float y = 0f;
		for (int i = 0; i < rows.size(); i++) {
			float above = i == 0 ? 0f : rows.get(i - 1).width();
			float below = i + 1 >= rows.size() ? 0f : rows.get(i + 1).width();
			drawRow(graphics, font, rows.get(i), guiWidth, y, rowH, above, below);
			y += rowH - STROKE;
		}
	}

	/** Half-pixel outline, padding, the name, its shadow, and the swatch. */
	private static float widthOf(Font font, String label) {
		return font.width(ClickGui.styled(label)) + 1f + PAD + SWATCH + STROKE * 2f;
	}

	private static void drawRow(GuiGraphicsExtractor graphics, Font font, Row row, int right, float y, float h, float above, float below) {
		float w = row.width;
		float x = right - w;
		float step = below <= 0f ? w : Math.max(0f, w - below);
		if (above <= 0f) {
			GuiDraw.fillSmooth(graphics, x, y, w, STROKE, OUTLINE);
		} else if (above < w) {
			GuiDraw.fillSmooth(graphics, x, y, w - above, STROKE, OUTLINE);
		}
		if (step > 0f) {
			GuiDraw.fillSmooth(graphics, x, y + h - STROKE, step, STROKE, OUTLINE);
		}
		GuiDraw.fillSmooth(graphics, x, y, STROKE, h, OUTLINE);
		GuiDraw.fillSmooth(graphics, x + w - STROKE, y, STROKE, h, OUTLINE);
		float fillTop = y + STROKE;
		float fillH = h - STROKE * 2f;
		if (below <= 0f) {
			GuiDraw.fillSmooth(graphics, x + STROKE, fillTop, w - STROKE * 2f, fillH, PANEL);
			GuiDraw.fillSmooth(graphics, x + w - STROKE - SWATCH, fillTop, SWATCH, fillH, row.color);
		} else {
			if (step > STROKE) {
				GuiDraw.fillSmooth(graphics, x + STROKE, fillTop, step - STROKE, fillH, PANEL);
			}
			float meetX = x + Math.max(step, STROKE);
			float meetW = x + w - STROKE - meetX;
			float meetH = y + h - fillTop;
			GuiDraw.fillSmooth(graphics, meetX, fillTop, meetW, meetH, PANEL);
			GuiDraw.fillSmooth(graphics, x + w - STROKE - SWATCH, fillTop, SWATCH, meetH, row.color);
		}
		graphics.text(
			font,
			ClickGui.styled(row.label),
			Math.round(x + STROKE + PAD),
			Math.round(y + STROKE),
			row.color,
			true
		);
	}

	private record Row(String label, int color, float width) {
	}
}
