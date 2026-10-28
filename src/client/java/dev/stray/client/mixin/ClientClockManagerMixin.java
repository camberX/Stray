package dev.stray.client.mixin;

import dev.stray.client.visual.CustomAmbience;
import net.minecraft.client.ClientClockManager;
//? if <26.3 {
/*import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

//? if >=26.3 {
@Mixin(ClientClockManager.ClientClockInstance.class)
//?} else {
/*@Mixin(ClientClockManager.class)*///?}
public class ClientClockManagerMixin {
	//? if >=26.3 {
	@Inject(method = "totalTicks", at = @At("HEAD"), cancellable = true)
	private void stray$dayTime(CallbackInfoReturnable<Long> cir) {
	//?} else {
	/*@Inject(method = "getTotalTicks", at = @At("HEAD"), cancellable = true)
	private void stray$dayTime(Holder<WorldClock> clock, CallbackInfoReturnable<Long> cir) {*///?}
		if (CustomAmbience.overridesTime()) {
			cir.setReturnValue(CustomAmbience.dayTime(0L));
		}
	}
}
