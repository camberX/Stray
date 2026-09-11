package dev.stray.client.mixin;

import dev.stray.client.visual.WorldTint;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.dimension.DimensionType;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public class SkyRendererMixin {
	@Inject(method = "extractRenderState", at = @At("RETURN"))
	private void stray$tintSkyState(ClientLevel level, float partialTick, Camera camera, SkyRenderState state, CallbackInfo ci) {
		if (WorldTint.endSkyboxActive()) {
			state.skybox = DimensionType.Skybox.END;
		}
		state.skyColor = WorldTint.tintSky(state.skyColor);
		state.sunriseAndSunsetColor = WorldTint.tintSky(state.sunriseAndSunsetColor);
	}

	@ModifyArg(
		method = "renderEndSky",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/DynamicUniforms;writeTransform(Lorg/joml/Matrix4fc;Lorg/joml/Vector4fc;Lorg/joml/Vector3fc;Lorg/joml/Matrix4fc;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"
		),
		index = 1
	)
	private Vector4fc stray$tintEndSky(Vector4fc color) {
		return WorldTint.endSkyColor(color);
	}
}
