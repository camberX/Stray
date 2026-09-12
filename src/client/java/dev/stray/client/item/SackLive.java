package dev.stray.client.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sack pickups never enter the inventory. Chat is
 * {@code [Sacks] +345 items. (Last 2s.)}; hover has the real lines
 * {@code +345 Coal (Mining Sack, …)}. Compacting is
 * {@code +8 items, -1,417 items} with added and removed hover rows.
 * {@code Last 2s} windows overlap, so later messages only apply the leftover.
 * A line with both + and − is compacting (coal out of one sack, enchanted into
 * another). That is the same materials changing form, so only convert raw we
 * already counted — do not treat the +N enchanted as new loot.
 */
public final class SackLive {
	private static final Pattern CHUNK = Pattern.compile("([+-])\\s*([\\d,]+)\\s+([^-+]+?)(?=\\s*[+-]\\s*[\\d,]|$)");
	private static final Pattern LAST = Pattern.compile("(?i)last\\s+(\\d+)\\s*s");
	private static final Pattern MOVED = Pattern.compile(
		"(?i)moved\\s+(?:([\\d,]+)x?\\s+)?(.+?)\\s+to your sacks"
	);
	private static final List<Seen> SEEN = new ArrayList<>();

	private SackLive() {
	}

	public static void reset() {
		SEEN.clear();
	}

	public static void forget(String id) {
		if (id == null || id.isBlank()) {
			return;
		}
		String key = SkyblockRecipes.normalize(id);
		SEEN.removeIf(seen -> seen.id.equals(key));
	}

	public static void onChat(Component message) {
		if (message == null || ItemStorage.sackScreenOpen()) {
			return;
		}
		String text = plain(message);
		if (text.isEmpty() || !isSackLine(text) && !MOVED.matcher(text).find()) {
			return;
		}
		List<Change> changes = isSackLine(text)
			? parseDeltas(hoverText(message))
			: parseMoved(text);
		if (changes.isEmpty()) {
			return;
		}
		int windowMs = windowMs(text);
		apply(changes, windowMs);
	}

	private static void apply(List<Change> changes, int windowMs) {
		long now = System.currentTimeMillis();
		prune(now);
		SkyblockItems.load();
		SkyblockRecipes.load();
		Map<String, Long> net = new LinkedHashMap<>();
		for (Change change : changes) {
			String id = SkyblockItems.idFromName(change.name);
			if (id == null) {
				continue;
			}
			net.merge(id, change.delta, Long::sum);
		}
		if (net.isEmpty()) {
			return;
		}
		boolean compact = false;
		boolean gain = false;
		for (long delta : net.values()) {
			if (delta > 0L) {
				gain = true;
			} else if (delta < 0L) {
				compact = true;
			}
		}
		if (compact && gain) {
			applyCompact(net);
			return;
		}
		for (Map.Entry<String, Long> entry : net.entrySet()) {
			long delta = entry.getValue();
			if (windowMs > 0) {
				delta -= already(entry.getKey(), now, windowMs);
			}
			if (delta == 0L) {
				continue;
			}
			ItemStorage.applySackDelta(entry.getKey(), delta);
			SEEN.add(new Seen(entry.getKey(), delta, now));
		}
	}

	/**
	 * Compacting pulls raw out of one sack and pushes the enchanted stack into
	 * another. Raw Mats already counts that raw (and shows it as enchanted), so
	 * crediting the full +N enchanted overshoots until you open the sack.
	 * Only convert as many raw items as we currently have stored.
	 */
	private static void applyCompact(Map<String, Long> net) {
		Map<String, Long> added = new LinkedHashMap<>();
		Map<String, Long> removed = new LinkedHashMap<>();
		for (Map.Entry<String, Long> entry : net.entrySet()) {
			if (entry.getValue() > 0L) {
				added.put(entry.getKey(), entry.getValue());
			} else if (entry.getValue() < 0L) {
				removed.put(entry.getKey(), -entry.getValue());
			}
		}
		Iterator<Map.Entry<String, Long>> it = added.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, Long> entry = it.next();
			String outputId = entry.getKey();
			long outputCount = entry.getValue();
			String ingredient = compactIngredient(outputId, removed);
			if (ingredient == null) {
				continue;
			}
			long cost = compactCost(outputId, ingredient);
			long producedEach = compactOutput(outputId);
			long have = ItemStorage.sackCount(ingredient);
			long take = Math.min(have, removed.getOrDefault(ingredient, 0L));
			long produced = cost <= 0L ? 0L : (take / cost) * producedEach;
			long credit = Math.min(outputCount, produced);
			ItemStorage.applySackDelta(ingredient, -take);
			ItemStorage.applySackDelta(outputId, credit);
			removed.remove(ingredient);
			it.remove();
		}
		for (Map.Entry<String, Long> entry : added.entrySet()) {
			ItemStorage.applySackDelta(entry.getKey(), entry.getValue());
		}
		for (Map.Entry<String, Long> entry : removed.entrySet()) {
			long take = Math.min(ItemStorage.sackCount(entry.getKey()), entry.getValue());
			ItemStorage.applySackDelta(entry.getKey(), -take);
		}
	}

	private static String compactIngredient(String outputId, Map<String, Long> removed) {
		SkyblockRecipes.Recipe recipe = SkyblockRecipes.get(outputId);
		if (recipe != null && recipe.ingredients().size() == 1) {
			String ingredient = recipe.ingredients().keySet().iterator().next();
			if (removed.containsKey(ingredient)) {
				return ingredient;
			}
		}
		if (SkyblockRecipes.enchantedCompact(outputId)) {
			String raw = outputId.substring("ENCHANTED_".length());
			if (removed.containsKey(raw)) {
				return raw;
			}
		}
		return null;
	}

	private static long compactCost(String outputId, String ingredient) {
		SkyblockRecipes.Recipe recipe = SkyblockRecipes.get(outputId);
		if (recipe != null) {
			Long cost = recipe.ingredients().get(ingredient);
			if (cost != null && cost > 0L) {
				return cost;
			}
		}
		return 160L;
	}

	private static long compactOutput(String outputId) {
		SkyblockRecipes.Recipe recipe = SkyblockRecipes.get(outputId);
		if (recipe == null) {
			return 1L;
		}
		return Math.max(1L, recipe.output());
	}

	private static long already(String id, long now, int windowMs) {
		long sum = 0L;
		for (Seen seen : SEEN) {
			if (seen.id.equals(id) && seen.at >= now - windowMs) {
				sum += seen.delta;
			}
		}
		return sum;
	}

	private static void prune(long now) {
		Iterator<Seen> it = SEEN.iterator();
		while (it.hasNext()) {
			if (it.next().at < now - 8_000L) {
				it.remove();
			}
		}
	}

	private static int windowMs(String text) {
		Matcher matcher = LAST.matcher(text);
		if (!matcher.find()) {
			return 0;
		}
		try {
			return Math.max(0, Integer.parseInt(matcher.group(1))) * 1000;
		} catch (NumberFormatException ignored) {
			return 0;
		}
	}

	private static boolean isSackLine(String text) {
		String lower = text.toLowerCase(Locale.ROOT);
		return lower.contains("[sacks]")
			|| lower.contains("sacks »")
			|| lower.contains("sacks >")
			|| lower.contains("sacks:");
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
			String line = raw.replaceAll("§.", "").replaceAll("\\([^)]*\\)", " ").trim();
			if (line.isEmpty()) {
				continue;
			}
			Matcher matcher = CHUNK.matcher(line);
			while (matcher.find()) {
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
			|| lower.startsWith("item")
			|| lower.startsWith("from ")
			|| lower.startsWith("added ")
			|| lower.startsWith("removed ")
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
		appendHover(node.getStyle().getHoverEvent(), out);
		for (Component part : node.toFlatList()) {
			appendHover(part.getStyle().getHoverEvent(), out);
		}
		for (Component child : node.getSiblings()) {
			collectHover(child, out);
		}
	}

	private static void appendHover(HoverEvent event, StringBuilder out) {
		if (!(event instanceof HoverEvent.ShowText show)) {
			return;
		}
		String text = plain(show.value());
		if (text.isEmpty() || out.toString().contains(text)) {
			return;
		}
		if (!out.isEmpty()) {
			out.append('\n');
		}
		out.append(text);
	}

	private record Change(String name, long delta) {
	}

	private record Seen(String id, long delta, long at) {
	}
}
