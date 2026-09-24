package dev.stray.client.mixin.sodium;

import dev.stray.client.render.TopDownCapture;
import dev.stray.client.visual.CustomFog;
import dev.stray.client.visual.WorldTint;
import net.caffeinemc.mods.sodium.client.gl.shader.uniform.GlUniformFloat;
import net.caffeinemc.mods.sodium.client.gl.shader.uniform.GlUniformFloat3v;
import net.caffeinemc.mods.sodium.client.gl.shader.uniform.GlUniformFloat4v;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderOptions;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.DefaultShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ShaderBindingContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DefaultShaderInterface.class, remap = false)
public class SodiumDefaultShaderInterfaceMixin {
	@Unique
	private GlUniformFloat4v stray$worldTint;
	@Unique
	private GlUniformFloat stray$fog;
	@Unique
	private GlUniformFloat3v stray$cut;

	@Inject(method = "<init>", at = @At("RETURN"), remap = false)
	private void stray$bindWorldTint(ShaderBindingContext context, ChunkShaderOptions options, CallbackInfo ci) {
		this.stray$worldTint = context.bindUniformOptional("u_WorldTint", GlUniformFloat4v::new);
		this.stray$fog = context.bindUniformOptional("u_StrayFog", GlUniformFloat::new);
		this.stray$cut = context.bindUniformOptional("u_StrayCut", GlUniformFloat3v::new);
	}

	@Inject(method = "setupState", at = @At("RETURN"), remap = false)
	private void stray$uploadWorldTint(CallbackInfo ci) {
		if (this.stray$worldTint != null) {
			int rgb = WorldTint.shaderRgb();
			this.stray$worldTint.set(
				((rgb >> 16) & 0xFF) / 255f,
				((rgb >> 8) & 0xFF) / 255f,
				(rgb & 0xFF) / 255f,
				WorldTint.shaderStrength()
			);
		}
		if (this.stray$fog != null) {
			this.stray$fog.setFloat(CustomFog.applied() ? 1f : 0f);
		}
		if (this.stray$cut != null) {
			if (TopDownCapture.capturing()) {
				this.stray$cut.set(TopDownCapture.cutThreshold(), 6f, 1f);
			} else {
				this.stray$cut.set(0f, 0f, 0f);
			}
		}
	}
}
