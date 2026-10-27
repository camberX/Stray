package dev.stray.client.mixin;

import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.stray.client.render.StrayUniforms;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

@Mixin(targets = "com.mojang.blaze3d.pipeline.RenderPipeline$Builder")
public abstract class RenderPipelineBuilderMixin {
	@Shadow
	private Optional<Identifier> vertexShader;

	@Shadow
	private Optional<Identifier> fragmentShader;

	@Shadow
	private Optional<List<BindGroupLayout>> bindGroupLayouts;

	@Shadow
	public abstract RenderPipeline.Builder withBindGroupLayout(BindGroupLayout layout);

	@Inject(method = "build", at = @At("HEAD"))
	private void stray$declareBlock(CallbackInfoReturnable<RenderPipeline> ci) {
		if (!stray$usesStrayBlock() || stray$alreadyDeclared()) {
			return;
		}
		withBindGroupLayout(StrayUniforms.layout());
	}

	private boolean stray$usesStrayBlock() {
		for (Identifier id : new Identifier[] {vertexShader.orElse(null), fragmentShader.orElse(null)}) {
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
			for (BindGroupLayout.UniformDescription uniform : group.getUniforms()) {
				if ("StrayBlock".equals(uniform.name())) {
					return true;
				}
			}
		}
		return false;
	}
}
