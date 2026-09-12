package dev.stray.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.stray.client.visual.CustomAmbience;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WeatherEffectRenderer.class)
public class WeatherEffectRendererMixin {
	@WrapOperation(
		method = "extractRenderState",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F")
	)
	private float stray$extractRain(ClientLevel level, float delta, Operation<Float> original) {
		return CustomAmbience.extractRainLevel(original.call(level, delta));
	}

	@WrapOperation(
		method = "extractRenderState",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;")
	)
	private Biome.Precipitation stray$snow(
		ClientLevel level,
		BlockPos pos,
		Operation<Biome.Precipitation> original
	) {
		return CustomAmbience.precipitation(original.call(level, pos));
	}

	@WrapOperation(
		method = "render",
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/client/renderer/state/level/WeatherRenderState;intensity:F",
			opcode = org.objectweb.asm.Opcodes.GETFIELD
		)
	)
	private float stray$gradient(net.minecraft.client.renderer.state.level.WeatherRenderState state, Operation<Float> original) {
		return CustomAmbience.precipitationGradient(original.call(state));
	}
}
