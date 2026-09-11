package dev.stray.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.stray.client.visual.WorldTint;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.level.dimension.DimensionType;
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

	@Inject(method = "renderEndSky", at = @At("RETURN"))
	private void stray$endStars(CallbackInfo ci) {
		if (!WorldTint.endSkyboxActive()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return;
		}
		float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		float time = client.level.getGameTime() + partial;
		float twinkle = 0.66f + 0.28f * (0.5f + 0.5f * Mth.sin(time * 0.038f));
		PoseStack pose = new PoseStack();
		pose.mulPose(Axis.YP.rotation(time * 0.0017f));
		pose.mulPose(Axis.XP.rotation(0.52f + time * 0.00035f));
		SkyRendererInvoker stars = (SkyRendererInvoker) this;
		stars.stray$renderStars(twinkle, pose);
		pose.mulPose(Axis.ZP.rotation(1.15f));
		pose.mulPose(Axis.YP.rotation(-0.82f));
		stars.stray$renderStars(twinkle * 0.42f, pose);
	}
}
