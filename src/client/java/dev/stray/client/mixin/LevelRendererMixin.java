package dev.stray.client.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.visual.motionblur.MotionBlurShaders;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
	@Unique
	private final Matrix4f stray$prevModelView = new Matrix4f();
	@Unique
	private final Matrix4f stray$prevProjection = new Matrix4f();
	@Unique
	private final Matrix4f stray$scratchModelView = new Matrix4f();
	@Unique
	private final Matrix4f stray$scratchProjection = new Matrix4f();
	@Unique
	private double stray$prevCamX;
	@Unique
	private double stray$prevCamY;
	@Unique
	private double stray$prevCamZ;
	@Unique
	private boolean stray$previousFrameReady;

	@Inject(method = "renderLevel", at = @At("HEAD"))
	private void stray$motionBlurHead(
		GraphicsResourceAllocator resourceAllocator,
		DeltaTracker deltaTracker,
		boolean renderOutline,
		CameraRenderState cameraState,
		Matrix4fc modelViewMatrix,
		GpuBufferSlice terrainFog,
		Vector4f fogColor,
		boolean shouldRenderSky,
		ChunkSectionsToRender chunkSectionsToRender,
		CallbackInfo ci
	) {
		boolean blurActive = MotionBlurShaders.active();
		boolean needsVelocity = blurActive && StrayConfig.get().motionBlurUsesVelocity();
		double cx = cameraState.pos.x();
		double cy = cameraState.pos.y();
		double cz = cameraState.pos.z();
		if (!blurActive) {
			MotionBlurShaders.clearFrameAllocator();
			stray$rememberFrame(modelViewMatrix, cameraState.projectionMatrix, cx, cy, cz);
			return;
		}
		MotionBlurShaders.captureAllocator(resourceAllocator);
		MotionBlurShaders.beginFrame();
		if (!needsVelocity) {
			stray$rememberFrame(modelViewMatrix, cameraState.projectionMatrix, cx, cy, cz);
			return;
		}
		stray$scratchModelView.set(modelViewMatrix);
		stray$scratchProjection.set(cameraState.projectionMatrix);
		if (!stray$previousFrameReady) {
			MotionBlurShaders.setFrameMotionBlur(
				stray$scratchModelView,
				stray$scratchModelView,
				stray$scratchProjection,
				stray$scratchProjection,
				0.0f,
				0.0f,
				0.0f
			);
			stray$rememberFrame(stray$scratchModelView, stray$scratchProjection, cx, cy, cz);
			return;
		}
		MotionBlurShaders.setFrameMotionBlur(
			stray$scratchModelView,
			stray$prevModelView,
			stray$scratchProjection,
			stray$prevProjection,
			(float) (cx - stray$prevCamX),
			(float) (cy - stray$prevCamY),
			(float) (cz - stray$prevCamZ)
		);
		stray$rememberFrame(stray$scratchModelView, stray$scratchProjection, cx, cy, cz);
	}

	@Unique
	private void stray$rememberFrame(Matrix4fc modelView, Matrix4fc projection, double cx, double cy, double cz) {
		stray$prevModelView.set(modelView);
		stray$prevProjection.set(projection);
		stray$prevCamX = cx;
		stray$prevCamY = cy;
		stray$prevCamZ = cz;
		stray$previousFrameReady = true;
	}

	@Inject(method = "submitEntities", at = @At("HEAD"))
	private void stray$motionBlurBeforeEntities(PoseStack poseStack, LevelRenderState levelRenderState, SubmitNodeCollector output, CallbackInfo ci) {
		if (!MotionBlurShaders.active() || !StrayConfig.get().motionBlurUsesVelocity()) {
			return;
		}
		if (stray$thirdPersonOrPassenger()) {
			MotionBlurShaders.applyF5EntityRideBlur();
			return;
		}
		MotionBlurShaders.applyPreEntityBlur();
	}

	@Inject(method = "renderLevel", at = @At("TAIL"))
	private void stray$motionBlurTail(
		GraphicsResourceAllocator resourceAllocator,
		DeltaTracker deltaTracker,
		boolean renderOutline,
		CameraRenderState cameraState,
		Matrix4fc modelViewMatrix,
		GpuBufferSlice terrainFog,
		Vector4f fogColor,
		boolean shouldRenderSky,
		ChunkSectionsToRender chunkSectionsToRender,
		CallbackInfo ci
	) {
		StrayConfig.MotionBlurAlgorithm algorithm = StrayConfig.get().motionBlurAlgorithm();
		boolean special = stray$thirdPersonOrPassenger();
		if (algorithm == StrayConfig.MotionBlurAlgorithm.HYBRID_BLENDING && !special) {
			MotionBlurShaders.applyPostRenderVelocityOnly();
		} else if (algorithm == StrayConfig.MotionBlurAlgorithm.VELOCITY_BASED && !special) {
			MotionBlurShaders.applyPostRenderVelocityOnly();
		}
	}

	@Unique
	private boolean stray$thirdPersonOrPassenger() {
		Minecraft client = Minecraft.getInstance();
		if (client.options.getCameraType() != CameraType.FIRST_PERSON) {
			return true;
		}
		return client.player != null && client.player.isPassenger();
	}
}
