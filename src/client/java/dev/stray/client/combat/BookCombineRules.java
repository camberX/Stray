package dev.stray.client.combat;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which SkyBlock book levels an anvil is allowed to combine.
 *
 * Two enchanted books combine into the next level only when they are the same
 * enchantment at the same level. The enchantment-table cap is the usual ceiling:
 * Protection V + Protection V does not become Protection VI, and combining two
 * Luck VI books comes back as Luck V. Feather Falling and Infinite Quiver are
 * the exception: V does not make VI, but VI through IX combine up to X.
 * Enchants that never come from the table use their real max instead, and a
 * level whose apply cost is 0 is not a real book tier.
 */
public final class BookCombineRules {
	private static final Map<String, Integer> TABLE_MAX = Map.ofEntries(
		Map.entry("sharpness", 5),
		Map.entry("smite", 5),
		Map.entry("bane_of_arthropods", 5),
		Map.entry("looting", 3),
		Map.entry("cubism", 5),
		Map.entry("cleave", 5),
		Map.entry("life_steal", 3),
		Map.entry("giant_killer", 5),
		Map.entry("critical", 5),
		Map.entry("first_strike", 4),
		Map.entry("triple_strike", 4),
		Map.entry("ender_slayer", 5),
		Map.entry("execute", 5),
		Map.entry("thunderlord", 5),
		Map.entry("lethality", 5),
		Map.entry("syphon", 3),
		Map.entry("vampirism", 5),
		Map.entry("venomous", 5),
		Map.entry("thunderbolt", 5),
		Map.entry("prosecute", 5),
		Map.entry("titan_killer", 5),
		Map.entry("luck", 5),
		Map.entry("protection", 5),
		Map.entry("blast_protection", 5),
		Map.entry("projectile_protection", 5),
		Map.entry("fire_protection", 5),
		Map.entry("thorns", 3),
		Map.entry("growth", 5),
		Map.entry("frost_walker", 2),
		Map.entry("feather_falling", 5),
		Map.entry("depth_strider", 3),
		Map.entry("aqua_affinity", 1),
		Map.entry("respiration", 3),
		Map.entry("silk_touch", 1),
		Map.entry("smelting_touch", 1),
		Map.entry("fortune", 3),
		Map.entry("experience", 3),
		Map.entry("efficiency", 5),
		Map.entry("harvesting", 5),
		Map.entry("piscary", 5),
		Map.entry("spiked_hook", 5),
		Map.entry("caster", 5),
		Map.entry("frail", 5),
		Map.entry("angler", 5),
		Map.entry("chance", 3),
		Map.entry("power", 5),
		Map.entry("infinite_quiver", 5),
		Map.entry("lure", 5),
		Map.entry("magnet", 5),
		Map.entry("luck_of_the_sea", 5),
		Map.entry("scavenger", 3)
	);

	/**
	 * Dungeon books that keep combining after the table cap. The cap itself still
	 * does not combine (Feather Falling V + V is not VI). VI through max - 1 do.
	 */
	private static final Map<String, Integer> DUNGEON_MAX = Map.of(
		"feather_falling", 10,
		"infinite_quiver", 10
	);

	/**
	 * Apply cost of each level, index 0 is level I. A 0 means that tier is not a book
	 * you can combine through. The length is the highest level the anvil can reach.
	 */
	private static final Map<String, int[]> COMBINE_COSTS = Map.ofEntries(
		Map.entry("knockback", new int[]{15, 30}),
		Map.entry("fire_aspect", new int[]{15, 30}),
		Map.entry("punch", new int[]{15, 30}),
		Map.entry("flame", new int[]{25, 50}),
		Map.entry("snipe", new int[]{20, 25, 30}),
		Map.entry("aiming", new int[]{10, 20, 30, 40, 50}),
		Map.entry("pristine", new int[]{25, 50, 100, 150, 200}),
		Map.entry("blessing", new int[]{10, 20, 30, 40, 50}),
		Map.entry("tabasco", new int[]{500, 500, 500}),
		Map.entry("smoldering", new int[]{10, 20, 30, 40, 50}),
		Map.entry("divine_gift", new int[]{50, 100, 150}),
		Map.entry("charm", new int[]{20, 25, 30, 40, 50}),
		Map.entry("vicious", new int[]{0, 0, 60, 80, 100}),
		Map.entry("big_brain", new int[]{0, 0, 60, 80, 100}),
		Map.entry("counter_strike", new int[]{0, 0, 60, 80, 100}),
		Map.entry("rejuvenate", new int[]{10, 20, 30, 40, 50}),
		Map.entry("mana_vampire", new int[]{30, 45, 60, 75, 90, 180, 240, 300, 360, 420}),
		Map.entry("ferocious_mana", new int[]{30, 45, 60, 75, 90, 180, 240, 300, 360, 420}),
		Map.entry("hardened_mana", new int[]{30, 45, 60, 75, 90, 180, 240, 300, 360, 420}),
		Map.entry("strong_mana", new int[]{30, 45, 60, 75, 90, 180, 240, 300, 360, 420}),
		Map.entry("smarty_pants", new int[]{20, 40, 60, 80, 100}),
		Map.entry("sugar_rush", new int[]{20, 25, 30}),
		Map.entry("respite", new int[]{10, 20, 30, 40, 50}),
		Map.entry("dragon_hunter", new int[]{50, 100, 150, 200, 250}),
		Map.entry("mana_steal", new int[]{20, 25, 30}),
		Map.entry("corruption", new int[]{20, 25, 30, 40, 50}),
		Map.entry("turbo_pumpkin", new int[]{10, 20, 30, 40, 50}),
		Map.entry("turbo_cactus", new int[]{10, 20, 30, 40, 50}),
		Map.entry("turbo_cane", new int[]{10, 20, 30, 40, 50}),
		Map.entry("turbo_carrot", new int[]{10, 20, 30, 40, 50}),
		Map.entry("turbo_melon", new int[]{10, 20, 30, 40, 50}),
		Map.entry("turbo_mushrooms", new int[]{10, 20, 30, 40, 50}),
		Map.entry("turbo_potato", new int[]{10, 20, 30, 40, 50}),
		Map.entry("turbo_warts", new int[]{10, 20, 30, 40, 50}),
		Map.entry("turbo_wheat", new int[]{10, 20, 30, 40, 50}),
		Map.entry("turbo_coco", new int[]{10, 20, 30, 40, 50}),
		Map.entry("turbo_rose", new int[]{10, 20, 30, 40, 50}),
		Map.entry("turbo_moonflower", new int[]{10, 20, 30, 40, 50}),
		Map.entry("turbo_sunflower", new int[]{10, 20, 30, 40, 50}),
		Map.entry("dedication", new int[]{20, 25, 30, 100}),
		Map.entry("quantum", new int[]{50, 50, 100}),
		Map.entry("reflection", new int[]{20, 40, 60, 80, 100}),
		Map.entry("transylvanian", new int[]{100, 150}),
		Map.entry("pesterminator", new int[]{5, 9, 13, 18, 23}),
		Map.entry("paleontologist", new int[]{23, 45, 91, 136, 179}),
		Map.entry("ice_cold", new int[]{27, 41, 55, 68, 82}),
		Map.entry("lapidary", new int[]{25, 50, 100, 150, 200}),
		Map.entry("small_brain", new int[]{60, 80, 100}),
		Map.entry("tidal", new int[]{25, 40, 55}),
		Map.entry("quick_bite", new int[]{18, 23, 27, 36, 45}),
		Map.entry("cayenne", new int[]{100, 100, 100, 100, 200}),
		Map.entry("prosperity", new int[]{100, 100, 100, 100, 200}),
		Map.entry("sunder", new int[]{5, 10, 15, 20, 25}),
		Map.entry("overload", new int[]{50, 100, 150, 200, 250}),
		Map.entry("ultimate_wise", new int[]{50, 100, 150, 200, 250}),
		Map.entry("ultimate_combo", new int[]{50, 100, 150, 200, 250}),
		Map.entry("ultimate_chimera", new int[]{50, 100, 150, 200, 250}),
		Map.entry("ultimate_swarm", new int[]{50, 100, 150, 200, 250}),
		Map.entry("ultimate_soul_eater", new int[]{50, 100, 150, 200, 250}),
		Map.entry("ultimate_fatal_tempo", new int[]{50, 100, 150, 200, 250}),
		Map.entry("ultimate_inferno", new int[]{50, 100, 150, 200, 250}),
		Map.entry("ultimate_flash", new int[]{50, 100, 150, 200, 250}),
		Map.entry("ultimate_bank", new int[]{50, 100, 150, 200, 250}),
		Map.entry("ultimate_wisdom", new int[]{50, 100, 150, 200, 250}),
		Map.entry("ultimate_habanero_tactics", new int[]{0, 0, 0, 250, 300}),
		Map.entry("ultimate_no_pain_no_gain", new int[]{50, 100, 150, 200, 250}),
		Map.entry("ultimate_last_stand", new int[]{50, 100, 150, 200, 250}),
		Map.entry("ultimate_legion", new int[]{50, 100, 150, 200, 250}),
		Map.entry("ultimate_duplex", new int[]{50, 100, 150, 200, 250}),
		Map.entry("ultimate_rend", new int[]{50, 100, 150, 200, 250}),
		Map.entry("ultimate_bobbin_time", new int[]{0, 0, 100, 120, 140}),
		Map.entry("ultimate_refrigerate", new int[]{50, 100, 150, 200, 250}),
		Map.entry("ultimate_the_one", new int[]{250, 300}),
		Map.entry("ultimate_flowstate", new int[]{50, 100, 150})
	);

	/**
	 * A single enchantment line from a book tooltip, such as "Feather Falling VI".
	 * The key is the anvil id, already normalized.
	 */
	public record EnchantLevel(String key, int level) {
	}

	private static final Pattern ENCHANT_LINE = Pattern.compile(
		"^(.*\\S)\\s+(x|ix|viii|vii|vi|iv|v|iii|ii|i)$"
	);

	private BookCombineRules() {
	}

	public static boolean known(String enchant) {
		String key = normalize(enchant);
		return !key.isEmpty() && (TABLE_MAX.containsKey(key) || COMBINE_COSTS.containsKey(key));
	}

	/**
	 * Maps the name printed on a book ("Feather Falling", "Ultimate Wise", "Wise")
	 * to the anvil id. Unknown names return empty so a description line cannot
	 * become an enchant.
	 */
	public static String fromDisplay(String display) {
		if (display == null || display.isBlank()) {
			return "";
		}
		String cleaned = display.toLowerCase(Locale.ROOT)
			.replace("'", "")
			.replace("\u2019", "")
			.replaceAll("[^a-z0-9]+", "_")
			.replaceAll("^_+|_+$", "")
			.replaceAll("_+", "_");
		if (cleaned.isEmpty()) {
			return "";
		}
		String direct = normalize(cleaned);
		if (known(direct)) {
			return direct;
		}
		if (!direct.startsWith("ultimate_")) {
			String ultimate = normalize("ultimate_" + direct);
			if (known(ultimate)) {
				return ultimate;
			}
		}
		return "";
	}

	/**
	 * Parses one tooltip line. Returns null unless the whole line is a known
	 * enchantment and a roman level, so "Combinable in Anvil" and the effect
	 * text are ignored.
	 */
	public static EnchantLevel parseLine(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String line = raw.toLowerCase(Locale.ROOT)
			.replace('\u00A0', ' ')
			.replace("'", "")
			.replace("\u2019", "")
			.replaceAll("[^a-z0-9\\s]", " ")
			.replaceAll("\\s+", " ")
			.trim();
		Matcher matcher = ENCHANT_LINE.matcher(line);
		if (!matcher.matches()) {
			return null;
		}
		String key = fromDisplay(matcher.group(1));
		if (key.isEmpty()) {
			return null;
		}
		int level = roman(matcher.group(2));
		if (level < 1) {
			return null;
		}
		return new EnchantLevel(key, level);
	}

	private static int roman(String token) {
		return switch (token) {
			case "i" -> 1;
			case "ii" -> 2;
			case "iii" -> 3;
			case "iv" -> 4;
			case "v" -> 5;
			case "vi" -> 6;
			case "vii" -> 7;
			case "viii" -> 8;
			case "ix" -> 9;
			case "x" -> 10;
			default -> -1;
		};
	}

	public static String normalize(String enchant) {
		if (enchant == null || enchant.isBlank()) {
			return "";
		}
		String key = enchant.toLowerCase(Locale.ROOT);
		return switch (key) {
			case "dragon_tracer" -> "aiming";
			case "turbo_cocoa", "turbo_cocoa_beans" -> "turbo_coco";
			case "turbo_cacti" -> "turbo_cactus";
			default -> key;
		};
	}

	public static boolean canUpgrade(String enchant, int level) {
		String key = normalize(enchant);
		if (key.isEmpty() || level < 1) {
			return false;
		}
		Integer dungeon = DUNGEON_MAX.get(key);
		Integer table = TABLE_MAX.get(key);
		if (dungeon != null && table != null) {
			if (level < table) {
				return true;
			}
			return level > table && level < dungeon;
		}
		if (table != null) {
			return level < table;
		}
		int[] costs = COMBINE_COSTS.get(key);
		if (costs == null || level >= costs.length) {
			return false;
		}
		return costs[level - 1] > 0 && costs[level] > 0;
	}
}
