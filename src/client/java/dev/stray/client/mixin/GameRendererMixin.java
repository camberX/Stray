package dev.stray.client.mixin;

import dev.stray.client.render.GuiFrostBlur;
import dev.stray.client.render.MobGlowRenderer;
import dev.stray.client.visual.HeldItemShader;
import dev.stray.client.visual.motionblur.MotionBlurShaders;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void stray$beginFrame(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
		MobGlowRenderer.beginFrame();
	}

	@Inject(method = "renderLevel", at = @At("HEAD"))
	private void stray$beginFillEsp(DeltaTracker deltaTracker, CallbackInfo ci) {
		HeldItemShader.beginFillEsp();
	}

	@Inject(
		method = "renderLevel",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZLnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;)V",
			shift = At.Shift.AFTER
		)
	)
	private void stray$compositeFillEsp(DeltaTracker deltaTracker, CallbackInfo ci) {
		HeldItemShader.compositeFillEsp();
		HeldItemShader.compositePlayerSilhouette();
	}

	@Inject(method = "renderItemInHand", at = @At("HEAD"))
	private void stray$beginHeldItemMask(CameraRenderState camera, float partialTick, Matrix4fc pose, CallbackInfo ci) {
		HeldItemShader.beginMask();
	}

	@Inject(
		method = "renderLevel",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endBatch()V",
			shift = At.Shift.AFTER
		)
	)
	private void stray$compositeHeldItemSilhouette(DeltaTracker deltaTracker, CallbackInfo ci) {
		HeldItemShader.compositeSilhouette();
	}

	@Inject(method = "renderLevel", at = @At("TAIL"))
	private void stray$motionBlurAfterLevel(DeltaTracker deltaTracker, CallbackInfo ci) {
		MotionBlurShaders.applyDeferredTemporalBlur();
		MotionBlurShaders.clearFrameAllocator();
	}

	@Inject(
		method = "render",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"
		)
	)
	private void stray$captureControlFrost(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
		GuiFrostBlur.captureAfterWorld();
	}
}
