package dev.stray.client.dungeon.map;

/**
 * Room colours for the Shadow map: the usual map colours (so a room reads at a glance),
 * pulled down in saturation and brightness so they sit quietly on the dark panel.
 */
public final class MapColors {
	public static final int UNOPENED = 0xFF2E333A;
	public static final int UNOPENED_DOOR = 0xFF2E333A;
	public static final int WITHER_DOOR = 0xFF0B0D10;
	/** Unopened rooms on the full-edition map that shows everything: the room colour, darkened. */
	public static final float UNDISCOVERED_TINT = 0.55f;

	private MapColors() {
	}

	public static int room(RoomType type) {
		return switch (type) {
			case BLOOD -> 0xFFB8403B;
			case FAIRY -> 0xFFD98AAE;
			case RARE -> 0xFF9AA3AD;
			case CHAMPION -> 0xFFCBB54A;
			case PUZZLE -> 0xFF8B58C2;
			case TRAP -> 0xFFC9803F;
			case ENTRANCE -> 0xFF2F8A55;
			case NORMAL -> 0xFF6E5038;
		};
	}

	/** Mixes {@code a} toward {@code b} by {@code t} (alpha from {@code a}). */
	public static int mix(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return (a & 0xFF000000) | (r << 16) | (g << 8) | bl;
	}

	public static int alpha(int color, float alpha) {
		int a = Math.round(((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
		return (a << 24) | (color & 0xFFFFFF);
	}

	/** Two shades darker, like Color.darker() twice. */
	public static int darker(int color) {
		int r = (int) (((color >> 16) & 0xFF) * 0.49f);
		int g = (int) (((color >> 8) & 0xFF) * 0.49f);
		int b = (int) ((color & 0xFF) * 0.49f);
		return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
	}
}
