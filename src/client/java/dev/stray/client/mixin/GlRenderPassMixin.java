package dev.stray.client.mixin;

import com.mojang.blaze3d.opengl.GlRenderPass;
import com.mojang.blaze3d.opengl.GlRenderPipeline;
import dev.stray.client.render.TopDownTerrainCut;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlRenderPass.class)
public class GlRenderPassMixin {
	@Shadow
	protected GlRenderPipeline pipeline;

	@Inject(method = {"drawIndexed", "draw", "drawMultipleIndexed"}, at = @At("HEAD"))
	private void stray$topDownCut(CallbackInfo ci) {
		TopDownTerrainCut.bind(pipeline);
	}
}
