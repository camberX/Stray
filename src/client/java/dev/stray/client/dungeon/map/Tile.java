package dev.stray.client.dungeon.map;

/** One cell of the 11 x 11 dungeon grid: a room piece, a door, or nothing known yet. */
public abstract class Tile {
	public final int x;
	public final int z;
	protected RoomState state = RoomState.UNDISCOVERED;

	protected Tile(int x, int z) {
		this.x = x;
		this.z = z;
	}

	public RoomState state() {
		return state;
	}

	public void setState(RoomState value) {
		state = value;
	}

	/** Fill colour on the map. */
	public abstract int color();

	public int row() {
		return (z - DungeonScanner.START_Z) / (DungeonScanner.ROOM_SIZE >> 1);
	}

	public int column() {
		return (x - DungeonScanner.START_X) / (DungeonScanner.ROOM_SIZE >> 1);
	}
}
