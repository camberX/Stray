package dev.stray.client.ui;

import dev.stray.client.render.GuiDraw;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The Minecraft look: the game's own font with its shadow, chat-black see-through panels with
 * square corners, Minecraft's formatting colours (yellow headings, green for on, the chat greys),
 * the item tooltip for windows, and widgets drawn like the game's buttons, fields, sliders and
 * checkboxes. Vanilla screens and the hotbar are left as the game draws them.
 */
public final class MinecraftLook {
	/** The chat box's black. */
	public static final int BG = 0x8C000000;
	public static final int YELLOW = 0xFFFFFF55;
	public static final int GREEN = 0xFF55FF55;
	public static final int WHITE = 0xFFFFFFFF;
	public static final int GREY = 0xFFAAAAAA;
	public static final int DGREY = 0xFF555555;
	/** A faint white rule, as between chat-like sections. */
	public static final int LINE = 0x26FFFFFF;
	public static final int HOVER = 0x24FFFFFF;
	/** The item tooltip's background and its purple border, top and bottom. */
	public static final int TIP_BG = 0xF0100010;
	public static final int TIP_TOP = 0x505000FF;
	public static final int TIP_BOTTOM = 0x5028007F;

	private MinecraftLook() {
	}

	/** A panel: the chat black (the caller's opacity kept if it gave one), square corners. */
	public static void panel(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int fill) {
		if (w < 1f || h < 1f) {
			return;
		}
		int a = (fill >>> 24) & 0xFF;
		GuiDraw.fill(graphics, x, y, w, h, a == 0 ? BG : Math.min(a, 0x8C) << 24);
	}

	/** A card or chip on a panel: a faint white wash, a stronger one on hover or when picked. */
	public static void control(GuiGraphicsExtractor graphics, float x, float y, float w, float h, boolean hover, boolean selected) {
		if (w < 1f || h < 1f) {
			return;
		}
		GuiDraw.fill(graphics, x, y, w, h, selected ? 0x40FFFFFF : hover ? 0x2CFFFFFF : 0x18FFFFFF);
		if (selected) {
			outline(graphics, x, y, w, h, WHITE);
		}
	}

	/** The item tooltip box, drawn the way the game draws it (its corners cut by one pixel). */
	public static void tooltip(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		GuiDraw.fill(graphics, x + 1f, y, w - 2f, h, TIP_BG);
		GuiDraw.fill(graphics, x, y + 1f, 1f, h - 2f, TIP_BG);
		GuiDraw.fill(graphics, x + w - 1f, y + 1f, 1f, h - 2f, TIP_BG);
		GuiDraw.fillGradient(graphics, x + 1f, y + 2f, 1f, h - 4f, TIP_TOP, TIP_BOTTOM);
		GuiDraw.fillGradient(graphics, x + w - 2f, y + 2f, 1f, h - 4f, TIP_TOP, TIP_BOTTOM);
		GuiDraw.fill(graphics, x + 1f, y + 1f, w - 2f, 1f, TIP_TOP);
		GuiDraw.fill(graphics, x + 1f, y + h - 2f, w - 2f, 1f, TIP_BOTTOM);
	}

	/** A one-pixel outline inside the box. */
	public static void outline(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int color) {
		GuiDraw.fill(graphics, x, y, w, 1f, color);
		GuiDraw.fill(graphics, x, y + h - 1f, w, 1f, color);
		GuiDraw.fill(graphics, x, y + 1f, 1f, h - 2f, color);
		GuiDraw.fill(graphics, x + w - 1f, y + 1f, 1f, h - 2f, color);
	}

	/** The game's grey button: black edge, light top and left, dark bottom; white edge on hover. */
	public static void button(GuiGraphicsExtractor graphics, float x, float y, float w, float h, boolean hover, boolean active) {
		GuiDraw.fill(graphics, x, y, w, h, 0xFF000000);
		if (!active) {
			GuiDraw.fill(graphics, x + 1f, y + 1f, w - 2f, h - 2f, 0xFF2B2B2B);
			return;
		}
		GuiDraw.fill(graphics, x + 1f, y + 1f, w - 2f, h - 2f, 0xFF6F6F6F);
		GuiDraw.fill(graphics, x + 1f, y + 1f, w - 2f, 1f, 0xFFA9A9A9);
		GuiDraw.fill(graphics, x + 1f, y + 2f, 1f, h - 4f, 0xFFA9A9A9);
		GuiDraw.fill(graphics, x + 1f, y + h - 3f, w - 2f, 2f, 0xFF555555);
		if (hover) {
			outline(graphics, x, y, w, h, WHITE);
		}
	}

	/** The game's text field: black, a grey edge, white while typing in it. */
	public static void field(GuiGraphicsExtractor graphics, float x, float y, float w, float h, boolean focused) {
		GuiDraw.fill(graphics, x, y, w, h, 0xFF000000);
		outline(graphics, x, y, w, h, focused ? WHITE : 0xFFA0A0A0);
	}

	/** The game's checkbox: a black box with a grey edge (white on hover) and a white tick when on. */
	public static void checkbox(GuiGraphicsExtractor graphics, float x, float y, float size, float t, boolean hover) {
		GuiDraw.fill(graphics, x, y, size, size, 0xFF000000);
		outline(graphics, x, y, size, size, hover ? WHITE : 0xFFA0A0A0);
		if (t <= 0.02f) {
			return;
		}
		int ink = Theme.withAlpha(0xFFFFFF, Math.round(255 * Math.min(1f, t)));
		// A seven by six pixel tick in the middle of the box.
		float px = Math.max(1f, Math.round(size / 10f));
		float ox = x + Math.round((size - 7f * px) / 2f);
		float oy = y + Math.round((size - 6f * px) / 2f);
		int[][] tick = {{6, 0}, {5, 1}, {6, 1}, {4, 2}, {5, 2}, {0, 2}, {0, 3}, {1, 3}, {3, 3}, {4, 3}, {1, 4}, {2, 4}, {3, 4}, {2, 5}};
		for (int[] p : tick) {
			GuiDraw.fill(graphics, ox + p[0] * px, oy + p[1] * px, px, px, ink);
		}
	}

	/** The game's slider: a dark track with a button-like handle at {@code t}. */
	public static void slider(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float t, boolean hover) {
		GuiDraw.fill(graphics, x, y, w, h, 0xFF000000);
		GuiDraw.fill(graphics, x + 1f, y + 1f, w - 2f, h - 2f, 0xFF2B2B2B);
		float hw = Math.min(8f, w);
		float hx = x + (w - hw) * Math.max(0f, Math.min(1f, t));
		button(graphics, Math.round(hx), y, hw, h, hover, true);
	}

	/** The small square before a module's name: filled green when on, a grey outline when off. */
	public static void bullet(GuiGraphicsExtractor graphics, float x, float y, float t) {
		if (t >= 0.5f) {
			GuiDraw.fill(graphics, x, y, 4f, 4f, Theme.withAlpha(GREEN & 0xFFFFFF, Math.round(255 * Math.min(1f, t))));
		} else {
			outline(graphics, x, y, 4f, 4f, DGREY);
		}
	}
}
