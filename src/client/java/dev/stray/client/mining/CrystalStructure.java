package dev.stray.client.mining;

import java.util.Locale;

public enum CrystalStructure {
	UNKNOWN("Unknown", 0xFFFFFF, null),
	JUNGLE_TEMPLE("Jungle Temple", 0xAA00AA, "[NPC] Kalhuiki Door Guardian:"),
	MINES_OF_DIVAN("Mines of Divan", 0x55FF55, "Jade Crystal"),
	GOBLIN_QUEENS_DEN("Goblin Queen's Den", 0xFFAA00, "Amber Crystal"),
	LOST_PRECURSOR_CITY("Lost Precursor City", 0x55FFFF, "Sapphire Crystal"),
	KHAZAD_DUM("Khazad-dûm", 0xFFFF55, "Topaz Crystal"),
	FAIRY_GROTTO("Fairy Grotto", 0xFF55FF, null),
	DRAGONS_LAIR("Dragon's Lair", 0xFFAA00, "[NPC] Golden Dragon:"),
	CORLEONE("Corleone", 0xFFFFFF, null),
	KING_YOLKAR("King Yolkar", 0xFF5555, "[NPC] King Yolkar:"),
	ODAWA("Odawa", 0xFF55FF, "[NPC] Odawa:"),
	KEY_GUARDIAN("Key Guardian", 0xAAAAAA, null),
	XALX("Xalx", 0x55FF55, "[NPC] Xalx:"),
	PETE("Professor Pete", 0xFFAA00, "[NPC] Professor Pete:");

	public final String label;
	public final int rgb;
	public final String chatHint;

	CrystalStructure(String label, int rgb, String chatHint) {
		this.label = label;
		this.rgb = rgb;
		this.chatHint = chatHint;
	}

	public static CrystalStructure fromWire(String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		try {
			return valueOf(name.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ignored) {
			return fromLabel(name);
		}
	}

	public static CrystalStructure fromLabel(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		String needle = normalize(text);
		if (needle.isEmpty() || zoneOnly(needle)) {
			return null;
		}
		for (CrystalStructure structure : values()) {
			if (structure == UNKNOWN) {
				continue;
			}
			if (needle.equals(normalize(structure.label))) {
				return structure;
			}
		}
		return switch (needle) {
			case "precursor city", "precursor ruins", "lost precursor", "lost precursor ruins", "city" -> LOST_PRECURSOR_CITY;
			case "goblin queen", "goblin queens den", "goblin hideout", "queens den", "queen" -> GOBLIN_QUEENS_DEN;
			case "odawa shop", "odawas shop" -> ODAWA;
			case "divan", "mines of divan", "divans mines" -> MINES_OF_DIVAN;
			case "khazad dum", "khazaddum", "bal" -> KHAZAD_DUM;
			case "dragon lair", "dragons lair", "golden dragon" -> DRAGONS_LAIR;
			case "yolkar", "king yolkar", "king" -> KING_YOLKAR;
			case "key guardian" -> KEY_GUARDIAN;
			case "jungle temple", "kalhuiki", "temple" -> JUNGLE_TEMPLE;
			case "pete", "professor pete" -> PETE;
			default -> null;
		};
	}

	private static String normalize(String text) {
		return text.toLowerCase(Locale.ROOT)
			.replace('û', 'u')
			.replace('ú', 'u')
			.replace("'", "")
			.replace("-", " ")
			.replaceAll("[^a-z0-9 ]", " ")
			.replaceAll("\\s+", " ")
			.trim();
	}

	private static boolean zoneOnly(String needle) {
		return switch (needle) {
			case "jungle",
				"goblin holdout",
				"precursor remnants",
				"mithril deposits",
				"mithril deposit",
				"magma fields",
				"magma field",
				"crystal nucleus",
				"nucleus",
				"crystal hollows",
				"crystal hollow" -> true;
			default -> false;
		};
	}

	public boolean matchesChat(String message) {
		if (chatHint == null || message == null) {
			return false;
		}
		return message.startsWith(chatHint) || message.contains(chatHint);
	}
}
