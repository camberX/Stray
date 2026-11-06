package dev.stray.client.dungeon.map;

import java.util.List;

/** One known room: its name, kind, shape and the core hashes that identify it in the world. */
public record RoomData(String name, RoomType type, RoomShape shape, List<Integer> cores, int secrets, int crypts, int trappedChests) {
	public boolean isUnknown() {
		return "Unknown".equals(name) && shape == RoomShape.UNKNOWN;
	}

	public static RoomData unknown(RoomType type) {
		return new RoomData("Unknown", type, RoomShape.UNKNOWN, List.of(), 0, 0, 0);
	}
}
