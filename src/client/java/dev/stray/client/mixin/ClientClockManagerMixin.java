package dev.stray.client.mixin;

import dev.stray.client.visual.CustomAmbience;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientClockManager.class)
public class ClientClockManagerMixin {
	@Inject(method = "getTotalTicks", at = @At("HEAD"), cancellable = true)
	private void stray$dayTime(Holder<WorldClock> clock, CallbackInfoReturnable<Long> cir) {
		if (CustomAmbience.overridesTime()) {
			cir.setReturnValue(CustomAmbience.dayTime(0L));
		}
	}
}
