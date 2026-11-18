package dev.stray.client.dungeon.map;

/** Room kinds, as in NoammAddons' map (used with noamm's permission). */
public enum RoomType {
	BLOOD, FAIRY, RARE, CHAMPION, PUZZLE, TRAP, NORMAL, ENTRANCE;

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
		try {
			return valueOf(raw);
		} catch (RuntimeException e) {
			return NORMAL;
		}
	}
}
