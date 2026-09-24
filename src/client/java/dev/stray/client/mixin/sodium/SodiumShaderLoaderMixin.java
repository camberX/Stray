package dev.stray.client.mixin.sodium;

import dev.stray.client.render.TopDownTerrainCut;
import dev.stray.client.visual.CustomFog;
import dev.stray.client.visual.WorldTint;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ShaderLoader.class, remap = false)
public class SodiumShaderLoaderMixin {
	@Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true, remap = false)
	private static void stray$injectWorldTint(Identifier name, CallbackInfoReturnable<String> cir) {
		String path = name.getPath();
		if (path.endsWith("fog.glsl")) {
			cir.setReturnValue(CustomFog.injectSodiumShader(cir.getReturnValue()));
			return;
		}
		if (path.endsWith(".vsh") && path.contains("block_layer")) {
			cir.setReturnValue(TopDownTerrainCut.patchSodiumVertex(cir.getReturnValue()));
			return;
		}
		if (!path.endsWith(".fsh") || !path.contains("block_layer")) {
			return;
		}
		cir.setReturnValue(TopDownTerrainCut.patchSodiumFragment(WorldTint.injectTerrainFragmentSource(cir.getReturnValue())));
	}
}
