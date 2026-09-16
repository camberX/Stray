package dev.stray.client.mixin;

import dev.stray.client.movement.MovementRings;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class ClientLevelMixin {
	@Inject(method = "setTimeFromServer", at = @At("TAIL"))
	private void stray$serverTps(long gameTime, CallbackInfo ci) {
		MovementRings.onServerTime(gameTime);
	}
}
