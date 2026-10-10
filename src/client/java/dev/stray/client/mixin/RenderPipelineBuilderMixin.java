package dev.stray.client.mixin;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import dev.stray.client.render.StrayUniforms;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Mixin(targets = "com.mojang.renderpearl.api.pipeline.RenderPipeline$Builder")
public abstract class RenderPipelineBuilderMixin {
	@Shadow
	private Map<ShaderType, Identifier> shaders;

	@Shadow
	private Optional<Set<BindGroupLayout>> bindGroupLayouts;

	@Shadow
	public abstract RenderPipeline.Builder withBindGroupLayout(BindGroupLayout layout);

	@Inject(method = "build", at = @At("HEAD"))
	private void stray$declareBlock(CallbackInfoReturnable<RenderPipeline> ci) {
		if (shaders == null || !stray$usesStrayBlock() || stray$alreadyDeclared()) {
			return;
		}
		withBindGroupLayout(StrayUniforms.layout());
	}

	private boolean stray$usesStrayBlock() {
		for (Identifier id : shaders.values()) {
			if (id == null) {
				continue;
			}
			String path = id.getPath();
			if (path.contains("terrain") || path.contains("block_layer")) {
				return true;
			}
		}
		return false;
	}

	private boolean stray$alreadyDeclared() {
		if (bindGroupLayouts == null || bindGroupLayouts.isEmpty()) {
			return false;
		}
		for (BindGroupLayout group : bindGroupLayouts.get()) {
			for (BindGroupLayout.UniformDescription uniform : group.uniforms()) {
				if ("StrayBlock".equals(uniform.name())) {
					return true;
				}
			}
		}
		return false;
	}
}
