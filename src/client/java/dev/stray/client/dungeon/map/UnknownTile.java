package dev.stray.client.dungeon.map;

public final class UnknownTile extends Tile {
	public UnknownTile(int x, int z) {
		super(x, z);
	}

	@Override
	public int color() {
		return 0;
	}
}
