package dev.stray.client.mixin;

import dev.stray.client.movement.Freecam;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freecam: mouse look turns the camera, not the local player (whose rotation stays as it was). */
@Mixin(Entity.class)
public class FreecamEntityMixin {
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true)
	private void stray$freecamTurn(double xo, double yo, CallbackInfo ci) {
		if ((Object) this == Minecraft.getInstance().player && Freecam.turn(xo, yo)) {
			ci.cancel();
		}
	}
}
