package dev.stray.client.mixin;

import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShaderManager.class)
public class ShaderManagerMixin {
	@Inject(method = "getShader", at = @At("RETURN"), cancellable = true)
	private void stray$topDownCut(Identifier id, ShaderType type, CallbackInfoReturnable<String> cir) {
		String source = cir.getReturnValue();
		if (source == null || id == null || !id.getPath().contains("terrain") || source.contains("strayRel")) {
			return;
		}
		if (source.contains("in vec3 Position")) {
			cir.setReturnValue(source
				.replace("out vec2 texCoord0;", "out vec2 texCoord0;\nout vec3 strayRel;")
				.replace("texCoord0 = UV0;", "texCoord0 = UV0;\n    strayRel = pos;"));
			return;
		}
		if (source.contains("fragColor")) {
			cir.setReturnValue(source
				.replace("in vec2 texCoord0;", "in vec2 texCoord0;\nin vec3 strayRel;\nuniform vec3 StrayCut;")
				.replace(
					"void main() {",
					"void main() {\n    if (StrayCut.z > 0.5) {\n        vec2 delta = strayRel.xz;\n        if (dot(delta, delta) <= StrayCut.y * StrayCut.y && strayRel.y > StrayCut.x) {\n            discard;\n        }\n    }\n"
				));
		}
	}
}
