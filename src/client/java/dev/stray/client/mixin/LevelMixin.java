package dev.stray.client.mixin;

import dev.stray.client.visual.CustomAmbience;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class LevelMixin {
	@Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
	private void stray$rain(float delta, CallbackInfoReturnable<Float> cir) {
		if (CustomAmbience.overridesWeather()) {
			cir.setReturnValue(CustomAmbience.rainLevel(0f));
		}
	}

	@Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
	private void stray$thunder(float delta, CallbackInfoReturnable<Float> cir) {
		if (CustomAmbience.overridesWeather()) {
			cir.setReturnValue(CustomAmbience.thunderLevel(0f));
		}
	}
}
