package dev.stray.client.dungeon;

/**
 * /stray debug dev: lets the F7 extras run outside a real Hypixel dungeon, so they can
 * be tried in singleplayer or on a private server. With it on, the custom terminal GUI
 * takes any window with a terminal's title and the F7 titles react anywhere.
 */
public final class DevMode {
	private static boolean on;

	private DevMode() {
	}

	public static boolean on() {
		return on;
	}

	public static void set(boolean value) {
		on = value;
	}
}
