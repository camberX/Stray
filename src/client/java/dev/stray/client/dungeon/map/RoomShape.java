package dev.stray.client.dungeon.map;

public enum RoomShape {
	UNKNOWN(0, "Unknown"),
	SL(3, "L"),
	S1x1(1, "1x1"),
	S2x1(2, "1x2"),
	S3x1(3, "1x3"),
	S4x1(4, "1x4"),
	S2x2(4, "2x2");

	public final int tileCount;
	private final String json;

	RoomShape(int tileCount, String json) {
		this.tileCount = tileCount;
		this.json = json;
	}

	public static RoomShape parse(String raw) {
		for (RoomShape shape : values()) {
			if (shape.json.equalsIgnoreCase(raw) || shape.name().equalsIgnoreCase(raw)) {
				return shape;
			}
		}
		return UNKNOWN;
	}
}
