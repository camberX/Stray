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
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Space Roof's rising debris: a few small stone pieces near the sky drift slowly up into it,
 * turning, with edges glowing in its colour, and shrink away as they reach it. Client-side only.
 */
final class SpaceDebris {
	private static final int MAX = 28;
	/** Pieces started per tick (when there's sky near you to rise into). */
	private static final float RATE = 0.06f;
	private static final double RADIUS = 20.0;
	/** Shrinks away over this last stretch under the sky. */
	private static final double ABSORB = 0.8;

	private static final double[] X = new double[MAX];
	private static final double[] Y = new double[MAX];
	private static final double[] Z = new double[MAX];
	private static final double[] PY = new double[MAX];
	private static final float[] SPEED = new float[MAX];
	private static final float[] SIZE = new float[MAX];
	private static final float[] SPIN = new float[MAX];
	private static final float[] AX = new float[MAX];
	private static final float[] AY = new float[MAX];
	private static final float[] AZ = new float[MAX];
	private static final int[] TOP = new int[MAX];
	private static final int[] AGE = new int[MAX];
	private static int count;
	private static float budget;
	private static final Random RANDOM = new Random();
	private static RenderType type;

	/** Cube faces: normal, then two edge directions; shade per face like vanilla blocks. */
	private static final float[][] FACE = {
		{0, 1, 0, 1, 0, 0, 0, 0, 1}, {0, -1, 0, 1, 0, 0, 0, 0, -1},
		{1, 0, 0, 0, 0, 1, 0, 1, 0}, {-1, 0, 0, 0, 0, -1, 0, 1, 0},
		{0, 0, 1, -1, 0, 0, 0, 1, 0}, {0, 0, -1, 1, 0, 0, 0, 1, 0}
	};
	private static final float[] SHADE = {1.0f, 0.5f, 0.6f, 0.6f, 0.8f, 0.8f};

	private SpaceDebris() {
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
		float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		int tint = StrayConfig.get().spaceRoofRgb & 0xFFFFFF;
		int n = count;
		Quaternionf turn = new Quaternionf();
		Vector3f a = new Vector3f();
		Vector3f b = new Vector3f();
		Vector3f c = new Vector3f();
		for (int i = 0; i < n; i++) {
			double y = PY[i] + (Y[i] - PY[i]) * partial;
			double left = TOP[i] - y;
			float s = SIZE[i] * (float) Math.clamp(left / ABSORB, 0.0, 1.0) * Math.min(1f, (AGE[i] + partial) / 20f);
			if (s <= 0.002f) {
				continue;
			}
			float angle = (AGE[i] + partial) * SPIN[i];
			turn.identity().rotateAxis(angle, AX[i], AY[i], AZ[i]);
			float cx = (float) (X[i] - eye.x);
			float cy = (float) (y - eye.y);
			float cz = (float) (Z[i] - eye.z);
			for (int f = 0; f < 6; f++) {
				float[] d = FACE[f];
				turn.transform(a.set(d[0], d[1], d[2]).mul(s));
				turn.transform(b.set(d[3], d[4], d[5]).mul(s));
				turn.transform(c.set(d[6], d[7], d[8]).mul(s));
				int argb = (int) (SHADE[f] * 255f) << 24 | tint;
				sink.vertex(cx + a.x - b.x - c.x, cy + a.y - b.y - c.y, cz + a.z - b.z - c.z, 0f, 0f, argb);
				sink.vertex(cx + a.x + b.x - c.x, cy + a.y + b.y - c.y, cz + a.z + b.z - c.z, 1f, 0f, argb);
				sink.vertex(cx + a.x + b.x + c.x, cy + a.y + b.y + c.y, cz + a.z + b.z + c.z, 1f, 1f, argb);
				sink.vertex(cx + a.x - b.x + c.x, cy + a.y - b.y + c.y, cz + a.z - b.z + c.z, 0f, 1f, argb);
			}
		}
	}


	private static void corner(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, float u, float v, int argb) {
		consumer.addVertex(pose, x, y, z).setUv(u, v).setColor(argb);
	}

	static void clear() {
		count = 0;
		budget = 0f;
	}

	/** Client tick: rises the pieces and starts new ones in the air a few blocks under the sky near you. */
	static void tick(Minecraft client) {
		if (!StrayConfig.get().spaceRoofDebris || client.level == null) {
			clear();
			return;
		}
		ClientLevel level = client.level;
		Vec3 eye = client.gameRenderer.mainCamera().position();
		for (int i = 0; i < count; i++) {
			PY[i] = Y[i];
			AGE[i]++;
			Y[i] += SPEED[i];
			double dx = X[i] - eye.x;
			double dz = Z[i] - eye.z;
			if (Y[i] >= TOP[i] || dx * dx + dz * dz > (RADIUS + 16) * (RADIUS + 16)) {
				remove(i--);
			}
		}
		budget = Math.min(budget + RATE, 2f);
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		float fade = Math.max(2f, StrayConfig.get().spaceRoofFade);
		for (int tries = 0; tries < 4 && budget >= 1f && count < MAX; tries++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2.0;
			double r = RADIUS * Math.sqrt(RANDOM.nextDouble());
			double x = eye.x + Math.cos(angle) * r;
			double z = eye.z + Math.sin(angle) * r;
			int top = SpaceRoof.skyAbove((int) Math.floor(x), eye.y - 6.0, (int) Math.floor(z));
			if (top == Integer.MIN_VALUE || top > eye.y + 32) {
				continue;
			}
			budget -= 1f;
			double y = top - 1.2 - RANDOM.nextDouble() * (fade + 2.0);
			net.minecraft.world.level.block.state.BlockState there = level.getBlockState(at.set(Math.floor(x), Math.floor(y), Math.floor(z)));
			if (!there.isAir() && !SpaceRoof.invisible(there)) {
				continue;
			}
			int i = count++;
			X[i] = x;
			Y[i] = PY[i] = y;
			Z[i] = z;
			TOP[i] = top;
			SPEED[i] = 0.008f + RANDOM.nextFloat() * 0.012f;
			SIZE[i] = 0.05f + RANDOM.nextFloat() * 0.07f;
			SPIN[i] = (0.01f + RANDOM.nextFloat() * 0.03f) * (RANDOM.nextBoolean() ? 1f : -1f);
			Vector3f axis = new Vector3f(RANDOM.nextFloat() - 0.5f, RANDOM.nextFloat() - 0.5f, RANDOM.nextFloat() - 0.5f);
			if (axis.lengthSquared() < 1e-4f) {
				axis.set(0f, 1f, 0f);
			}
			axis.normalize();
			AX[i] = axis.x;
			AY[i] = axis.y;
			AZ[i] = axis.z;
			AGE[i] = 0;
		}
	}

	private static void remove(int i) {
		int last = --count;
		if (i == last) {
			return;
		}
		X[i] = X[last];
		Y[i] = Y[last];
		Z[i] = Z[last];
		PY[i] = PY[last];
		SPEED[i] = SPEED[last];
		SIZE[i] = SIZE[last];
		SPIN[i] = SPIN[last];
		AX[i] = AX[last];
		AY[i] = AY[last];
		AZ[i] = AZ[last];
		TOP[i] = TOP[last];
		AGE[i] = AGE[last];
	}

	static synchronized RenderType renderType() {
		if (type == null) {
			RenderPipeline pipeline = RenderPipelinesInvoker.stray$register(RenderPipeline.builder()
				.withLocation(Stray.id("pipeline/space_debris"))
				.withVertexShader(Stray.id("core/space_roof"))
				.withFragmentShader(Stray.id("core/space_debris"))
				.withBindGroupLayout(BindGroupLayouts.GLOBALS)
				.withBindGroupLayout(BindGroupLayouts.PROJECTION)
				.withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
				.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
				.withPrimitiveTopology(PrimitiveTopology.QUADS)
				// Solid little blocks: depth tested and written like vanilla (reversed depth).
				.withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), true))
				.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
				.withCull(false)
				.build());
			type = RenderTypeAccessor.stray$create("stray_space_debris", RenderSetup.builder(pipeline).createRenderSetup());
			SpaceRoofGpu.own(type);
		}
		return type;
	}
}
