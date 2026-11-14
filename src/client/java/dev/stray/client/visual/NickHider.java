package dev.stray.client.visual;

import dev.stray.client.config.StrayConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;

public final class NickHider {
	/** How deep this thread is inside a rewrite (a plain counter, so reading it allocates nothing). */
	private static final class Depth {
		int value;
	}

	private static final ThreadLocal<Depth> DEPTH = ThreadLocal.withInitial(Depth::new);

	/** Neither name feature is on: every text hook can hand its text straight back. */
	private static boolean idle() {
		StrayConfig config = StrayConfig.get();
		return !config.nickEnabled && !config.hideOtherNames;
	}
	private static String cachedRaw = "";
	private static Component cachedFormatted = Component.empty();
	private static String cachedPlain = "";

	private NickHider() {
	}

	public static boolean active() {
		if (DEPTH.get().value > 0) {
			return false;
		}
		StrayConfig config = StrayConfig.get();
		if (!config.nickEnabled) {
			return false;
		}
		return !realName().isEmpty();
	}

	public static void suppress() {
		DEPTH.get().value++;
	}

	public static void resume() {
		Depth depth = DEPTH.get();
		depth.value = Math.max(0, depth.value - 1);
	}

	public static String realName() {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			String name = client.player.getGameProfile().name();
			return name == null ? "" : name;
		}
		String name = client.getGameProfile().name();
		return name == null ? "" : name;
	}

	public static String plainNick() {
		refreshCache();
		String nick = cachedPlain;
		return nick.isEmpty() ? realName() : nick;
	}

	public static Component formattedNick() {
		refreshCache();
		return cachedFormatted;
	}

	private static String rewriteSelf(String text) {
		if (!active() || text == null || text.isEmpty()) {
			return text;
		}
		String name = realName();
		if (name.isEmpty() || !containsName(text, name)) {
			return text;
		}
		return replaceName(text, name, plainNick());
	}

	private static Component rewriteSelf(Component component) {
		if (!active() || component == null) {
			return component;
		}
		String name = realName();
		if (name.isEmpty() || !containsName(component.getString(), name)) {
			return component;
		}
		DEPTH.get().value++;
		try {
			if (NickSteal.active()) {
				// Rank and level tags live in sibling components before the
				// name, so swap them in flat legacy text. Hover and click
				// events on that line are dropped; only lines with a tag in
				// front of our name take this path.
				String tagged = NickSteal.rewriteTags(NickSteal.toLegacy(component), name);
				if (tagged != null) {
					return parseLegacy(replaceName(tagged, name, plainNick()));
				}
			}
			return rewriteTree(component, name);
		} finally {
			resume();
		}
	}

	/** Your own name (nick hider), then everyone else's (Hide other names). */
	public static String rewrite(String text) {
		if (idle()) {
			return text;
		}
		String out = rewriteSelf(text);
		return DEPTH.get().value > 0 ? out : OtherNames.rewrite(out);
	}

	/**
	 * Only your own name: for chat as it comes in, which Stray's trackers (party finder stats,
	 * dungeon party, leap menu) still read with everyone else's real names. Other names are
	 * swapped later, when the line is drawn.
	 */
	public static Component rewriteOwnName(Component component) {
		return rewriteSelf(component);
	}

	public static Component rewrite(Component component) {
		if (idle()) {
			return component;
		}
		Component out = rewriteSelf(component);
		return DEPTH.get().value > 0 ? out : OtherNames.rewrite(out);
	}

	public static FormattedCharSequence rewrite(FormattedCharSequence sequence) {
		if (idle()) {
			return sequence;
		}
		FormattedCharSequence out = rewriteSelf(sequence);
		return DEPTH.get().value > 0 ? out : OtherNames.rewrite(out);
	}

	/** Reused per thread: the characters and styles of the sequence being checked. */
	private static final class Buf {
		int[] cps = new int[128];
		Style[] styles = new Style[128];
		int len;

		void add(int cp, Style style) {
			if (len == cps.length) {
				cps = java.util.Arrays.copyOf(cps, len * 2);
				styles = java.util.Arrays.copyOf(styles, len * 2);
			}
			cps[len] = cp;
			styles[len] = style;
			len++;
		}
	}

	private static final ThreadLocal<Buf> BUF = ThreadLocal.withInitial(Buf::new);
	private static String nickRunsFor = null;
	private static int[] nickCps = new int[0];
	private static Style[] nickStyles = new Style[0];

	/**
	 * The last stop before text is drawn: titles, the scoreboard and anything else that reaches
	 * the screen as a char sequence. Works on the whole line, so a name split across several
	 * components (Hypixel's scoreboard team prefix and suffix) is still found.
	 */
	private static FormattedCharSequence rewriteSelf(FormattedCharSequence sequence) {
		if (sequence == null || !active()) {
			return sequence;
		}
		String name = realName();
		int n = name.length();
		if (n == 0) {
			return sequence;
		}
		Buf buf = BUF.get();
		buf.len = 0;
		sequence.accept((index, style, cp) -> {
			buf.add(cp, style);
			return true;
		});
		int first = find(buf, name, 0);
		if (first < 0) {
			return sequence;
		}
		refreshNickRuns();
		int size = buf.len;
		int[] outCps = new int[size + 16];
		Style[] outStyles = new Style[size + 16];
		int out = 0;
		int i = 0;
		int at = first;
		while (i < size) {
			if (i == at) {
				Style base = buf.styles[i];
				for (int k = 0; k < nickCps.length; k++) {
					if (out == outCps.length) {
						outCps = java.util.Arrays.copyOf(outCps, out * 2);
						outStyles = java.util.Arrays.copyOf(outStyles, out * 2);
					}
					outCps[out] = nickCps[k];
					outStyles[out] = nickStyles[k].applyTo(base);
					out++;
				}
				i += n;
				at = find(buf, name, i);
				continue;
			}
			if (out == outCps.length) {
				outCps = java.util.Arrays.copyOf(outCps, out * 2);
				outStyles = java.util.Arrays.copyOf(outStyles, out * 2);
			}
			outCps[out] = buf.cps[i];
			outStyles[out] = buf.styles[i];
			out++;
			i++;
		}
		int count = out;
		int[] cps = outCps;
		Style[] styles = outStyles;
		return sink -> {
			for (int k = 0; k < count; k++) {
				if (!sink.accept(k, styles[k], cps[k])) {
					return false;
				}
			}
			return true;
		};
	}

	/** Start of the next whole-word match of {@code name} in the buffer from {@code from}, or -1. */
	private static int find(Buf buf, String name, int from) {
		int n = name.length();
		for (int start = from; start + n <= buf.len; start++) {
			boolean match = true;
			for (int k = 0; k < n; k++) {
				if (buf.cps[start + k] != name.charAt(k)) {
					match = false;
					break;
				}
			}
			if (!match) {
				continue;
			}
			boolean left = start == 0 || !nameCodePoint(buf.cps[start - 1]);
			boolean right = start + n >= buf.len || !nameCodePoint(buf.cps[start + n]);
			if (left && right) {
				return start;
			}
		}
		return -1;
	}

	private static boolean nameCodePoint(int cp) {
		return Character.isLetterOrDigit(cp) || cp == '_';
	}

	/** The nick as characters with their own styles (only what the nick sets itself). */
	private static synchronized void refreshNickRuns() {
		Component nick = formattedNick();
		String key = cachedRaw;
		if (key.equals(nickRunsFor)) {
			return;
		}
		Buf runs = new Buf();
		nick.visit((style, text) -> {
			text.codePoints().forEach(cp -> runs.add(cp, style));
			return java.util.Optional.empty();
		}, Style.EMPTY);
		if (runs.len == 0) {
			String plain = realName();
			plain.codePoints().forEach(cp -> runs.add(cp, Style.EMPTY));
		}
		nickCps = java.util.Arrays.copyOf(runs.cps, runs.len);
		nickStyles = java.util.Arrays.copyOf(runs.styles, runs.len);
		nickRunsFor = key;
	}

	public static Component parseLegacy(String raw) {
		if (raw == null || raw.isEmpty()) {
			return Component.empty();
		}
		MutableComponent root = Component.empty();
		Style style = Style.EMPTY;
		StringBuilder buffer = new StringBuilder();
		for (int i = 0; i < raw.length(); i++) {
			char current = raw.charAt(i);
			if ((current == '&' || current == '§') && i + 1 < raw.length()) {
				char code = Character.toLowerCase(raw.charAt(i + 1));
				ChatFormatting formatting = ChatFormatting.getByCode(code);
				if (formatting != null) {
					flush(root, buffer, style);
					style = formatting == ChatFormatting.RESET ? Style.EMPTY : style.applyFormat(formatting);
					i++;
					continue;
				}
				if (raw.charAt(i + 1) == current) {
					buffer.append(current);
					i++;
					continue;
				}
			}
			buffer.append(current);
		}
		flush(root, buffer, style);
		return root;
	}

	private static void refreshCache() {
		String raw = NickSteal.active() ? NickSteal.name() : StrayConfig.get().nick;
		if (raw == null) {
			raw = "";
		}
		if (raw.equals(cachedRaw)) {
			return;
		}
		cachedRaw = raw;
		cachedFormatted = parseLegacy(raw);
		cachedPlain = ChatFormatting.stripFormatting(cachedFormatted.getString());
	}

	private static Component rewriteTree(Component component, String name) {
		MutableComponent out;
		var contents = component.getContents();
		if (contents instanceof PlainTextContents plain) {
			out = Component.empty().setStyle(component.getStyle());
			appendReplaced(out, plain.text(), component.getStyle(), name);
		} else if (contents instanceof TranslatableContents trans) {
			Object[] args = trans.getArgs();
			Object[] next = new Object[args.length];
			for (int i = 0; i < args.length; i++) {
				Object arg = args[i];
				if (arg instanceof Component nested) {
					next[i] = rewriteTree(nested, name);
				} else if (arg instanceof String text) {
					next[i] = replaceName(text, name, plainNick());
				} else {
					next[i] = arg;
				}
			}
			String fallback = trans.getFallback();
			if (fallback == null) {
				out = Component.translatable(trans.getKey(), next).setStyle(component.getStyle());
			} else {
				out = Component.translatableWithFallback(trans.getKey(), fallback, next).setStyle(component.getStyle());
			}
		} else {
			out = component.plainCopy().setStyle(component.getStyle());
		}
		for (Component sibling : component.getSiblings()) {
			out.append(rewriteTree(sibling, name));
		}
		return out;
	}

	private static void appendReplaced(MutableComponent out, String text, Style style, String name) {
		int cursor = 0;
		while (cursor <= text.length()) {
			int at = indexOfName(text, name, cursor);
			if (at < 0) {
				if (cursor < text.length()) {
					out.append(Component.literal(text.substring(cursor)).setStyle(style));
				}
				return;
			}
			if (at > cursor) {
				out.append(Component.literal(text.substring(cursor, at)).setStyle(style));
			}
			out.append(formattedNick().copy());
			cursor = at + name.length();
		}
	}

	private static String replaceName(String text, String name, String nick) {
		StringBuilder out = new StringBuilder(text.length());
		int cursor = 0;
		while (true) {
			int at = indexOfName(text, name, cursor);
			if (at < 0) {
				out.append(text, cursor, text.length());
				return out.toString();
			}
			out.append(text, cursor, at);
			out.append(nick);
			cursor = at + name.length();
		}
	}

	private static boolean containsName(String text, String name) {
		return indexOfName(text, name, 0) >= 0;
	}

	private static int indexOfName(String text, String name, int from) {
		int at = from;
		while (true) {
			at = text.indexOf(name, at);
			if (at < 0) {
				return -1;
			}
			boolean left = at == 0 || !nameChar(text.charAt(at - 1));
			boolean right = at + name.length() >= text.length() || !nameChar(text.charAt(at + name.length()));
			if (left && right) {
				return at;
			}
			at += name.length();
		}
	}

	private static boolean nameChar(char value) {
		return Character.isLetterOrDigit(value) || value == '_';
	}

	private static void flush(MutableComponent root, StringBuilder buffer, Style style) {
		if (buffer.isEmpty()) {
			return;
		}
		root.append(Component.literal(buffer.toString()).setStyle(style));
		buffer.setLength(0);
	}
}
