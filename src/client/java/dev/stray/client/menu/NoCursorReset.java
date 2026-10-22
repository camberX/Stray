package dev.stray.client.menu;

import dev.stray.client.config.StrayConfig;
import net.minecraft.client.Minecraft;

/**
 * Keeps the cursor where it was when a chest menu is replaced by another one.
 * The clock stays fresh while a screen is open, and for a short time after it closes.
 */
public final class NoCursorReset {
	private static long clock = System.currentTimeMillis();
	private static boolean wasNotNull;

	private NoCursorReset() {
	}

	public static void tick(Minecraft client) {
		if (client.screen != null) {
			wasNotNull = true;
			clock = System.currentTimeMillis();
		} else if (wasNotNull) {
			wasNotNull = false;
			clock = System.currentTimeMillis();
		}
	}

	public static boolean shouldHookMouse() {
		StrayConfig config = StrayConfig.get();
		return config.noCursorReset && System.currentTimeMillis() - clock < config.noCursorResetTimeout;
	}
}
