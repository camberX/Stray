package dev.stray.client.chat;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.ui.Theme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Collapses Hypixel stash pickup spam into one clickable line.
 * Clicks and hovers are copied from the original {@code CLICK HERE} span.
 */
public final class StashChat {
	private static final Pattern HEADER = Pattern.compile(
		"(?i)you have\\s+([\\d,]+)\\s+(items?|materials?)\\s+stashed away"
	);
	private static final Pattern TYPES = Pattern.compile(
		"(?i)this totals\\s+([\\d,]+)\\s+types? of materials"
	);
	private static final Pattern CLICK = Pattern.compile(
		"(?i)click here\\s+to pick them up"
	);
	private static final long HOLD_MS = 2500L;
	private static final long BLANK_MS = 1000L;

	private static boolean reentry;
	private static Pending pending;
	private static long lastCompactAt;

	private StashChat() {
	}

	/** {@code null} drops this chat line (held for a later compact line). */
	public static Component filter(Component message) {
		if (reentry || message == null || !StrayConfig.get().stashChatCompact) {
			return message;
		}
		String plain = plain(message);
		if (plain.isEmpty()) {
			return dropBlank() ? null : message;
		}
		Parsed complete = parseComplete(plain);
		if (complete != null) {
			flushPending();
			lastCompactAt = now();
			return compact(complete, message);
		}
		if (isHeaderOnly(plain)) {
			flushPending();
			Matcher header = HEADER.matcher(plain);
			if (header.find()) {
				pending = new Pending(fromHeader(header), message, now());
				return null;
			}
			return message;
		}
		if (pending != null && now() - pending.at <= HOLD_MS) {
			if (isTypesOnly(plain)) {
				Matcher types = TYPES.matcher(plain);
				if (types.find()) {
					pending.parsed = pending.parsed.withTypes(types.group(1));
				}
				pending.held.add(message);
				pending.at = now();
				return null;
			}
			if (CLICK.matcher(plain).find()) {
				Parsed parsed = pending.parsed;
				pending = null;
				lastCompactAt = now();
				return compact(parsed, message);
			}
		}
		flushPending();
		return message;
	}

	private static void flushPending() {
		if (pending == null) {
			return;
		}
		List<Component> held = pending.held;
		pending = null;
		Minecraft client = Minecraft.getInstance();
		if (client.gui == null || held.isEmpty()) {
			return;
		}
		reentry = true;
		try {
			for (Component line : held) {
				client.gui.getChat().addClientSystemMessage(line);
			}
		} finally {
			reentry = false;
		}
	}

	private static Parsed parseComplete(String plain) {
		Matcher header = HEADER.matcher(plain);
		if (!header.find() || !CLICK.matcher(plain).find()) {
			return null;
		}
		Matcher types = TYPES.matcher(plain);
		String typeCount = types.find() ? types.group(1) : "";
		return fromHeader(header).withTypes(typeCount);
	}

	private static Parsed fromHeader(Matcher header) {
		String kind = header.group(2).toLowerCase(Locale.ROOT);
		boolean materials = kind.startsWith("material");
		return new Parsed(header.group(1), materials, "");
	}

	private static boolean isHeaderOnly(String plain) {
		return HEADER.matcher(plain).find()
			&& !CLICK.matcher(plain).find()
			&& !TYPES.matcher(plain).find();
	}

	private static boolean isTypesOnly(String plain) {
		return TYPES.matcher(plain).find()
			&& !HEADER.matcher(plain).find()
			&& !CLICK.matcher(plain).find();
	}

	private static Component compact(Parsed parsed, Component source) {
		ClickEvent click = findClick(source);
		HoverEvent hover = findHover(source);
		if (hover == null) {
			hover = new HoverEvent.ShowText(Component.literal("Click to pick up your stash"));
		}
		int countColor = parsed.materials ? 0x55FFFF : 0xFFAA00;
		MutableComponent line = Component.empty();
		line.append(bit("Stash", style(Theme.ACCENT).withBold(true), click, hover));
		line.append(bit("  ·  ", style(Theme.MUTED), click, hover));
		line.append(bit(parsed.count, style(countColor).withBold(true), click, hover));
		line.append(bit(parsed.materials ? " materials" : " items", style(Theme.TEXT), click, hover));
		if (parsed.materials && !parsed.types.isBlank()) {
			line.append(bit("  ·  ", style(Theme.MUTED), click, hover));
			line.append(bit(parsed.types, style(countColor), click, hover));
			line.append(bit(" types", style(Theme.MUTED), click, hover));
		}
		line.append(bit("  [PICK UP]", style(Theme.ACCENT).withBold(true), click, hover));
		return line;
	}

	private static boolean dropBlank() {
		long at = now();
		if (pending != null && at - pending.at <= HOLD_MS) {
			return true;
		}
		return lastCompactAt != 0L && at - lastCompactAt <= BLANK_MS;
	}

	private static MutableComponent bit(String text, Style look, ClickEvent click, HoverEvent hover) {
		Style style = look;
		if (click != null) {
			style = style.withClickEvent(click);
		}
		if (hover != null) {
			style = style.withHoverEvent(hover);
		}
		return Component.literal(text).withStyle(style);
	}

	private static ClickEvent findClick(Component node) {
		if (node == null) {
			return null;
		}
		ClickEvent event = node.getStyle().getClickEvent();
		if (event != null) {
			return event;
		}
		for (Component part : node.toFlatList()) {
			ClickEvent nested = part.getStyle().getClickEvent();
			if (nested != null) {
				return nested;
			}
		}
		for (Component child : node.getSiblings()) {
			ClickEvent nested = findClick(child);
			if (nested != null) {
				return nested;
			}
		}
		return null;
	}

	private static HoverEvent findHover(Component node) {
		if (node == null) {
			return null;
		}
		HoverEvent event = node.getStyle().getHoverEvent();
		if (event != null) {
			return event;
		}
		for (Component part : node.toFlatList()) {
			HoverEvent nested = part.getStyle().getHoverEvent();
			if (nested != null) {
				return nested;
			}
		}
		for (Component child : node.getSiblings()) {
			HoverEvent nested = findHover(child);
			if (nested != null) {
				return nested;
			}
		}
		return null;
	}

	private static Style style(int color) {
		return Style.EMPTY.withColor(color & 0xFFFFFF);
	}

	private static String plain(Component message) {
		String text = ChatFormatting.stripFormatting(message.getString());
		return text == null ? "" : text.replace('\n', ' ').trim();
	}

	private static long now() {
		return System.currentTimeMillis();
	}

	private record Parsed(String count, boolean materials, String types) {
		Parsed withTypes(String next) {
			return new Parsed(count, materials, next == null ? "" : next);
		}
	}

	private static final class Pending {
		private Parsed parsed;
		private final List<Component> held = new ArrayList<>();
		private long at;

		private Pending(Parsed parsed, Component first, long at) {
			this.parsed = parsed;
			this.held.add(first);
			this.at = at;
		}
	}
}
