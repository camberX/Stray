package dev.stray.client.mixin;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.backend.opengl.GlRenderPipeline;
import com.mojang.renderpearl.backend.opengl.Uniform;
import dev.stray.client.render.StrayUniforms;
import it.unimi.dsi.fastutil.objects.ReferenceList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Sodium's terrain pipeline declares Stray's uniform block, but the chunk
 * renderer never assigns it. The OpenGL draw then calls {@code buffer()} on a
 * null slice. Substituting the slice at the lookup keeps that draw alive.
 */
@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlCommandEncoder")
public class GlCommandEncoderMixin {
	@Shadow
	private GlRenderPipeline lastPipeline;

	@Redirect(
		method = "setupDraw",
		at = @At(
			value = "INVOKE",
			target = "Lit/unimi/dsi/fastutil/objects/ReferenceList;get(I)Ljava/lang/Object;"
		)
	)
	private Object stray$missingUniform(ReferenceList<Object> uniforms, int index) {
		Object value = uniforms.get(index);
		if (value != null || lastPipeline == null || lastPipeline.program() == null) {
			return value;
		}
		Uniform uniform = lastPipeline.program().getUniform(index);
		if (!(uniform instanceof Uniform.Ubo) && !(uniform instanceof Uniform.Utb)) {
			return value;
		}
		GpuBufferSlice slice = StrayUniforms.upload();
		uniforms.set(index, slice);
		return slice;
	}
}
