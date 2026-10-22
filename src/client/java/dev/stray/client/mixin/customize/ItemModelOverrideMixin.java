package dev.stray.client.mixin.customize;

// Ported from SkyOcean (https://github.com/meowdding/SkyOcean), MIT, Copyright (c) 2026 meowdding.

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.stray.client.item.custom.CustomItems;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Draw the item with another item's model (customization "Model"). */
@Mixin(ItemModelResolver.class)
public class ItemModelOverrideMixin {
	@WrapOperation(method = {"appendItemLayers", "shouldPlaySwapAnimation"}, require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
	private <T> T stray$model(ItemStack stack, DataComponentType<T> type, Operation<T> original) {
		T custom = CustomItems.replacement(stack, type);
		return custom != null ? custom : original.call(stack, type);
	}
}
