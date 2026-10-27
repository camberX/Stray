package dev.stray.client.mixin;

import com.mojang.renderpearl.backend.opengl.GlRenderPipeline;
import dev.stray.client.mining.FocusMode;
import dev.stray.client.render.TopDownTerrainCut;
import dev.stray.client.visual.CustomFog;
import dev.stray.client.visual.WorldTint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlRenderPass")
public class GlRenderPassMixin {
	@Shadow
	protected GlRenderPipeline pipeline;

	@Inject(method = {"drawIndexed", "draw", "multiDrawIndexed"}, at = @At("HEAD"))
	private void stray$terrainUniforms(CallbackInfo ci) {
		TopDownTerrainCut.bind(pipeline);
		FocusMode.bind(pipeline);
		WorldTint.bind(pipeline);
		CustomFog.bind(pipeline);
	}
}
