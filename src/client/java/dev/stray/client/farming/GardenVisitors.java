package dev.stray.client.farming;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Visitor crop preview from Hypixel Garden NPCs. Empty needs are Any. */
public final class GardenVisitors {
	public static final String ANY = "Any";
	public static final String UNKNOWN = "Unknown";

	private static final String[] NONE = new String[0];
	private static final Map<String, String[]> BY_NAME = new HashMap<>();
	private static final Map<String, Boolean> UNKNOWN_REWARD = new HashMap<>();

	static {
		need("alchemist", "Nether Wart");
		any("andrew");
		need("anita", "Carrot");
		need("arthur", "Wheat");
		need("baker", "Fine Flour");
		need("banker broadjaw", "Cactus");
		any("bartender");
		any("beth");
		need("carrot king avatar", "Carrot", "Lucky Clover");
		need("clerk seraphine", "Sugar Cane");
		need("combat merchant", "Potato");
		any("dalbrek");
		any("duke");
		need("dusk", "Melon Slice");
		need("emissary carlton", "Cocoa Beans");
		need("emissary ceanna", "Potato");
		need("emissary fraiser", "Melon Slice");
		need("emissary sisko", "Red Mushroom Block");
		need("emissary wilson", "Carrot");
		need("farmer jon", "Sugar Cane");
		need("farmhand", "Wheat", "Carrot", "Potato");
		need("fear mongerer", "Pumpkin");
		any("felix");
		need("fisherman gerald", "Carrot");
		need("fragilis", "Cactus");
		need("friendly hiker", "Raw Mutton", "Raw Porkchop", "Raw Rabbit");
		need("geonathan greatforge", "Compost");
		need("gimley", "Wheat");
		need("gold forger", "Golden Carrot");
		any("grandma wolf");
		need("guy", "Red Mushroom Block");
		need("gwendolyn", "Cocoa Beans");
		need("hornum", "Wheat");
		need("hungry hiker", "Pumpkin");
		need("iron forger", "Carrot");
		any("jack");
		need("jacob", "Wheat");
		any("jamie");
		any("jerry");
		need("jotraeline greatforge", "Compost");
		need("lazy miner", "Cocoa Beans");
		any("leo");
		any("liam");
		need("librarian", "Sugar Cane");
		need("lumber jack", "Carrot", "Potato", "Melon Slice", "Pumpkin", "Red Mushroom Block");
		need("lumina", "Pumpkin");
		any("lynn");
		need("madame eleanor q. goldsworth iii", "Enchanted Golden Carrot");
		need("mason", "Raw Mutton", "Potato", "Carrot", "Red Mushroom Block");
		need("maeve", "Cropie", "Squash", "Fermento");
		any("odawa");
		need("old man garry", "Cocoa Beans");
		need("oringo", "Wheat");
		need("pest wrangler", "Compost", "Dung", "Honey Jar", "Plant Matter", "Tasty Cheese", "Jelly");
		need("pest wrangler?", "Tasty Cheese");
		need("pete", "Sugar Cane");
		need("plumber joe", "Cactus");
		need("puzzler", "Carrot");
		any("queen mismyla");
		need("ravenous rhino", "Bread", "Cake");
		need("rhys", "Jack o' Lantern");
		any("resident snooty");
		any("resident neighbor");
		need("royal resident", "Wheat", "Sugar Cane");
		any("rusty");
		any("ryu");
		need("sargwyn", "Wheat");
		need("seymour", "Pumpkin");
		any("shaggy");
		need("shifty", "Melon Slice");
		any("sirius");
		any("spaceman");
		any("stella");
		need("tammy", "Cactus");
		need("tarwen", "Wheat");
		need("tal ker", "Moonflower");
		need("terry", "Melon Slice");
		any("tia the fairy");
		any("tom");
		any("trevor");
		any("vex");
		need("vinyl collector", "Music Disc - 13");
		need("weaponsmith", "Potato");
		need("wizard", "Potato");
		need("xalx", "Enchanted Sugar");
		need("zog", "Melon Slice", "Cactus");
		need("vincent", "Sunflower");
		need("alchemage", "Carrot");
		need("an", "Nether Wart");
		need("archaeologist", "Nether Wart");
		need("bednom", "Cocoa Beans");
		need("bruuh", "Carrot");
		need("chantelle", "Sunflower", "Moonflower", "Wild Rose");
		need("chief scorn", "Nether Wart");
		any("chunk");
		need("cold enjoyer", "Cactus", "Cocoa Beans");
		need("dante goon", "Wild Rose");
		need("dulin", "Potato");
		need("duncan", "Melon Slice");
		need("elle", "Red Mushroom Block");
		need("erihann", "Nether Wart");
		need("fann", "Nether Wart", "Red Mushroom Block");
		any("farm merchant");
		need("frozen alex", "Sunflower", "Sugar Cane");
		need("gary", "Cocoa Beans");
		need("gemma", "Sugar Cane");
		need("hendrik", "Cactus");
		need("hoppity", "Cocoa Beans");
		need("jacobus", "Sugar Cane");
		any("lift operator");
		any("ludleth");
		need("marco", "Moonflower", "Sunflower", "Wild Rose");
		need("marigold", "Melon Slice");
		need("master tactician funk", "Red Mushroom Block");
		unknown("mayor aatrox");
		unknown("mayor cole");
		need("mayor diana", "Wheat");
		need("mayor diaz", "Potato");
		unknown("mayor finnegan");
		unknown("mayor foxy");
		unknown("mayor marina");
		unknown("mayor paul");
		need("moby", "Red Mushroom Block");
		any("old shaman nyko");
		need("ophelia", "Sugar Cane");
		need("pearl dealer", "Nether Wart");
		need("queen nyx", "Melon Slice");
		need("romero", "Moonflower", "Sunflower", "Wild Rose");
		need("ryan", "Potato");
		need("scout scardius", "Pumpkin");
		need("sherry", "Pumpkin");
		need("spider tamer", "Cocoa Beans");
		need("st. jerry", "Pumpkin", "Cactus");
		need("tomioka", "Red Mushroom Block");
		need("trinity", "Sugar Cane");
		unknown("tyashoi alchemist");
		need("vargul", "Pumpkin");
		need("carpenter", "Melon Slice", "Carrot");
		need("tyzzo", "Nether Wart");
		need("taylor", "Deepfries", "Salted Sunflower Seeds", "Enchanted Baked Potato", "Floral Gelatin", "Melon Juice", "Compacted Wild Rose");
	}

	private GardenVisitors() {
	}

	public static List<String> preview(String name) {
		String key = matchKey(fold(name));
		if (key == null) {
			return List.of(ANY);
		}
		if (Boolean.TRUE.equals(UNKNOWN_REWARD.get(key))) {
			return List.of(UNKNOWN);
		}
		String[] items = BY_NAME.get(key);
		if (items == null || items.length == 0) {
			return List.of(ANY);
		}
		return List.of(items);
	}

	private static String matchKey(String folded) {
		if (folded.isEmpty()) {
			return null;
		}
		if (BY_NAME.containsKey(folded)) {
			return folded;
		}
		String found = null;
		int hits = 0;
		for (String key : BY_NAME.keySet()) {
			if (key.endsWith(" " + folded) || folded.endsWith(" " + key)) {
				found = key;
				hits++;
			}
		}
		return hits == 1 ? found : null;
	}

	private static void any(String key) {
		BY_NAME.put(key, NONE);
	}

	private static void unknown(String key) {
		BY_NAME.put(key, NONE);
		UNKNOWN_REWARD.put(key, Boolean.TRUE);
	}

	private static void need(String key, String... items) {
		BY_NAME.put(key, items);
	}

	private static String fold(String value) {
		if (value == null) {
			return "";
		}
		return value.replaceAll("§.", "").replace('\u00A0', ' ').replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
	}
}
