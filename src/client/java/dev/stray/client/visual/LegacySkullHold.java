package dev.stray.client.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.stray.client.config.StrayConfig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SkullBlock;

/**
 * 1.8 skull hold from Animatium {@code skullPosition}: rotate and scale the
 * modern 3D head so it sits in first person like a 1.8 item.
 */
public final class LegacySkullHold {
	private LegacySkullHold() {
	}

	public static boolean enabled() {
		return StrayConfig.get().legacySkullHold;
	}

	public static boolean skull(ItemStack stack) {
		return stack != null && !stack.isEmpty() && Block.byItem(stack.getItem()) instanceof SkullBlock;
	}

	public static void applyFirstPerson(PoseStack pose, ItemStack stack) {
		if (!enabled() || pose == null || !skull(stack)) {
			return;
		}
		pose.mulPose(Axis.YP.rotationDegrees(45.0F));
		pose.scale(0.4F, 0.4F, 0.4F);
		pose.mulPose(Axis.YP.rotationDegrees(-180.0F));
		pose.translate(0.0F, 0.25F, 0.0F);
		pose.scale(1.125F, 1.125F, 1.125F);
	}
}
