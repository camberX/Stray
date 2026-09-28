package dev.stray.client.item;

import dev.stray.Stray;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public final class SkyblockRecipes {
	public record Recipe(String id, long output, Map<String, Long> ingredients, String type) {
	}

	public enum Expand {
		RAW,
		ENCHANTED
	}

	private static final Map<String, Recipe> BY_ID = new HashMap<>();
	private static boolean loaded;

	/**
	 * Owned stacks the walk may spend. {@link #take} removes up to {@code quantity}
	 * of {@code id} and returns how many it actually took. Called for every recipe
	 * node, so an owned intermediate covers its ingredients instead of only leaves.
	 */
	public interface Stock {
		long take(String id, long quantity);

		default void onIntermediate(String id, long taken) {
		}
	}

	private SkyblockRecipes() {
	}

	public static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		try (InputStream raw = SkyblockRecipes.class.getResourceAsStream("/assets/stray/skyblock_recipes.tsv.gz")) {
			if (raw == null) {
				Stray.LOGGER.warn("Skyblock recipe catalog is missing");
				return;
			}
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(raw), StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					Recipe recipe = parse(line);
					if (recipe != null) {
						BY_ID.put(recipe.id, recipe);
					}
				}
			}
			Stray.LOGGER.info("Loaded {} Skyblock recipes", BY_ID.size());
		} catch (Exception exception) {
			Stray.LOGGER.warn("Could not read Skyblock recipes", exception);
		}
	}

	public static Recipe get(String id) {
		load();
		if (id == null || id.isBlank()) {
			return null;
		}
		return BY_ID.get(normalize(id));
	}

	public static boolean has(String id) {
		return get(id) != null;
	}

	public static Map<String, Long> expand(String id) {
		return expand(id, 1L, Expand.RAW);
	}

	public static Map<String, Long> expand(String id, long quantity) {
		return expand(id, quantity, Expand.RAW);
	}

	public static Map<String, Long> expand(String id, long quantity, Expand expand) {
		Map<String, Long> out = new LinkedHashMap<>();
		collect(id, quantity, expand, null, out, null);
		return out;
	}

	/**
	 * One walk for the whole craft. Extra outputs (a recipe that makes 4 sticks
	 * when the next row only needed 3) fill a later use of that same item.
	 * Caching one craft and multiplying it over-counts those leftovers.
	 * {@code stock} spends owned items at the node they match; {@code have}
	 * receives that amount plus the share already covered by an owned parent.
	 */
	public static void collect(
		String id,
		long quantity,
		Expand expand,
		Stock stock,
		Map<String, Long> need,
		Map<String, Long> have
	) {
		load();
		if (id == null || id.isBlank() || quantity <= 0L || need == null) {
			return;
		}
		walk(
			normalize(id),
			quantity,
			need,
			have,
			new HashMap<>(),
			expand == null ? Expand.RAW : expand,
			new HashMap<>(),
			stock,
			0L,
			1L,
			true
		);
	}

	/**
	 * First enchanted compact form: Enchanted Iron, not Enchanted Iron Block
	 * and not the collection item (Iron Ingot).
	 */
	public static boolean enchantedCompact(String id) {
		if (id == null || !id.startsWith("ENCHANTED_") || id.endsWith("_BLOCK")) {
			return false;
		}
		return id.length() > "ENCHANTED_".length();
	}

	private static void walk(
		String id,
		long quantity,
		Map<String, Long> need,
		Map<String, Long> have,
		Map<String, Integer> stack,
		Expand mode,
		Map<String, Long> remainder,
		Stock stock,
		long satisfied,
		long denominator,
		boolean root
	) {
		if (quantity <= 0L) {
			return;
		}
		long carried = Math.min(remainder.getOrDefault(id, 0L), quantity);
		if (carried > 0L) {
			long left = remainder.get(id) - carried;
			if (left <= 0L) {
				remainder.remove(id);
			} else {
				remainder.put(id, left);
			}
			quantity -= carried;
		}
		if (quantity <= 0L) {
			return;
		}
		int depth = stack.getOrDefault(id, 0);
		boolean leaf = depth > 0 || stack.size() > 32;
		if (!leaf && mode == Expand.ENCHANTED && !stack.isEmpty() && enchantedCompact(id)) {
			leaf = true;
		}
		Recipe recipe = leaf ? null : BY_ID.get(id);
		if (recipe == null || recipe.ingredients.isEmpty()) {
			leaf = true;
		}
		long covered = root ? 0L : mulDiv(quantity, satisfied, denominator);
		if (covered > quantity) {
			covered = quantity;
		}
		long taken = 0L;
		if (!root && stock != null) {
			taken = stock.take(id, quantity - covered);
			if (taken > quantity - covered) {
				taken = quantity - covered;
			}
		}
		if (leaf) {
			need.merge(id, quantity, Long::sum);
			if (have != null) {
				have.merge(id, covered + taken, Long::sum);
			}
			return;
		}
		if (taken > 0L && stock != null) {
			stock.onIntermediate(id, taken);
		}
		long crafts = ceilDiv(quantity, recipe.output);
		long produced = safeMul(crafts, recipe.output);
		if (produced > quantity) {
			remainder.merge(id, produced - quantity, Long::sum);
		}
		long nowSatisfied = covered + taken;
		if (nowSatisfied > quantity) {
			nowSatisfied = quantity;
		}
		stack.put(id, 1);
		for (Map.Entry<String, Long> ingredient : recipe.ingredients.entrySet()) {
			walk(
				ingredient.getKey(),
				safeMul(ingredient.getValue(), crafts),
				need,
				have,
				stack,
				mode,
				remainder,
				stock,
				nowSatisfied,
				quantity,
				false
			);
		}
		stack.remove(id);
	}

	private static long ceilDiv(long numerator, long denominator) {
		if (numerator <= 0L) {
			return 0L;
		}
		if (denominator <= 1L) {
			return numerator;
		}
		long quotient = numerator / denominator;
		if (numerator % denominator == 0L) {
			return quotient;
		}
		return quotient == Long.MAX_VALUE ? Long.MAX_VALUE : quotient + 1L;
	}

	private static long safeMul(long left, long right) {
		if (left <= 0L || right <= 0L) {
			return 0L;
		}
		if (left > Long.MAX_VALUE / right) {
			return Long.MAX_VALUE;
		}
		return left * right;
	}

	/** {@code floor(value * numerator / denominator)}, capped at {@code value}. */
	private static long mulDiv(long value, long numerator, long denominator) {
		if (value <= 0L || numerator <= 0L || denominator <= 0L) {
			return 0L;
		}
		if (numerator >= denominator) {
			return value;
		}
		long whole = value / denominator;
		long rem = value % denominator;
		long high = safeMul(whole, numerator);
		long low;
		if (rem > Long.MAX_VALUE / numerator) {
			low = Long.MAX_VALUE / denominator;
		} else {
			low = (rem * numerator) / denominator;
		}
		if (high >= Long.MAX_VALUE - low) {
			return Long.MAX_VALUE;
		}
		return high + low;
	}

	public static String normalize(String raw) {
		if (raw == null) {
			return "";
		}
		String id = raw.trim();
		int colon = id.indexOf(':');
		if (colon >= 0) {
			String prefix = id.substring(0, colon).toLowerCase(Locale.ROOT);
			if (prefix.equals("sb") || prefix.equals("skyblock") || prefix.equals("minecraft")) {
				id = id.substring(colon + 1);
			}
		}
		return id.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
	}

	private static Recipe parse(String line) {
		String[] parts = line.split("\t", -1);
		if (parts.length < 3 || parts[0].isBlank() || parts[2].isBlank()) {
			return null;
		}
		String id = normalize(parts[0]);
		long output = 1L;
		try {
			output = Math.max(1L, Long.parseLong(parts[1].trim()));
		} catch (NumberFormatException ignored) {
		}
		Map<String, Long> ingredients = new LinkedHashMap<>();
		for (String piece : parts[2].split(",")) {
			if (piece.isBlank()) {
				continue;
			}
			int at = piece.lastIndexOf(':');
			String name = at < 0 ? piece : piece.substring(0, at);
			long count = 1L;
			if (at >= 0) {
				try {
					count = Long.parseLong(piece.substring(at + 1).trim());
				} catch (NumberFormatException ignored) {
					count = 1L;
				}
			}
			name = normalize(name);
			if (!name.isBlank() && count > 0L) {
				ingredients.merge(name, count, Long::sum);
			}
		}
		if (ingredients.isEmpty()) {
			return null;
		}
		String type = parts.length > 3 ? parts[3].trim() : "crafting";
		return new Recipe(id, output, Map.copyOf(ingredients), type);
	}
}
