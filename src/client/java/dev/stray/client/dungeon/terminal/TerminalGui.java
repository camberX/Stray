package dev.stray.client.dungeon.terminal;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.ui.Anim;
import dev.stray.client.ui.MenuFont;
import dev.stray.client.ui.ShadowLook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * The look of the custom terminal: a Shadow-style window (see-through black, soft
 * outward shadow, Inter) with the terminal's grid, where only the slots still to
 * click light up. Slots fade and shrink away as they are solved, a click leaves a
 * short ring and the Melody marker glides between columns.
 * Drawing only: what to click comes from {@link Term}, clicks from {@link TerminalSession}.
 */
final class TerminalGui {
	private static final int DIM_ROSE = 0xFB7185;

	private static int color(String key) {
		return StrayConfig.get().terminalColor(key);
	}

	/** Soft light around a lit slot, in its own colour. */
	private static void glow(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float s, int rgb, float strength) {
		if (!StrayConfig.get().terminalGlow || strength <= 0.01f) {
			return;
		}
		int alpha = Math.round(Mth.clamp(strength, 0f, 1f) * 0x88);
		dev.stray.client.ui.ShadowLook.glow(graphics, x, y, w, h, Math.min(w, h) * 0.25f, Math.max(4, Math.round(9f * s)), (alpha << 24) | (rgb & 0xFFFFFF));
	}

	private static final float[] PRESENT = new float[54];
	private static final int[] TINT = new int[54];
	private static final float[] PULSE = new float[54];
	private static final float[] HOVER = new float[54];
	private static float open;
	private static float progress;
	private static float melodyX = -1f;
	private static float melodyColumn = -1f;
	private static float doneFade;
	private static long lastNs;

	/** Slot under the cursor after the last draw (Melody: the button row), or -1. */
	static int hovered = -1;
	// Where the grid was last drawn, so a click is matched to the slot under the
	// cursor at the moment of the click (not at the last frame).
	private static float lastGx;
	private static float lastGy;
	private static float lastCell;
	private static float lastGap;
	private static Term lastTerm;
	private static float lastGrow = 1f;
	private static float lastCx;
	private static float lastCy;
	// Grid bounds in chest columns/rows: the terminal's usual grid, grown to include any
	// slot the solution uses (like Noamm, which places slots relative to the solution).
	private static int col0 = -1;
	private static int row0 = -1;
	private static int col1 = -1;
	private static int row1 = -1;

	private static void bounds(Term term) {
		if (col0 < 0) {
			col0 = term.startCol();
			row0 = term.startRow();
			col1 = term.startCol() + term.gridW - 1;
			row1 = term.startRow() + term.gridH - 1;
		}
		for (Term.Click click : term.solution) {
			int c = click.slot() % 9;
			int r = click.slot() / 9;
			col0 = Math.min(col0, c);
			col1 = Math.max(col1, c);
			row0 = Math.min(row0, r);
			row1 = Math.max(row1, r);
		}
	}

	/** The slot (Melody: button row) at this point, or -1. */
	static int slotAt(double mx, double my) {
		Term term = lastTerm;
		if (term == null || lastCell <= 0f) {
			return -1;
		}
		// The window is drawn slightly scaled while it opens: undo that first.
		mx = lastCx + (mx - lastCx) / lastGrow;
		my = lastCy + (my - lastCy) / lastGrow;
		float step = lastCell + lastGap;
		int col = (int) Math.floor((mx - lastGx) / step);
		int row = (int) Math.floor((my - lastGy) / step);
		if (mx < lastGx || my < lastGy || mx - lastGx - col * step >= lastCell || my - lastGy - row * step >= lastCell) {
			return -1;
		}
		if (term == Term.MELODY) {
			return col == 5 && row >= 0 && row < ((Term.Melody) term).rows ? row : -1;
		}
		if (col < 0 || col > col1 - col0 || row < 0 || row > row1 - row0) {
			return -1;
		}
		return (row0 + row) * 9 + col0 + col;
	}

	private TerminalGui() {
	}

	static void reset() {
		col0 = -1;
		row0 = -1;
		col1 = -1;
		row1 = -1;
		lastGrow = 1f;
		java.util.Arrays.fill(PRESENT, 0f);
		java.util.Arrays.fill(PULSE, 0f);
		java.util.Arrays.fill(HOVER, 0f);
		open = 0f;
		progress = 0f;
		melodyX = -1f;
		melodyColumn = -1f;
		doneFade = 0f;
		hovered = -1;
		lastNs = 0L;
	}

	static void pulse(int slot) {
		if (slot >= 0 && slot < PULSE.length) {
			PULSE[slot] = 1f;
		}
	}

	private static float scale() {
		return Mth.clamp(StrayConfig.get().terminalScale / 100f, 0.5f, 2f);
	}

	static void render(
		GuiGraphicsExtractor graphics,
		int width,
		int height,
		Term term,
		boolean loaded,
		boolean blocked,
		String done,
		double mouseX,
		double mouseY
	) {
		Minecraft client = Minecraft.getInstance();
		Font font = client.font;
		long now = System.nanoTime();
		float dt = lastNs == 0L ? 0.016f : Mth.clamp((now - lastNs) / 1e9f, 0.001f, 0.05f);
		lastNs = now;
		open = Anim.exp(open, 1f, 14f, dt);
		doneFade = Anim.exp(doneFade, done != null ? 1f : 0f, 12f, dt);
		for (int i = 0; i < PULSE.length; i++) {
			PULSE[i] = Math.max(0f, PULSE[i] - dt * 3.2f);
		}

		float s = scale();
		float cell = 18f * s;
		float gap = 3f * s;
		float pad = 9f * s;
		float header = 15f * s;
		float footer = 0f;
		int cols;
		int rows;
		if (term == Term.MELODY) {
			cols = term.gridW;
			rows = ((Term.Melody) term).rows;
		} else {
			bounds(term);
			cols = col1 - col0 + 1;
			rows = row1 - row0 + 1;
		}
		float gridW = cols * cell + (cols - 1) * gap;
		float gridH = rows * cell + (rows - 1) * gap;
		float panelW = Math.max(gridW + pad * 2f, 128f * s);
		float panelH = header + gridH + pad * 2f + footer;
		float px = (width - panelW) * 0.5f;
		float py = (height - panelH) * 0.5f;
		float gx = px + (panelW - gridW) * 0.5f;
		float gy = py + header + pad;

		// Faint dim so the window stands out from the world.
		GuiDraw.fill(graphics, 0, 0, width, height, Anim.fade(0x40000000, open));

		graphics.pose().pushMatrix();
		float grow = 0.94f + 0.06f * open;
		lastGrow = grow;
		lastCx = px + panelW * 0.5f;
		lastCy = py + panelH * 0.5f;
		graphics.pose().translate(px + panelW * 0.5f, py + panelH * 0.5f);
		graphics.pose().scale(grow, grow);
		graphics.pose().translate(-(px + panelW * 0.5f), -(py + panelH * 0.5f));

		float radius = 10f * s;
		ShadowLook.shadow(graphics, px, py, panelW, panelH, radius, 14, Math.round(0x70 * open));
		GuiDraw.roundedFine(graphics, px, py, panelW, panelH, radius, Anim.fade(ShadowLook.FILL, open));
		GuiDraw.hairline(graphics, px, py, panelW, panelH, radius, Anim.fade(ShadowLook.LINE, open));

		// Header: name left, progress right.
		float ty = py + pad * 0.75f;
		text(graphics, font, term.name, px + pad, ty, s, Anim.fade(blocked ? ShadowLook.MUTED : ShadowLook.TEXT, open));
		hovered = -1;
		lastGx = gx;
		lastGy = gy;
		lastCell = cell;
		lastGap = gap;
		lastTerm = term;
		if (term == Term.MELODY) {
			melody(graphics, font, (Term.Melody) term, gx, gy, cell, gap, s, dt, mouseX, mouseY, loaded);
		} else {
			grid(graphics, font, term, gx, gy, cell, gap, s, dt, mouseX, mouseY, loaded);
		}

		if (doneFade > 0.01f && done != null) {
			GuiDraw.roundedFine(graphics, gx - 3f * s, gy - 3f * s, gridW + 6f * s, gridH + 6f * s, 6f * s, Anim.fade(0xB8050709, doneFade));
			Component c = MenuFont.shadow(done);
			text(graphics, font, done, gx + (gridW - font.width(c) * s) * 0.5f, gy + (gridH - 9f * s) * 0.5f, s, Anim.fade(0xFF000000 | color("solution"), doneFade));
		}
		graphics.pose().popMatrix();
	}

	private static void grid(
		GuiGraphicsExtractor graphics,
		Font font,
		Term term,
		float gx,
		float gy,
		float cell,
		float gap,
		float s,
		float dt,
		double mouseX,
		double mouseY,
		boolean loaded
	) {
		Map<Integer, Integer> order = new HashMap<>();
		for (int i = 0; i < term.solution.size(); i++) {
			order.putIfAbsent(term.solution.get(i).slot(), i);
		}
		float r = 4.5f * s;
		for (int row = 0; row <= row1 - row0; row++) {
			for (int col = 0; col <= col1 - col0; col++) {
				int slot = (row0 + row) * 9 + col0 + col;
				float x = gx + col * (cell + gap);
				float y = gy + row * (cell + gap);
				boolean over = mouseX >= x && mouseX < x + cell && mouseY >= y && mouseY < y + cell;
				Integer index = loaded ? order.get(slot) : null;
				Term.Click click = index == null ? null : term.solution.get(index);
				int tint = click == null ? 0 : tint(term, click, index);
				if (tint != 0) {
					TINT[slot] = tint;
				}
				PRESENT[slot] = Anim.exp(PRESENT[slot], tint != 0 ? 1f : 0f, 16f, dt);
				HOVER[slot] = Anim.exp(HOVER[slot], over && tint != 0 ? 1f : 0f, 22f, dt);
				if (over) {
					hovered = slot;
				}

				GuiDraw.roundedFine(graphics, x, y, cell, cell, r, Anim.fade(over ? ShadowLook.CARD_HOVER : ShadowLook.CARD, open));
				float p = PRESENT[slot];
				if (p > 0.01f) {
					float size = cell * (0.62f + 0.38f * p) * (1f + 0.05f * HOVER[slot]);
					float ox = x + (cell - size) * 0.5f;
					float oy = y + (cell - size) * 0.5f;
					int base = TINT[slot];
					glow(graphics, ox, oy, size, size, s, base, p * open * (0.7f + 0.6f * HOVER[slot]));
					int color = Anim.mix(base, base | 0xFF000000, 0.25f * HOVER[slot]);
					GuiDraw.roundedFine(graphics, ox, oy, size, size, r * (size / cell), Anim.fade(color, p * open));
					if (HOVER[slot] > 0.02f) {
						GuiDraw.hairline(graphics, ox, oy, size, size, r * (size / cell), Anim.fade(0x80FFFFFF, HOVER[slot] * p * open));
					}
					String label = label(term, click, index);
					if (label != null && click != null) {
						Component c = MenuFont.shadow(label);
						float ts = s * 0.9f;
						text(graphics, font, label, x + (cell - font.width(c) * ts) * 0.5f, y + (cell - 9f * ts) * 0.5f + 0.5f, ts, Anim.fade(ShadowLook.TEXT, p * open));
					}
				}
				ring(graphics, slot, x, y, cell, r, s);
			}
		}
	}

	/** Colour of a slot still to click (with its alpha), or 0 for "not highlighted". */
	private static int tint(Term term, Term.Click click, int index) {
		if (term == Term.NUMBERS) {
			return switch (index) {
				case 0 -> 0xC0000000 | color("first");
				case 1 -> 0x78000000 | color("second");
				case 2 -> 0x50000000 | color("third");
				default -> 0;
			};
		}
		if (term == Term.RUBIX) {
			return click.btn() > 0 ? 0x90000000 | color("plus") : 0x90000000 | color("minus");
		}
		return 0x96000000 | color("solution");
	}

	private static String label(Term term, Term.Click click, Integer index) {
		if (click == null || index == null) {
			return null;
		}
		if (term == Term.NUMBERS && index < 3 && StrayConfig.get().terminalShowNumbers) {
			Integer count = ((Term.Numbers) term).counts.get(click.slot());
			return count == null ? null : String.valueOf(count);
		}
		if (term == Term.RUBIX) {
			return (click.btn() > 0 ? "+" : "") + click.btn();
		}
		return null;
	}

	private static void ring(GuiGraphicsExtractor graphics, int slot, float x, float y, float cell, float r, float s) {
		float k = PULSE[slot];
		if (k <= 0.01f) {
			return;
		}
		float grow = (1f - k) * 4f * s;
		GuiDraw.hairline(graphics, x - grow, y - grow, cell + grow * 2f, cell + grow * 2f, r + grow, Anim.fade(0xC0FFFFFF, k * open));
	}

	private static void melody(
		GuiGraphicsExtractor graphics,
		Font font,
		Term.Melody term,
		float gx,
		float gy,
		float cell,
		float gap,
		float s,
		float dt,
		double mouseX,
		double mouseY,
		boolean loaded
	) {
		float r = 4.5f * s;
		float step = cell + gap;
		int rows = term.rows;
		float lanesH = rows * cell + (rows - 1) * gap;
		// Lanes (5 columns x 3 or 4 rows) and the button column on the right.
		for (int row = 0; row < rows; row++) {
			for (int col = 0; col < 5; col++) {
				GuiDraw.roundedFine(graphics, gx + col * step, gy + row * step, cell, cell, r, Anim.fade(row == term.buttonRow ? ShadowLook.CARD_HOVER : ShadowLook.CARD, open));
			}
		}
		if (loaded && term.correct >= 0) {
			melodyColumn = melodyColumn < 0f ? term.correct : Anim.exp(melodyColumn, term.correct, 18f, dt);
			int column = color("column");
			glow(graphics, gx + melodyColumn * step - 1.5f * s, gy - 1.5f * s, cell + 3f * s, lanesH + 3f * s, s, column, 0.45f * open);
			GuiDraw.roundedFine(graphics, gx + melodyColumn * step - 1.5f * s, gy - 1.5f * s, cell + 3f * s, lanesH + 3f * s, r + 1f, Anim.fade(0x40000000 | column, open));
			GuiDraw.hairline(graphics, gx + melodyColumn * step - 1.5f * s, gy - 1.5f * s, cell + 3f * s, lanesH + 3f * s, r + 1f, Anim.fade(0x70000000 | column, open));
		}
		if (loaded && term.current >= 0 && term.buttonRow >= 0 && term.buttonRow < rows) {
			melodyX = melodyX < 0f ? term.current : Anim.exp(melodyX, term.current, 26f, dt);
			boolean onTime = term.current == term.correct;
			int rgb = onTime ? color("button") : color("marker");
			int color = (onTime ? 0xE0000000 : 0xC8000000) | rgb;
			glow(graphics, gx + melodyX * step, gy + term.buttonRow * step, cell, cell, s, rgb, open);
			GuiDraw.roundedFine(graphics, gx + melodyX * step, gy + term.buttonRow * step, cell, cell, r, Anim.fade(color, open));
		}
		float bx = gx + 5 * step;
		for (int row = 0; row < rows; row++) {
			float y = gy + row * step;
			boolean over = mouseX >= bx && mouseX < bx + cell && mouseY >= y && mouseY < y + cell;
			if (over) {
				hovered = row;
			}
			int slot = Term.Melody.CLAY[row];
			HOVER[slot] = Anim.exp(HOVER[slot], over ? 1f : 0f, 22f, dt);
			boolean live = loaded && row == term.buttonRow;
			int buttonRgb = color("button");
			int color = live ? 0xB0000000 | buttonRgb : 0x40000000 | DIM_ROSE;
			if (live) {
				glow(graphics, bx, y, cell, cell, s, buttonRgb, open * (0.8f + 0.5f * HOVER[slot]));
			}
			float size = cell * (1f + 0.05f * HOVER[slot]);
			float o = (cell - size) * 0.5f;
			GuiDraw.roundedFine(graphics, bx + o, y + o, size, size, r, Anim.fade(Anim.mix(color, color | 0xFF000000, 0.2f * HOVER[slot]), open));
			if (HOVER[slot] > 0.02f) {
				GuiDraw.hairline(graphics, bx + o, y + o, size, size, r, Anim.fade(0x80FFFFFF, HOVER[slot] * open));
			}
			ring(graphics, slot, bx, y, cell, r, s);
		}
	}

	private static void text(GuiGraphicsExtractor graphics, Font font, String value, float x, float y, float scale, int color) {
		if (((color >>> 24) & 0xFF) < 6) {
			return;
		}
		GuiDraw.text(graphics, font, MenuFont.shadow(value), x, y, scale, color, true);
	}
}
