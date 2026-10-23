package dev.stray.client.mixin.cs2;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.stray.client.cs2.Cs2Knives;
import dev.stray.client.cs2.Cs2Viewmodel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** CS2 knives: replaces the first-person main hand (arm + item) with the CS2 viewmodel. */
@Mixin(ItemInHandRenderer.class)
public class Cs2FirstPersonMixin {
	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true, require = 0)
	private void stray$cs2Viewmodel(
		AbstractClientPlayer player,
		float tickDelta,
		float pitch,
		InteractionHand hand,
		float swingProgress,
		ItemStack itemStack,
		float equippedProgress,
		PoseStack poseStack,
		SubmitNodeCollector collector,
		int light,
		CallbackInfo ci
	) {
		if (hand != InteractionHand.MAIN_HAND || !Cs2Knives.applies(itemStack) || player.isScoping()) {
			return;
		}
		// Replay the draw only when the slot or item type changes, not when the server resends the stack.
		Object equipKey = player.getInventory().getSelectedSlot() + ":" + itemStack.getItem();
		if (Cs2Viewmodel.render(player, player.getMainArm(), swingProgress, equipKey, poseStack, collector, light)) {
			ci.cancel();
		}
	}
}
