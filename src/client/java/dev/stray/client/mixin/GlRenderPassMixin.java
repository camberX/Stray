package dev.stray.client.mixin;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.backend.opengl.GlProgram;
import com.mojang.renderpearl.backend.opengl.GlRenderPipeline;
import com.mojang.renderpearl.backend.opengl.Uniform;
import dev.stray.client.mining.FocusMode;
import dev.stray.client.render.StrayUniforms;
import dev.stray.client.render.TopDownTerrainCut;
import dev.stray.client.visual.CustomFog;
import dev.stray.client.visual.WorldTint;
import it.unimi.dsi.fastutil.objects.ReferenceList;
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
	protected ReferenceList<Object> uniforms;

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
		GlProgram program = pipeline.program();
		if (program == null) {
			return;
		}
		TopDownTerrainCut.bind();
		FocusMode.bind();
		WorldTint.bind();
		CustomFog.bind();
		GpuBufferSlice slice = null;
		int count = Math.min(program.uniformCount(), uniforms.size());
		for (int index = 0; index < count; index++) {
			if (!(program.getUniform(index) instanceof Uniform.Ubo) || uniforms.get(index) != null) {
				continue;
			}
			if (slice == null) {
				slice = StrayUniforms.upload();
			}
			uniforms.set(index, slice);
			setUniform(index, slice);
		}
	}
}
