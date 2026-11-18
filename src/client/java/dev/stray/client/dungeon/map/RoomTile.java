package dev.stray.client.dungeon.map;

import dev.stray.client.config.StrayConfig;

public final class RoomTile extends Tile {
	public RoomData data;
	public UniqueRoom uniqueRoom;
	public boolean isSeparator;

	public RoomTile(int x, int z, RoomData data) {
		super(x, z);
		this.data = data;
	}

	@Override
	public void setState(RoomState next) {
		RoomState old = state;
		state = next;
		if (old == next || uniqueRoom == null || uniqueRoom.mainRoom != this || data.isUnknown()) {
			return;
		}
		if (next == RoomState.GREEN) {
			uniqueRoom.setFoundSecrets(uniqueRoom.data.secrets());
		}
	}

	@Override
	public int color() {
		StrayConfig c = StrayConfig.get();
		if (state == RoomState.UNOPENED) {
			return c.dungeonMapColorUnopened;
		}
		return switch (data.type()) {
			case BLOOD -> c.dungeonMapColorBlood;
			case FAIRY -> c.dungeonMapColorFairy;
			case RARE -> c.dungeonMapColorRare;
			case CHAMPION -> c.dungeonMapColorMiniboss;
			case PUZZLE -> c.dungeonMapColorPuzzle;
			case TRAP -> c.dungeonMapColorTrap;
			case ENTRANCE -> c.dungeonMapColorEntrance;
			default -> c.dungeonMapColorRoom;
		};
	}

	/** Adds this tile to the room called {@code name} (making that room when it's the first tile). */
	public void addToUnique(int row, int column, String name) {
		UniqueRoom unique = Scanner.UNIQUE_ROOMS.get(name);
		if (unique == null) {
			unique = new UniqueRoom(column, row, this);
			Scanner.UNIQUE_ROOMS.put(data.name(), unique);
		} else {
			unique.addTile(column, row, this);
		}
		uniqueRoom = unique;
	}

	public void addToUnique(int row, int column) {
		addToUnique(row, column, data.name());
	}
}
