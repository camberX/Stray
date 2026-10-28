package dev.stray.client.mixin;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
//? if >=26.3 {
import com.mojang.renderpearl.api.pipeline.ShaderType;
//?}
import dev.stray.client.render.StrayUniforms;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

//? if >=26.3 {
import java.util.Map;
//?} else {
/*import java.util.List;*///?}
import java.util.Optional;
//? if >=26.3 {
import java.util.Set;
//?}

@Mixin(targets = "com.mojang.renderpearl.api.pipeline.RenderPipeline$Builder")
public abstract class RenderPipelineBuilderMixin {
	@Shadow
	//? if >=26.3 {
	private Map<ShaderType, Identifier> shaders;
	//?} else {
	/*private Optional<Identifier> vertexShader;*///?}

	@Shadow
	//? if >=26.3 {
	private Optional<Set<BindGroupLayout>> bindGroupLayouts;
	//?} else {
	/*private Optional<Identifier> fragmentShader;

	@Shadow
	private Optional<List<BindGroupLayout>> bindGroupLayouts;*///?}

	@Shadow
	public abstract RenderPipeline.Builder withBindGroupLayout(BindGroupLayout layout);

	@Inject(method = "build", at = @At("HEAD"))
	private void stray$declareBlock(CallbackInfoReturnable<RenderPipeline> ci) {
		//? if >=26.3 {
		if (shaders == null || !stray$usesStrayBlock() || stray$alreadyDeclared()) {
		//?} else {
		/*if (!stray$usesStrayBlock() || stray$alreadyDeclared()) {*///?}
			return;
		}
		withBindGroupLayout(StrayUniforms.layout());
	}

	private boolean stray$usesStrayBlock() {
		//? if >=26.3 {
		for (Identifier id : shaders.values()) {
		//?} else {
		/*for (Identifier id : new Identifier[] {vertexShader.orElse(null), fragmentShader.orElse(null)}) {*///?}
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
			//? if >=26.3 {
			for (BindGroupLayout.UniformDescription uniform : group.uniforms()) {
			//?} else {
			/*for (BindGroupLayout.UniformDescription uniform : group.getUniforms()) {*///?}
				if ("StrayBlock".equals(uniform.name())) {
					return true;
				}
			}
		}
		return false;
	}
}
