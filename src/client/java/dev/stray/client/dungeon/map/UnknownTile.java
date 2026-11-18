package dev.stray.client.dungeon.map;

public final class UnknownTile extends Tile {
	public static final UnknownTile EMPTY = new UnknownTile(0, 0);

	public UnknownTile(int x, int z) {
		super(x, z);
	}

	@Override
	public void setState(RoomState state) {
		if (this != EMPTY) {
			super.setState(state);
		}
	}

	@Override
	public int color() {
		return 0;
	}
}
