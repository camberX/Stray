package dev.stray.client.ui;

import dev.stray.client.render.GuiDraw;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Dear ImGui {@code StyleColorsDark} (ocornut/imgui). Rounding stays at 0.
 * Colors are the style defaults, packed as ARGB.
 */
public final class ImGuiLook {
	public static final int TEXT = 0xFFFFFFFF;
	public static final int TEXT_DISABLED = 0xFF808080;
	public static final int WINDOW_BG = 0xF00F0F0F;
	public static final int BORDER = 0x806E6E80;
	public static final int FRAME_BG = 0x8A294A7A;
	public static final int FRAME_BG_HOVERED = 0x664296FA;
	public static final int FRAME_BG_ACTIVE = 0xAB4296FA;
	public static final int TITLE_BG_ACTIVE = 0xFF294A7A;
	public static final int CHECK = 0xFF4296FA;
	public static final int BUTTON = 0x664296FA;
	public static final int BUTTON_HOVERED = 0xFF4296FA;
	public static final int BUTTON_ACTIVE = 0xFF0F87FA;
	public static final int HEADER = 0x4F4296FA;
	public static final int HEADER_HOVERED = 0xCC4296FA;
	public static final int SLIDER_GRAB = 0xFF3D85E0;
	public static final int SLIDER_GRAB_ACTIVE = 0xFF4296FA;
	public static final int SCROLLBAR_BG = 0x87050505;
	public static final int SCROLLBAR_GRAB = 0xFF4F4F4F;
	public static final int SCROLLBAR_GRAB_HOVERED = 0xFF696969;

	private ImGuiLook() {
	}

	public static void window(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		GuiDraw.fill(graphics, x, y, w, h, WINDOW_BG);
		border(graphics, x, y, w, h, BORDER);
	}

	public static void titleBar(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		GuiDraw.fill(graphics, x + 1f, y + 1f, Math.max(1f, w - 2f), Math.max(1f, h - 1f), TITLE_BG_ACTIVE);
	}

	public static void frame(GuiGraphicsExtractor graphics, float x, float y, float w, float h, boolean hovered) {
		frame(graphics, x, y, w, h, hovered, false);
	}

	public static void frame(GuiGraphicsExtractor graphics, float x, float y, float w, float h, boolean hovered, boolean active) {
		int fill = active ? FRAME_BG_ACTIVE : hovered ? FRAME_BG_HOVERED : FRAME_BG;
		GuiDraw.fill(graphics, x, y, w, h, fill);
		border(graphics, x, y, w, h, BORDER);
	}

	public static void button(GuiGraphicsExtractor graphics, float x, float y, float w, float h, boolean hovered) {
		button(graphics, x, y, w, h, hovered, false);
	}

	public static void button(GuiGraphicsExtractor graphics, float x, float y, float w, float h, boolean hovered, boolean active) {
		int fill = active ? BUTTON_ACTIVE : hovered ? BUTTON_HOVERED : BUTTON;
		GuiDraw.fill(graphics, x, y, w, h, fill);
		border(graphics, x, y, w, h, BORDER);
	}

	public static void checkbox(GuiGraphicsExtractor graphics, float x, float y, float size, boolean checked, boolean hovered) {
		frame(graphics, x, y, size, size, hovered, false);
		if (checked) {
			check(graphics, x, y, size);
		}
	}

	public static void border(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int color) {
		GuiDraw.fill(graphics, x, y, w, 1f, color);
		GuiDraw.fill(graphics, x, y + h - 1f, w, 1f, color);
		GuiDraw.fill(graphics, x, y, 1f, h, color);
		GuiDraw.fill(graphics, x + w - 1f, y, 1f, h, color);
	}

	private static void check(GuiGraphicsExtractor graphics, float x, float y, float size) {
		int x0 = Math.round(x + size * 0.22f);
		int y0 = Math.round(y + size * 0.52f);
		int x1 = Math.round(x + size * 0.42f);
		int y1 = Math.round(y + size * 0.74f);
		int x2 = Math.round(x + size * 0.78f);
		int y2 = Math.round(y + size * 0.28f);
		mark(graphics, x0, y0, x1, y1);
		mark(graphics, x1, y1, x2, y2);
	}

	private static void mark(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
		int steps = Math.max(1, Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)));
		for (int i = 0; i <= steps; i++) {
			float t = i / (float) steps;
			int px = Math.round(x0 + (x1 - x0) * t);
			int py = Math.round(y0 + (y1 - y0) * t);
			GuiDraw.fill(graphics, px, py, 2f, 2f, CHECK);
		}
	}
}
