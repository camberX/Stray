package dev.voidmark.client.mixin;

import dev.voidmark.client.render.GuiFrostBlur;
import dev.voidmark.client.visual.HeldItemShader;
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
	@Inject(method = "renderItemInHand", at = @At("HEAD"))
	private void voidmark$beginHeldItemMask(CameraRenderState camera, float partialTick, Matrix4fc pose, CallbackInfo ci) {
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
	private void voidmark$compositeHeldItemSilhouette(DeltaTracker deltaTracker, CallbackInfo ci) {
		HeldItemShader.compositeSilhouette();
	}

	@Inject(
		method = "render",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"
		)
	)
	private void voidmark$captureControlFrost(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
		GuiFrostBlur.captureAfterWorld();
	}
}
