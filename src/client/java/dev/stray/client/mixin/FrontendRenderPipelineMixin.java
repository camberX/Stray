package dev.stray.client.mixin;

import com.mojang.renderpearl.backend.api.BackendRenderPipeline;
import dev.stray.client.render.StrayUniforms;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(targets = "com.mojang.renderpearl.frontend.FrontendRenderPipeline")
public class FrontendRenderPipelineMixin {
	@Inject(method = "<init>", at = @At("RETURN"))
	private void stray$rememberBlock(
		String name,
		BackendRenderPipeline backend,
		List<?> vertexFormats,
		Object2IntMap<String> uniformIndices,
		List<?> uniforms,
		List<?> colorTargetStates,
		boolean wantsDepthTexture,
		int pushConstantSize,
		CallbackInfo ci
	) {
		if (backend == null || uniformIndices == null || !uniformIndices.containsKey("StrayBlock")) {
			return;
		}
		StrayUniforms.remember(backend, uniformIndices.getInt("StrayBlock"));
	}
}
