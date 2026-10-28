package dev.stray.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.stray.client.visual.LegacySkullHold;
//? if >=26.3 {
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
//?} else {
/*import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;*///?}
import net.minecraft.client.renderer.SubmitNodeCollector;
//? if >=26.3 {
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
//?}
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=26.3 {
@Mixin(FirstPersonHandsAndItemsRenderer.class)
//?} else {
/*@Mixin(ItemInHandRenderer.class)*///?}
public class ItemInHandRendererMixin {
	@Inject(
		method = "submitArmWithItem",
		at = @At(
			value = "INVOKE",
			//? if >=26.3 {
			target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"
			//?} else {
			/*target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"*///?}
		)
	)
	private void stray$skullHold(
		//? if >=26.3 {
		PlayerRenderState player,
		FirstPersonHandsAndItemsRenderState hands,
		float partialTick,
		//?} else {
		/*AbstractClientPlayer player,
		float tickDelta,*///?}
		float pitch,
		InteractionHand hand,
		float swingProgress,
		//? if >=26.3 {
		ItemStack stack,
		//?} else {
		/*ItemStack itemStack,*///?}
		float equippedProgress,
		PoseStack poseStack,
		//? if >=26.3 {
		SubmitNodeCollector collector,
		int light,
		//?} else {
		/*SubmitNodeCollector submitNodeCollector,
		int lightCoords,*///?}
		CallbackInfo ci
	) {
		//? if >=26.3 {
		LegacySkullHold.applyFirstPerson(poseStack, stack);
		//?} else {
		/*LegacySkullHold.applyFirstPerson(poseStack, itemStack);*///?}
	}
}
