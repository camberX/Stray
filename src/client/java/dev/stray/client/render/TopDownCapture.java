package dev.stray.client.render;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.stray.client.farming.TopDownView;
import dev.stray.client.mixin.CameraAccessor;
import dev.stray.client.mixin.GameRendererAccessor;
import dev.stray.client.mixin.MinecraftAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Renders the world a second time from above the player into a square target.
 * The main camera is restored before this returns. A near plane just above the
 * eyes clips ceilings and other blocks between the overhead camera and the player.
 */
public final class TopDownCapture {
	public static final int SIZE = 256;
	private static final float FOV = 70f;
	private static final float CLIP_ABOVE_EYES = 0.35f;

	private static MainTarget target;
	private static ProjectionMatrixBuffer projection;
	private static boolean capturing;
	private static boolean ready;

	private TopDownCapture() {
	}

	public static boolean capturing() {
		return capturing;
	}

	public static GpuTextureView colorView() {
		if (!ready || target == null) {
			return null;
		}
		return target.getColorTextureView();
	}

	public static void render(DeltaTracker delta) {
		if (capturing || !TopDownView.showing()) {
			if (!TopDownView.showing()) {
				ready = false;
			}
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || client.gameRenderer == null || client.levelRenderer == null) {
			ready = false;
			return;
		}
		Camera camera = client.gameRenderer.getMainCamera();
		if (!camera.isInitialized()) {
			return;
		}
		CameraAccessor access = (CameraAccessor) camera;
		Vec3 savedPos = camera.position();
		float savedYaw = camera.yRot();
		float savedPitch = camera.xRot();
		Frustum savedFrustum = access.stray$cullFrustum();
		float savedFar = access.stray$depthFar();
		float savedFov = camera.getFov();
		int windowW = Math.max(1, client.getWindow().getWidth());
		int windowH = Math.max(1, client.getWindow().getHeight());
		GameRenderer renderer = client.gameRenderer;
		CameraRenderState state = renderer.getGameRenderState().levelRenderState.cameraRenderState;
		FogData savedFog = state.fogData;
		RenderTarget main = client.getMainRenderTarget();
		if (main == null || state == null) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		float altitude = TopDownView.height();
		Vec3 eye = client.player.getEyePosition(partial);
		float near = Math.max(0.25f, altitude - CLIP_ABOVE_EYES);
		float far = Math.max(savedFar, near + 32f);
		boolean swapped = false;
		capturing = true;
		try {
			access.stray$setPosition(new Vec3(eye.x, eye.y + altitude, eye.z));
			access.stray$setRotation(client.player.getViewYRot(partial), 90f);
			access.stray$setupPerspective(near, far, FOV, SIZE, SIZE);
			Matrix4f view = camera.getViewRotationMatrix(new Matrix4f());
			boolean zeroToOne = RenderSystem.getDevice().isZZeroToOne();
			Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(FOV), 1f, near, far, zeroToOne);
			Frustum frustum = new Frustum(view, proj);
			Vec3 overhead = camera.position();
			frustum.prepare(overhead.x, overhead.y, overhead.z);
			access.stray$cullFrustum(frustum);
			camera.extractRenderState(state, partial);
			if (state.projectionMatrix != null) {
				state.projectionMatrix.set(proj);
			}
			GameRendererAccessor game = (GameRendererAccessor) renderer;
			FogRenderer fog = game.stray$fogRenderer();
			FogData overheadFog = fog.setupFog(
				camera,
				client.options.getEffectiveRenderDistance(),
				delta,
				renderer.getBossOverlayWorldDarkening(partial),
				client.level
			);
			state.fogData = overheadFog;
			fog.updateBuffer(overheadFog);
			client.levelRenderer.update(camera);
			client.levelRenderer.extractLevel(delta, camera, partial);
			if (renderer.getGameRenderState().levelRenderState.chunkSectionsToRender == null) {
				return;
			}
			ensureTarget();
			if (target == null || target.getColorTexture() == null || target.getDepthTexture() == null) {
				return;
			}
			((MinecraftAccessor) client).stray$mainRenderTarget(target);
			swapped = true;
			RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
				target.getColorTexture(),
				0xFF87CEEB,
				target.getDepthTexture(),
				1.0
			);
			if (projection == null) {
				projection = new ProjectionMatrixBuffer("stray-top-down");
			}
			RenderSystem.setProjectionMatrix(projection.getBuffer(proj), ProjectionType.PERSPECTIVE);
			client.levelRenderer.renderLevel(
				game.stray$resourcePool(),
				delta,
				false,
				state,
				new Matrix4f(state.viewRotationMatrix),
				fog.getBuffer(FogRenderer.FogMode.WORLD),
				overheadFog.color,
				true,
				renderer.getGameRenderState().levelRenderState.chunkSectionsToRender
			);
			ready = target.getColorTextureView() != null;
		} catch (RuntimeException ignored) {
			ready = false;
		} finally {
			if (swapped) {
				((MinecraftAccessor) client).stray$mainRenderTarget(main);
			}
			access.stray$setPosition(savedPos);
			access.stray$setRotation(savedYaw, savedPitch);
			access.stray$setupPerspective(Camera.PROJECTION_Z_NEAR, savedFar, savedFov, windowW, windowH);
			if (savedFrustum != null) {
				access.stray$cullFrustum(savedFrustum);
			}
			camera.extractRenderState(state, partial);
			if (savedFog != null) {
				state.fogData = savedFog;
				((GameRendererAccessor) renderer).stray$fogRenderer().updateBuffer(savedFog);
			}
			capturing = false;
		}
	}

	private static void ensureTarget() {
		if (target != null && target.width == SIZE && target.height == SIZE && target.getColorTextureView() != null) {
			return;
		}
		if (target != null) {
			target.destroyBuffers();
		}
		target = new MainTarget(SIZE, SIZE);
	}
}
