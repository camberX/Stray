package dev.stray.client.mixin;

//? if >=26.3 {
import com.mojang.renderpearl.api.textures.GpuTextureView;
//?}
import dev.stray.client.render.GuiFrostBlur;
import dev.stray.client.render.MobGlowRenderer;
import dev.stray.client.render.TopDownCapture;
import dev.stray.client.visual.HeldItemShader;
import dev.stray.client.visual.motionblur.MotionBlurShaders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
//? if >=26.3 {
import net.minecraft.client.renderer.state.level.PlayerRenderState;
//?}
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
			//? if >=26.3 {
			target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V",
			//?} else {
			/*target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",*///?}
			shift = At.Shift.AFTER
		)
	)
	private void stray$compositeFillEsp(CallbackInfo ci) {
		HeldItemShader.compositeFillEsp();
		HeldItemShader.compositePlayerSilhouette();
	}

	/** Freecam: no first-person hands floating in front of the flying camera. */
	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void stray$freecamNoHand(CallbackInfo ci) {
		if (dev.stray.client.movement.Freecam.active()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderItemInHand", at = @At("HEAD"))
	//? if >=26.3 {
	private void stray$beginHeldItemMask(CameraRenderState camera, PlayerRenderState player, GpuTextureView lightmap, CallbackInfo ci) {
	//?} else {
	/*private void stray$beginHeldItemMask(CallbackInfo ci) {*///?}
		HeldItemShader.beginMask();
	}

	@Inject(method = "renderItemInHand", at = @At("RETURN"))
	//? if >=26.3 {
	private void stray$compositeHeldItemSilhouette(CameraRenderState camera, PlayerRenderState player, GpuTextureView lightmap, CallbackInfo ci) {
	//?} else {
	/*private void stray$compositeHeldItemSilhouette(CallbackInfo ci) {*///?}
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
		dev.stray.client.render.TitleBackdrop.flush();
		GuiFrostBlur.captureAfterWorld();
	}
}
