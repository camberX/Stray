package dev.stray.client.combat;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.location.SkyblockLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * After Mort hands out the dungeon map, swap to a hotbar item whose name
 * contains "rogue", right-click it, and swap back. The whole sequence runs
 * on tick start, before movement, and repeats 30 seconds after each click
 * until the world changes.
 */
public final class AutoRogue {
	private static final long GAP_MS = 30_000L;
	private static final String START = "here, i found this map when i first entered the dungeon.";

	private static boolean dungeonStarted;
	private static long nextAt;

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
	}

	public static void tick(Minecraft client) {
		if (!StrayConfig.get().autoRogueEnabled || !dungeonStarted || !SkyblockLocation.inDungeon) {
			return;
		}
		if (client.player == null || client.gameMode == null || client.screen != null || client.level == null) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now < nextAt) {
			return;
		}
		LocalPlayer player = client.player;
		Inventory inventory = player.getInventory();
		int previous = inventory.getSelectedSlot();
		int rogue = findRogue(inventory);
		if (rogue < 0) {
			return;
		}
		try {
			select(player, rogue);
			client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
		} finally {
			select(player, previous);
		}
		nextAt = now + GAP_MS;
	}

	private static int findRogue(Inventory inventory) {
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			String name = stack.getHoverName().getString();
			if (name != null && name.toLowerCase().contains("rogue")) {
				return slot;
			}
		}
		return -1;
	}

	private static void select(LocalPlayer player, int slot) {
		if (slot < 0 || slot > 8) {
			return;
		}
		Inventory inventory = player.getInventory();
		if (inventory.getSelectedSlot() == slot) {
			return;
		}
		inventory.setSelectedSlot(slot);
		player.connection.send(new ServerboundSetCarriedItemPacket(slot));
	}
}
