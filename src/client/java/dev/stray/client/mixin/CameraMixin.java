package dev.stray.client.mixin;

import dev.stray.client.render.AspectFov;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Camera.class)
public class CameraMixin {
	@ModifyArg(
		method = "createProjectionMatrixForCulling",
		at = @At(
			value = "INVOKE",
			target = "Lorg/joml/Matrix4f;perspective(FFFFZ)Lorg/joml/Matrix4f;"
		),
		index = 1
	)
	private float stray$aspectCull(float aspect) {
		return AspectFov.apply(aspect);
	}
}
