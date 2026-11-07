package dev.stray.client.mining;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.debug.StrayDebug;
import dev.stray.client.item.ItemIds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * Keep break progress. Every tick of a block break, vanilla asks whether you are still on the
 * same block with exactly the same held stack ({@code MultiPlayerGameMode.sameDestroyTarget}).
 * Hypixel re-sends the held tool with small changes (lore, counters) while you mine, so that
 * check fails, and the client sends Hypixel ABORT then START, which restarts the break on the
 * server too. While the block is the same and the tool is the same SkyBlock item (same UUID,
 * or the same ID), this counts it as unchanged, so nothing is aborted.
 *
 * <p>This covers every way the item can change (slot update, full inventory resend). Not in
 * Stray Legit. {@code /stray debug break} prints each time it keeps a break.
 */
public final class KeepBreakProgress {
	private static String lastNote = "";

	private KeepBreakProgress() {
	}

	/** Whether a break that vanilla would restart should carry on. */
	public static boolean keep(BlockPos pos, BlockPos destroying, ItemStack started, ItemStack held) {
		if (pos == null || !pos.equals(destroying)) {
			return false;
		}
		boolean same = sameItem(started, held);
		if (StrayDebug.enabled("break")) {
			note(same, started, held);
		}
		return same && StrayConfig.get().keepBreakProgress;
	}

	static boolean sameItem(ItemStack a, ItemStack b) {
		if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
			return false;
		}
		if (!ItemStack.isSameItem(a, b)) {
			return false;
		}
		String uuidA = ItemIds.uuidOf(a);
		String uuidB = ItemIds.uuidOf(b);
		if (uuidA != null && uuidB != null) {
			return uuidA.equals(uuidB);
		}
		String idA = ItemIds.skyblockId(a);
		String idB = ItemIds.skyblockId(b);
		return idA != null && Objects.equals(idA, idB);
	}

	private static void note(boolean same, ItemStack started, ItemStack held) {
		String id = ItemIds.skyblockId(held);
		String text = same
			? (StrayConfig.get().keepBreakProgress
				? "§aKept break§7: held " + (id == null ? "item" : id) + " was re-sent with changes."
				: "§eBreak would be kept§7, but Keep break progress is off.")
			: "§cBreak restarted§7: the held item is a different item ("
				+ (ItemIds.skyblockId(started) == null ? "?" : ItemIds.skyblockId(started)) + " → " + (id == null ? "?" : id) + ").";
		if (text.equals(lastNote)) {
			return;
		}
		lastNote = text;
		Minecraft client = Minecraft.getInstance();
		if (client.gui != null) {
			client.gui.hud.getChat().addClientSystemMessage(Component.literal("§bStray §7[break] ").append(Component.literal(text)));
		}
	}

	/** A new break started; the next change is worth reporting again. */
	public static void onStart() {
		lastNote = "";
	}
}
