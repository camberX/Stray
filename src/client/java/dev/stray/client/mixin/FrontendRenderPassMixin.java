package dev.stray.client.mixin;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.frontend.FrontendRenderPipeline;
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

import java.util.HashMap;

@Mixin(targets = "com.mojang.renderpearl.frontend.FrontendRenderPass")
public abstract class FrontendRenderPassMixin {
	@Shadow
	private FrontendRenderPipeline boundPipeline;

	@Shadow
	protected HashMap<String, Object> uniforms;

	@Shadow
	private void setUniform(String name, Object value) {
		throw new AssertionError();
	}

	@Inject(method = "validateDraw", at = @At("HEAD"))
	private void stray$fillMissingBuffers(CallbackInfo ci) {
		if (boundPipeline == null) {
			return;
		}
		GpuBufferSlice slice = null;
		for (BindGroupLayout.UniformDescription uniform : boundPipeline.uniforms()) {
			UniformType type = uniform.type();
			if (type != UniformType.UNIFORM_BUFFER && type != UniformType.TEXEL_BUFFER) {
				continue;
			}
			if (uniforms.containsKey(uniform.name())) {
				continue;
			}
			if (slice == null) {
				TopDownTerrainCut.bind();
				FocusMode.bind();
				WorldTint.bind();
				CustomFog.bind();
				slice = StrayUniforms.upload();
			}
			setUniform(uniform.name(), slice);
		}
	}
}
