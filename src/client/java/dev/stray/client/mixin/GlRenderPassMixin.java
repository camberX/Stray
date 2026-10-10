package dev.stray.client.mixin;

import com.mojang.renderpearl.backend.opengl.GlRenderPipeline;
import com.mojang.renderpearl.backend.opengl.Uniform;
import dev.stray.client.mining.FocusMode;
import dev.stray.client.render.StrayUniforms;
import dev.stray.client.render.TopDownTerrainCut;
import dev.stray.client.visual.CustomFog;
import dev.stray.client.visual.WorldTint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlRenderPass")
public abstract class GlRenderPassMixin {
	@Shadow
	protected GlRenderPipeline pipeline;

	@Shadow
	public abstract void setUniform(int index, Object value);

	@Inject(method = {
		"drawIndexed",
		"draw",
		"multiDrawIndexed",
		"multiDraw",
		"drawIndexedIndirect",
		"drawIndirect"
	}, at = @At("HEAD"))
	private void stray$terrainUniforms(CallbackInfo ci) {
		if (pipeline == null) {
			return;
		}
		TopDownTerrainCut.bind();
		FocusMode.bind();
		WorldTint.bind();
		CustomFog.bind();
		int index = StrayUniforms.index(pipeline);
		if (index < 0 || pipeline.program() == null) {
			return;
		}
		if (!(pipeline.program().getUniform(index) instanceof Uniform.Ubo)) {
			return;
		}
		setUniform(index, StrayUniforms.upload());
	}
}
