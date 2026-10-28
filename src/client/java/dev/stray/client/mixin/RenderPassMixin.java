package dev.stray.client.mixin;

//? if <26.3 {
/*import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.commands.RenderPass;
import dev.stray.client.mining.FocusMode;
import dev.stray.client.render.StrayUniforms;
import dev.stray.client.render.TopDownTerrainCut;
import dev.stray.client.visual.CustomFog;
import dev.stray.client.visual.WorldTint;
import java.util.Map;
import java.util.WeakHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^*
 * Terrain pipelines (vanilla and Sodium) get Stray's uniform block declared,
 * but nothing that draws them assigns it. Assign it whenever such a pipeline is
 * bound. This sits on the backend-neutral render pass, so it covers OpenGL and Vulkan.
 ^/
@Mixin(RenderPass.class)
public abstract class RenderPassMixin {
	@Unique
	private static final Map<RenderPipeline, Boolean> STRAY$USES_BLOCK = new WeakHashMap<>();

	@Inject(method = "setPipeline", at = @At("RETURN"))
	private void stray$bindStrayBlock(RenderPipeline pipeline, CallbackInfo ci) {
		if (pipeline == null || !STRAY$USES_BLOCK.computeIfAbsent(pipeline, RenderPassMixin::stray$declares)) {
			return;
		}
		TopDownTerrainCut.bind();
		FocusMode.bind();
		WorldTint.bind();
		CustomFog.bind();
		((RenderPass) (Object) this).setUniform("StrayBlock", StrayUniforms.upload());
	}

	@Unique
	private static boolean stray$declares(RenderPipeline pipeline) {
		for (BindGroupLayout group : pipeline.getBindGroupLayouts()) {
			for (BindGroupLayout.UniformDescription uniform : group.getUniforms()) {
				if ("StrayBlock".equals(uniform.name())) {
					return true;
				}
			}
		}
		return false;
	}
}*///?}
