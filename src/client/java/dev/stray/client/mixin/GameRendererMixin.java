package dev.stray.client.mixin;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.stray.client.render.GuiFrostBlur;
import dev.stray.client.render.MobGlowRenderer;
import dev.stray.client.render.TopDownCapture;
import dev.stray.client.visual.HeldItemShader;
import dev.stray.client.visual.motionblur.MotionBlurShaders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void stray$beginFrame(CallbackInfo ci) {
		MobGlowRenderer.beginFrame();
	}

	@Inject(method = "renderLevel", at = @At("HEAD"))
	private void stray$beginFillEsp(CallbackInfo ci) {
		HeldItemShader.beginFillEsp();
	}

	@Inject(
		method = "renderLevel",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V",
			shift = At.Shift.AFTER
		)
	)
	private void stray$compositeFillEsp(CallbackInfo ci) {
		HeldItemShader.compositeFillEsp();
		HeldItemShader.compositePlayerSilhouette();
	}

	@Inject(method = "renderItemInHand", at = @At("HEAD"))
	private void stray$beginHeldItemMask(CameraRenderState camera, PlayerRenderState player, GpuTextureView lightmap, CallbackInfo ci) {
		HeldItemShader.beginMask();
	}

	@Inject(method = "renderItemInHand", at = @At("RETURN"))
	private void stray$compositeHeldItemSilhouette(CameraRenderState camera, PlayerRenderState player, GpuTextureView lightmap, CallbackInfo ci) {
		HeldItemShader.compositeSilhouette();
	}

	@Inject(method = "renderLevel", at = @At("TAIL"))
	private void stray$motionBlurAfterLevel(CallbackInfo ci) {
		MotionBlurShaders.applyDeferredTemporalBlur();
		MotionBlurShaders.clearFrameAllocator();
		TopDownCapture.render(Minecraft.getInstance().getDeltaTracker());
	}

	@Inject(
		method = "render",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"
		)
	)
	private void stray$captureControlFrost(CallbackInfo ci) {
		GuiFrostBlur.captureAfterWorld();
	}
}
