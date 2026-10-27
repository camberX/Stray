package dev.stray.client.mixin;

import dev.stray.client.mining.FocusMode;
import dev.stray.client.render.TopDownTerrainCut;
import dev.stray.client.visual.CustomFog;
import dev.stray.client.visual.WorldTint;
import net.minecraft.client.renderer.ShaderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ShaderManager.class)
public class ShaderManagerMixin {
	@ModifyArg(
		method = "loadShader",
		at = @At(
			value = "INVOKE",
			target = "Lcom/google/common/collect/ImmutableMap$Builder;put(Ljava/lang/Object;Ljava/lang/Object;)Lcom/google/common/collect/ImmutableMap$Builder;"
		),
		index = 1
	)
	private static Object stray$patchLoaded(Object source) {
		if (!(source instanceof String text)) {
			return source;
		}
		text = FocusMode.patchVanilla(TopDownTerrainCut.patchSource(text));
		text = FocusMode.patchSodiumVertex(TopDownTerrainCut.patchSodiumVertex(text));
		text = TopDownTerrainCut.patchSodiumFragment(FocusMode.patchSodiumFragment(text));
		text = WorldTint.injectTerrainFragmentSource(text);
		return CustomFog.injectSodiumShader(text);
	}
}
