package dev.stray.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.stray.client.visual.CustomAmbience;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WeatherEffectRenderer.class)
public class WeatherEffectRendererMixin {
	@WrapOperation(
		method = "prepare",
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
