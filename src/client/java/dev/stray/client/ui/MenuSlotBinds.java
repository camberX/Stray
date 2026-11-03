package dev.stray.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.stray.client.combat.OdinClicks;
import dev.stray.client.config.StrayConfig;

/**
 * Shared 1-9 (or remapped) keys for loadout and wardrobe slots.
 */
public final class MenuSlotBinds {
	private MenuSlotBinds() {
	}

	public static int index(int keyCode) {
		String[] keys = StrayConfig.normalizeMenuSlotKeys(StrayConfig.get().menuSlotKeys);
		for (int i = 0; i < keys.length; i++) {
			InputConstants.Key key = OdinClicks.parseKey(keys[i]);
			if (OdinClicks.bound(key) && key.getType() == InputConstants.Type.KEYBOARD && key.getValue() == keyCode) {
				return i;
			}
		}
		return -1;
	}

	/** Short label of the key bound to slot {@code index}, or null when unbound. */
	public static String keyName(int index) {
		String[] keys = StrayConfig.normalizeMenuSlotKeys(StrayConfig.get().menuSlotKeys);
		if (index < 0 || index >= keys.length) {
			return null;
		}
		InputConstants.Key key = OdinClicks.parseKey(keys[index]);
		if (!OdinClicks.bound(key)) {
			return null;
		}
		String name = key.getDisplayName().getString();
		return name.length() > 4 ? name.substring(0, 4) : name;
	}

	public static String hint() {
		String[] keys = StrayConfig.normalizeMenuSlotKeys(StrayConfig.get().menuSlotKeys);
		String[] defaults = StrayConfig.defaultMenuSlotKeys();
		for (int i = 0; i < keys.length; i++) {
			if (!defaults[i].equals(keys[i])) {
				return "Slot keys";
			}
		}
		return "1-9";
	}
}
