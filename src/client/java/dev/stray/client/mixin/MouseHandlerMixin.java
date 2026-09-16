package dev.stray.client.mixin;

import dev.stray.client.movement.MovementRings;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	@Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
	private void stray$lockLook(double movementTime, CallbackInfo ci) {
		if (MovementRings.playing()) {
			ci.cancel();
		}
	}
}
