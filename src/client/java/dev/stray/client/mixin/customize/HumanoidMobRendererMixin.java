package dev.stray.client.mixin.customize;

// Ported from SkyOcean (https://github.com/meowdding/SkyOcean), MIT, Copyright (c) 2026 meowdding.

import dev.stray.client.item.custom.CustomItems;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractSkullBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Worn heads (players, armor stands): custom or animated skin. */
@Mixin(HumanoidMobRenderer.class)
public class HumanoidMobRendererMixin {
	@Inject(
		method = "extractHumanoidRenderState",
		require = 0,
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;extractArmedEntityRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;Lnet/minecraft/client/renderer/item/ItemModelResolver;F)V",
			shift = At.Shift.AFTER
		)
	)
	private static void stray$wornSkin(LivingEntity entity, HumanoidRenderState state, float partialTick, ItemModelResolver resolver, CallbackInfo ci) {
		ItemStack head = entity.getItemBySlot(EquipmentSlot.HEAD);
		if (!(head.getItem() instanceof BlockItem block) || !(block.getBlock() instanceof AbstractSkullBlock)) {
			return;
		}
		var profile = CustomItems.replacement(head, DataComponents.PROFILE);
		if (profile != null) {
			state.wornHeadProfile = profile;
		}
	}
}
