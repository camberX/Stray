package dev.stray.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.stray.client.visual.LegacyBackwardsWalk;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
	@Shadow
	public float yBodyRot;

	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;abs(F)F"))
	private float stray$rotateBackwardsWalking(float value, Operation<Float> original) {
		if (LegacyBackwardsWalk.enabled()) {
			return 0.0F;
		}
		return original.call(value);
	}

	@WrapOperation(method = "tickHeadTurn", at = @At(value = "INVOKE", target = "Ljava/lang/Math;abs(F)F"))
	private float stray$backwardsWalkingHeadRotation(float value, Operation<Float> original) {
		if (!LegacyBackwardsWalk.enabled()) {
			return original.call(value);
		}
		float rotation = Mth.clamp(value, -75.0F, 75.0F);
		this.yBodyRot = ((LivingEntity) (Object) this).getYRot() - rotation;
		if (Math.abs(rotation) > 50.0F) {
			this.yBodyRot += rotation * 0.2F;
		}
		return Float.MIN_VALUE;
	}
}
