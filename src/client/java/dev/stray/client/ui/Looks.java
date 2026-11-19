package dev.stray.client.ui;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.config.UnloadState;

/**
 * The look everything Stray draws follows: Shadow, uid? or Expressive (Material 3 Expressive).
 * One setting picks the menu, the HUD, Stray's own screens and the vanilla menus together.
 */
public final class Looks {
	public static final String SHADOW = "shadow";
	public static final String UID = "uid";
	public static final String EXPRESSIVE = "expressive";
	public static final String[] ALL = {SHADOW, UID, EXPRESSIVE};
	public static final String[] LABELS = {"Shadow", "uid?", "Expressive"};

	/** While a preview draws another look than the saved one (the first-boot picker). */
	private static String forced;

	private Looks() {
	}

	public static String current() {
		if (forced != null) {
			return forced;
		}
		StrayConfig config = StrayConfig.get();
		return config == null ? SHADOW : StrayConfig.normalizeTheme(config.theme);
	}

	public static int index() {
		String look = current();
		for (int i = 0; i < ALL.length; i++) {
			if (ALL[i].equals(look)) {
				return i;
			}
		}
		return 0;
	}

	public static boolean shadow() {
		return SHADOW.equals(current());
	}

	public static boolean uid() {
		return UID.equals(current());
	}

	public static boolean ex() {
		return EXPRESSIVE.equals(current());
	}

	/** Switches everything to this look and saves it. */
	public static void set(String look) {
		StrayConfig config = StrayConfig.get();
		String next = StrayConfig.normalizeTheme(look);
		config.theme = next;
		config.clickGuiStyle = next;
		config.watermarkStyle = next;
		ExColors.invalidate();
		Theme.refresh();
		config.save();
		UnloadState.markDirty();
	}

	/** Draws with another look for a moment (the picker's previews); {@code null} goes back. */
	public static void force(String look) {
		forced = look;
		Theme.refresh();
	}
}
