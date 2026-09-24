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
 * is held. Height is how many blocks above the eyes that camera sits.
 */
public final class TopDownView {
	public static final float MIN_HEIGHT = 4f;
	public static final float MAX_HEIGHT = 40f;
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

	public static float height() {
		return StrayConfig.clamp(StrayConfig.get().topDownHeight, MIN_HEIGHT, MAX_HEIGHT);
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
