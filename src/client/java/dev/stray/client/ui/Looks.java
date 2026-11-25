package dev.stray.client.ui;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.config.UnloadState;

/**
 * The look everything Stray draws follows: Minecraft (the game's own font and widgets, chat-black
 * panels), Modern (neutral grey panels) or Skeet (the uid? menu). One setting picks the menu, the
 * HUD, Stray's own screens and the vanilla menus together. Stray is the house look: near-black,
 * hairlines and one accent colour. Shadow and Expressive were removed;
 * a config that still names one opens as Minecraft.
 */
public final class Looks {
	public static final String UID = "uid";
	/** Neutral greys, every category's panel side by side, what is on in white with the accent. */
	public static final String PANELS = "panels";
	/** The game's own look: its font with shadow, chat-black panels, vanilla screens left as they are. */
	public static final String MINECRAFT = "minecraft";
	/** Windows 98: grey bevelled windows, a blue title bar and a taskbar watermark. */
	public static final String WIN98 = "win98";
	/** Stray: near-black panels, hairlines, numbered categories and one accent colour. */
	public static final String SIGNATURE = "signature";
	public static final String[] ALL = {MINECRAFT, PANELS, UID, WIN98, SIGNATURE};
	public static final String[] LABELS = {"Minecraft", "Modern", "Skeet", "Windows 98", "Stray"};

	/** While a preview draws another look than the saved one (the first-boot picker). */
	private static String forced;

	private Looks() {
	}

	public static String current() {
		if (forced != null) {
			return forced;
		}
		StrayConfig config = StrayConfig.get();
		return config == null ? MINECRAFT : StrayConfig.normalizeTheme(config.theme);
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

	public static boolean minecraft() {
		return MINECRAFT.equals(current());
	}

	public static boolean uid() {
		return UID.equals(current());
	}


	public static boolean win98() {
		return WIN98.equals(current());
	}

	public static boolean signature() {
		return SIGNATURE.equals(current());
	}

	public static boolean panels() {
		return PANELS.equals(current());
	}

	/** Switches everything to this look and saves it. */
	public static void set(String look) {
		StrayConfig config = StrayConfig.get();
		String next = StrayConfig.normalizeTheme(look);
		config.theme = next;
		config.clickGuiStyle = next;
		config.watermarkStyle = next;
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
