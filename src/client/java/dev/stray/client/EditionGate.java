package dev.stray.client;

import dev.stray.Edition;
import net.minecraft.client.gui.screens.Screen;

/**
 * Calls into features that Stray Legit leaves out. Each body is behind
 * {@code if (!Edition.LEGIT)}, a compile-time constant, so the legit build has no
 * reference to those classes and the jar can ship without them.
 */
public final class EditionGate {
	private EditionGate() {
	}

	/** True while the world editor is open. */
	public static boolean editorActive() {
		if (!Edition.LEGIT) {
			return dev.stray.client.editor.WorldEditor.active();
		}
		return false;
	}

	public static void openEditor() {
		if (!Edition.LEGIT) {
			dev.stray.client.editor.WorldEditor.enable();
		}
	}

	public static void toggleFreecam() {
		if (!Edition.LEGIT) {
			dev.stray.client.movement.Freecam.toggle();
		}
	}

	/** Scroll changes the freecam speed while it is on. */
	public static boolean freecamScroll(double amount) {
		if (!Edition.LEGIT) {
			return dev.stray.client.movement.Freecam.scroll(amount);
		}
		return false;
	}

	/** True while the auto clicker holds left click. */
	public static boolean autoClickerLeftHeld() {
		if (!Edition.LEGIT) {
			return dev.stray.client.combat.AutoClicker.leftHeld();
		}
		return false;
	}

	/** Stray's loadouts or wardrobe menu. */
	public static boolean loadoutMenu(Screen screen) {
		if (!Edition.LEGIT) {
			return screen instanceof dev.stray.client.ui.LoadoutsScreen || screen instanceof dev.stray.client.ui.WardrobeScreen;
		}
		return false;
	}

	public static boolean loadoutsScreen(Screen screen) {
		if (!Edition.LEGIT) {
			return screen instanceof dev.stray.client.ui.LoadoutsScreen;
		}
		return false;
	}

	/** Stray's loadouts menu replacing Hypixel's chest: the chest is not drawn. */
	public static boolean hideDefaultChest(Screen screen) {
		if (!Edition.LEGIT) {
			return dev.stray.client.ui.LoadoutsScreen.hideDefaultChest(screen);
		}
		return false;
	}

	/** Swaps Hypixel's loadouts / wardrobe chest for Stray's menu. */
	public static Screen wrapScreen(Screen screen) {
		if (!Edition.LEGIT) {
			return dev.stray.client.ui.WardrobeScreen.wrap(dev.stray.client.ui.LoadoutsScreen.wrap(screen));
		}
		return screen;
	}

	/** "1-9" or the remapped keys that pick loadout and wardrobe slots. */
	public static String slotHint() {
		if (!Edition.LEGIT) {
			return dev.stray.client.ui.MenuSlotBinds.hint();
		}
		return "";
	}
}
