package dev.stray.client.mixin;

import dev.stray.client.visual.WorldTint;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public class LightmapRenderStateExtractorMixin {
	@Shadow
	private boolean needsUpdate;

	@Inject(method = "extract", at = @At("HEAD"))
	private void stray$refreshTintedLightmap(LightmapRenderState state, float partialTick, CallbackInfo ci) {
		if (WorldTint.shouldRefreshLightmap()) {
			this.needsUpdate = true;
		}
	}

	@Inject(method = "extract", at = @At("RETURN"))
	private void stray$tintLightmap(LightmapRenderState state, float partialTick, CallbackInfo ci) {
		WorldTint.tintLightmap(state);
	}
}
