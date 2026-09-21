package dev.stray.client.ui;

import dev.stray.client.combat.OdinClicks;
import dev.stray.client.config.StrayConfig;

/**
 * Toggles between two loadout slots chosen in settings.
 * The loadouts menu closes after the click.
 */
public final class LoadoutSwap {
	private LoadoutSwap() {
	}

	public static void toggle() {
		StrayConfig config = StrayConfig.get();
		if (!OdinClicks.bound(OdinClicks.parseKey(config.loadoutSwapKey))) {
			return;
		}
		int first = StrayConfig.clampLoadoutSwapSlot(config.loadoutSwapSlotA) - 1;
		int second = StrayConfig.clampLoadoutSwapSlot(config.loadoutSwapSlotB) - 1;
		int current = LoadoutsScreen.selectedIndex();
		int next;
		if (first == second) {
			next = first;
		} else if (current == first) {
			next = second;
		} else if (current == second) {
			next = first;
		} else {
			next = config.loadoutSwapNextIsB ? second : first;
		}
		config.loadoutSwapNextIsB = next == first && first != second;
		config.save();
		LoadoutsScreen.equipIndex(next);
	}
}
