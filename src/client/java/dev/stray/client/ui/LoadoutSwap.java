package dev.stray.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.stray.client.StrayClient;
import dev.stray.client.combat.OdinClicks;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.farming.FarmKeys;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.lwjgl.glfw.GLFW;

/**
 * Toggles between two loadout slots without showing the loadouts menu.
 * With farm keys on, breaking and movement are released for the click and
 * pressed again on the next tick, from {@code handleKeybinds} before movement
 * so the click is not sent after the position packet.
 */
public final class LoadoutSwap {
	private static final int WAIT_TICKS = 40;
	private static boolean wasHeld;
	private static boolean releaseAttack;
	private static KeyMapping[] released;
	private static Phase phase = Phase.IDLE;
	private static int waited;

	private enum Phase {
		IDLE,
		HELD,
		RELEASED,
		RESTORE
	}

	private LoadoutSwap() {
	}

	public static void syncEdge() {
		wasHeld = held();
	}

	/**
	 * Runs at the start of the client tick, including while a screen is open.
	 * {@code handleKeybinds} does not run then, so the click has to happen here,
	 * still before this tick's movement packet.
	 */
	public static void onStart(Minecraft client) {
		if (client == null || client.options == null || phase != Phase.HELD) {
			return;
		}
		suppress(client);
		if (LoadoutsScreen.clickSilentNow(client)) {
			phase = Phase.RELEASED;
			return;
		}
		if (++waited > WAIT_TICKS) {
			LoadoutsScreen.cancelSilent();
			phase = Phase.RELEASED;
		}
	}

	/**
	 * Runs at the start of {@code handleKeybinds}, before this tick's attack
	 * and movement packets. Skipped while a screen is open.
	 */
	public static void preKeybinds(Minecraft client) {
		if (client == null || client.options == null) {
			return;
		}
		if (phase == Phase.RESTORE) {
			restore(client);
			phase = Phase.IDLE;
			return;
		}
		if (phase == Phase.RELEASED) {
			suppress(client);
			phase = Phase.RESTORE;
			return;
		}
		if (phase == Phase.HELD) {
			suppress(client);
			return;
		}
		boolean down = held();
		boolean press = down && !wasHeld;
		wasHeld = down;
		if (!press || client.screen != null || client.player == null) {
			return;
		}
		if (!arm()) {
			return;
		}
		boolean pause = FarmKeys.enabled()
			&& (FarmKeys.breaking() || client.options.keyAttack.isDown() || moving(client.options));
		if (pause) {
			capture(client);
		} else {
			released = null;
			releaseAttack = false;
		}
		LoadoutsCommands.openHidden();
		suppress(client);
		waited = 0;
		phase = Phase.HELD;
	}

	private static boolean arm() {
		StrayConfig config = StrayConfig.get();
		if (!OdinClicks.bound(OdinClicks.parseKey(config.loadoutSwapKey))) {
			return false;
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
		LoadoutsScreen.armHidden(next);
		return true;
	}

	private static boolean held() {
		return StrayClient.menuKeyHeld(StrayConfig.get().loadoutSwapKey);
	}

	private static boolean moving(Options options) {
		return options.keyUp.isDown()
			|| options.keyDown.isDown()
			|| options.keyLeft.isDown()
			|| options.keyRight.isDown()
			|| options.keyJump.isDown()
			|| options.keySprint.isDown()
			|| options.keyShift.isDown();
	}

	private static void capture(Minecraft client) {
		Options options = client.options;
		releaseAttack = FarmKeys.breaking() || options.keyAttack.isDown();
		KeyMapping[] keys = {
			options.keyUp,
			options.keyDown,
			options.keyLeft,
			options.keyRight,
			options.keyJump,
			options.keySprint,
			options.keyShift
		};
		int count = 0;
		for (KeyMapping key : keys) {
			if (key.isDown()) {
				count++;
			}
		}
		released = new KeyMapping[count];
		int index = 0;
		for (KeyMapping key : keys) {
			if (key.isDown()) {
				released[index++] = key;
			}
		}
	}

	private static void suppress(Minecraft client) {
		if (releaseAttack) {
			client.options.keyAttack.setDown(false);
		}
		if (released == null) {
			return;
		}
		for (KeyMapping key : released) {
			key.setDown(false);
		}
	}

	private static void restore(Minecraft client) {
		if (releaseAttack && (FarmKeys.breaking() || physical(client, client.options.keyAttack))) {
			client.options.keyAttack.setDown(true);
		}
		if (released != null) {
			for (KeyMapping key : released) {
				if (physical(client, key)) {
					key.setDown(true);
				}
			}
		}
		released = null;
		releaseAttack = false;
	}

	private static boolean physical(Minecraft client, KeyMapping mapping) {
		if (client.getWindow() == null || mapping == null) {
			return false;
		}
		InputConstants.Key key = InputConstants.getKey(mapping.saveString());
		if (key == null || key.equals(InputConstants.UNKNOWN)) {
			return false;
		}
		return switch (key.getType()) {
			case KEYSYM -> InputConstants.isKeyDown(client.getWindow(), key.getValue());
			case MOUSE -> GLFW.glfwGetMouseButton(client.getWindow().handle(), key.getValue()) == GLFW.GLFW_PRESS;
			default -> false;
		};
	}
}
