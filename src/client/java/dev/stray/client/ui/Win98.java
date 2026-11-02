package dev.stray.client.ui;

import dev.stray.client.render.GuiDraw;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/**
 * Windows 98 drawing kit: the gray 3D bevels, navy title bars, sunken white fields, checkboxes,
 * scroll bars and trackbars, with Minecraft's bitmap font standing in for MS Sans Serif. Used by
 * the Win98 click GUI and the Win98 HUD style. One GUI pixel per bevel line, so it scales with
 * the GUI like the rest of the game.
 */
public final class Win98 {
	// The scheme colors are swapped by sync() between the classic gray scheme and a dark one.
	public static int FACE = 0xFFC0C0C0;
	public static int LIGHT = 0xFFDFDFDF;
	/** The bevel highlight (3D Highlight), not a background. */
	public static int WHITE = 0xFFFFFFFF;
	public static int SHADOW = 0xFF808080;
	public static int DARK = 0xFF000000;
	public static int NAVY = 0xFF000080;
	public static int NAVY_LIGHT = 0xFF1084D0;
	public static int GRAY_TITLE = 0xFF808080;
	public static int GRAY_TITLE_LIGHT = 0xFFB5B5B5;
	public static int TEXT = 0xFF000000;
	public static int DISABLED = 0xFF808080;
	/** Background of text fields, list boxes and drop-down lists. */
	public static int WINDOW = 0xFFFFFFFF;
	/** Text on the navy selection and title bars. */
	public static int HILITE_TEXT = 0xFFFFFFFF;
	/** Secondary text, like descriptions. */
	public static int MUTED = 0xFF404040;
	/** Scroll bar trough. */
	public static int TROUGH = 0xFFE3E3E3;
	/** Accent for values and fills drawn on the window face. */
	public static int ACCENT = 0xFF000080;
	public static final int TEAL = 0xFF008080;
	public static final int TOOLTIP = 0xFFFFFFE1;
	private static boolean dark;

	/** Applies the scheme picked in the config (classic or dark). Cheap when nothing changed. */
	public static void sync() {
		boolean want = dev.stray.client.config.StrayConfig.get().win98Dark;
		if (want == dark) {
			return;
		}
		dark = want;
		if (want) {
			// A dark scheme in the spirit of Win98's own color schemes: same bevels, darker paint.
			FACE = 0xFF3A3A3A;
			LIGHT = 0xFF555555;
			WHITE = 0xFF737373;
			SHADOW = 0xFF202020;
			DARK = 0xFF000000;
			NAVY = 0xFF000080;
			NAVY_LIGHT = 0xFF1084D0;
			GRAY_TITLE = 0xFF2A2A2A;
			GRAY_TITLE_LIGHT = 0xFF555555;
			TEXT = 0xFFE4E4E4;
			DISABLED = 0xFF7A7A7A;
			WINDOW = 0xFF1C1C1C;
			HILITE_TEXT = 0xFFFFFFFF;
			MUTED = 0xFFA8A8A8;
			TROUGH = 0xFF2A2A2A;
			ACCENT = 0xFF7FA8FF;
		} else {
			FACE = 0xFFC0C0C0;
			LIGHT = 0xFFDFDFDF;
			WHITE = 0xFFFFFFFF;
			SHADOW = 0xFF808080;
			DARK = 0xFF000000;
			NAVY = 0xFF000080;
			NAVY_LIGHT = 0xFF1084D0;
			GRAY_TITLE = 0xFF808080;
			GRAY_TITLE_LIGHT = 0xFFB5B5B5;
			TEXT = 0xFF000000;
			DISABLED = 0xFF808080;
			WINDOW = 0xFFFFFFFF;
			HILITE_TEXT = 0xFFFFFFFF;
			MUTED = 0xFF404040;
			TROUGH = 0xFFE3E3E3;
			ACCENT = 0xFF000080;
		}
	}

	public static boolean dark() {
		return dark;
	}

	public static final float TITLE_H = 13;

	private Win98() {
	}

	// ------------------------------------------------------------------ bevels

	/** Raised: window frames, buttons. Light top-left, dark bottom-right, two lines each. */
	public static void raised(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		raised(graphics, x, y, w, h, FACE);
	}

	public static void raised(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int face) {
		if (w < 2 || h < 2) {
			return;
		}
		GuiDraw.fill(graphics, x, y, w, h, DARK);
		GuiDraw.fill(graphics, x, y, w - 1, h - 1, LIGHT);
		GuiDraw.fill(graphics, x + 1, y + 1, w - 2, h - 2, SHADOW);
		GuiDraw.fill(graphics, x + 1, y + 1, w - 3, h - 3, WHITE);
		GuiDraw.fill(graphics, x + 2, y + 2, w - 4, h - 4, face);
	}

	/** Pressed button: the bevel flips. */
	public static void pressed(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		if (w < 2 || h < 2) {
			return;
		}
		GuiDraw.fill(graphics, x, y, w, h, WHITE);
		GuiDraw.fill(graphics, x, y, w - 1, h - 1, DARK);
		GuiDraw.fill(graphics, x + 1, y + 1, w - 2, h - 2, LIGHT);
		GuiDraw.fill(graphics, x + 1, y + 1, w - 3, h - 3, SHADOW);
		GuiDraw.fill(graphics, x + 2, y + 2, w - 4, h - 4, FACE);
	}

	/** Sunken: text fields, list boxes, the clock tray. */
	public static void sunken(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int fill) {
		if (w < 2 || h < 2) {
			return;
		}
		GuiDraw.fill(graphics, x, y, w, h, WHITE);
		GuiDraw.fill(graphics, x, y, w - 1, h - 1, SHADOW);
		GuiDraw.fill(graphics, x + 1, y + 1, w - 2, h - 2, LIGHT);
		GuiDraw.fill(graphics, x + 1, y + 1, w - 3, h - 3, DARK);
		GuiDraw.fill(graphics, x + 2, y + 2, w - 4, h - 4, fill);
	}

	/** Shallow sunken edge (status bar panels, the taskbar tray): one line each side. */
	public static void shallow(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int fill) {
		if (w < 2 || h < 2) {
			return;
		}
		GuiDraw.fill(graphics, x, y, w, h, WHITE);
		GuiDraw.fill(graphics, x, y, w - 1, h - 1, SHADOW);
		GuiDraw.fill(graphics, x + 1, y + 1, w - 2, h - 2, fill);
	}

	/** Etched line around a group box ("frame" with a caption). */
	public static void groupBox(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, float h, String caption) {
		GuiDraw.fill(graphics, x, y, w - 1, 1, SHADOW);
		GuiDraw.fill(graphics, x + 1, y + 1, w - 2, 1, WHITE);
		GuiDraw.fill(graphics, x, y, 1, h - 1, SHADOW);
		GuiDraw.fill(graphics, x + 1, y + 1, 1, h - 2, WHITE);
		GuiDraw.fill(graphics, x, y + h - 2, w - 1, 1, SHADOW);
		GuiDraw.fill(graphics, x, y + h - 1, w, 1, WHITE);
		GuiDraw.fill(graphics, x + w - 2, y, 1, h - 1, SHADOW);
		GuiDraw.fill(graphics, x + w - 1, y, 1, h, WHITE);
		if (caption != null && !caption.isEmpty()) {
			float cw = width(font, caption) + 4;
			GuiDraw.fill(graphics, x + 6, y - 1, cw, 3, FACE);
			text(graphics, font, caption, x + 8, y - 4, TEXT);
		}
	}

	/** Horizontal gradient (title bars). */
	public static void gradientX(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int left, int right) {
		if (w <= 0 || h <= 0) {
			return;
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().rotate((float) (-Math.PI / 2));
		GuiDraw.fillGradient(graphics, -h, 0, h, w, left, right);
		graphics.pose().popMatrix();
	}

	// ------------------------------------------------------------------ windows

	/**
	 * A window: raised frame, title bar with the caption and the minimize / maximize / close
	 * buttons. Returns the x where the caption buttons start (for hit testing by the caller).
	 */
	public static void window(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, float h, String title, boolean active) {
		raised(graphics, x, y, w, h);
		titleBar(graphics, font, x + 3, y + 3, w - 6, title, active);
	}

	public static void titleBar(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, String title, boolean active) {
		gradientX(graphics, x, y, w, TITLE_H, active ? NAVY : GRAY_TITLE, active ? NAVY_LIGHT : GRAY_TITLE_LIGHT);
		// App icon: a tiny Stray mark.
		GuiDraw.fill(graphics, x + 2, y + 2, 9, 9, 0xFF008080);
		GuiDraw.fill(graphics, x + 3, y + 3, 7, 7, 0xFF00C0C0);
		bold(graphics, font, "S", x + 4, y + 3, HILITE_TEXT);
		String shown = fit(font, title, (int) (w - 14 - 3 * 15));
		bold(graphics, font, shown, x + 14, y + 3, active ? HILITE_TEXT : (dark ? 0xFF8A8A8A : FACE));
	}

	/** Caption button (11 tall) with a glyph: "_", "□", "x". */
	public static void captionButton(GuiGraphicsExtractor graphics, float x, float y, float w, float h, char glyph, boolean down) {
		if (down) {
			pressed(graphics, x, y, w, h);
		} else {
			raised(graphics, x, y, w, h);
		}
		float o = down ? 1 : 0;
		float cx = x + w / 2f + o;
		float cy = y + h / 2f + o;
		switch (glyph) {
			case '_' -> GuiDraw.fill(graphics, cx - 3, cy + 1, 6, 2, TEXT);
			case 'o' -> {
				GuiDraw.fill(graphics, cx - 4, cy - 4, 8, 7, TEXT);
				GuiDraw.fill(graphics, cx - 3, cy - 2, 6, 4, FACE);
			}
			case 'r' -> {
				GuiDraw.fill(graphics, cx - 2, cy - 4, 6, 5, TEXT);
				GuiDraw.fill(graphics, cx - 1, cy - 2, 4, 2, FACE);
				GuiDraw.fill(graphics, cx - 4, cy - 1, 6, 5, TEXT);
				GuiDraw.fill(graphics, cx - 3, cy + 1, 4, 2, FACE);
			}
			default -> {
				for (int i = 0; i < 6; i++) {
					GuiDraw.fill(graphics, cx - 3 + i, cy - 3 + i, 2, 1, TEXT);
					GuiDraw.fill(graphics, cx + 2 - i, cy - 3 + i, 2, 1, TEXT);
				}
			}
		}
	}

	/** A push button with a centered label; the focused default button gets the black ring. */
	public static void button(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, float h, String label, boolean down, boolean enabled) {
		if (down) {
			pressed(graphics, x, y, w, h);
		} else {
			raised(graphics, x, y, w, h);
		}
		float tx = x + (w - width(font, label)) / 2f + (down ? 1 : 0);
		float ty = y + (h - 8) / 2f + (down ? 1 : 0);
		if (enabled) {
			text(graphics, font, label, tx, ty, TEXT);
		} else {
			disabledText(graphics, font, label, tx, ty);
		}
	}

	/** Checkbox: sunken white square with a black tick. */
	public static void checkbox(GuiGraphicsExtractor graphics, float x, float y, float size, boolean checked, boolean enabled) {
		sunken(graphics, x, y, size, size, enabled ? WINDOW : FACE);
		if (!checked) {
			return;
		}
		int ink = enabled ? TEXT : SHADOW;
		// The classic 7x7 tick, scaled to the box (13px box -> 1px steps).
		float s = (size - 3) / 7f * 0.85f;
		float ox = x + (size - 7 * s) / 2f;
		float oy = y + (size - 7 * s) / 2f;
		int[][] tick = {{0, 2}, {1, 3}, {2, 4}, {3, 3}, {4, 2}, {5, 1}, {6, 0}};
		for (int[] p : tick) {
			GuiDraw.fill(graphics, ox + p[0] * s, oy + p[1] * s, s, s * 3, ink);
		}
	}

	/** Radio button: a round sunken well with a black dot. */
	public static void radio(GuiGraphicsExtractor graphics, float x, float y, float size, boolean on) {
		float r = size / 2f;
		GuiDraw.circle(graphics, x + r, y + r, r, SHADOW);
		GuiDraw.circle(graphics, x + r + 0.5f, y + r + 0.5f, r - 0.5f, WHITE);
		GuiDraw.circle(graphics, x + r, y + r, r - 1, WINDOW);
		if (on) {
			GuiDraw.circle(graphics, x + r, y + r, Math.max(1.2f, r * 0.4f), TEXT);
		}
	}

	/** Trackbar: a sunken groove and a raised pointer. {@code t} 0..1. */
	public static void trackbar(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float t) {
		float gy = y + h / 2f - 2;
		sunken(graphics, x, gy, w, 4, WINDOW);
		float thumbW = 6;
		float tx = x + (w - thumbW) * Math.max(0, Math.min(1, t));
		raised(graphics, tx, y, thumbW, h);
	}

	/** Combo box: sunken white field, value, raised arrow button. */
	public static void combo(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, float h, String value, boolean open) {
		sunken(graphics, x, y, w, h, WINDOW);
		text(graphics, font, fit(font, value, (int) (w - h - 6)), x + 4, y + (h - 8) / 2f, TEXT);
		float bw = h - 4;
		if (open) {
			pressed(graphics, x + w - bw - 2, y + 2, bw, h - 4);
		} else {
			raised(graphics, x + w - bw - 2, y + 2, bw, h - 4);
		}
		arrowDown(graphics, x + w - bw / 2f - 2 + (open ? 1 : 0), y + h / 2f + (open ? 1 : 0), TEXT);
	}

	public static void arrowDown(GuiGraphicsExtractor graphics, float cx, float cy, int color) {
		GuiDraw.fill(graphics, cx - 3, cy - 1, 7, 1, color);
		GuiDraw.fill(graphics, cx - 2, cy, 5, 1, color);
		GuiDraw.fill(graphics, cx - 1, cy + 1, 3, 1, color);
		GuiDraw.fill(graphics, cx, cy + 2, 1, 1, color);
	}

	public static void arrowUp(GuiGraphicsExtractor graphics, float cx, float cy, int color) {
		GuiDraw.fill(graphics, cx, cy - 2, 1, 1, color);
		GuiDraw.fill(graphics, cx - 1, cy - 1, 3, 1, color);
		GuiDraw.fill(graphics, cx - 2, cy, 5, 1, color);
		GuiDraw.fill(graphics, cx - 3, cy + 1, 7, 1, color);
	}

	/** Vertical scroll bar: arrow buttons, a dithered track and a raised thumb. */
	public static void scrollbar(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float content, float view, float scroll) {
		GuiDraw.fill(graphics, x, y, w, h, TROUGH);
		raised(graphics, x, y, w, w);
		arrowUp(graphics, x + w / 2f, y + w / 2f, TEXT);
		raised(graphics, x, y + h - w, w, w);
		arrowDown(graphics, x + w / 2f, y + h - w / 2f - 1, TEXT);
		float track = h - w * 2;
		if (content <= view || track <= 8) {
			return;
		}
		float thumb = Math.max(8, track * view / content);
		float ty = y + w + (track - thumb) * (scroll / Math.max(1, content - view));
		raised(graphics, x, ty, w, thumb);
	}

	/** The diagonal "size grip" in a resizable window's corner. */
	public static void sizeGrip(GuiGraphicsExtractor graphics, float right, float bottom) {
		for (int i = 0; i < 3; i++) {
			float o = 3 + i * 3;
			for (int k = 0; k < o; k++) {
				GuiDraw.fill(graphics, right - 2 - o + k, bottom - 2 - k, 1, 1, k % 3 == 2 ? FACE : (k % 3 == 0 ? WHITE : SHADOW));
			}
		}
	}

	// ------------------------------------------------------------------ text

	/** MS Sans Serif stand-in: Minecraft's bitmap font, black, no shadow. */
	public static void text(GuiGraphicsExtractor graphics, Font font, String value, float x, float y, int color) {
		if (value == null || value.isEmpty()) {
			return;
		}
		graphics.text(font, Component.literal(value).withStyle(Style.EMPTY), Math.round(x), Math.round(y), color, false);
	}

	public static void bold(GuiGraphicsExtractor graphics, Font font, String value, float x, float y, int color) {
		if (value == null || value.isEmpty()) {
			return;
		}
		graphics.text(font, Component.literal(value).withStyle(ChatFormatting.BOLD), Math.round(x), Math.round(y), color, false);
	}

	/** Grayed-out text with the white emboss under it. */
	public static void disabledText(GuiGraphicsExtractor graphics, Font font, String value, float x, float y) {
		text(graphics, font, value, x + 1, y + 1, WHITE);
		text(graphics, font, value, x, y, SHADOW);
	}

	public static int width(Font font, String value) {
		return value == null ? 0 : font.width(Component.literal(value));
	}

	public static int boldWidth(Font font, String value) {
		return value == null ? 0 : font.width(Component.literal(value).withStyle(ChatFormatting.BOLD));
	}

	public static String fit(Font font, String value, int max) {
		if (value == null) {
			return "";
		}
		if (max <= 6 || width(font, value) <= max) {
			return value;
		}
		String trimmed = value;
		while (trimmed.length() > 1 && width(font, trimmed + "...") > max) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed + "...";
	}

	/**
	 * Re-inks a color meant for a dark background so it reads on Windows gray: whites and light
	 * grays turn black, mid grays dark gray, bright colors get darker but keep their hue.
	 */
	public static int ink(int color) {
		int a = color >>> 24;
		int r = (color >> 16) & 0xFF;
		int g = (color >> 8) & 0xFF;
		int b = color & 0xFF;
		int max = Math.max(r, Math.max(g, b));
		int min = Math.min(r, Math.min(g, b));
		boolean gray = max - min < 40;
		if (dark) {
			// On the dark scheme: light text stays light, dark colors get lifted.
			if (gray) {
				return (a << 24) | (max >= 150 ? 0xE4E4E4 : 0xA8A8A8);
			}
			float lift = max > 0 ? Math.max(1f, 190f / max) : 1f;
			return (a << 24) | (Math.min(255, Math.round(r * lift)) << 16) | (Math.min(255, Math.round(g * lift)) << 8) | Math.min(255, Math.round(b * lift));
		}
		if (gray) {
			if (max >= 150) {
				return (a << 24);
			}
			return (a << 24) | 0x404040;
		}
		float k = max > 0 ? Math.min(1f, 150f / max) : 1f;
		return (a << 24) | (Math.round(r * k) << 16) | (Math.round(g * k) << 8) | Math.round(b * k);
	}

	/** {@link #ink} applied to every color in a component, including legacy section-sign codes. */
	public static Component recolor(Component value) {
		if (value == null) {
			return null;
		}
		net.minecraft.network.chat.MutableComponent out = Component.empty();
		StringBuilder run = new StringBuilder();
		Style[] runStyle = {null};
		net.minecraft.util.StringDecomposer.iterateFormatted(value, Style.EMPTY, (pos, style, codepoint) -> {
			if (runStyle[0] != null && !runStyle[0].equals(style)) {
				out.append(Component.literal(run.toString()).withStyle(inkStyle(runStyle[0])));
				run.setLength(0);
			}
			runStyle[0] = style;
			run.appendCodePoint(codepoint);
			return true;
		});
		if (runStyle[0] != null && !run.isEmpty()) {
			out.append(Component.literal(run.toString()).withStyle(inkStyle(runStyle[0])));
		}
		return out;
	}

	private static Style inkStyle(Style style) {
		net.minecraft.network.chat.TextColor color = style.getColor();
		if (color == null) {
			return style;
		}
		return style.withColor(ink(0xFF000000 | color.getValue()) & 0xFFFFFF);
	}
}
