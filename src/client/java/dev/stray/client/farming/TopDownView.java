package dev.stray.client.farming;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.item.ItemIds;
import dev.stray.client.render.HudLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Overhead farming camera shown in a movable HUD window while a farming tool
 * is held. The camera sits {@link #CAMERA_ABOVE} blocks above the player.
 */
public final class TopDownView {
	/** Blocks above the player's feet. */
	public static final float CAMERA_ABOVE = 5f;
	public static final float WINDOW = 132f;

	private TopDownView() {
	}

	public static boolean showing() {
		if (!StrayConfig.get().topDownView) {
			return false;
		}
		if (HudLayout.editorOpen()) {
			return true;
		}
		Minecraft client = Minecraft.getInstance();
		return client.player != null && farmingTool(client.player.getMainHandItem());
	}

	public static boolean farmingTool(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		if (stack.getItem() instanceof HoeItem) {
			return true;
		}
		String id = ItemIds.skyblockId(stack);
		if (id == null || id.isBlank()) {
			return false;
		}
		String key = id.toUpperCase(Locale.ROOT);
		return key.contains("HOE")
			|| key.contains("DICER")
			|| key.contains("CHOPPER")
			|| key.contains("FUNGI_CUTTER")
			|| key.contains("CACTUS_KNIFE");
	}
}
