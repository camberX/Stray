package dev.stray.client.mixin;

import dev.stray.client.visual.HeldItemShader;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FeatureRenderDispatcher.PreparedFrame.class)
public class FeatureRenderDispatcherMixin {
	@Inject(method = "executeSolid", at = @At("HEAD"))
	private void stray$playerFillMaskDepth(CallbackInfo ci) {
		HeldItemShader.capturePlayerMaskDepth();
	}

	@Inject(method = "close", at = @At("HEAD"))
	private void stray$flushOffscreen(CallbackInfo ci) {
		HeldItemShader.flushOffscreen();
	}
}
