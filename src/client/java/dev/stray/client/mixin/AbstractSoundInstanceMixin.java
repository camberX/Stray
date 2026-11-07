package dev.stray.client.mixin;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** M4 Sounds (BirdAddon): mutes the noisy Thorn fight sounds by id. */
@Mixin(AbstractSoundInstance.class)
public class AbstractSoundInstanceMixin {
	@Inject(method = "getVolume", at = @At("RETURN"), cancellable = true)
	private void stray$m4Mute(CallbackInfoReturnable<Float> cir) {
		AbstractSoundInstance sound = (AbstractSoundInstance) (Object) this;
		if (sound.getIdentifier() != null && dev.stray.client.dungeon.m4.M4Features.muteSound(sound.getIdentifier().toString())) {
			cir.setReturnValue(0f);
		}
	}
}
