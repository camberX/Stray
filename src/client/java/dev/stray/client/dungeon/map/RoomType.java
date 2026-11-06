package dev.stray.client.dungeon.map;

/** What a dungeon room is, as the map and room data name it. From NoammAddons (CC0). */
public enum RoomType {
	BLOOD, FAIRY, RARE, CHAMPION, PUZZLE, TRAP, NORMAL, ENTRANCE;

	/** The room's colour on the vanilla dungeon map, or null when it is not a room colour. */
	public static RoomType fromMapColor(int color) {
		return switch (color) {
			case 18 -> BLOOD;
			case 82 -> FAIRY;
			case 34 -> RARE;
			case 74 -> CHAMPION;
			case 66 -> PUZZLE;
			case 62 -> TRAP;
			case 63, 85 -> NORMAL;
			case 30 -> ENTRANCE;
			default -> null;
		};
	}

	public static RoomType parse(String raw) {
		if (raw == null) {
			return NORMAL;
		}
		try {
			return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return NORMAL;
		}
	}
}
