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
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Overhead picture of the world. The near plane sits just above the player's
 * head, so blocks between the camera and the player are not drawn. The player
 * is rendered detached, and the field of view stays the player's.
 */
public final class TopDownCapture {
	public static final int SIZE = 256;

	private static MainTarget wideTarget;
	private static ProjectionMatrixBuffer projection;
	private static boolean capturing;
	private static boolean ready;
	private static float cutThreshold;

	private TopDownCapture() {
	}

	public static boolean capturing() {
		return capturing;
	}

	/** Camera-relative Y where blocks above the player start. */
	public static float cutThreshold() {
		return cutThreshold;
	}

	public static GpuTextureView colorView() {
		if (!ready || wideTarget == null) {
			return null;
		}
		return wideTarget.getColorTextureView();
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
		boolean savedDetached = camera.isDetached();
		Frustum savedFrustum = access.stray$cullFrustum();
		float savedFar = access.stray$depthFar();
		float savedFov = camera.getFov();
		int windowW = Math.max(1, client.getWindow().getWidth());
		int windowH = Math.max(1, client.getWindow().getHeight());
		GameRenderer renderer = client.gameRenderer;
		CameraRenderState state = renderer.getGameRenderState().levelRenderState.cameraRenderState;
		FogData savedFog = state.fogData;
		Matrix4f savedProjection = state.projectionMatrix == null ? new Matrix4f() : new Matrix4f(state.projectionMatrix);
		RenderTarget main = client.getMainRenderTarget();
		if (main == null) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		float altitude = TopDownView.height();
		Vec3 eye = client.player.getEyePosition(partial);
		float fov = Mth.clamp(savedFov, 30f, 110f);
		float far = Math.max(savedFar, altitude + 64f);
		float near = Camera.PROJECTION_Z_NEAR;
		cutThreshold = (float) (client.player.getBlockY() - (eye.y + altitude));
		boolean swapped = false;
		capturing = true;
		try {
			access.stray$detached(true);
			access.stray$setPosition(new Vec3(eye.x, eye.y + altitude, eye.z));
			access.stray$setRotation(client.player.getViewYRot(partial), 90f);
			access.stray$setupPerspective(near, far, fov, SIZE, SIZE);
			boolean zeroToOne = RenderSystem.getDevice().isZZeroToOne();
			Matrix4f view = camera.getViewRotationMatrix(new Matrix4f());
			Matrix4f cullProj = new Matrix4f().perspective((float) Math.toRadians(fov), 1f, near, far, zeroToOne);
			Frustum frustum = new Frustum(view, cullProj);
			Vec3 overhead = camera.position();
			frustum.prepare(overhead.x, overhead.y, overhead.z);
			access.stray$cullFrustum(frustum);
			GameRendererAccessor game = (GameRendererAccessor) renderer;
			FogRenderer fog = game.stray$fogRenderer();
			client.levelRenderer.update(camera);
			ensureTargets();
			if (projection == null) {
				projection = new ProjectionMatrixBuffer("stray-top-down");
			}
			((MinecraftAccessor) client).stray$mainRenderTarget(wideTarget);
			swapped = true;
			drawPass(client, renderer, game, fog, camera, state, delta, partial, cullProj);
			ready = wideTarget.getColorTextureView() != null;
		} catch (RuntimeException ignored) {
			ready = false;
		} finally {
			if (swapped) {
				((MinecraftAccessor) client).stray$mainRenderTarget(main);
			}
			access.stray$detached(savedDetached);
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
			if (projection != null) {
				RenderSystem.setProjectionMatrix(projection.getBuffer(savedProjection), ProjectionType.PERSPECTIVE);
			}
			TopDownTerrainCut.clear();
			capturing = false;
		}
	}

	private static void drawPass(
		Minecraft client,
		GameRenderer renderer,
		GameRendererAccessor game,
		FogRenderer fog,
		Camera camera,
		CameraRenderState state,
		DeltaTracker delta,
		float partial,
		Matrix4f proj
	) {
		camera.extractRenderState(state, partial);
		if (state.projectionMatrix != null) {
			state.projectionMatrix.set(proj);
		}
		FogData overheadFog = fog.setupFog(
			camera,
			client.options.getEffectiveRenderDistance(),
			delta,
			renderer.getBossOverlayWorldDarkening(partial),
			client.level
		);
		state.fogData = overheadFog;
		fog.updateBuffer(overheadFog);
		client.levelRenderer.extractLevel(delta, camera, partial);
		renderer.getGameRenderState().levelRenderState.haveGlowingEntities = false;
		if (renderer.getGameRenderState().levelRenderState.chunkSectionsToRender == null) {
			return;
		}
		RenderTarget current = client.getMainRenderTarget();
		if (current == null || current.getColorTexture() == null || current.getDepthTexture() == null) {
			return;
		}
		RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
			current.getColorTexture(),
			0xFF87CEEB,
			current.getDepthTexture(),
			1.0
		);
		RenderSystem.setProjectionMatrix(projection.getBuffer(proj), ProjectionType.PERSPECTIVE);
		client.levelRenderer.renderLevel(
			game.stray$resourcePool(),
			delta,
			false,
			state,
			new Matrix4f(state.viewRotationMatrix),
			fog.getBuffer(FogRenderer.FogMode.WORLD),
			overheadFog.color,
			false,
			renderer.getGameRenderState().levelRenderState.chunkSectionsToRender
		);
	}

	private static void ensureTargets() {
		wideTarget = ensure(wideTarget);
	}

	private static MainTarget ensure(MainTarget target) {
		if (target != null && target.width == SIZE && target.height == SIZE && target.getColorTextureView() != null) {
			return target;
		}
		if (target != null) {
			target.destroyBuffers();
		}
		return new MainTarget(SIZE, SIZE);
	}
}
