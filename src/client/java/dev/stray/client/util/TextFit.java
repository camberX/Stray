package dev.stray.client.util;

import dev.stray.client.render.GuiDraw;
import net.minecraft.client.gui.Font;

/** Cutting labels and text-field contents down to the width they have on screen. */
public final class TextFit {
	private TextFit() {
	}

	/**
	 * What a text field shows when its content is wider than the field: the part around the
	 * cursor, measured in the menu font.
	 */
	public static String clipField(Font font, String value, int cursor, int maxWidth) {
		if (GuiDraw.menuWidth(font, value) <= maxWidth) {
			return value;
		}
		int from = 0;
		while (from < cursor && GuiDraw.menuWidth(font, value.substring(from)) > maxWidth) {
			from++;
		}
		String slice = value.substring(from);
		while (slice.length() > 1 && GuiDraw.menuWidth(font, slice) > maxWidth) {
			slice = slice.substring(0, slice.length() - 1);
		}
		return slice;
	}

	/** Like {@link #small}, but a width of 8 or less is taken to mean "no room known yet" and nothing is cut. */
	public static String smallUnlessTiny(Font font, String value, int maxWidth) {
		if (maxWidth <= 8 || GuiDraw.smallWidth(font, value) <= maxWidth) {
			return value;
		}
		String trimmed = value;
		while (trimmed.length() > 1 && GuiDraw.smallWidth(font, trimmed + "..") > maxWidth) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed + "..";
	}

	/** The label cut to {@code maxWidth} in the small font, ending in ".." when something was cut. */
	public static String small(Font font, String value, int maxWidth) {
		if (GuiDraw.smallWidth(font, value) <= maxWidth) {
			return value;
		}
		String trimmed = value;
		while (trimmed.length() > 1 && GuiDraw.smallWidth(font, trimmed + "..") > maxWidth) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed + "..";
	}
}
