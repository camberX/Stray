package dev.stray.client.farming;

import com.mojang.blaze3d.platform.InputConstants;
import dev.stray.client.StrayClient;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.mixin.AbstractContainerScreenInvoker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * Keybind: {@code /call Philip}, then one click on the hopper minecart named
 * Empty Vacuum Bag once the Pesthunter chest is open.
 * With farm keys on, breaking and movement are released for that click and
 * pressed again on the next tick, from {@code handleKeybinds} before movement,
 * same as loadout swap.
 */
public final class EmptyVacuum {
	private static final int WAIT_TICKS = 200;
	private static final String COMMAND = "call Philip";
	private static final String MENU = "Pesthunter";
	private static final String BAG = "Empty Vacuum Bag";
	private static int waiting;
	private static int menuTicks;
	private static boolean wasHeld;
	private static boolean releaseAttack;
	private static KeyMapping[] released;
	private static Phase phase = Phase.IDLE;

	private enum Phase {
		IDLE,
		HELD,
		RELEASED,
		RESTORE
	}

	private EmptyVacuum() {
	}

	public static void syncEdge() {
		wasHeld = held();
	}

	public static void reset() {
		waiting = 0;
		menuTicks = 0;
		Minecraft client = Minecraft.getInstance();
		if (phase != Phase.IDLE && client != null && client.options != null) {
			restore(client);
		} else {
			released = null;
			releaseAttack = false;
		}
		phase = Phase.IDLE;
		wasHeld = held();
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
		begin(client);
	}

	/** Keeps the released keys up after farm keys relatch attack. */
	public static void afterFarmKeys(Minecraft client) {
		if (client == null || client.options == null) {
			return;
		}
		if (phase == Phase.HELD || phase == Phase.RELEASED) {
			suppress(client);
		}
	}

	public static void poll(Minecraft client, boolean ignore) {
		if (phase != Phase.IDLE) {
			wasHeld = held();
			return;
		}
		boolean down = held();
		if (!ignore && down && !wasHeld && client.screen == null) {
			begin(client);
		}
		wasHeld = down;
	}

	/** Runs at tick start, before movement, so the menu click is not post. */
	public static void onStart(Minecraft client) {
		if (phase == Phase.HELD && client != null && client.options != null) {
			suppress(client);
		}
		if (waiting <= 0) {
			return;
		}
		waiting--;
		if (client.player == null || client.level == null) {
			waiting = 0;
			menuTicks = 0;
			finish();
			return;
		}
		if (!pesthunterOpen(client)) {
			menuTicks = 0;
			if (waiting <= 0) {
				finish();
			}
			return;
		}
		int delay = StrayConfig.clamp(StrayConfig.get().emptyVacuumDelay, 1, 10);
		if (menuTicks < delay) {
			menuTicks++;
			if (waiting <= 0) {
				finish();
			}
			return;
		}
		if (clickBag(client) || waiting <= 0) {
			waiting = 0;
			menuTicks = 0;
			finish();
		}
	}

	private static void begin(Minecraft client) {
		boolean pause = FarmKeys.enabled()
			&& (FarmKeys.breaking() || client.options.keyAttack.isDown() || moving(client.options));
		if (pause) {
			capture(client);
			suppress(client);
			phase = Phase.HELD;
		} else {
			released = null;
			releaseAttack = false;
		}
		call(client);
		waiting = WAIT_TICKS;
		menuTicks = 0;
	}

	private static void finish() {
		if (phase == Phase.HELD) {
			phase = Phase.RELEASED;
		}
	}

	private static boolean held() {
		return StrayClient.menuKeyHeld(StrayConfig.get().emptyVacuumKey);
	}

	private static void call(Minecraft client) {
		if (client.player == null || client.player.connection == null) {
			return;
		}
		try {
			client.player.connection.send(new ServerboundChatCommandPacket(COMMAND));
		} catch (RuntimeException ignored) {
			try {
				client.player.connection.sendCommand(COMMAND);
			} catch (RuntimeException ignoredToo) {
			}
		}
	}

	private static boolean pesthunterOpen(Minecraft client) {
		if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
			return false;
		}
		if (!(screen.getMenu() instanceof ChestMenu)) {
			return false;
		}
		String title = plain(screen.getTitle() == null ? "" : screen.getTitle().getString());
		return MENU.equalsIgnoreCase(title);
	}

	private static boolean clickBag(Minecraft client) {
		if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
			return false;
		}
		if (!(screen.getMenu() instanceof ChestMenu chest)) {
			return false;
		}
		if (!pesthunterOpen(client)) {
			return false;
		}
		int size = chest.getContainer().getContainerSize();
		for (int i = 0; i < size && i < chest.slots.size(); i++) {
			Slot slot = chest.getSlot(i);
			ItemStack stack = slot.getItem();
			if (stack.isEmpty() || !stack.is(Items.HOPPER_MINECART)) {
				continue;
			}
			if (!BAG.equalsIgnoreCase(plain(stack.getHoverName().getString()))) {
				continue;
			}
			((AbstractContainerScreenInvoker) screen).stray$slotClicked(slot, slot.index, 0, ContainerInput.PICKUP);
			return true;
		}
		return false;
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

	private static String plain(String text) {
		if (text == null) {
			return "";
		}
		String stripped = ChatFormatting.stripFormatting(text);
		return stripped == null ? "" : stripped.trim();
	}
}
