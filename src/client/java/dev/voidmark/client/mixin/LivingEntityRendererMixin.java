package dev.voidmark.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.voidmark.client.item.ItemAppearance;
import dev.voidmark.client.visual.HeldItemShader;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Shadow
	public abstract Identifier getTextureLocation(LivingEntityRenderState state);

	@Redirect(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"
		)
	)
	private ItemStack voidmark$visualEquip(LivingEntity entity, EquipmentSlot slot) {
		ItemStack stack = entity.getItemBySlot(slot);
		if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
			return stack;
		}
		return ItemAppearance.visual(stack);
	}

	@Inject(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At("HEAD")
	)
	private void voidmark$beginPlayerFill(
		LivingEntityRenderState state,
		PoseStack pose,
		SubmitNodeCollector collector,
		CameraRenderState camera,
		CallbackInfo ci
	) {
		if (HeldItemShader.shouldFill(state)) {
			HeldItemShader.pushPlayerFill();
		}
	}

	@Inject(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At("RETURN")
	)
	private void voidmark$endPlayerFill(
		LivingEntityRenderState state,
		PoseStack pose,
		SubmitNodeCollector collector,
		CameraRenderState camera,
		CallbackInfo ci
	) {
		if (HeldItemShader.shouldFill(state)) {
			HeldItemShader.popPlayerFill();
		}
	}

	@Inject(method = "getRenderType", at = @At("RETURN"), cancellable = true)
	private void voidmark$playerFill(
		LivingEntityRenderState state,
		boolean visible,
		boolean translucent,
		boolean glowing,
		CallbackInfoReturnable<RenderType> cir
	) {
		if (!HeldItemShader.shouldFill(state) || (!visible && !translucent)) {
			return;
		}
		RenderType original = cir.getReturnValue();
		if (original == null) {
			return;
		}
		cir.setReturnValue(HeldItemShader.wrapFill(original, getTextureLocation(state)));
	}
}
