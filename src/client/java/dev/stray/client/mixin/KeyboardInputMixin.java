package dev.stray.client.mixin;

import dev.stray.client.movement.PathWalker;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {
	@Inject(method = "tick", at = @At("RETURN"))
	private void stray$replayMove(CallbackInfo ci) {
		if (dev.stray.client.movement.Freecam.active()) {
			dev.stray.client.movement.Freecam.applyInput(this);
			return;
		}
		if (!dev.stray.Edition.LEGIT) {
			if (PathWalker.walking()) {
				PathWalker.applyInput(this);
			}
		}
	}
}
