package dev.stray.client.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.opengl.GlRenderPipeline;
import com.mojang.blaze3d.opengl.Uniform;
import dev.stray.client.mining.FocusMode;
import dev.stray.client.render.StrayUniforms;
import dev.stray.client.render.TopDownTerrainCut;
import dev.stray.client.visual.CustomFog;
import dev.stray.client.visual.WorldTint;
import java.util.HashMap;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pipelines patched with Stray's uniform block (vanilla and Sodium terrain) never get that
 * block assigned by the code that draws them, and the GL backend would bind a null slice.
 * Fill any unassigned uniform block with Stray's buffer right before the draw.
 */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlRenderPass")
public abstract class GlRenderPassMixin {
	@Shadow
	protected GlRenderPipeline pipeline;

	@Shadow
	protected HashMap<String, GpuBufferSlice> uniforms;

	@Shadow
	public abstract void setUniform(String name, GpuBufferSlice value);

	@Inject(method = {
		"drawIndexed",
		"draw",
		"multiDrawIndexed",
		"multiDraw",
		"drawIndexedIndirect",
		"drawIndirect",
		"drawMultipleIndexed"
	}, at = @At("HEAD"))
	private void stray$terrainUniforms(CallbackInfo ci) {
		if (pipeline == null) {
			return;
		}
		GlProgram program = pipeline.program();
		if (program == null) {
			return;
		}
		GpuBufferSlice slice = null;
		for (Map.Entry<String, Uniform> entry : program.getUniforms().entrySet()) {
			if (!(entry.getValue() instanceof Uniform.Ubo) || uniforms.containsKey(entry.getKey())) {
				continue;
			}
			if (slice == null) {
				TopDownTerrainCut.bind();
				FocusMode.bind();
				WorldTint.bind();
				CustomFog.bind();
				slice = StrayUniforms.upload();
			}
			setUniform(entry.getKey(), slice);
		}
	}
}
