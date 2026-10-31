package dev.stray.client.mixin;

import dev.stray.client.movement.MovementRings;
import dev.stray.client.render.AspectFov;
import dev.stray.client.render.TopDownCapture;
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

	@Shadow
	protected abstract void setPosition(double x, double y, double z);

	@Shadow
	private boolean detached;

	@Inject(
		method = "update",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V",
			shift = At.Shift.AFTER
		)
	)
	private void stray$replayLook(DeltaTracker delta, CallbackInfo ci) {
		if (dev.stray.client.movement.Freecam.active()) {
			float partial = delta.getGameTimeDeltaPartialTick(true);
			this.setPosition(
				dev.stray.client.movement.Freecam.x(partial),
				dev.stray.client.movement.Freecam.y(partial),
				dev.stray.client.movement.Freecam.z(partial)
			);
			this.setRotation(dev.stray.client.movement.Freecam.yaw(), dev.stray.client.movement.Freecam.pitch());
			// Detached: the player model is drawn where it stands, except while the camera is
			// still inside its head (looking out through the face isn't useful).
			net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
			this.detached = player == null
				|| dev.stray.client.movement.Freecam.position(partial).distanceToSqr(player.getEyePosition(partial)) > 0.6 * 0.6;
			return;
		}
		if (MovementRings.sampleCameraLook()) {
			this.setRotation(MovementRings.cameraYaw(), MovementRings.cameraPitch());
		}
	}

	@Inject(method = "update", at = @At("RETURN"))
	private void stray$replayLookTail(DeltaTracker delta, CallbackInfo ci) {
		if (MovementRings.playing()) {
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
		if (TopDownCapture.capturing()) {
			return aspect;
		}
		return AspectFov.apply(aspect);
	}
}
