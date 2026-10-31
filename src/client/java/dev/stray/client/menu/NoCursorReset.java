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
		if (client.gui.screen() != null) {
			wasNotNull = true;
			clock = System.currentTimeMillis();
		} else if (wasNotNull) {
			wasNotNull = false;
			clock = System.currentTimeMillis();
		}
	}

	private static final String[] SKIPPED_TITLES = {"spirit leap", "teleport to player"};

	/** Menus the cursor should reset in anyway (the leap menus), when that toggle is on. */
	public static boolean skipped(net.minecraft.client.gui.screens.Screen screen) {
		if (screen == null || !StrayConfig.get().noCursorResetSkipLeap) {
			return false;
		}
		String title = dev.stray.client.util.Text.strip(screen.getTitle().getString()).toLowerCase(java.util.Locale.ROOT);
		for (String skip : SKIPPED_TITLES) {
			if (title.contains(skip)) {
				return true;
			}
		}
		return false;
	}

	public static boolean shouldHookMouse() {
		StrayConfig config = StrayConfig.get();
		return config.noCursorReset && System.currentTimeMillis() - clock < config.noCursorResetTimeout;
	}
}
