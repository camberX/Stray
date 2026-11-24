package dev.stray.client.debug;

import dev.stray.client.net.SlotGuard;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * {@code /stray debug dev menuswap}: the leap's swap-and-use on the SkyBlock Menu in the 9th
 * hotbar slot, for testing it against an anticheat on any server. At the next tick start
 * (before keys and movement): select slot 9 and use it in the same tick, the way the leap
 * does. Then it waits for the menu to open and close again (or 3 s without a menu), and the
 * next tick start after that swaps back. One slot change per tick ({@link SlotGuard}). Full
 * edition only.
 */
public final class MenuSwapTest {
	private static final int MENU_SLOT = 8;

	private static final int OPEN_TIMEOUT = 60;

	private enum Step { IDLE, SWAP_USE, AWAIT_OPEN, AWAIT_CLOSE, SWAP_BACK }

	private static Step step = Step.IDLE;
	private static int previousSlot = -1;
	private static int waitTicks;

	private MenuSwapTest() {
	}

	/** From the command. Returns why it can't run, or null. */
	public static String start() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return "not in a world";
		}
		if (step != Step.IDLE) {
			return "already running";
		}
		if (player.getInventory().getItem(MENU_SLOT).isEmpty()) {
			return "slot 9 is empty";
		}
		if (player.getInventory().getSelectedSlot() == MENU_SLOT) {
			return "you're already on slot 9: select another slot first";
		}
		step = Step.SWAP_USE;
		return null;
	}

	/**
	 * Every tick start, also with a screen open (vanilla skips key handling then): only watches
	 * for the menu opening and closing, does nothing.
	 */
	public static void onTickStart(Minecraft client) {
		boolean open = client.gui.screen() != null;
		if (step == Step.AWAIT_OPEN) {
			if (open) {
				step = Step.AWAIT_CLOSE;
			} else if (++waitTicks > OPEN_TIMEOUT) {
				message("§eNo menu opened in 3 s: swapping back.");
				step = Step.SWAP_BACK;
			}
		} else if (step == Step.AWAIT_CLOSE && !open) {
			step = Step.SWAP_BACK;
		}
	}

	/** Tick start, before keys are handled (no screen open). */
	public static void beforeKeybinds(Minecraft client) {
		LocalPlayer player = client.player;
		if (step == Step.IDLE || player == null || client.gameMode == null || client.gui.screen() != null) {
			return;
		}
		if (!SlotGuard.canSwap()) {
			// A slot change already went out this tick: next tick.
			return;
		}
		switch (step) {
			case SWAP_USE -> {
				ItemStack stack = player.getInventory().getItem(MENU_SLOT);
				if (stack.isEmpty()) {
					message("§cslot 9 is empty");
					step = Step.IDLE;
					return;
				}
				// Your own number keys this tick would be a second slot change: dropped.
				for (KeyMapping key : client.options.keyHotbarSlots) {
					while (key.consumeClick()) {
						// drained
					}
				}
				previousSlot = player.getInventory().getSelectedSlot();
				// As a number key and a right click in one tick: useItem sends the slot first.
				player.getInventory().setSelectedSlot(MENU_SLOT);
				client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
				message("§aSwapped to slot 9 and used §f" + stack.getHoverName().getString() + " §ain one tick. Swapping back after you close the menu.");
				step = Step.AWAIT_OPEN;
				waitTicks = 0;
			}
			case SWAP_BACK -> {
				if (previousSlot >= 0 && previousSlot < 9) {
					player.getInventory().setSelectedSlot(previousSlot);
				}
				message("§aSwapped back to slot " + (previousSlot + 1) + ".");
				previousSlot = -1;
				step = Step.IDLE;
			}
			default -> {
			}
		}
	}

	/** World change: stop. */
	public static void reset() {
		step = Step.IDLE;
		waitTicks = 0;
		previousSlot = -1;
	}

	private static void message(String text) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			player.sendSystemMessage(Component.literal("§bStray §7» " + text));
		}
	}
}
