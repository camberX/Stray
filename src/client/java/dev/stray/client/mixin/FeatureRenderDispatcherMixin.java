package dev.stray.client.mixin;

import dev.stray.client.visual.HeldItemShader;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FeatureRenderDispatcher.class)
public class FeatureRenderDispatcherMixin {
	@Inject(method = "renderSolidFeatures", at = @At("HEAD"))
	private void stray$playerFillMaskDepth(CallbackInfo ci) {
		HeldItemShader.capturePlayerMaskDepth();
	}
}
