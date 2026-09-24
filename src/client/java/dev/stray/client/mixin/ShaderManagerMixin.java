package dev.stray.client.mixin;

import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import dev.stray.client.render.TopDownTerrainCut;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShaderManager.class)
public class ShaderManagerMixin {
	@Inject(method = "getShader", at = @At("RETURN"), cancellable = true)
	private void stray$topDownCut(Identifier id, ShaderType type, CallbackInfoReturnable<String> cir) {
		String source = cir.getReturnValue();
		if (source == null || id == null || !id.getPath().contains("terrain")) {
			return;
		}
		cir.setReturnValue(TopDownTerrainCut.patchSource(source));
	}

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
		return TopDownTerrainCut.patchSource(text);
	}
}
