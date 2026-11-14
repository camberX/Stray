package dev.stray.client.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.Optional;

/**
 * Small string helpers used all over the mod for chat, scoreboard, tab list,
 * lore and nametag text. They do what the old {@code replaceAll} chains did
 * ({@code "§."} and {@code "\\s+"}) without compiling a regex on every call,
 * which mattered because many of them run per entity per frame.
 */
public final class Text {
	private Text() {
	}

	/** Same as {@code text.replaceAll("§.", "")}: removes a section sign and the character after it. */
	public static String strip(String text) {
		if (text == null) {
			return null;
		}
		int i = text.indexOf('§');
		if (i < 0) {
			return text;
		}
		StringBuilder out = new StringBuilder(text.length());
		out.append(text, 0, i);
		int length = text.length();
		while (i < length) {
			char c = text.charAt(i);
			if (c == '§' && i + 1 < length && !isLineTerminator(text.charAt(i + 1))) {
				i += 2;
				continue;
			}
			out.append(c);
			i++;
		}
		return out.toString();
	}

	/** Same as {@code text.replaceAll("\\s+", " ")}: each run of ASCII whitespace becomes one space. */
	public static String collapse(String text) {
		if (text == null) {
			return null;
		}
		int length = text.length();
		StringBuilder out = null;
		for (int i = 0; i < length; i++) {
			char c = text.charAt(i);
			if (!isSpace(c)) {
				if (out != null) {
					out.append(c);
				}
				continue;
			}
			int end = i + 1;
			while (end < length && isSpace(text.charAt(end))) {
				end++;
			}
			if (out == null) {
				if (c == ' ' && end == i + 1) {
					continue; // already a single space
				}
				out = new StringBuilder(length);
				out.append(text, 0, i);
			}
			out.append(' ');
			i = end - 1;
		}
		return out == null ? text : out.toString();
	}

	/** Same as {@code text.replaceAll("[0-9]", "0")}: used to measure numbers at a steady width. */
	public static String zeroDigits(String text) {
		if (text == null) {
			return null;
		}
		char[] chars = null;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c >= '1' && c <= '9') {
				if (chars == null) {
					chars = text.toCharArray();
				}
				chars[i] = '0';
			}
		}
		return chars == null ? text : new String(chars);
	}

	/** Regex {@code \s}: space, tab, newline, vertical tab, form feed, carriage return. */
	private static boolean isSpace(char c) {
		return c == ' ' || c == '\t' || c == '\n' || c == '\u000b' || c == '\f' || c == '\r';
	}

	/** Characters regex {@code .} does not match by default. */
	private static boolean isLineTerminator(char c) {
		return c == '\n' || c == '\r' || c == '\u0085' || c == ' ' || c == ' ';
	}

	/** The first value that is not null or blank, "" when there is none. */
	public static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return "";
	}

	/** Appends a line break and then the component's text as written (formatting codes kept). */
	public static void appendLine(StringBuilder out, Component component) {
		if (component == null) {
			return;
		}
		out.append('\n');
		component.visit((style, text) -> {
			if (text != null) {
				out.append(text);
			}
			return Optional.empty();
		}, Style.EMPTY);
	}

	/** A whole number that may use thousands separators ("1,234"), 0 when it is not one. */
	public static long count(String raw) {
		if (raw == null || raw.isBlank()) {
			return 0L;
		}
		try {
			return Long.parseLong(raw.replace(",", ""));
		} catch (NumberFormatException ignored) {
			return 0L;
		}
	}
}
