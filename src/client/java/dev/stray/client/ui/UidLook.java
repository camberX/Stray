package dev.stray.client.ui;

import dev.stray.client.render.GuiDraw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The uid? style's drawing: a layered dark frame, a thin colour strip on top, group boxes with
 * their title set into the top line, 8 px gradient checkboxes, 7 px slider tracks, gradient
 * dropdown boxes and plain bordered buttons. Colours and offsets follow the menu it copies, in
 * its own pixels (one unit is one screen pixel at 100 % DPI scale).
 */
public final class UidLook {
	public static final int BLACK = 0xFF000000;
	public static final int EDGE = 0xFF0C0C0C;
	public static final int FRAME_LIGHT = 0xFF3C3C3C;
	public static final int FRAME = 0xFF282828;
	public static final int GROUP = 0xFF171717;
	public static final int GROUP_EDGE = 0xFF282828;
	/**
	 * Labels: the original's 0xC8 white, a little brighter, as its ClearType text reads bolder
	 * than Minecraft's plain grayscale glyphs.
	 */
	public static final int LABEL = 0xE0FFFFFF;
	public static final int LABEL_OFF = 0x46FFFFFF;
	public static final int VALUE = 0xA0FFFFFF;
	public static final int KEY = 0x64FFFFFF;
	public static final int KEY_HOVER = 0x82FFFFFF;
	public static final int KEY_LISTEN = 0xFFFF0000;
	/**
	 * Minecraft puts this font's glyphs higher in the line than GDI does: 2 px down centres a
	 * label's capitals on its 8 px checkbox and a value in its 20 px box (measured on screen).
	 */
	private static final float TEXT_DROP = 2f;

	private UidLook() {
	}

	// ------------------------------------------------------------------ colour math

	/** Adds the same amount to each channel, clamped (the menu's add_color). */
	public static int add(int color, int amount) {
		int a = color >>> 24;
		int r = Math.min(255, (color >> 16 & 0xFF) + amount);
		int g = Math.min(255, (color >> 8 & 0xFF) + amount);
		int b = Math.min(255, (color & 0xFF) + amount);
		return a << 24 | r << 16 | g << 8 | b;
	}

	/** Takes the same amount from each channel, clamped (the menu's subtract_color). */
	public static int sub(int color, int amount) {
		return add2(color, -amount);
	}

	private static int add2(int color, int amount) {
		int a = color >>> 24;
		int r = Math.max(0, Math.min(255, (color >> 16 & 0xFF) + amount));
		int g = Math.max(0, Math.min(255, (color >> 8 & 0xFF) + amount));
		int b = Math.max(0, Math.min(255, (color & 0xFF) + amount));
		return a << 24 | r << 16 | g << 8 | b;
	}

	// ------------------------------------------------------------------ primitives

	public static void rect(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int color) {
		GuiDraw.fill(graphics, x, y, w, h, color);
	}

	/** A 1 px outline drawn inside the rectangle. */
	public static void outline(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int color) {
		rect(graphics, x, y, w, 1, color);
		rect(graphics, x, y + h - 1, w, 1, color);
		rect(graphics, x, y + 1, 1, h - 2, color);
		rect(graphics, x + w - 1, y + 1, 1, h - 2, color);
	}

	public static void vertical(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int top, int bottom) {
		GuiDraw.fillGradient(graphics, x, y, w, h, top, bottom);
	}

	/** Left to right in columns: Minecraft only has a vertical gradient fill. */
	public static void horizontal(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int left, int right) {
		int steps = Math.max(1, Math.round(w));
		float step = w / steps;
		for (int i = 0; i < steps; i++) {
			float t = steps == 1 ? 0f : i / (float) (steps - 1);
			rect(graphics, x + i * step, y, step + 0.02f, h, mix(left, right, t));
		}
	}

	private static int mix(int a, int b, float t) {
		int aa = a >>> 24, ar = a >> 16 & 0xFF, ag = a >> 8 & 0xFF, ab = a & 0xFF;
		int ba = b >>> 24, br = b >> 16 & 0xFF, bg = b >> 8 & 0xFF, bb = b & 0xFF;
		return Math.round(aa + (ba - aa) * t) << 24 | Math.round(ar + (br - ar) * t) << 16
			| Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
	}

	// ------------------------------------------------------------------ text

	/**
	 * Text at exactly (x, y) through the pose: GuiDraw rounds to whole menu units, which at
	 * 125 % puts every other row half a screen pixel off and blurs it.
	 */
	static void draw(GuiGraphicsExtractor graphics, Font font, Component value, float x, float y, float scale, int color) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		if (scale != 1f) {
			graphics.pose().scale(scale, scale);
		}
		graphics.text(font, MenuFont.crisp(value, GuiDraw.poseScale(graphics)), 0, 0, GuiDraw.fade(color), false);
		graphics.pose().popMatrix();
	}

	/** Any text while uid? draws, placed on whole screen pixels. */
	public static void drawSnapped(GuiGraphicsExtractor graphics, Font font, Component value, float x, float y, int color) {
		int family = MenuFont.uidFamily(value);
		if (family > 0 && UidText.ready()) {
			UidText.draw(graphics, family, value.getString(), x, y, color);
		} else {
			draw(graphics, font, value, snap(x), snap(y), 1f, color);
		}
		// Rows not drawn in this look still count toward their group's height through their text.
		UidMenu.extent(y + 11);
	}

	/** Onto a whole screen pixel: the glyphs are rasterized at this exact scale, so they then land 1:1. */
	static float snap(float v) {
		double s = UidMenu.dpiScale();
		return (float) (Math.round(v * s) / s);
	}

	public static void text(GuiGraphicsExtractor graphics, Font font, String value, float x, float y, int color) {
		if ((color >>> 24) < 4 || value == null || value.isEmpty()) {
			return;
		}
		if (UidText.ready()) {
			UidText.draw(graphics, 7, value, x, y + TEXT_DROP, color);
			return;
		}
		draw(graphics, font, MenuFont.uid(value), snap(x), snap(y + TEXT_DROP), 1f, color);
	}

	public static void bold(GuiGraphicsExtractor graphics, Font font, String value, float x, float y, int color) {
		if ((color >>> 24) < 4 || value == null || value.isEmpty()) {
			return;
		}
		if (UidText.ready()) {
			UidText.draw(graphics, 8, value, x, y + TEXT_DROP, color);
			return;
		}
		draw(graphics, font, MenuFont.uidBold(value), snap(x), snap(y + TEXT_DROP), 1f, color);
	}

	/**
	 * The pixel font is only sharp at whole multiples of its pixels: draw it at the nearest
	 * whole multiple of the DPI scale (so at 125 % it stays 1:1), centred where 8 px would sit.
	 */
	private static float smallScale() {
		double s = UidMenu.dpiScale();
		return (float) (Math.max(1, Math.round(s)) / s);
	}

	public static void small(GuiGraphicsExtractor graphics, Font font, String value, float x, float y, int color) {
		if ((color >>> 24) < 4 || value == null || value.isEmpty()) {
			return;
		}
		float k = smallScale();
		draw(graphics, font, MenuFont.uidSmall(value), snap(x), snap(y + (1f - k) * 4f), k, color);
	}

	/** Small text with a 1 px black outline (slider values). */
	public static void smallOutlined(GuiGraphicsExtractor graphics, Font font, String value, float x, float y, int color) {
		if ((color >>> 24) < 4 || value == null || value.isEmpty()) {
			return;
		}
		int shade = (color >>> 24) << 24;
		Component c = MenuFont.uidSmall(value);
		float k = smallScale();
		float px = (float) (1.0 / UidMenu.dpiScale());
		float sx = snap(x);
		float sy = snap(y + (1f - k) * 4f);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				if (dx != 0 || dy != 0) {
					draw(graphics, font, c, sx + dx * px * Math.round(k * UidMenu.dpiScale()), sy + dy * px * Math.round(k * UidMenu.dpiScale()), k, shade);
				}
			}
		}
		draw(graphics, font, c, sx, sy, k, color);
	}

	public static float width(Font font, String value) {
		if (value != null && UidText.ready()) {
			return UidText.width(7, value);
		}
		return value == null ? 0f : font.width(MenuFont.uid(value));
	}

	public static float boldWidth(Font font, String value) {
		if (value != null && UidText.ready()) {
			return UidText.width(8, value);
		}
		return value == null ? 0f : font.width(MenuFont.uidBold(value));
	}

	public static float smallWidth(Font font, String value) {
		return value == null ? 0f : font.width(MenuFont.uidSmall(value)) * smallScale();
	}

	// ------------------------------------------------------------------ window

	/**
	 * The window: 1 px #0c0c0c, 1 px #3c3c3c, a #282828 band, another 1 px #3c3c3c and the
	 * dark inside (draw_window_frame).
	 */
	public static void window(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		outline(graphics, x, y, w, h, EDGE);
		outline(graphics, x + 1, y + 1, w - 2, h - 2, FRAME_LIGHT);
		rect(graphics, x + 2, y + 2, w - 4, h - 4, FRAME);
		outline(graphics, x + 5, y + 5, w - 10, h - 10, FRAME_LIGHT);
		rect(graphics, x + 6, y + 6, w - 12, h - 12, EDGE);
		// The textured inside of the original, as a flat shade a touch lighter than the rail.
		rect(graphics, x + 6, y + 10, w - 12, h - 16, 0xFF111111);
	}

	/** Blue to pink to yellow across the top, with a dark line under it (draw_window_accent). */
	public static void strip(GuiGraphicsExtractor graphics, float x, float y, float w) {
		float sx = x + 7;
		float sy = y + 7;
		float sw = w - 14;
		float half = (float) Math.floor(sw / 2f);
		horizontal(graphics, sx, sy, half, 2, 0xFF37B1DA, 0xFFCC46CD);
		horizontal(graphics, sx + half, sy, sw - half, 2, 0xFFCC46CD, 0xFFCCE335);
		rect(graphics, sx, sy + 1, sw, 2, 0x77000000);
	}

	/**
	 * The tab rail: a #0c0c0c column with a black and a #282828 line on its right, open where
	 * the active tab is so that tab joins the content (draw_window_decorations).
	 */
	public static void rail(GuiGraphicsExtractor graphics, float wx, float wy, float wh, float tabW, float activeY, float activeH) {
		float x = wx + 6;
		float y = wy + 10;
		float topH = activeY - 10;
		float blackX = x + tabW - 2;
		float borderX = x + tabW - 1;
		if (topH > 0) {
			rect(graphics, x, y, tabW, topH, EDGE);
			rect(graphics, blackX, y, 1, topH - 1, BLACK);
			rect(graphics, borderX, y, 1, topH, GROUP_EDGE);
			rect(graphics, x, y + topH - 2, tabW - 1, 1, BLACK);
			rect(graphics, x, y + topH - 1, tabW, 1, GROUP_EDGE);
		}
		float bottomY = y + topH + activeH;
		float bottomH = wy + wh - 6 - bottomY;
		if (bottomH > 0) {
			rect(graphics, x, bottomY, tabW, bottomH, EDGE);
			rect(graphics, blackX, bottomY + 1, 1, bottomH - 1, BLACK);
			rect(graphics, borderX, bottomY, 1, bottomH, GROUP_EDGE);
			rect(graphics, x, bottomY, tabW, 1, GROUP_EDGE);
		}
	}

	/**
	 * A group box with its title set into the top line: #171717 inside, a #0c0c0c outer and a
	 * #282828 inner border, the title 14 px in (Category::draw).
	 */
	public static void group(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, float h, String title) {
		rect(graphics, x + 2, y + 2, w - 3, h - 3, GROUP);
		float tw = boldWidth(font, title);
		// Top line: a 10 px piece, the title, the rest.
		rect(graphics, x, y, 10, 1, EDGE);
		rect(graphics, x + 1, y + 1, 9, 1, GROUP_EDGE);
		float rx = x + 17 + tw;
		float rw = w - tw - 16;
		rect(graphics, rx, y, rw, 1, EDGE);
		rect(graphics, rx, y + 1, rw, 1, GROUP_EDGE);
		rect(graphics, x, y, 1, h, EDGE);
		rect(graphics, x + 1, y + 1, 1, h, GROUP_EDGE);
		rect(graphics, x + w, y, 1, h, EDGE);
		rect(graphics, x + w - 1, y + 1, 1, h, GROUP_EDGE);
		rect(graphics, x, y + h, w + 1, 1, EDGE);
		rect(graphics, x + 1, y + h - 1, w - 1, 1, GROUP_EDGE);
		bold(graphics, font, title, x + 14, y - 5, LABEL);
	}

	/** Track #282828 and thumb #414141, 7 px wide (draw_scrollbar). */
	public static void scrollbar(GuiGraphicsExtractor graphics, float x, float y, float h, float view, float content, float offset) {
		rect(graphics, x, y, 7, h, 0xFF282828);
		if (content <= view) {
			return;
		}
		float thumb = Math.max(16f, h * view / content);
		float ty = y + (h - thumb) * (offset / Math.max(1f, content - view));
		rect(graphics, x, ty, 7, thumb, 0xFF414141);
	}

	/** A 5-3-1 px arrow with a black line on its flat side (draw_up_arrow / draw_down_arrow). */
	public static void arrow(GuiGraphicsExtractor graphics, float x, float y, int color, boolean down) {
		int step = down ? 1 : -1;
		rect(graphics, x, y - step, 5, 1, BLACK);
		rect(graphics, x, y, 5, 1, color);
		rect(graphics, x + 1, y + step, 3, 1, color);
		rect(graphics, x + 2, y + step * 2, 1, 1, color);
	}

	// ------------------------------------------------------------------ controls

	/** 8 px box: #0c0c0c outline, a gradient fill, the accent when on (Checkbox::draw). */
	public static void checkbox(GuiGraphicsExtractor graphics, float x, float y, boolean on, boolean hovered, int accent, boolean enabled) {
		int top = on ? accent : 0xBB646464;
		if (!enabled) {
			top = top & 0x00FFFFFF | 0x46000000;
		}
		int bottom = sub(top, 0x28);
		if (hovered) {
			top = add(top, 0x0A);
			bottom = add(bottom, 0x0A);
		}
		outline(graphics, x, y, 8, 8, EDGE);
		vertical(graphics, x + 1, y + 1, 6, 6, top, bottom);
	}

	/** 7 px track, #0c0c0c outline, gray gradient, accent fill to the value (Slider::draw). */
	public static void slider(GuiGraphicsExtractor graphics, float x, float y, float w, float t, boolean hovered, int accent) {
		outline(graphics, x, y, w, 7, EDGE);
		int upper = hovered ? 0xFF3C3C3C : 0xFF323232;
		int lower = hovered ? 0xFF505050 : 0xFF464646;
		vertical(graphics, x + 1, y + 1, w - 2, 5, upper, lower);
		float fill = (float) Math.floor(Math.max(0f, Math.min(1f, t)) * (w - 2));
		if (fill > 0) {
			vertical(graphics, x + 1, y + 1, fill, 5, accent, sub(accent, 0x3C));
		}
	}

	/** The 20 px dropdown box with its arrow (ComboBox::draw_box). */
	public static void combo(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, String value, boolean highlight) {
		int first = highlight ? 0xFF292929 : 0xFF1F1F1F;
		int second = highlight ? 0xFF2E2E2E : 0xFF242424;
		vertical(graphics, x + 1, y + 1, w - 2, 18, first, second);
		outline(graphics, x, y, w, 20, EDGE);
		String shown = fit(font, value, w - 20);
		text(graphics, font, shown, x + 7, y + 5, VALUE);
		arrow(graphics, x + w - 12, y + 8, VALUE, true);
	}

	/** A dropdown's open list: #232323 rows of 20 px, #191919 under the pointer. */
	public static void popup(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		rect(graphics, x, y, w, h, 0xFF232323);
		outline(graphics, x, y, w, h, EDGE);
	}

	public static void popupRow(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, String label, boolean hovered, boolean selected, int accent) {
		if (hovered) {
			rect(graphics, x + 1, y + 1, w - 2, 18, 0xFF191919);
		}
		String shown = fit(font, label, w - 14);
		if (selected) {
			bold(graphics, font, shown, x + 7, y + 5, accent);
		} else {
			text(graphics, font, shown, x + 7, y + 5, LABEL);
		}
	}

	/** Button: #0c0c0c then #323232 outlines, a dark gradient (Button::draw). */
	public static void button(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, float h, String label, boolean hovered, boolean held) {
		outline(graphics, x, y, w, h, EDGE);
		outline(graphics, x + 1, y + 1, w - 2, h - 2, 0xFF323232);
		int top = held ? 0xFF1E1E1E : hovered ? 0xFF272727 : 0xFF222222;
		int bottom = held ? 0xFF141414 : hovered ? 0xFF232323 : 0xFF1E1E1E;
		vertical(graphics, x + 2, y + 2, w - 4, h - 4, top, bottom);
		String shown = fit(font, label, w - 6);
		bold(graphics, font, shown, x + (w - boldWidth(font, shown)) / 2f, y + (h - 9) / 2f - 1, LABEL);
	}

	/** Text box: #232323 with #0c0c0c, then #191919 with #323232, then a #101010 line (Textbox::draw). */
	public static void textbox(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		rect(graphics, x, y, w, h, 0xFF232323);
		outline(graphics, x, y, w, h, EDGE);
		rect(graphics, x + 1, y + 1, w - 2, h - 2, 0xFF191919);
		outline(graphics, x + 1, y + 1, w - 2, h - 2, 0xFF323232);
		outline(graphics, x + 2, y + 2, w - 4, h - 4, 0xFF101010);
	}

	/** 17 x 11 colour swatch, lighter at the top (ColorPicker::draw). */
	public static void swatch(GuiGraphicsExtractor graphics, float x, float y, int argb) {
		int color = argb | 0xFF000000;
		outline(graphics, x, y, 17, 11, EDGE);
		vertical(graphics, x + 1, y + 1, 15, 9, add(color, 0x14), sub(color, 0x14));
	}

	/** The picker panel: #0c0c0c, #3c3c3c and a #282828 inside. */
	public static void panel(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		outline(graphics, x, y, w, h, EDGE);
		outline(graphics, x + 1, y + 1, w - 2, h - 2, FRAME_LIGHT);
		rect(graphics, x + 2, y + 2, w - 4, h - 4, FRAME);
	}

	public static String fit(Font font, String value, float max) {
		if (value == null) {
			return "";
		}
		if (width(font, value) <= max) {
			return value;
		}
		String dots = "...";
		int end = value.length();
		while (end > 0 && width(font, value.substring(0, end) + dots) > max) {
			end--;
		}
		return value.substring(0, end) + dots;
	}
}
