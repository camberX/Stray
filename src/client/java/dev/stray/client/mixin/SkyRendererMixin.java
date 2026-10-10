package dev.stray.client.mixin;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import dev.stray.client.visual.EndSkyDecor;
import dev.stray.client.visual.WorldTint;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DynamicGpuData;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.dimension.DimensionType;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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

	@Redirect(
		method = "renderEndSky",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/DynamicGpuData;writeTransform(Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"
		)
	)
	private GpuBufferSlice stray$tintEndSky(DynamicGpuData uniforms, Matrix4f modelView) {
		Vector4f tint = new Vector4f(WorldTint.endSkyColor(new Vector4f(1f, 1f, 1f, 1f)));
		return uniforms.writeTransform(modelView, tint);
	}

	@Inject(method = "renderEndSky", at = @At("RETURN"))
	private void stray$endDecor(CallbackInfo ci) {
		if (!WorldTint.endSkyboxActive()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return;
		}
		float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		EndSkyDecor.render(client.level.getGameTime() + partial);
	}
}
