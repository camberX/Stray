package dev.stray.client.mixin.customize;

// Ported from SkyOcean (https://github.com/meowdding/SkyOcean), MIT, Copyright (c) 2026 meowdding.

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.stray.client.item.custom.CustomItems;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The held item name above the hotbar uses the custom name. */
@Mixin(Gui.class)
public class GuiSelectedNameMixin {
	@WrapOperation(method = {"extractSelectedItemName", "tick()V"}, require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getHoverName()Lnet/minecraft/network/chat/Component;"))
	private Component stray$heldName(ItemStack stack, Operation<Component> original) {
		Component custom = CustomItems.customName(stack);
		return custom != null ? custom : original.call(stack);
	}
}
