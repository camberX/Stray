package dev.stray.client.visual;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.stray.Stray;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.mixin.RenderPipelinesInvoker;
import dev.stray.client.mixin.RenderTypeAccessor;
import dev.stray.client.render.StrayShaders;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3fc;

import java.util.Random;

/**
 * Space Roof's falling stardust: small glowing specks in the sky's colour that come out of the
 * sky layers near you, drift down slowly with a little sway, twinkle, and fade out on the floor
 * or on the way. Client-side only, a fixed pool, one draw call.
 */
final class SpaceDust {
	private static final int MAX = 220;
	/** Specks started per tick under a fully covered sky around you. */
	private static final float RATE = 0.9f;
	/** Spawn area: blocks around the camera, and how far below the camera a layer may be. */
	private static final double RADIUS = 22.0;
	private static final double BELOW = 6.0;
	private static final double ABOVE = 32.0;
	private static final int FADE_IN = 14;
	private static final int FADE_OUT = 40;
	private static final int LANDED_FADE = 16;

	private static final double[] X = new double[MAX];
	private static final double[] Y = new double[MAX];
	private static final double[] Z = new double[MAX];
	private static final double[] PX = new double[MAX];
	private static final double[] PY = new double[MAX];
	private static final double[] PZ = new double[MAX];
	private static final float[] SPEED = new float[MAX];
	private static final float[] SEED = new float[MAX];
	private static final float[] SIZE = new float[MAX];
	/** How much of the sky's colour (the rest is white). */
	private static final float[] TINT = new float[MAX];
	private static final int[] AGE = new int[MAX];
	private static final int[] LIFE = new int[MAX];
	private static final boolean[] LANDED = new boolean[MAX];
	private static int count;
	private static float budget;
	private static final Random RANDOM = new Random();

	private static RenderType type;

	private SpaceDust() {
	}

	static void init() {
		// Only when the GPU draw doesn't work: then each kind goes through Minecraft's queue on its own.
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
			if (!SpaceRoofGpu.broken() || count == 0 || context.poseStack() == null || !StrayShaders.ready() || !SpaceRoof.active() || SpaceRoof.hidden(8)) {
				return;
			}
			Vec3 eye = context.levelState().cameraRenderState.pos;
			context.submitNodeCollector().order(2).submitCustomGeometry(context.poseStack(), renderType(),
				(pose, consumer) -> emit((x, y, z, u, v, argb) -> consumer.addVertex(pose, x, y, z).setUv(u, v).setColor(argb), eye));
		});
	}

	static boolean any() {
		return count > 0;
	}

	/** Writes this frame's quads (camera-relative) into {@code sink}. */
	static void emit(SpaceRoofGpu.Sink sink, Vec3 eye) {
		Minecraft client = Minecraft.getInstance();
		Camera camera = client.gameRenderer.mainCamera();
		Vector3fc left = camera.leftVector();
		Vector3fc up = camera.upVector();
		float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		int rgb = StrayConfig.get().spaceRoofRgb & 0xFFFFFF;
		float tr = (rgb >> 16 & 0xFF) / 255f;
		float tg = (rgb >> 8 & 0xFF) / 255f;
		float tb = (rgb & 0xFF) / 255f;
		int n = count;
		for (int i = 0; i < n; i++) {
			float alpha = alpha(i, partial);
			if (alpha <= 0.01f) {
				continue;
			}
			float x = (float) (PX[i] + (X[i] - PX[i]) * partial - eye.x);
			float y = (float) (PY[i] + (Y[i] - PY[i]) * partial - eye.y);
			float z = (float) (PZ[i] + (Z[i] - PZ[i]) * partial - eye.z);
			float s = SIZE[i];
			float t = TINT[i];
			int color = (int) (alpha * 255f) << 24
				| (int) ((1f - t + tr * t) * 255f) << 16
				| (int) ((1f - t + tg * t) * 255f) << 8
				| (int) ((1f - t + tb * t) * 255f);
			float lx = left.x() * s;
			float ly = left.y() * s;
			float lz = left.z() * s;
			float ux = up.x() * s;
			float uy = up.y() * s;
			float uz = up.z() * s;
			sink.vertex(x + lx + ux, y + ly + uy, z + lz + uz, -1f, 1f, color);
			sink.vertex(x - lx + ux, y - ly + uy, z - lz + uz, 1f, 1f, color);
			sink.vertex(x - lx - ux, y - ly - uy, z - lz - uz, 1f, -1f, color);
			sink.vertex(x + lx - ux, y + ly - uy, z + lz - uz, -1f, -1f, color);
		}
	}


	private static void corner(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, float u, float v, int argb) {
		consumer.addVertex(pose, x, y, z).setUv(u, v).setColor(argb);
	}

	/** Fades in, twinkles, fades out at the end of its life (fast once it has landed). */
	private static float alpha(int i, float partial) {
		float age = AGE[i] + partial;
		float a = Math.min(1f, age / FADE_IN);
		float left = LIFE[i] - age;
		a *= Math.clamp(left / (LANDED[i] ? LANDED_FADE : FADE_OUT), 0f, 1f);
		float twinkle = 0.72f + 0.28f * (float) Math.sin(age * (0.18f + 0.22f * SEED[i]) + SEED[i] * 40f);
		return a * twinkle;
	}

	static void clear() {
		count = 0;
		budget = 0f;
	}

	/** Client tick (only while Space Roof is on): moves the specks and starts new ones under the sky near you. */
	static void tick(Minecraft client) {
		if (!StrayConfig.get().spaceRoofStardust || client.level == null) {
			clear();
			return;
		}
		ClientLevel level = client.level;
		Vec3 eye = client.gameRenderer.mainCamera().position();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int i = 0; i < count; i++) {
			PX[i] = X[i];
			PY[i] = Y[i];
			PZ[i] = Z[i];
			AGE[i]++;
			double dx = X[i] - eye.x;
			double dz = Z[i] - eye.z;
			if (AGE[i] >= LIFE[i] || dx * dx + dz * dz > (RADIUS + 16) * (RADIUS + 16)) {
				remove(i--);
				continue;
			}
			if (LANDED[i]) {
				continue;
			}
			float age = AGE[i];
			float seed = SEED[i];
			double nx = X[i] + Math.sin(age * 0.045 + seed * 6.283) * 0.012;
			double ny = Y[i] - SPEED[i];
			double nz = Z[i] + Math.cos(age * 0.037 + seed * 3.1) * 0.012;
			if (solid(level, at, nx, ny, nz)) {
				// Settles where it touched and fades out there.
				LANDED[i] = true;
				LIFE[i] = Math.min(LIFE[i], AGE[i] + LANDED_FADE);
				continue;
			}
			X[i] = nx;
			Y[i] = ny;
			Z[i] = nz;
		}
		budget = Math.min(budget + RATE, 4f);
		for (int tries = 0; tries < 8 && budget >= 1f && count < MAX; tries++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2.0;
			double r = RADIUS * Math.sqrt(RANDOM.nextDouble());
			double x = eye.x + Math.cos(angle) * r;
			double z = eye.z + Math.sin(angle) * r;
			int bx = (int) Math.floor(x);
			int bz = (int) Math.floor(z);
			int top = SpaceRoof.skyAbove(bx, eye.y - BELOW, bz);
			if (top == Integer.MIN_VALUE || top > eye.y + ABOVE) {
				continue;
			}
			budget -= 1f;
			double y = top - 0.15 - RANDOM.nextDouble() * 0.5;
			if (solid(level, at, x, y, z)) {
				continue;
			}
			int i = count++;
			X[i] = PX[i] = x;
			Y[i] = PY[i] = y;
			Z[i] = PZ[i] = z;
			SPEED[i] = 0.022f + RANDOM.nextFloat() * 0.03f;
			SEED[i] = RANDOM.nextFloat();
			boolean star = RANDOM.nextFloat() < 0.15f;
			SIZE[i] = star ? 0.16f + RANDOM.nextFloat() * 0.06f : 0.08f + RANDOM.nextFloat() * 0.06f;
			TINT[i] = star ? 0.15f : 0.4f + RANDOM.nextFloat() * 0.45f;
			AGE[i] = 0;
			LIFE[i] = 180 + RANDOM.nextInt(200);
			LANDED[i] = false;
		}
	}

	/** Inside a block's collision shape (not air or barriers; glass counts as solid so specks land on it). */
	private static boolean solid(ClientLevel level, BlockPos.MutableBlockPos at, double x, double y, double z) {
		at.set(Math.floor(x), Math.floor(y), Math.floor(z));
		BlockState state = level.getBlockState(at);
		if (state.isAir() || SpaceRoof.invisible(state)) {
			return false;
		}
		VoxelShape shape = state.getCollisionShape(level, at);
		if (shape.isEmpty()) {
			return false;
		}
		double lx = x - at.getX();
		double ly = y - at.getY();
		double lz = z - at.getZ();
		for (AABB box : shape.toAabbs()) {
			if (lx >= box.minX && lx <= box.maxX && ly >= box.minY && ly <= box.maxY && lz >= box.minZ && lz <= box.maxZ) {
				return true;
			}
		}
		return false;
	}

	/** Swap-remove. */
	private static void remove(int i) {
		int last = --count;
		if (i == last) {
			return;
		}
		X[i] = X[last];
		Y[i] = Y[last];
		Z[i] = Z[last];
		PX[i] = PX[last];
		PY[i] = PY[last];
		PZ[i] = PZ[last];
		SPEED[i] = SPEED[last];
		SEED[i] = SEED[last];
		SIZE[i] = SIZE[last];
		TINT[i] = TINT[last];
		AGE[i] = AGE[last];
		LIFE[i] = LIFE[last];
		LANDED[i] = LANDED[last];
	}

	static synchronized RenderType renderType() {
		if (type == null) {
			RenderPipeline pipeline = RenderPipelinesInvoker.stray$register(RenderPipeline.builder()
				.withLocation(Stray.id("pipeline/space_dust"))
				.withVertexShader(Stray.id("core/space_roof"))
				.withFragmentShader(Stray.id("core/space_dust"))
				.withBindGroupLayout(BindGroupLayouts.GLOBALS)
				.withBindGroupLayout(BindGroupLayouts.PROJECTION)
				.withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
				.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
				.withPrimitiveTopology(PrimitiveTopology.QUADS)
				// Hidden behind blocks like vanilla (reversed depth), but glows additively and
				// doesn't hide what's behind it.
				.withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
				.withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
				.withCull(false)
				.build());
			type = RenderTypeAccessor.stray$create("stray_space_dust", RenderSetup.builder(pipeline).createRenderSetup());
			SpaceRoofGpu.own(type);
		}
		return type;
	}
}
