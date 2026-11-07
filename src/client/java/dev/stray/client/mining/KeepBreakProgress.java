package dev.stray.client.mining;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.item.ItemIds;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * Keep break progress: vanilla restarts a block break whenever the held item stops being
 * exactly the same stack ({@code MultiPlayerGameMode.sameDestroyTarget} compares components).
 * Hypixel re-sends the held tool with small lore changes while you mine, so progress kept
 * resetting. While a block is being broken, an update to the held slot that is the same
 * SkyBlock item (same UUID, or same ID) is held back and applied as soon as the break ends.
 *
 * <p>Only the client's copy of the item waits; nothing extra is sent. Not in Stray Legit.
 */
public final class KeepBreakProgress {
	/** The held-back update, replayed through the normal packet handler once breaking stops. */
	private static Runnable pending;
	private static int pendingSlot = -1;
	private static boolean replaying;

	private KeepBreakProgress() {
	}

	/**
	 * True to hold back an update of player inventory slot {@code inventoryIndex} (0-8 hotbar)
	 * to {@code incoming}; {@code replay} applies it later. Called on the render thread.
	 */
	public static boolean hold(int inventoryIndex, ItemStack incoming, Runnable replay) {
		Minecraft client = Minecraft.getInstance();
		if (replaying || client.player == null || client.gameMode == null) {
			return false;
		}
		if (pendingSlot == inventoryIndex && !client.gameMode.isDestroying()) {
			// A newer update for that slot replaces the one we were holding.
			clear();
		}
		if (!StrayConfig.get().keepBreakProgress || !client.gameMode.isDestroying()) {
			return false;
		}
		if (inventoryIndex != client.player.getInventory().getSelectedSlot()) {
			return false;
		}
		if (!sameItem(client.player.getInventory().getSelectedItem(), incoming)) {
			return false;
		}
		pending = replay;
		pendingSlot = inventoryIndex;
		return true;
	}

	public static void tick(Minecraft client) {
		if (pending == null) {
			return;
		}
		if (client.player == null || client.gameMode == null) {
			clear();
			return;
		}
		if (client.gameMode.isDestroying()) {
			return;
		}
		Runnable replay = pending;
		clear();
		replaying = true;
		try {
			replay.run();
		} finally {
			replaying = false;
		}
	}

	/** A full inventory resend makes a held-back slot update stale. */
	public static void clear() {
		pending = null;
		pendingSlot = -1;
	}

	private static boolean sameItem(ItemStack held, ItemStack incoming) {
		if (held == null || incoming == null || held.isEmpty() || incoming.isEmpty()) {
			return false;
		}
		if (!ItemStack.isSameItem(held, incoming)) {
			return false;
		}
		String heldUuid = ItemIds.uuidOf(held);
		String incomingUuid = ItemIds.uuidOf(incoming);
		if (heldUuid != null && incomingUuid != null) {
			return heldUuid.equals(incomingUuid);
		}
		String heldId = ItemIds.skyblockId(held);
		String incomingId = ItemIds.skyblockId(incoming);
		return heldId != null && Objects.equals(heldId, incomingId);
	}
}
