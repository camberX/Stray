package dev.stray.client.mixin.customize;

// Ported from SkyOcean (https://github.com/meowdding/SkyOcean), MIT, Copyright (c) 2026 meowdding.

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.stray.client.item.custom.CustomItems;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.SpecialModelWrapper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Glint forced on or off per item. */
@Mixin(value = {CuboidItemModelWrapper.class, SpecialModelWrapper.class})
public class ItemEnchantmentGlintMixin {
	@WrapOperation(method = "update", require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hasFoil()Z"))
	private boolean stray$glint(ItemStack stack, Operation<Boolean> original) {
		Boolean custom = CustomItems.replacement(stack, DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
		return custom != null ? custom : original.call(stack);
	}
}
