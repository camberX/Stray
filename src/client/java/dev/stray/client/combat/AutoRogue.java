package dev.stray.client.combat;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.debug.StrayDebug;
import dev.stray.client.location.SkyblockLocation;
import dev.stray.client.mixin.MultiPlayerGameModeInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * After Mort hands out the dungeon map, swap to a hotbar item whose name
 * contains "rogue", right-click it, and swap back. Each of those happens on
 * its own tick start, before movement, and the click repeats 30 seconds
 * later until the world changes.
 */
public final class AutoRogue {
	private static final long GAP_MS = 30_000L;
	private static final String START = "here, i found this map when i first entered the dungeon.";

	private enum Step {
		NONE,
		CLICK,
		RESTORE
	}

	private static boolean dungeonStarted;
	private static long nextAt;
	private static Step step = Step.NONE;
	private static int returnSlot = -1;
	private static int targetSlot = -1;
	private static boolean clicked;

	private AutoRogue() {
	}

	public static void onChat(Component message) {
		if (message == null || dungeonStarted) {
			return;
		}
		String text = message.getString();
		if (text != null && text.toLowerCase().contains(START)) {
			dungeonStarted = true;
			nextAt = 0L;
		}
	}

	public static void onWorldChange() {
		dungeonStarted = false;
		nextAt = 0L;
		step = Step.NONE;
		returnSlot = -1;
		targetSlot = -1;
		clicked = false;
	}

	public static void tick(Minecraft client) {
		if (client.player == null || client.gameMode == null || client.level == null) {
			return;
		}
		if (!StrayConfig.get().autoRogueEnabled) {
			if (step != Step.NONE) {
				restore(client.player);
			}
			return;
		}
		boolean debug = StrayDebug.enabled("rogue");
		if (!debug && (!dungeonStarted || !SkyblockLocation.inDungeon)) {
			if (step != Step.NONE) {
				restore(client.player);
			}
			return;
		}
		if (step == Step.CLICK) {
			click(client);
			return;
		}
		if (step == Step.RESTORE) {
			restore(client.player);
			return;
		}
		if (client.screen != null || System.currentTimeMillis() < nextAt) {
			return;
		}
		Inventory inventory = client.player.getInventory();
		int slot = find(inventory);
		if (slot < 0) {
			return;
		}
		int current = inventory.getSelectedSlot();
		if (current == slot) {
			client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
			nextAt = System.currentTimeMillis() + GAP_MS;
			return;
		}
		returnSlot = current;
		targetSlot = slot;
		select(client, slot);
		step = Step.CLICK;
	}

	private static void click(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || client.screen != null || player.getInventory().getSelectedSlot() != targetSlot) {
			step = Step.RESTORE;
			clicked = false;
			return;
		}
		client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
		clicked = true;
		step = Step.RESTORE;
	}

	private static void restore(LocalPlayer player) {
		if (returnSlot >= 0) {
			select(Minecraft.getInstance(), returnSlot);
		}
		if (clicked) {
			nextAt = System.currentTimeMillis() + GAP_MS;
		}
		clicked = false;
		step = Step.NONE;
		returnSlot = -1;
		targetSlot = -1;
	}

	private static int find(Inventory inventory) {
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			if (matches(stack)) {
				return slot;
			}
		}
		return -1;
	}

	private static boolean matches(ItemStack stack) {
		if (StrayDebug.enabled("rogue")) {
			return stack.is(Items.GOLDEN_SWORD);
		}
		String name = stack.getHoverName().getString();
		return name != null && name.toLowerCase().contains("rogue");
	}

	/** One held-slot packet, the same one vanilla sends when the slot changes. */
	private static void select(Minecraft client, int slot) {
		if (client.player == null || client.gameMode == null || slot < 0 || slot > 8) {
			return;
		}
		client.player.getInventory().setSelectedSlot(slot);
		((MultiPlayerGameModeInvoker) client.gameMode).stray$ensureHasSentCarriedItem();
	}
}
