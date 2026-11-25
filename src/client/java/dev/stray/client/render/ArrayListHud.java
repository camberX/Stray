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
		List<Row> rows = rows(font);
		float right = width(font);
		float y = 0f;
		boolean background = HudChrome.background();
		if (HudChrome.win98()) {
			drawWin98(graphics, font, rows, right, background);
			return;
		}
		for (Row row : rows) {
			float x = right - row.width;
			if (dev.stray.client.ui.Looks.uid()) {
				// uid?: flat dark rows, the module's colour as a bar on the right edge.
				if (background) {
					GuiDraw.fillSmooth(graphics, x, y, row.width, ROW_H, 0xC0111111);
				}
				GuiDraw.fillSmooth(graphics, right - 2f, y, 2f, ROW_H, row.color);
				GuiDraw.text(graphics, font, row.text, x + PAD - 1f, GuiDraw.middle(y, ROW_H), ShadowLook.TEXT, false);
			} else if (dev.stray.client.ui.Looks.signature()) {
				// Stray: near-black rows, every module marked with the one accent on the right.
				if (background) {
					GuiDraw.roundedFine(graphics, x, y, row.width, ROW_H, 2f, Theme.withAlpha(dev.stray.client.ui.SignatureLook.BG, 0xE0));
				}
				GuiDraw.fillSmooth(graphics, right - 1.5f, y + 2f, 1.5f, ROW_H - 4f, dev.stray.client.ui.SignatureLook.accent());
				GuiDraw.text(graphics, font, row.text, x + PAD - 1f, GuiDraw.middle(y, ROW_H), dev.stray.client.ui.SignatureLook.TEXT, false);
			} else if (dev.stray.client.ui.Looks.minecraft()) {
				// Minecraft: chat-black rows, the game's font with its shadow, the module's colour on the right.
				if (background) {
					GuiDraw.fill(graphics, x, y, row.width, ROW_H, Theme.HUD_WINDOW);
				}
				GuiDraw.fill(graphics, right - 1f, y, 1f, ROW_H, row.color);
				GuiDraw.text(graphics, font, row.text, x + PAD - 1f, GuiDraw.middle(y, ROW_H) + 0.5f, 0xFFFFFFFF, true);
			} else {
				// Modern: flat grey rows, the module's colour as a thin bar on the right.
				if (background) {
					GuiDraw.roundedFine(graphics, x, y, row.width, ROW_H, 2f, 0xE01C1C1C);
				}
				GuiDraw.fillSmooth(graphics, right - 1.5f, y + 2f, 1.5f, ROW_H - 4f, row.color);
				GuiDraw.text(graphics, font, row.text, x + PAD - 1f, GuiDraw.middle(y, ROW_H), ShadowLook.TEXT, false);
			}
			y += ROW_H + GAP;
		}
	}

	/** Windows 98: one raised menu, like a Start menu cascade, with a coloured icon per module. */
	private static void drawWin98(GuiGraphicsExtractor graphics, Font font, List<Row> rows, float right, boolean background) {
		if (rows.isEmpty()) {
			return;
		}
		float textW = 0f;
		for (Row row : rows) {
			textW = Math.max(textW, dev.stray.client.ui.Win98.width(font, row.text.getString()));
		}
		float w = Math.min(right, textW + 22f);
		float h = height(font);
		float x = right - w;
		if (background) {
			dev.stray.client.ui.Win98.sync();
			dev.stray.client.ui.Win98.raised(graphics, x, 0, w, h);
		}
		int ink = background ? dev.stray.client.ui.Win98.TEXT : 0xFFFFFFFF;
		float y = 0f;
		for (Row row : rows) {
			float ty = GuiDraw.middle(y, ROW_H);
			GuiDraw.fill(graphics, x + 5f, y + (ROW_H - 7f) * 0.5f, 7f, 7f, 0xFF000000);
			GuiDraw.fill(graphics, x + 6f, y + (ROW_H - 5f) * 0.5f, 5f, 5f, 0xFF000000 | row.color);
			if (background) {
				dev.stray.client.ui.Win98.text(graphics, font, row.text.getString(), x + 16f, ty, ink);
			} else {
				graphics.text(font, row.text.getString(), Math.round(x + 16f), Math.round(ty), ink, true);
			}
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
		// The styled label is kept with the row: it is measured here and drawn every frame.
		net.minecraft.network.chat.Component text = MenuFont.shadow(label);
		return new Row(text, 0xFF000000 | color, PAD + font.width(text) + PAD + DOT + 2f);
	}

	private record Row(net.minecraft.network.chat.Component text, int color, float width) {
	}
}
