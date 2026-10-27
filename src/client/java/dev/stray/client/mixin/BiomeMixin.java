package dev.stray.client.mixin;

import dev.stray.client.visual.CustomAmbience;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Biome.class)
public class BiomeMixin {
	@Inject(method = "getPrecipitationAt", at = @At("HEAD"), cancellable = true)
	private void stray$snow(BlockPos pos, int seaLevel, CallbackInfoReturnable<Biome.Precipitation> cir) {
		if (CustomAmbience.snowy()) {
			cir.setReturnValue(Biome.Precipitation.SNOW);
		}
	}
}
