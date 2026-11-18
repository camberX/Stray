package dev.stray.client.dungeon.map;

import dev.stray.client.config.StrayConfig;

import java.util.ArrayList;
import java.util.List;

public final class DoorTile extends Tile {
	public DoorType type;
	public boolean opened;
	private final Tile[] grid;
	/** Grid indices of the two cells this door joins. */
	public final int[] roomTileIndices;

	public DoorTile(int x, int z, DoorType type) {
		this(x, z, type, Scanner.GRID);
	}

	public DoorTile(int x, int z, DoorType type, Tile[] grid) {
		super(x, z);
		this.type = type;
		this.grid = grid;
		int row = row();
		int column = column();
		List<Integer> out = new ArrayList<>(2);
		if ((row & 1) == 0) {
			if (column - 1 >= 0) {
				out.add(row * 11 + column - 1);
			}
			if (column + 1 <= 10) {
				out.add(row * 11 + column + 1);
			}
		} else {
			if (row - 1 >= 0) {
				out.add((row - 1) * 11 + column);
			}
			if (row + 1 <= 10) {
				out.add((row + 1) * 11 + column);
			}
		}
		roomTileIndices = out.stream().mapToInt(Integer::intValue).toArray();
	}

	public List<RoomTile> roomTiles() {
		List<RoomTile> out = new ArrayList<>(2);
		for (int index : roomTileIndices) {
			if (grid[index] instanceof RoomTile room) {
				out.add(room);
			}
		}
		return out;
	}

	@Override
	public int color() {
		StrayConfig c = StrayConfig.get();
		if (state == RoomState.UNOPENED) {
			return c.dungeonMapColorUnopenedDoor;
		}
		if (type == DoorType.BLOOD) {
			return c.dungeonMapColorBloodDoor;
		}
		if (type == DoorType.ENTRANCE) {
			return c.dungeonMapColorEntranceDoor;
		}
		if (type == DoorType.WITHER || DungeonTree.isFairy(this)) {
			return opened && state != RoomState.UNDISCOVERED ? c.dungeonMapColorOpenWitherDoor : c.dungeonMapColorWitherDoor;
		}
		List<RoomTile> colored = new ArrayList<>(2);
		for (RoomTile room : roomTiles()) {
			if (room.data.type() != RoomType.NORMAL) {
				colored.add(room);
			}
		}
		RoomTile pick = null;
		if (colored.size() == 2) {
			for (RoomTile room : colored) {
				if (room.data.type() != RoomType.FAIRY) {
					pick = room;
					break;
				}
			}
		} else if (!colored.isEmpty()) {
			pick = colored.get(0);
		}
		return pick != null ? pick.color() : c.dungeonMapColorRoomDoor;
	}
}
