package dev.stray.client.dungeon.map;

public enum RoomShape {
	UNKNOWN("Unknown", 0), SL("L", 3), S1x1("1x1", 1), S2x1("1x2", 2), S3x1("1x3", 3), S4x1("1x4", 4), S2x2("2x2", 4);

	public final String id;
	public final int tileCount;

	RoomShape(String id, int tileCount) {
		this.id = id;
		this.tileCount = tileCount;
	}

	public static RoomShape parse(String raw) {
		for (RoomShape shape : values()) {
			if (shape.id.equals(raw)) {
				return shape;
			}
		}
		return UNKNOWN;
	}
}
