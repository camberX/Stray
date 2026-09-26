package dev.stray.client.farming;

import dev.stray.client.StrayClient;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.mixin.AbstractContainerScreenInvoker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Keybind: {@code /call Philip}, then one click on the hopper minecart named
 * Empty Vacuum Bag once the Pesthunter chest is open.
 */
public final class EmptyVacuum {
	private static final int WAIT_TICKS = 200;
	private static final String COMMAND = "call Philip";
	private static final String MENU = "Pesthunter";
	private static final String BAG = "Empty Vacuum Bag";
	private static int waiting;
	private static boolean wasHeld;

	private EmptyVacuum() {
	}

	public static void syncEdge() {
		wasHeld = held();
	}

	public static void reset() {
		waiting = 0;
		wasHeld = held();
	}

	public static void poll(Minecraft client, boolean ignore) {
		boolean down = held();
		if (!ignore && down && !wasHeld && client.screen == null) {
			call(client);
			waiting = WAIT_TICKS;
		}
		wasHeld = down;
	}

	/** Runs at tick start, before movement, so the menu click is not post. */
	public static void onStart(Minecraft client) {
		if (waiting <= 0) {
			return;
		}
		waiting--;
		if (client.player == null || client.level == null) {
			waiting = 0;
			return;
		}
		if (clickBag(client)) {
			waiting = 0;
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

	private static boolean clickBag(Minecraft client) {
		if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
			return false;
		}
		if (!(screen.getMenu() instanceof ChestMenu chest)) {
			return false;
		}
		String title = plain(screen.getTitle() == null ? "" : screen.getTitle().getString());
		if (!MENU.equalsIgnoreCase(title)) {
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

	private static String plain(String text) {
		if (text == null) {
			return "";
		}
		String stripped = ChatFormatting.stripFormatting(text);
		return stripped == null ? "" : stripped.trim();
	}
}
