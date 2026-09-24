package dev.stray.client.farming;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.item.ItemIds;
import dev.stray.client.movement.MovementRings;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * Top-down camera while a farming tool is held. The near plane starts just
 * above the player's eyes, so blocks between the camera and the player are cut.
 */
public final class TopDownView {
	public static final float MIN_HEIGHT = 4f;
	public static final float MAX_HEIGHT = 40f;

	private TopDownView() {
	}

	public static boolean active() {
		if (!StrayConfig.get().topDownView || MovementRings.playing()) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.screen != null) {
			return false;
		}
		return farmingTool(client.player.getMainHandItem());
	}

	public static float height() {
		return StrayConfig.clamp(StrayConfig.get().topDownHeight, MIN_HEIGHT, MAX_HEIGHT);
	}

	public static void place(Entity entity, float partial, CameraPose pose) {
		Vec3 eye = entity.getEyePosition(partial);
		float lift = height();
		pose.x = eye.x;
		pose.y = eye.y + lift;
		pose.z = eye.z;
		pose.yaw = entity.getViewYRot(partial);
		pose.pitch = 90f;
	}

	/**
	 * @param zNear vanilla near distance
	 * @return a near plane that drops everything above the player's eyes
	 */
	public static float nearPlane(float zNear) {
		if (!active()) {
			return zNear;
		}
		return Math.max(zNear, height() - 0.2f);
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

	public static boolean holding(Player player) {
		return player != null && farmingTool(player.getMainHandItem());
	}

	public static final class CameraPose {
		public double x;
		public double y;
		public double z;
		public float yaw;
		public float pitch;
	}
}
