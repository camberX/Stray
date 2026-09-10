package dev.stray.client.item;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stray.Stray;
import net.minecraft.util.Util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Fills NEU pet lore templates ({@code {STRENGTH}}, {@code {0}}, {@code {LVL}})
 * using {@code petnums.json}, and levels pets with the NEU XP tables.
 */
public final class SkyblockPetLore {
	private static final String[] PETNUM_URLS = {
		"https://cdn.jsdelivr.net/gh/NotEnoughUpdates/NotEnoughUpdates-REPO@master/constants/petnums.json",
		"https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/petnums.json"
	};
	private static final String[] PETS_URLS = {
		"https://cdn.jsdelivr.net/gh/NotEnoughUpdates/NotEnoughUpdates-REPO@master/constants/pets.json",
		"https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/pets.json"
	};
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL)
		.connectTimeout(Duration.ofSeconds(8))
		.build();
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{[^}]+\\}");
	private static final Pattern ICONS = Pattern.compile("[\\uE000-\\uF8FF]");
	private static volatile JsonObject NUMS;
	private static volatile JsonObject PETS;
	private static volatile boolean pending;

	private SkyblockPetLore() {
	}

	public static void request() {
		if (NUMS != null && PETS != null || pending) {
			return;
		}
		pending = true;
		Util.nonCriticalIoPool().execute(SkyblockPetLore::fetch);
	}

	public static void ensure() {
		if (NUMS != null && PETS != null) {
			return;
		}
		fetch();
	}

	public static int level(String type, String tier, double xp) {
		int max = maxLevel(type);
		int[] costs = costs(type, tier, max);
		if (costs.length == 0) {
			return Math.max(1, Math.min(max, 1));
		}
		int level = 1;
		double total = 0d;
		for (int i = 0; i < costs.length && level < max; i++) {
			total += costs[i];
			if (xp >= total) {
				level++;
			} else {
				break;
			}
		}
		return Math.min(max, level);
	}

	public static int maxLevel(String type) {
		JsonObject custom = custom(type);
		int max = custom == null ? 0 : (int) num(custom, "max_level");
		return max > 0 ? max : 100;
	}

	public static ItemText tooltip(
		String type,
		String tier,
		int level,
		String name,
		String held,
		int candy,
		boolean active,
		SkyblockLore.Snapshot snapshot
	) {
		String color = tierCode(tier);
		String label = name == null || name.isBlank() ? pretty(type) : name;
		String title = "§7[Lvl " + Math.max(1, level) + "] " + color + label;
		Map<String, String> keys = replacements(type, tier, level, held);
		List<String> lore = new ArrayList<>();
		if (snapshot != null && snapshot.present() && snapshot.lore() != null) {
			for (String line : snapshot.lore()) {
				if (line == null) {
					lore.add("");
					continue;
				}
				if (line.contains("Right-click to add this pet") || line.contains("Click to view recipe")) {
					continue;
				}
				lore.add(clean(fill(line, keys)));
			}
			while (!lore.isEmpty() && lore.getLast().isBlank()) {
				lore.removeLast();
			}
		}
		if (lore.isEmpty()) {
			lore.add("§8" + family(type) + " Pet");
			lore.add("");
			addStats(lore, keys);
		}
		insertHeld(lore, held, candy, active);
		if (!hasRarity(lore)) {
			lore.add("");
			lore.add(color + "§l" + (tier == null || tier.isBlank() ? "COMMON" : tier.toUpperCase(Locale.ROOT)));
		}
		return ItemText.fromLegacy(title, lore);
	}

	private static void fetch() {
		try {
			if (NUMS == null) {
				JsonObject nums = download(PETNUM_URLS);
				if (nums != null) {
					NUMS = nums;
				}
			}
			if (PETS == null) {
				JsonObject pets = download(PETS_URLS);
				if (pets != null) {
					PETS = pets;
				}
			}
		} catch (Exception exception) {
			Stray.LOGGER.warn("Skyblock pet constants lookup failed", exception);
		} finally {
			pending = false;
		}
	}

	private static JsonObject download(String[] urls) {
		for (String url : urls) {
			try {
				HttpRequest request = HttpRequest.newBuilder(URI.create(url))
					.timeout(Duration.ofSeconds(12))
					.header("User-Agent", "Stray/" + Stray.MOD_ID)
					.GET()
					.build();
				HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() < 200 || response.statusCode() >= 300) {
					continue;
				}
				JsonElement root = JsonParser.parseString(response.body());
				if (root != null && root.isJsonObject()) {
					return root.getAsJsonObject();
				}
			} catch (Exception ignored) {
			}
		}
		return null;
	}

	private static Map<String, String> replacements(String type, String tier, int level, String held) {
		Map<String, String> out = new HashMap<>();
		out.put("LVL", String.valueOf(Math.max(1, level)));
		JsonObject nums = NUMS;
		if (nums == null || type == null || type.isBlank()) {
			return out;
		}
		JsonObject pet = object(nums, type.trim().toUpperCase(Locale.ROOT));
		if (pet == null) {
			return out;
		}
		String rarity = boostedTier(tier, held);
		JsonObject band = object(pet, rarity);
		if (band == null || !band.has("1") || !band.has("100")) {
			return out;
		}
		JsonObject min = object(band, "1");
		JsonObject max = object(band, "100");
		int minStatsLevel = 0;
		int maxStatsLevel = 100;
		int curve = -1;
		int statsLevel = Math.max(1, level);
		String rawCurve = string(band, "stats_levelling_curve");
		if (!rawCurve.isBlank()) {
			String[] parts = rawCurve.split(":");
			if (parts.length == 3) {
				minStatsLevel = parseInt(parts[0]);
				maxStatsLevel = parseInt(parts[1]);
				curve = parseInt(parts[2]);
				if (curve == 0 || curve == 1) {
					if (level < minStatsLevel) {
						statsLevel = 1;
					} else if (level < maxStatsLevel) {
						statsLevel = level - minStatsLevel + 1;
					} else {
						statsLevel = maxStatsLevel - minStatsLevel + 1;
					}
				}
			}
		}
		float minMix = (maxStatsLevel - (minStatsLevel - (curve == -1 ? 0 : 1)) - statsLevel) / 99f;
		float maxMix = (statsLevel - 1) / 99f;
		JsonArray otherMin = array(min, "otherNums");
		JsonArray otherMax = array(max, "otherNums");
		int others = Math.min(size(otherMin), size(otherMax));
		for (int i = 0; i < others; i++) {
			float val = otherMin.get(i).getAsFloat() * minMix + otherMax.get(i).getAsFloat() * maxMix;
			out.put(String.valueOf(i), prettyNum(Math.floor(val * 10f) / 10f));
		}
		JsonObject maxStats = object(max, "statNums");
		JsonObject minStats = object(min, "statNums");
		if (maxStats != null) {
			for (String key : maxStats.keySet()) {
				float statMax = (float) num(maxStats, key);
				float statMin = (float) num(minStats, key);
				float val = statMin * minMix + statMax * maxMix;
				String body = prettyNum(Math.floor(val * 10f) / 10f);
				out.put(key, (statMin > 0 ? "+" : "") + body);
			}
		}
		return out;
	}

	private static String boostedTier(String tier, String held) {
		String rarity = tier == null ? "COMMON" : tier.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
		if (held == null || !held.equalsIgnoreCase("PET_ITEM_TIER_BOOST")) {
			return rarity;
		}
		return switch (rarity) {
			case "COMMON" -> "UNCOMMON";
			case "UNCOMMON" -> "RARE";
			case "RARE" -> "EPIC";
			case "EPIC" -> "LEGENDARY";
			case "LEGENDARY" -> "MYTHIC";
			default -> rarity;
		};
	}

	private static int[] costs(String type, String tier, int max) {
		JsonObject pets = PETS;
		if (pets != null) {
			int offset = (int) num(object(pets, "pet_rarity_offset"), rarityKey(tier));
			JsonArray levels = array(pets, "pet_levels");
			List<Integer> out = new ArrayList<>();
			if (levels != null) {
				int take = Math.min(Math.max(0, max - 1), Math.max(0, levels.size() - offset));
				for (int i = 0; i < take; i++) {
					out.add((int) Math.round(levels.get(offset + i).getAsDouble()));
				}
			}
			JsonObject custom = custom(type);
			if (custom != null && (int) num(custom, "type") == 1) {
				JsonArray extra = array(custom, "pet_levels");
				if (extra != null) {
					for (int i = 0; i < extra.size() && out.size() < max - 1; i++) {
						out.add((int) Math.round(extra.get(i).getAsDouble()));
					}
				}
			}
			if (!out.isEmpty()) {
				int[] arr = new int[out.size()];
				for (int i = 0; i < out.size(); i++) {
					arr[i] = out.get(i);
				}
				return arr;
			}
		}
		return fallbackCosts(type, tier, max);
	}

	private static int[] fallbackCosts(String type, String tier, int max) {
		int[] cumulative = switch (tier == null ? "" : tier.toLowerCase(Locale.ROOT)) {
			case "common" -> new int[]{0, 100, 210, 330, 460, 605, 765, 940, 1130, 1340, 1570, 1820, 2095, 2395, 2725, 3085, 3485, 3925, 4415, 4955, 5555, 6215, 6945, 7745, 8625, 9595, 10655, 11815, 13085, 14475, 15995, 17655, 19465, 21435, 23575, 25895, 28405, 31115, 34035, 37175, 40545, 44155, 48015, 52135, 56525, 61195, 66155, 71415, 76985, 82875, 89095, 95655, 102565, 109835, 117475, 125495, 133905, 142715, 151935, 161575, 171645, 182155, 193115, 204535, 216425, 228795, 241655, 255015, 268885, 283275, 298195, 313655, 329665, 346235, 363375, 381095, 399405, 418315, 437835, 457975, 478745, 500155, 522215, 544935, 568325, 592395, 617155, 642615, 668785, 695675, 723295, 751655, 780765, 810635, 841275, 872695, 904905, 937915, 971735, 1006375};
			case "uncommon" -> new int[]{0, 175, 365, 575, 805, 1055, 1330, 1630, 1960, 2320, 2715, 3145, 3615, 4130, 4690, 5300, 5965, 6685, 7465, 8310, 9225, 10215, 11285, 12440, 13685, 15025, 16465, 18010, 19665, 21435, 23325, 25340, 27485, 29765, 32185, 34750, 37465, 40335, 43365, 46560, 49925, 53465, 57185, 61090, 65185, 69475, 73965, 78660, 83565, 88685, 94025, 99590, 105385, 111415, 117685, 124200, 130965, 137985, 145265, 152810, 160625, 168715, 177085, 185740, 194685, 203925, 213465, 223310, 233465, 243935, 254725, 265840, 277285, 289065, 301185, 313650, 326465, 339635, 353165, 367060, 381325, 395965, 410985, 426390, 442185, 458375, 474965, 491960, 509365, 527185, 545425, 564090, 583185, 602715, 622685, 643100, 663965, 685285, 707065, 729310};
			default -> new int[]{0, 660, 1390, 2190, 3070, 4040, 5110, 6290, 7590, 9020, 10590, 12310, 14190, 16240, 18470, 20890, 23510, 26340, 29390, 32670, 36190, 39960, 43990, 48290, 52870, 57740, 62910, 68390, 74190, 80320, 86790, 93610, 100790, 108340, 116270, 124590, 133310, 142440, 151990, 161970, 172390, 183260, 194590, 206390, 218670, 231440, 244710, 258490, 272790, 287620, 302990, 318910, 335390, 352440, 370070, 388290, 407110, 426540, 446590, 467270, 488590, 510560, 533190, 556490, 580470, 605140, 630510, 656590, 683390, 710920, 739190, 768210, 797990, 828540, 859870, 891990, 924910, 958640, 993190, 1028570, 1064690, 1101550, 1139160, 1177530, 1216670, 1256590, 1297300, 1338810, 1381130, 1424270, 1468240, 1513050, 1558710, 1605230, 1652620, 1700890, 1750050, 1800110, 1851080, 1902970};
		};
		int need = Math.max(1, max - 1);
		int[] costs = new int[need];
		for (int i = 0; i < Math.min(need, cumulative.length - 1); i++) {
			costs[i] = Math.max(0, cumulative[i + 1] - cumulative[i]);
		}
		String key = type == null ? "" : type.toUpperCase(Locale.ROOT);
		if ((key.equals("GOLDEN_DRAGON") || key.equals("JADE_DRAGON")) && need > 99) {
			for (int i = 99; i < need; i++) {
				costs[i] = 1_886_700;
			}
		}
		return costs;
	}

	private static JsonObject custom(String type) {
		if (type == null || type.isBlank() || PETS == null) {
			return null;
		}
		return object(object(PETS, "custom_pet_leveling"), type.trim().toUpperCase(Locale.ROOT));
	}

	private static String rarityKey(String tier) {
		String rarity = tier == null ? "COMMON" : tier.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
		return switch (rarity) {
			case "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC" -> rarity;
			default -> "COMMON";
		};
	}

	private static void addStats(List<String> lore, Map<String, String> keys) {
		boolean any = false;
		for (Map.Entry<String, String> entry : keys.entrySet()) {
			if (entry.getKey().equals("LVL") || entry.getKey().chars().allMatch(Character::isDigit)) {
				continue;
			}
			lore.add("§7" + pretty(entry.getKey()) + ": §c" + entry.getValue());
			any = true;
		}
		if (any) {
			lore.add("");
		}
	}

	private static void insertHeld(List<String> lore, String held, int candy, boolean active) {
		int at = rarityIndex(lore);
		if (at < 0) {
			at = lore.size();
		}
		List<String> extra = new ArrayList<>();
		if (held != null && !held.isBlank()) {
			extra.add("§6Held Item: §d" + pretty(held.replace("PET_ITEM_", "")));
		}
		if (candy > 0) {
			extra.add("§7Candy Used: §e" + candy + " §6/ §e10");
		}
		if (active) {
			extra.add("§aActive Pet");
		}
		if (extra.isEmpty()) {
			return;
		}
		if (at > 0 && !lore.get(at - 1).isBlank()) {
			lore.add(at, "");
			at++;
		}
		lore.addAll(at, extra);
	}

	private static boolean hasRarity(List<String> lore) {
		return rarityIndex(lore) >= 0;
	}

	private static int rarityIndex(List<String> lore) {
		for (int i = lore.size() - 1; i >= 0; i--) {
			String plain = lore.get(i).replaceAll("§.", "").toUpperCase(Locale.ROOT);
			if (plain.contains("COMMON") || plain.contains("UNCOMMON") || plain.contains("RARE")
				|| plain.contains("EPIC") || plain.contains("LEGENDARY") || plain.contains("MYTHIC")) {
				return i;
			}
		}
		return -1;
	}

	private static String fill(String line, Map<String, String> keys) {
		String out = line;
		for (Map.Entry<String, String> entry : keys.entrySet()) {
			out = out.replace("{" + entry.getKey() + "}", entry.getValue());
		}
		return PLACEHOLDER.matcher(out).replaceAll("0");
	}

	private static String clean(String line) {
		return ICONS.matcher(line == null ? "" : line).replaceAll("");
	}

	private static String family(String type) {
		return switch (type == null ? "" : type.toUpperCase(Locale.ROOT)) {
			case "WOLF", "TIGER", "LION", "ENDERMAN", "ENDER_DRAGON", "GOLDEN_DRAGON", "BLAZE",
				"SKELETON", "ZOMBIE", "SPIDER", "TARANTULA", "HOUND", "PHOENIX", "GRIFFIN",
				"BLACK_CAT", "WITHER_SKELETON", "GOLEM", "KUUDRA" -> "Combat";
			case "RABBIT", "CHICKEN", "PIG", "MOOSHROOM_COW", "ELEPHANT", "SLUG", "BEE" -> "Farming";
			case "ARMADILLO", "SILVERFISH", "ROCK", "MITHRIL_GOLEM", "SCATHA", "SNAIL",
				"BAL", "MOLE", "GLACITE_GOLEM", "GOBLIN" -> "Mining";
			case "SQUID", "DOLPHIN", "BLUE_WHALE", "FLYING_FISH", "MEGALODON", "BABY_YETI",
				"AMMONITE", "PENGUIN", "REINDEER", "SPINOSAURUS" -> "Fishing";
			case "OCELOT", "MONKEY", "GIRAFFE" -> "Foraging";
			case "JELLYFISH", "PARROT", "SHEEP" -> "Alchemy";
			case "GUARDIAN" -> "Enchanting";
			case "OWL" -> "Taming";
			default -> "Skyblock";
		};
	}

	private static String tierCode(String tier) {
		return switch (tier == null ? "" : tier.toLowerCase(Locale.ROOT)) {
			case "uncommon" -> "§a";
			case "rare" -> "§9";
			case "epic" -> "§5";
			case "legendary" -> "§6";
			case "mythic" -> "§d";
			default -> "§f";
		};
	}

	private static String pretty(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		String cleaned = raw.replace('_', ' ').trim();
		StringBuilder out = new StringBuilder(cleaned.length());
		boolean cap = true;
		for (int i = 0; i < cleaned.length(); i++) {
			char ch = cleaned.charAt(i);
			if (ch == ' ') {
				out.append(ch);
				cap = true;
				continue;
			}
			out.append(cap ? Character.toUpperCase(ch) : Character.toLowerCase(ch));
			cap = false;
		}
		return out.toString();
	}

	private static String prettyNum(double value) {
		if (Math.abs(value - Math.rint(value)) < 0.05d) {
			return String.valueOf((int) Math.rint(value));
		}
		return String.valueOf(value);
	}

	private static JsonObject object(JsonObject parent, String key) {
		if (parent == null || key == null || !parent.has(key)) {
			return null;
		}
		JsonElement value = parent.get(key);
		return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
	}

	private static JsonArray array(JsonObject parent, String key) {
		if (parent == null || key == null || !parent.has(key)) {
			return null;
		}
		JsonElement value = parent.get(key);
		return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
	}

	private static int size(JsonArray array) {
		return array == null ? 0 : array.size();
	}

	private static String string(JsonObject object, String key) {
		if (object == null || key == null || !object.has(key) || !object.get(key).isJsonPrimitive()) {
			return "";
		}
		String value = object.get(key).getAsString();
		return value == null ? "" : value;
	}

	private static double num(JsonObject object, String key) {
		if (object == null || key == null || !object.has(key) || !object.get(key).isJsonPrimitive()) {
			return 0d;
		}
		try {
			return object.get(key).getAsDouble();
		} catch (Exception ignored) {
			return 0d;
		}
	}

	private static int parseInt(String raw) {
		try {
			return Integer.parseInt(raw.trim());
		} catch (Exception ignored) {
			return 0;
		}
	}
}
