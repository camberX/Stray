package dev.stray.client.dungeon.map;

import java.util.ArrayList;
import java.util.List;

public final class DoorTile extends Tile {
	public DoorType type;
	public boolean opened;
	private final Tile[] grid;
	public final int[] roomTileIndices;

	public DoorTile(int x, int z, DoorType type) {
		this(x, z, type, DungeonScanner.GRID);
	}

	public DoorTile(int x, int z, DoorType type, Tile[] grid) {
		super(x, z);
		this.type = type;
		this.grid = grid;
		int row = row();
		int column = column();
		List<Integer> neighbors = new ArrayList<>(2);
		if ((row & 1) == 0) {
			if (column - 1 >= 0) {
				neighbors.add(row * 11 + column - 1);
			}
			if (column + 1 <= 10) {
				neighbors.add(row * 11 + column + 1);
			}
		} else {
			if (row - 1 >= 0) {
				neighbors.add((row - 1) * 11 + column);
			}
			if (row + 1 <= 10) {
				neighbors.add((row + 1) * 11 + column);
			}
		}
		roomTileIndices = neighbors.stream().mapToInt(Integer::intValue).toArray();
	}

	public List<RoomTile> roomTiles() {
		List<RoomTile> out = new ArrayList<>(2);
		for (int index : roomTileIndices) {
			if (index >= 0 && index < grid.length && grid[index] instanceof RoomTile room) {
				out.add(room);
			}
		}
		return out;
	}

	@Override
	public int color() {
		if (state == RoomState.UNOPENED) {
			return MapColors.UNOPENED_DOOR;
		}
		if (type == DoorType.BLOOD) {
			return MapColors.room(RoomType.BLOOD);
		}
		if (type == DoorType.ENTRANCE) {
			return MapColors.room(RoomType.ENTRANCE);
		}
		if (type == DoorType.WITHER || DungeonTree.isFairy(this)) {
			return opened && state != RoomState.UNDISCOVERED ? MapColors.room(RoomType.NORMAL) : MapColors.WITHER_DOOR;
		}
		List<RoomTile> colored = new ArrayList<>();
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
		return pick == null ? MapColors.room(RoomType.NORMAL) : pick.color();
	}
}
