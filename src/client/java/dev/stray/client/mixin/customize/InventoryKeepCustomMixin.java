package dev.stray.client.mixin.customize;

import dev.stray.client.item.custom.CustomItems;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hypixel animates dyes by re-sending the item every few ticks, and some of those copies come
 * without the item's UUID. The new stack in the same inventory slot keeps the old one's
 * customization key, so the custom colour does not flicker back to Hypixel's.
 */
@Mixin(Inventory.class)
public class InventoryKeepCustomMixin {
	@Inject(method = "setItem", at = @At("HEAD"), require = 0)
	private void stray$keepCustomized(int slot, ItemStack stack, CallbackInfo ci) {
		Inventory self = (Inventory) (Object) this;
		if (self.player == Minecraft.getInstance().player) {
			CustomItems.inherit(self.getItem(slot), stack);
		}
	}
}
