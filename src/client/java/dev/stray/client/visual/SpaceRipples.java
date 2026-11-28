package dev.stray.client.visual;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
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
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Footstep ripples: when you or anyone else walks, a ring of light in the chosen colour spreads
 * from each step and fades. Client-side only, both editions.
 */
public final class SpaceRipples {
	private static final int MAX = 64;
	/** Blocks walked between two ripples. */
	private static final double STEP = 1.5;
	private static final int LIFE = 34;
	private static final float START = 0.25f;
	private static final float END = 2.3f;
	private static final double RANGE = 48.0;

	private static final double[] X = new double[MAX];
	private static final double[] Y = new double[MAX];
	private static final double[] Z = new double[MAX];
	private static final int[] AGE = new int[MAX];
	private static int count;
	/** Per player id: last x, z and the distance walked since the last ripple. */
	private static final Map<Integer, double[]> WALKED = new HashMap<>();
	private static RenderType type;

	private SpaceRipples() {
	}

	public static void init() {
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
			if (count == 0 || context.poseStack() == null || !StrayShaders.ready() || !StrayConfig.get().footstepRipples) {
				return;
			}
			Vec3 eye = context.levelState().cameraRenderState.pos;
			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), renderType(),
				(pose, consumer) -> emit((x, y, z, u, v, argb) -> consumer.addVertex(pose, x, y, z).setUv(u, v).setColor(argb), eye));
		});
	}

	static boolean any() {
		return count > 0;
	}

	/** Writes this frame's quads (camera-relative) into {@code sink}. */
	static void emit(SpaceRoofGpu.Sink sink, Vec3 eye) {
		float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		int tint = StrayConfig.get().footstepRipplesRgb & 0xFFFFFF;
		int n = count;
		for (int i = 0; i < n; i++) {
			float p = Math.min(1f, (AGE[i] + partial) / LIFE);
			float ease = 1f - (1f - p) * (1f - p) * (1f - p);
			float r = START + (END - START) * ease;
			float alpha = (float) Math.pow(1f - p, 1.5f);
			int argb = (int) (alpha * 255f) << 24 | tint;
			float x = (float) (X[i] - eye.x);
			float y = (float) (Y[i] - eye.y);
			float z = (float) (Z[i] - eye.z);
			sink.vertex(x - r, y, z - r, -1f, -1f, argb);
			sink.vertex(x + r, y, z - r, 1f, -1f, argb);
			sink.vertex(x + r, y, z + r, 1f, 1f, argb);
			sink.vertex(x - r, y, z + r, -1f, 1f, argb);
		}
	}


	static void clear() {
		count = 0;
		WALKED.clear();
	}

	/** Client tick: ages the ripples and starts one for every player who walked a step on a floor under the sky. */
	public static void tick(Minecraft client) {
		if (!StrayConfig.get().footstepRipples || client.level == null) {
			clear();
			return;
		}
		for (int i = 0; i < count; i++) {
			if (++AGE[i] >= LIFE) {
				int last = --count;
				X[i] = X[last];
				Y[i] = Y[last];
				Z[i] = Z[last];
				AGE[i] = AGE[last];
				i--;
			}
		}
		Vec3 eye = client.gameRenderer.mainCamera().position();
		Map<Integer, double[]> seen = new HashMap<>();
		for (Player player : client.level.players()) {
			if (player.isSpectator() || player.isInvisible() || player.distanceToSqr(eye) > RANGE * RANGE) {
				continue;
			}
			double[] last = WALKED.get(player.getId());
			double px = player.getX();
			double pz = player.getZ();
			if (last == null) {
				last = new double[] {px, pz, 0.0};
			} else {
				last[2] += Math.hypot(px - last[0], pz - last[1]);
				last[0] = px;
				last[1] = pz;
			}
			seen.put(player.getId(), last);
			if (last[2] < STEP) {
				continue;
			}
			if (!player.onGround()) {
				continue;
			}
			last[2] = 0.0;
			if (count >= MAX) {
				continue;
			}
			int i = count++;
			X[i] = px;
			Y[i] = player.getY() + 0.02;
			Z[i] = pz;
			AGE[i] = 0;
		}
		WALKED.clear();
		WALKED.putAll(seen);
	}

	static synchronized RenderType renderType() {
		if (type == null) {
			RenderPipeline pipeline = RenderPipelinesInvoker.stray$register(RenderPipeline.builder()
				.withLocation(Stray.id("pipeline/space_ripple"))
				.withVertexShader(Stray.id("core/space_roof"))
				.withFragmentShader(Stray.id("core/space_ripple"))
				.withBindGroupLayout(BindGroupLayouts.GLOBALS)
				.withBindGroupLayout(BindGroupLayouts.PROJECTION)
				.withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
				.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
				.withPrimitiveTopology(PrimitiveTopology.QUADS)
				.withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
				.withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
				.withCull(false)
				.build());
			type = RenderTypeAccessor.stray$create("stray_space_ripple", RenderSetup.builder(pipeline).createRenderSetup());
		}
		return type;
	}
}
