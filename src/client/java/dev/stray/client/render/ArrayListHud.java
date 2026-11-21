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
 * row per module in the HUD fill, Google Sans Flex text and a small dot in the module's color.
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
		if (dev.stray.client.ui.Looks.ex() && !dev.stray.client.ui.ExMotion.reduced()) {
			drawExMoving(graphics, font);
			return;
		}
		List<Row> rows = rows(font);
		float right = width(font);
		float y = 0f;
		boolean background = HudChrome.background();
		for (Row row : rows) {
			float x = right - row.width;
			if (dev.stray.client.ui.Looks.uid()) {
				// uid?: flat dark rows, the module's colour as a bar on the right edge.
				if (background) {
					GuiDraw.fillSmooth(graphics, x, y, row.width, ROW_H, 0xC0111111);
				}
				GuiDraw.fillSmooth(graphics, right - 2f, y, 2f, ROW_H, row.color);
				GuiDraw.text(graphics, font, row.text, x + PAD - 1f, GuiDraw.middle(y, ROW_H), ShadowLook.TEXT, false);
			} else if (dev.stray.client.ui.Looks.minecraft()) {
				// Minecraft: chat-black rows, the game's font with its shadow, the module's colour on the right.
				if (background) {
					GuiDraw.fill(graphics, x, y, row.width, ROW_H, Theme.HUD_WINDOW);
				}
				GuiDraw.fill(graphics, right - 1f, y, 1f, ROW_H, row.color);
				GuiDraw.text(graphics, font, row.text, x + PAD - 1f, GuiDraw.middle(y, ROW_H) + 0.5f, 0xFFFFFFFF, true);
			} else if (dev.stray.client.ui.Looks.panels()) {
				// Panels: flat grey rows, the module's colour as a thin bar on the right.
				if (background) {
					GuiDraw.roundedFine(graphics, x, y, row.width, ROW_H, 2f, 0xE01C1C1C);
				}
				GuiDraw.fillSmooth(graphics, right - 1.5f, y + 2f, 1.5f, ROW_H - 4f, row.color);
				GuiDraw.text(graphics, font, row.text, x + PAD - 1f, GuiDraw.middle(y, ROW_H), ShadowLook.TEXT, false);
			} else if (dev.stray.client.ui.Looks.ex()) {
				// Expressive: tonal pills, each with a small coloured shape.
				if (background) {
					GuiDraw.roundedFine(graphics, x, y, row.width, ROW_H, ROW_H * 0.5f, Theme.HUD_WINDOW);
				}
				GuiDraw.text(graphics, font, row.text, x + PAD, GuiDraw.middle(y, ROW_H), ShadowLook.TEXT, false);
				float d = DOT + 2f;
				float dx = right - PAD * 0.5f - d * 0.5f - 2f;
				GuiDraw.roundedFine(graphics, dx - d * 0.5f, y + (ROW_H - d) * 0.5f, d, d, rows.indexOf(row) % 2 == 0 ? d * 0.5f : 1.5f, row.color);
			} else {
				if (background) {
					GuiFrostBlur.blitWindow(graphics, x, y, row.width, ROW_H, ShadowLook.SLOT_R);
					GuiDraw.roundedFine(graphics, x, y, row.width, ROW_H, ShadowLook.SLOT_R, Theme.HUD_WINDOW);
				}
				GuiDraw.text(graphics, font, row.text, x + PAD, GuiDraw.middle(y, ROW_H), ShadowLook.TEXT, false);
				GuiDraw.circle(graphics, right - PAD * 0.5f - DOT * 0.5f - 1f, y + ROW_H * 0.5f, DOT * 0.5f, row.color);
			}
			y += ROW_H + GAP;
		}
	}

	/** Rows on screen last frame by label, and rows on their way out. */
	private static final java.util.Map<String, Row> shown = new java.util.LinkedHashMap<>();
	private static final java.util.Map<String, float[]> leaving = new java.util.LinkedHashMap<>();

	/**
	 * Expressive: new modules slide in from the right on a spatial spring, switched-off ones slide
	 * back out and fade, and the rest glide up or down to their new places.
	 */
	private static void drawExMoving(GuiGraphicsExtractor graphics, Font font) {
		List<Row> rows = rows(font);
		float right = width(font);
		boolean background = HudChrome.background();
		java.util.Set<String> now = new java.util.HashSet<>();
		float y = 0f;
		int index = 0;
		for (Row row : rows) {
			String key = row.text.getString();
			now.add(key);
			if (!shown.containsKey(key)) {
				dev.stray.client.ui.ExMotion.snap("ex-al-in" + key, 0f);
				dev.stray.client.ui.ExMotion.snap("ex-al-y" + key, y);
			}
			leaving.remove(key);
			shown.put(key, row);
			float in = dev.stray.client.ui.ExMotion.spring("ex-al-in" + key, 1f, dev.stray.client.ui.ExMotion.SPATIAL_FAST);
			float ry = dev.stray.client.ui.ExMotion.spring("ex-al-y" + key, y, dev.stray.client.ui.ExMotion.SPATIAL);
			exRow(graphics, font, row, right, ry, in, index++, background);
			y += ROW_H + GAP;
		}
		for (java.util.Iterator<java.util.Map.Entry<String, Row>> it = shown.entrySet().iterator(); it.hasNext(); ) {
			java.util.Map.Entry<String, Row> e = it.next();
			if (!now.contains(e.getKey())) {
				leaving.putIfAbsent(e.getKey(), new float[]{dev.stray.client.ui.ExMotion.spring("ex-al-y" + e.getKey(), 0f, dev.stray.client.ui.ExMotion.SPATIAL)});
				ghosts.put(e.getKey(), e.getValue());
				it.remove();
			}
		}
		for (java.util.Iterator<java.util.Map.Entry<String, float[]>> it = leaving.entrySet().iterator(); it.hasNext(); ) {
			java.util.Map.Entry<String, float[]> e = it.next();
			Row row = ghosts.get(e.getKey());
			float out = dev.stray.client.ui.ExMotion.spring("ex-al-in" + e.getKey(), 0f, dev.stray.client.ui.ExMotion.EFFECTS);
			if (row == null || out < 0.02f) {
				it.remove();
				ghosts.remove(e.getKey());
				continue;
			}
			exRow(graphics, font, row, right, e.getValue()[0], out, 0, background);
		}
	}

	private static final java.util.Map<String, Row> ghosts = new java.util.HashMap<>();

	private static void exRow(GuiGraphicsExtractor graphics, Font font, Row row, float right, float y, float in, int index, boolean background) {
		float was = GuiDraw.alphaScale;
		GuiDraw.alphaScale = was * Math.max(0f, Math.min(1f, in));
		try {
			float x = right - row.width + (1f - in) * (row.width + 8f);
			if (background) {
				GuiDraw.roundedFine(graphics, x, y, row.width, ROW_H, ROW_H * 0.5f, Theme.HUD_WINDOW);
			}
			GuiDraw.text(graphics, font, row.text, x + PAD, GuiDraw.middle(y, ROW_H), ShadowLook.TEXT, false);
			float d = DOT + 2f;
			float dx = x + row.width - PAD * 0.5f - d * 0.5f - 2f;
			GuiDraw.roundedFine(graphics, dx - d * 0.5f, y + (ROW_H - d) * 0.5f, d, d, index % 2 == 0 ? d * 0.5f : 1.5f, row.color);
		} finally {
			GuiDraw.alphaScale = was;
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
		// The styled label is kept with the row: it is measured here and drawn every frame.
		net.minecraft.network.chat.Component text = MenuFont.shadow(label);
		return new Row(text, 0xFF000000 | color, PAD + font.width(text) + PAD + DOT + 2f);
	}

	private record Row(net.minecraft.network.chat.Component text, int color, float width) {
	}
}
