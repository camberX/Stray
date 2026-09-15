package dev.stray.client.mixin;

import dev.stray.client.render.TitleBackdrop;
import net.minecraft.client.renderer.DynamicUniforms;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(DynamicUniforms.class)
public class DynamicUniformsMixin {
	@ModifyVariable(method = "writeTransform", at = @At("HEAD"), argsOnly = true)
	private Vector3fc stray$marbleTime(Vector3fc offset) {
		float time = TitleBackdrop.flowTime();
		if (time == 0f) {
			return offset;
		}
		return new Vector3f(time, 0f, 0f);
	}
}
