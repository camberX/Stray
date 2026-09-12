package dev.stray.client.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sack pickups never enter the inventory. Hypixel announces
 * {@code [Sacks] +345 items.} and puts the breakdown on hover:
 * {@code +345 Coal (Mining Sack, Lava Fishing Sack)}.
 */
public final class SackLive {
	private static final Pattern LINE = Pattern.compile("([+-])\\s*([\\d,]+)\\s+(.+)");
	private static final Pattern MOVED = Pattern.compile(
		"(?i)moved\\s+(?:([\\d,]+)x?\\s+)?(.+?)\\s+to your sacks"
	);

	private SackLive() {
	}

	public static void onChat(Component message) {
		if (message == null || ItemStorage.sackScreenOpen()) {
			return;
		}
		String text = plain(message);
		if (text.isEmpty()) {
			return;
		}
		List<Change> changes = List.of();
		if (isSackLine(text)) {
			changes = parseDeltas(hoverText(message));
			if (changes.isEmpty()) {
				changes = parseDeltas(stripPrefix(text));
			}
		} else if (MOVED.matcher(text).find()) {
			changes = parseMoved(text);
		}
		if (changes.isEmpty()) {
			return;
		}
		SkyblockItems.load();
		for (Change change : changes) {
			String id = SkyblockItems.idFromName(change.name);
			if (id != null) {
				ItemStorage.applySackDelta(id, change.delta);
			}
		}
	}

	private static boolean isSackLine(String text) {
		String lower = text.toLowerCase(Locale.ROOT);
		return lower.contains("[sacks]")
			|| lower.contains("sacks »")
			|| lower.contains("sacks >")
			|| lower.contains("sacks:");
	}

	private static String stripPrefix(String text) {
		return text.replaceAll("(?i)\\[sacks\\]|sacks\\s*[»>]\\s*|sacks:\\s*", " ").trim();
	}

	private static List<Change> parseMoved(String text) {
		Matcher matcher = MOVED.matcher(text);
		List<Change> out = new ArrayList<>();
		if (matcher.find()) {
			long amount = matcher.group(1) == null ? 1L : number(matcher.group(1));
			String name = cleanName(matcher.group(2));
			if (amount > 0L && !skipName(name)) {
				out.add(new Change(name, amount));
			}
		}
		return out;
	}

	private static List<Change> parseDeltas(String text) {
		if (text == null || text.isBlank()) {
			return List.of();
		}
		List<Change> out = new ArrayList<>();
		for (String raw : text.split("\\R")) {
			String line = raw.replaceAll("§.", "").trim();
			if (line.isEmpty()) {
				continue;
			}
			Matcher matcher = LINE.matcher(line);
			if (!matcher.find()) {
				continue;
			}
			String name = cleanName(matcher.group(3));
			if (skipName(name)) {
				continue;
			}
			long amount = number(matcher.group(2));
			if (amount == 0L) {
				continue;
			}
			if ("-".equals(matcher.group(1))) {
				amount = -amount;
			}
			out.add(new Change(name, amount));
		}
		return out;
	}

	private static boolean skipName(String name) {
		if (name.isEmpty()) {
			return true;
		}
		String lower = name.toLowerCase(Locale.ROOT);
		return lower.equals("item")
			|| lower.equals("items")
			|| lower.startsWith("from ")
			|| lower.startsWith("added ")
			|| lower.startsWith("this message")
			|| lower.startsWith("last ");
	}

	private static String cleanName(String raw) {
		if (raw == null) {
			return "";
		}
		String name = raw.replaceAll("§.", "").trim();
		name = name.replaceAll("(?i)\\s*\\([^)]*\\)\\s*$", "");
		name = name.replaceAll("\\s+", " ").trim();
		return name;
	}

	private static long number(String raw) {
		if (raw == null || raw.isBlank()) {
			return 0L;
		}
		try {
			return Long.parseLong(raw.replace(",", ""));
		} catch (NumberFormatException ignored) {
			return 0L;
		}
	}

	private static String plain(Component message) {
		if (message == null) {
			return "";
		}
		String text = ChatFormatting.stripFormatting(message.getString());
		return text == null ? "" : text.trim();
	}

	private static String hoverText(Component message) {
		StringBuilder out = new StringBuilder();
		collectHover(message, out);
		return out.toString();
	}

	private static void collectHover(Component node, StringBuilder out) {
		if (node == null) {
			return;
		}
		if (node.getStyle().getHoverEvent() instanceof HoverEvent.ShowText show) {
			String text = plain(show.value());
			if (!text.isEmpty() && !out.toString().contains(text)) {
				if (!out.isEmpty()) {
					out.append('\n');
				}
				out.append(text);
			}
		}
		for (Component part : node.toFlatList()) {
			if (part != node && part.getStyle().getHoverEvent() instanceof HoverEvent.ShowText show) {
				String text = plain(show.value());
				if (!text.isEmpty() && !out.toString().contains(text)) {
					if (!out.isEmpty()) {
						out.append('\n');
					}
					out.append(text);
				}
			}
		}
		for (Component child : node.getSiblings()) {
			collectHover(child, out);
		}
	}

	private record Change(String name, long delta) {
	}
}
