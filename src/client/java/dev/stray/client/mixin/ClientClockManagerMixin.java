package dev.stray.client.mixin;

import dev.stray.client.visual.CustomAmbience;
import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientClockManager.ClientClockInstance.class)
public class ClientClockManagerMixin {
	@Inject(method = "totalTicks", at = @At("HEAD"), cancellable = true)
	private void stray$dayTime(CallbackInfoReturnable<Long> cir) {
		if (CustomAmbience.overridesTime()) {
			cir.setReturnValue(CustomAmbience.dayTime(0L));
		}
	}
}
