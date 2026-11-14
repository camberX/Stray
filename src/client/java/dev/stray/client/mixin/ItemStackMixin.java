package dev.stray.client.mixin;

import dev.stray.client.menu.SackRecipe;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ItemStack.class)
public class ItemStackMixin {
	@Inject(method = "copy", at = @At("RETURN"), require = 0)
	private void stray$copyCustomKey(CallbackInfoReturnable<ItemStack> cir) {
		dev.stray.client.item.custom.CustomItems.copied((ItemStack) (Object) this, cir.getReturnValue());
	}

	/** Item customization: the custom name wins over the item's own (SkyOcean port). */
	@Inject(method = {"getStyledHoverName", "getHoverName"}, at = @At("RETURN"), cancellable = true, require = 0)
	private void stray$customizedName(CallbackInfoReturnable<Component> cir) {
		Component custom = dev.stray.client.item.custom.CustomItems.displayName((ItemStack) (Object) this, cir.getReturnValue());
		if (custom != null) {
			cir.setReturnValue(custom);
		}
	}

	@Inject(method = "getTooltipLines", at = @At("RETURN"), cancellable = true)
	private void stray$sackRecipeLore(
		Item.TooltipContext context,
		Player player,
		TooltipFlag flag,
		CallbackInfoReturnable<List<Component>> cir
	) {
		List<Component> extra = SackRecipe.withLore((ItemStack) (Object) this, cir.getReturnValue());
		if (extra != null) {
			cir.setReturnValue(extra);
		}
		List<Component> dyed = dev.stray.client.item.custom.CustomItems.withDyeLore((ItemStack) (Object) this, cir.getReturnValue());
		if (dyed != null) {
			cir.setReturnValue(dyed);
		}
	}
}
