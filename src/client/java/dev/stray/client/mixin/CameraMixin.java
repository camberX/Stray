package dev.stray.client.mixin;

import dev.stray.client.farming.TopDownView;
import dev.stray.client.movement.MovementRings;
import dev.stray.client.render.AspectFov;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.entity.Entity;
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

	@Shadow
	protected abstract void setPosition(double x, double y, double z);

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

	@Inject(method = "update", at = @At("RETURN"))
	private void stray$replayLookTail(DeltaTracker delta, CallbackInfo ci) {
		if (MovementRings.playing()) {
			this.setRotation(MovementRings.cameraYaw(), MovementRings.cameraPitch());
			return;
		}
		if (!TopDownView.active()) {
			return;
		}
		Entity entity = ((Camera) (Object) this).entity();
		if (entity == null) {
			return;
		}
		TopDownView.CameraPose pose = new TopDownView.CameraPose();
		TopDownView.place(entity, delta.getGameTimeDeltaPartialTick(true), pose);
		this.setRotation(pose.yaw, pose.pitch);
		this.setPosition(pose.x, pose.y, pose.z);
	}

	@ModifyArg(
		method = "createProjectionMatrixForCulling",
		at = @At(
			value = "INVOKE",
			target = "Lorg/joml/Matrix4f;perspective(FFFFZ)Lorg/joml/Matrix4f;"
		),
		index = 2
	)
	private float stray$topDownCullNear(float zNear) {
		return TopDownView.nearPlane(zNear);
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
