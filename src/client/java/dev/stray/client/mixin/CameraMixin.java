package dev.stray.client.mixin;

import dev.stray.client.movement.MovementRings;
import dev.stray.client.render.AspectFov;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(
		method = "update",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V",
			shift = At.Shift.AFTER
		)
	)
	private void stray$replayLook(DeltaTracker delta, CallbackInfo ci) {
		if (MovementRings.sampleCameraLook()) {
			this.setRotation(MovementRings.cameraYaw(), MovementRings.cameraPitch());
		}
	}

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
