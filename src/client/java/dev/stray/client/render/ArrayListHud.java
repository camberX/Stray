package dev.stray.client.render;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.ui.ClickGui;
import dev.stray.client.ui.MenuFont;
import dev.stray.client.ui.ShadowLook;
import dev.stray.client.ui.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Enabled modules, longest name on top, right-aligned in its HUD box. Shadow look: one rounded
 * row per module in the HUD fill, Inter text and a small dot in the module's color.
 */
public final class ArrayListHud {
	private static final float ROW_H = 13f;
	private static final float GAP = 1f;
	private static final float PAD = 6f;
	private static final float DOT = 3f;
	private static final String[] EXAMPLE = {"Leap Menu", "Dungeon Map", "Nametags"};

	private ArrayListHud() {
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.gui.hud.isHidden() || client.gui.screen() != null) {
			return;
		}
		if (!StrayConfig.get().arrayList) {
			return;
		}
		HudLayout.apply(graphics, client.font, HudLayout.Id.ARRAY_LIST, () -> draw(graphics, client.font));
	}

	/** Width of the widest row, so the layout box hugs the list. */
	public static float width(Font font) {
		float w = 0f;
		for (Row row : rows(font)) {
			w = Math.max(w, row.width);
		}
		return Math.max(w, 40f);
	}

	public static float height(Font font) {
		int n = rows(font).size();
		return n == 0 ? ROW_H : n * ROW_H + (n - 1) * GAP;
	}

	private static void draw(GuiGraphicsExtractor graphics, Font font) {
		List<Row> rows = rows(font);
		float right = width(font);
		float y = 0f;
		boolean background = HudChrome.background();
		for (Row row : rows) {
			float x = right - row.width;
			if (background) {
				GuiFrostBlur.blitWindow(graphics, x, y, row.width, ROW_H, ShadowLook.SLOT_R);
				GuiDraw.roundedFine(graphics, x, y, row.width, ROW_H, ShadowLook.SLOT_R, Theme.HUD_WINDOW);
			}
			GuiDraw.text(graphics, font, MenuFont.shadow(row.label), x + PAD, GuiDraw.middle(y, ROW_H), ShadowLook.TEXT, false);
			GuiDraw.circle(graphics, right - PAD * 0.5f - DOT * 0.5f - 1f, y + ROW_H * 0.5f, DOT * 0.5f, row.color);
			y += ROW_H + GAP;
		}
	}

	private static List<Row> cached = List.of();
	private static long cachedAt;
	private static boolean haveCache;

	/** Rows, rebuilt at most every 100ms: building them walks every module. */
	private static List<Row> rows(Font font) {
		long now = System.nanoTime();
		if (haveCache && now - cachedAt < 100_000_000L) {
			return cached;
		}
		cachedAt = now;
		haveCache = true;
		cached = buildRows(font);
		return cached;
	}

	private static List<Row> buildRows(Font font) {
		StrayConfig config = StrayConfig.get();
		List<Row> rows = new ArrayList<>();
		for (String name : ClickGui.enabledLabels()) {
			rows.add(row(font, ClickGui.display(name), config.arrayListAccent ? Theme.ACCENT : ClickGui.colorOf(name)));
		}
		if (rows.isEmpty() && HudLayout.editorOpen()) {
			// Something to place in the HUD editor even with nothing turned on.
			for (String name : EXAMPLE) {
				rows.add(row(font, name, ClickGui.colorOf(name)));
			}
		}
		rows.sort(Comparator.comparingDouble(Row::width).reversed());
		return rows;
	}

	private static Row row(Font font, String label, int color) {
		return new Row(label, 0xFF000000 | color, PAD + font.width(MenuFont.shadow(label)) + PAD + DOT + 2f);
	}

	private record Row(String label, int color, float width) {
	}
}
