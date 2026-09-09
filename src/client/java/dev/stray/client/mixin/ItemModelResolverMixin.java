package dev.stray.client.mixin;

import dev.stray.client.item.ItemAppearance;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemModelResolver.class)
public class ItemModelResolverMixin {
	@ModifyVariable(method = "updateForTopItem", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private ItemStack stray$visualTop(ItemStack stack) {
		return ItemAppearance.visual(stack);
	}

	@ModifyVariable(method = "shouldPlaySwapAnimation", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private ItemStack stray$visualSwap(ItemStack stack) {
		return ItemAppearance.visual(stack);
	}

	@ModifyVariable(method = "swapAnimationScale", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private ItemStack stray$visualSwapScale(ItemStack stack) {
		return ItemAppearance.visual(stack);
	}
}
