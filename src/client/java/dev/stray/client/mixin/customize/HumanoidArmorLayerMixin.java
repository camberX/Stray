package dev.stray.client.mixin.customize;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.stray.client.item.custom.CustomItems;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Worn armor: draw the armor texture of the customized model item ("Model" in /stray customize). */
@Mixin(HumanoidArmorLayer.class)
public class HumanoidArmorLayerMixin {
	@WrapOperation(
		method = "renderArmorPiece",
		require = 0,
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;")
	)
	@SuppressWarnings("unchecked")
	private <T> T stray$wornModel(ItemStack stack, DataComponentType<T> type, Operation<T> original) {
		T value = original.call(stack, type);
		if (type == DataComponents.EQUIPPABLE) {
			return (T) CustomItems.wornArmor(stack, (Equippable) value);
		}
		return value;
	}
}
