package dev.stray.client.dungeon.map;

import java.util.List;

public record RoomData(String name, RoomType type, RoomShape shape, List<Integer> cores, int secrets, int crypts, int trappedChests) {
	public static RoomData unknown(RoomType type) {
		return new RoomData("Unknown", type, RoomShape.UNKNOWN, List.of(), 0, 0, 0);
	}

	public boolean isUnknown() {
		return "Unknown".equals(name) && shape == RoomShape.UNKNOWN;
	}
}
