package dev.stray.client.chat;

import dev.stray.client.config.StrayConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites Hypixel NPC chat from {@code [NPC] Name: message} to
 * {@code [Name] message}, keeping yellow brackets and the original name color.
 */
public final class NpcChat {
	private static final Pattern LINE = Pattern.compile(
		"^\\[NPC]\\s+(.+?)\\s*:\\s*(.*)$",
		Pattern.DOTALL
	);
	private static final Style BRACKET = Style.EMPTY.withColor(ChatFormatting.YELLOW);

	private NpcChat() {
	}

	public static Component rewrite(Component message) {
		if (message == null || !StrayConfig.get().npcChatClean) {
			return message;
		}
		List<Span> spans = flatten(message);
		if (spans.isEmpty()) {
			return message;
		}
		String text = concat(spans);
		Matcher matcher = LINE.matcher(text);
		if (!matcher.find() || !leadingBlank(text, matcher.start())) {
			return message;
		}
		int nameStart = matcher.start(1);
		int nameEnd = matcher.end(1);
		if (nameStart >= nameEnd) {
			return message;
		}
		MutableComponent out = Component.empty();
		out.append(Component.literal("[").setStyle(BRACKET));
		appendSlice(out, spans, nameStart, nameEnd);
		out.append(Component.literal("]").setStyle(BRACKET));
		int messageStart = matcher.start(2);
		int messageEnd = matcher.end(2);
		if (messageStart < messageEnd) {
			out.append(Component.literal(" "));
			appendSlice(out, spans, messageStart, messageEnd);
		}
		if (matcher.end() < text.length()) {
			appendSlice(out, spans, matcher.end(), text.length());
		}
		return out;
	}

	private static List<Span> flatten(Component message) {
		List<Span> spans = new ArrayList<>();
		message.visit((style, text) -> {
			if (text != null && !text.isEmpty()) {
				expand(spans, text, style == null ? Style.EMPTY : style);
			}
			return Optional.empty();
		}, Style.EMPTY);
		return spans;
	}

	private static void expand(List<Span> spans, String text, Style base) {
		Style style = base;
		StringBuilder buffer = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char current = text.charAt(i);
			if (current == '§' && i + 1 < text.length()) {
				ChatFormatting formatting = ChatFormatting.getByCode(Character.toLowerCase(text.charAt(i + 1)));
				if (formatting != null) {
					flush(spans, buffer, style);
					style = applyCode(style, formatting, base);
					i++;
					continue;
				}
			}
			buffer.append(current);
		}
		flush(spans, buffer, style);
	}

	private static Style applyCode(Style current, ChatFormatting formatting, Style base) {
		if (formatting == ChatFormatting.RESET) {
			return Style.EMPTY
				.withClickEvent(base.getClickEvent())
				.withHoverEvent(base.getHoverEvent())
				.withInsertion(base.getInsertion());
		}
		return current.applyFormat(formatting);
	}

	private static void flush(List<Span> spans, StringBuilder buffer, Style style) {
		if (buffer.isEmpty()) {
			return;
		}
		spans.add(new Span(buffer.toString(), style));
		buffer.setLength(0);
	}

	private static String concat(List<Span> spans) {
		StringBuilder out = new StringBuilder();
		for (Span span : spans) {
			out.append(span.text);
		}
		return out.toString();
	}

	private static boolean leadingBlank(String text, int until) {
		for (int i = 0; i < until; i++) {
			if (!Character.isWhitespace(text.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	private static void appendSlice(MutableComponent out, List<Span> spans, int start, int end) {
		int cursor = 0;
		for (Span span : spans) {
			int spanEnd = cursor + span.text.length();
			int from = Math.max(start, cursor);
			int to = Math.min(end, spanEnd);
			if (from < to) {
				out.append(Component.literal(span.text.substring(from - cursor, to - cursor)).setStyle(span.style));
			}
			cursor = spanEnd;
			if (cursor >= end) {
				return;
			}
		}
	}

	private record Span(String text, Style style) {
	}
}
