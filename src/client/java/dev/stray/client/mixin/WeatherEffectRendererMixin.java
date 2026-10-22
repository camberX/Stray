package dev.stray.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.stray.client.visual.CustomAmbience;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WeatherEffectRenderer.class)
public class WeatherEffectRendererMixin {
	@Inject(method = "getPrecipitationAt", at = @At("HEAD"), cancellable = true)
	private void stray$snow(Level level, BlockPos pos, CallbackInfoReturnable<Biome.Precipitation> cir) {
		if (CustomAmbience.snowy()) {
			cir.setReturnValue(Biome.Precipitation.SNOW);
		}
	}

	@WrapOperation(
		method = "render",
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/client/renderer/state/level/WeatherRenderState;intensity:F",
			opcode = Opcodes.GETFIELD
		)
	)
	private float stray$gradient(WeatherRenderState state, Operation<Float> original) {
		return CustomAmbience.precipitationGradient(original.call(state));
	}
}
