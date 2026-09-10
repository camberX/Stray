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
	XALX("Xalx", 0x55FF55, "[NPC] Xalx:");

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
		String needle = text.toLowerCase(Locale.ROOT).replace('û', 'u').replace("'", "");
		for (CrystalStructure structure : values()) {
			if (structure == UNKNOWN) {
				continue;
			}
			String label = structure.label.toLowerCase(Locale.ROOT).replace('û', 'u').replace("'", "");
			if (needle.equals(label) || needle.contains(label) || label.contains(needle)) {
				return structure;
			}
		}
		return null;
	}

	public boolean matchesChat(String message) {
		if (chatHint == null || message == null) {
			return false;
		}
		return message.startsWith(chatHint) || message.contains(chatHint);
	}
}
