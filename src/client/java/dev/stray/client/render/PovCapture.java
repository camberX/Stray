package dev.stray.client.render;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.dungeon.DungeonParty;
import dev.stray.client.dungeon.LeapMenu;
import dev.stray.client.mixin.CameraAccessor;
import dev.stray.client.mixin.GameRendererAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * What each teammate sees, for the leap menu. While the menu is open the world is
 * drawn once more per frame from one teammate's eyes into that teammate's target,
 * taking turns, so each picture refreshes every few frames. Client-side drawing
 * only: the camera is moved for the extra pass and put back right after.
 */
public final class PovCapture {
	private static final MainTarget[] TARGETS = new MainTarget[4];
	private static final boolean[] READY = new boolean[4];
	private static ProjectionMatrixBuffer projection;
	private static boolean capturing;
	private static Entity subject;
	private static int next;

	private PovCapture() {
	}

	public static boolean capturing() {
		return capturing;
	}

	/** The teammate whose eyes the camera is in; their own model is not drawn. */
	public static boolean hidden(Entity entity) {
		return capturing && entity != null && entity == subject;
	}

	public static GpuTextureView view(int index) {
		if (index < 0 || index >= TARGETS.length || !READY[index] || TARGETS[index] == null) {
			return null;
		}
		return TARGETS[index].getColorTextureView();
	}

	public static boolean wanted() {
		return StrayConfig.get().leapPov && LeapMenu.active();
	}

	public static void render(DeltaTracker delta) {
		if (capturing) {
			return;
		}
		if (!wanted()) {
			for (int i = 0; i < READY.length; i++) {
				READY[i] = false;
			}
			return;
		}
		LeapMenu.Entry[] entries = LeapMenu.players();
		int index = -1;
		for (int step = 0; step < 4; step++) {
			int i = (next + step) % 4;
			LeapMenu.Entry entry = entries[i];
			if (entry != null && entry.player().entity != null && !entry.player().entity.isRemoved()) {
				index = i;
				break;
			}
		}
		for (int i = 0; i < 4; i++) {
			LeapMenu.Entry entry = entries[i];
			if (entry == null || entry.player().entity == null) {
				READY[i] = false;
			}
		}
		if (index < 0) {
			return;
		}
		next = (index + 1) % 4;
		capture(index, entries[index].player().entity, delta);
	}

	private static void capture(int index, AbstractClientPlayer target, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || client.gameRenderer == null || client.levelRenderer == null) {
			return;
		}
		Camera camera = client.gameRenderer.mainCamera();
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
		int w = Math.max(64, windowW / 2);
		int h = Math.max(64, windowH / 2);
		GameRenderer renderer = client.gameRenderer;
		CameraRenderState state = renderer.gameRenderState().levelRenderState.cameraRenderState;
		FogData savedFog = state.fogData;
		Matrix4f savedProjection = state.projectionMatrix == null ? new Matrix4f() : new Matrix4f(state.projectionMatrix);
		RenderTarget main = renderer.mainRenderTarget();
		if (main == null) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		double x = Mth.lerp(partial, target.xo, target.getX());
		double y = Mth.lerp(partial, target.yo, target.getY()) + target.getEyeHeight();
		double z = Mth.lerp(partial, target.zo, target.getZ());
		float yaw = target.getViewYRot(partial);
		float pitch = target.getViewXRot(partial);
		float fov = Mth.clamp(client.options.fov().get().floatValue(), 30f, 110f);
		float near = Camera.PROJECTION_Z_NEAR;
		float far = savedFar;
		boolean swapped = false;
		capturing = true;
		subject = target;
		try {
			access.stray$detached(true);
			access.stray$setPosition(new Vec3(x, y, z));
			access.stray$setRotation(yaw, pitch);
			access.stray$setupPerspective(near, far, fov, w, h);
			boolean zeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
			Matrix4f view = camera.getViewRotationMatrix(new Matrix4f());
			Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(fov), w / (float) h, near, far, zeroToOne);
			Frustum frustum = new Frustum(view, proj);
			frustum.prepare(x, y, z);
			access.stray$cullFrustum(frustum);
			GameRendererAccessor game = (GameRendererAccessor) renderer;
			FogRenderer fog = game.stray$fogRenderer();
			TARGETS[index] = ensure(TARGETS[index], w, h);
			if (projection == null) {
				projection = new ProjectionMatrixBuffer("stray-leap-pov");
			}
			game.stray$mainRenderTarget(TARGETS[index]);
			swapped = true;
			drawPass(client, renderer, game, fog, camera, state, delta, partial, proj);
			READY[index] = TARGETS[index].getColorTextureView() != null;
		} catch (RuntimeException ignored) {
			READY[index] = false;
		} finally {
			if (swapped) {
				((GameRendererAccessor) renderer).stray$mainRenderTarget(main);
			}
			access.stray$detached(savedDetached);
			access.stray$setPosition(savedPos);
			access.stray$setRotation(savedYaw, savedPitch);
			access.stray$setupPerspective(Camera.PROJECTION_Z_NEAR, savedFar, savedFov, windowW, windowH);
			if (savedFrustum != null) {
				access.stray$cullFrustum(savedFrustum);
			}
			//? if >=26.3 {
			camera.extractRenderState(state, delta);
			//?} else {
			/*camera.extractRenderState(state, partial);*///?}
			if (savedFog != null) {
				state.fogData = savedFog;
				((GameRendererAccessor) renderer).stray$fogRenderer().updateBuffer(savedFog);
			}
			if (projection != null) {
				RenderSystem.setProjectionMatrix(projection.getBuffer(savedProjection), ProjectionType.PERSPECTIVE);
			}
			capturing = false;
			subject = null;
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
		//? if >=26.3 {
		camera.extractRenderState(state, delta);
		//?} else {
		/*camera.extractRenderState(state, partial);*///?}
		if (state.projectionMatrix != null) {
			state.projectionMatrix.set(proj);
		}
		FogData povFog = fog.setupFog(
			camera,
			client.options.getEffectiveRenderDistance(),
			delta,
			renderer.bossOverlayWorldDarkening(partial),
			client.level
		);
		state.fogData = povFog;
		fog.updateBuffer(povFog);
		client.levelExtractor.extract(delta, camera, partial);
		renderer.gameRenderState().levelRenderState.shouldShowEntityOutlines = false;
		RenderTarget current = renderer.mainRenderTarget();
		if (current == null || current.getColorTexture() == null || current.getDepthTexture() == null) {
			return;
		}
		RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
			current.getColorTexture(),
			new Vector4f(povFog.color.x(), povFog.color.y(), povFog.color.z(), 1f),
			current.getDepthTexture(),
			0.0
		);
		RenderSystem.setProjectionMatrix(projection.getBuffer(proj), ProjectionType.PERSPECTIVE);
		client.levelRenderer.render(
			game.stray$resourcePool(),
			//? if <26.3 {
			/*delta,*///?}
			false,
			state,
			//? if <26.3 {
			/*new Matrix4f(state.viewRotationMatrix),*///?}
			fog.getBuffer(FogRenderer.FogMode.WORLD),
			povFog.color,
			true
			//? if >=26.3 {
			, false
			//?}
		);
	}

	private static MainTarget ensure(MainTarget target, int w, int h) {
		if (target != null && target.width == w && target.height == h && target.getColorTextureView() != null) {
			return target;
		}
		if (target != null) {
			target.destroyBuffers();
		}
		return new MainTarget(w, h);
	}

	/** Party members are looked up by their tab name; kept here so the view code stays small. */
	public static DungeonParty.Member member(int index) {
		LeapMenu.Entry entry = index >= 0 && index < 4 ? LeapMenu.players()[index] : null;
		return entry == null ? null : entry.player();
	}
}
