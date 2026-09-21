package dev.stray.client.mixin.sodium;

import dev.stray.client.visual.CustomFog;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderFogComponent;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = ChunkShaderFogComponent.Smooth.class, remap = false)
public class SodiumChunkFogMixin {
	@ModifyVariable(method = "setup", at = @At("HEAD"), argsOnly = true, remap = false)
	private FogParameters stray$customFog(FogParameters incoming) {
		if (!CustomFog.applied()) {
			return incoming;
		}
		CustomFog.Sample sample = CustomFog.sample();
		return new FogParameters(
			sample.red(),
			sample.green(),
			sample.blue(),
			sample.alpha(),
			sample.start(),
			sample.end(),
			sample.start(),
			sample.end()
		);
	}
}
