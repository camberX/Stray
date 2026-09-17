package dev.stray.client.mixin;

import dev.stray.client.movement.MovementRings;
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
		if (MovementRings.playing()) {
			MovementRings.applyInput(this);
		} else if (PathWalker.walking()) {
			PathWalker.applyInput(this);
		}
	}
}
