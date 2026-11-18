package dev.stray.client.dungeon.map;

/** One cell of the 11x11 dungeon grid: a room, a door, a room connector or nothing. */
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

	public void setState(RoomState state) {
		this.state = state;
	}

	/** ARGB. */
	public abstract int color();

	public int row() {
		return (z - Scanner.START_Z) / (Scanner.ROOM_SIZE >> 1);
	}

	public int column() {
		return (x - Scanner.START_X) / (Scanner.ROOM_SIZE >> 1);
	}
}
