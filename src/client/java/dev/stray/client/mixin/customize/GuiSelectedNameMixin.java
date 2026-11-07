package dev.stray.client.mixin.customize;

// Ported from SkyOcean (https://github.com/meowdding/SkyOcean), MIT, Copyright (c) 2026 meowdding.

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.stray.client.item.custom.CustomItems;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The held item name above the hotbar uses the custom name. */
@Mixin(Hud.class)
public class GuiSelectedNameMixin {
	@WrapOperation(method = "extractSelectedItemName", require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getHoverName()Lnet/minecraft/network/chat/Component;"))
	private Component stray$heldName(ItemStack stack, Operation<Component> original) {
		// getHoverName already carries the custom name and the recoloured dye mark.
		return original.call(stack);
	}

	/**
	 * The "did the held item change" check compares names; without the dye mark, so an animated
	 * dye cycling its mark colour does not keep re-showing the item name.
	 */
	@WrapOperation(method = "tick", require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getHoverName()Lnet/minecraft/network/chat/Component;"))
	private Component stray$heldNameTick(ItemStack stack, Operation<Component> original) {
		Component custom = CustomItems.customName(stack);
		return custom != null ? custom : original.call(stack);
	}
}
