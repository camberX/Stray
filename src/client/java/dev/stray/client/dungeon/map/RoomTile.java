package dev.stray.client.dungeon.map;

public final class RoomTile extends Tile {
	public RoomData data;
	public UniqueRoom uniqueRoom;
	/** The piece between two cells of the same room (or the middle of a 2x2). */
	public boolean isSeparator;

	public RoomTile(int x, int z, RoomData data) {
		super(x, z);
		this.data = data;
	}

	@Override
	public void setState(RoomState value) {
		RoomState old = state;
		state = value;
		if (old == value || uniqueRoom == null || uniqueRoom.mainRoom != this || "Unknown".equals(data.name())) {
			return;
		}
		if (value == RoomState.GREEN) {
			uniqueRoom.setFoundSecrets(uniqueRoom.data.secrets());
		}
	}

	@Override
	public int color() {
		if (state == RoomState.UNOPENED) {
			return MapColors.UNOPENED;
		}
		return MapColors.room(data.type());
	}

	public void addToUnique(int row, int column) {
		addToUnique(row, column, data.name());
	}

	public void addToUnique(int row, int column, String roomName) {
		UniqueRoom unique = DungeonScanner.UNIQUE_ROOMS.get(roomName);
		if (unique == null) {
			UniqueRoom made = new UniqueRoom(column, row, this);
			DungeonScanner.UNIQUE_ROOMS.put(data.name(), made);
			uniqueRoom = made;
		} else {
			unique.addTile(column, row, this);
			uniqueRoom = unique;
		}
	}
}
