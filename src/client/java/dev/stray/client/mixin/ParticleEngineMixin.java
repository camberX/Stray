package dev.stray.client.mixin;

import dev.stray.client.slayer.SlayerFx;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {
	@Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
	private void stray$slayerParticles(ParticleOptions options, double x, double y, double z, double dx, double dy, double dz,
		CallbackInfoReturnable<Particle> cir) {
		if (SlayerFx.hideParticle(options, x, y, z)) {
			cir.setReturnValue(null);
		}
	}
}
