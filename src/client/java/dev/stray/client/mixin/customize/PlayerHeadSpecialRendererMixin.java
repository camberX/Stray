package dev.stray.client.mixin.customize;

// Ported from SkyOcean (https://github.com/meowdding/SkyOcean), MIT, Copyright (c) 2026 meowdding.

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.stray.client.item.custom.CustomItems;
import net.minecraft.client.renderer.special.PlayerHeadSpecialRenderer;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Head items (inventory, hand): custom or animated skin. */
@Mixin(PlayerHeadSpecialRenderer.class)
public class PlayerHeadSpecialRendererMixin {
	@WrapOperation(method = "extractArgument(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/client/renderer/PlayerSkinRenderCache$RenderInfo;", require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
	private <T> T stray$skin(ItemStack stack, DataComponentType<T> type, Operation<T> original) {
		T custom = CustomItems.replacement(stack, type);
		return custom != null ? custom : original.call(stack, type);
	}
}
