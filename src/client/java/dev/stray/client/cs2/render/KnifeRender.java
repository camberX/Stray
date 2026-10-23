package dev.stray.client.cs2.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.stray.Stray;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.cs2.Cs2Knives;
import dev.stray.client.cs2.s2.S2Model;
import dev.stray.client.visual.HeldItemShader;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Draws a CS2 knife mesh with its baked finish, skinned by per-bone matrices. */
public final class KnifeRender {
	private static final Identifier PIPELINE_ID = Stray.id("pipeline/cs2_knife");
	private static final Identifier SHADER_ID = Stray.id("core/cs2_knife");
	private static RenderPipeline pipeline;
	private static RenderType type;

	/** Source units (inches) to blocks for the first-person rig. Projection makes this mostly a depth choice. */
	public static final float VIEW_SCALE = 0.04f;

	private KnifeRender() {
	}

	private static synchronized RenderType type() {
		if (type != null) {
			return type;
		}
		pipeline = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
				.withLocation(PIPELINE_ID)
				.withVertexShader(SHADER_ID)
				.withFragmentShader(SHADER_ID)
				.withSampler("Sampler3")
				.withShaderDefine("NO_OVERLAY")
				.withShaderDefine("CS2_GPU_SKIN")
				.withUniform("KnifeBones", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
				.withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.TRIANGLES)
				.withCull(false)
				.build()
		);
		type = RenderType.create(
			"stray_cs2_knife",
			RenderSetup.builder(pipeline)
				.withTexture("Sampler0", Cs2Knives.ALBEDO, () -> RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR))
				.withTexture("Sampler3", Cs2Knives.MATERIAL, () -> RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR))
				.useLightmap()
				.affectsCrumbling()
				.setOutline(RenderSetup.OutlineProperty.NONE)
				.createRenderSetup()
		);
		return type;
	}

	/**
	 * Submits the knife. {@code toView} maps knife-model space (after skinning) into the
	 * current pose; {@code skin} holds one matrix per model bone (may be null for bind pose).
	 */
	public static void submit(PoseStack pose, SubmitNodeCollector collector, int light, Cs2Knives.Knife knife, Matrix4f toView, Matrix4f[] skin) {
		// The mesh lives on the GPU; the queue only carries this frame's bone matrices. Copies, since
		// the batch is built later in the frame.
		Matrix4f view = new Matrix4f(toView);
		Matrix4f[] bones = copy(skin);
		RenderType main = type();
		collector.submitCustomGeometry(pose, main, (p, consumer) -> {
			KnifeGpu.queue(main, KnifeGpu.mesh(knife), false, p.pose(), view, bones, light);
			placeholder(consumer, light);
		});
		if (StrayConfig.get().cs2BladeShader) {
			RenderType fill = HeldItemShader.bladeTypeFor(Cs2Knives.BLADE);
			if (fill != null) {
				collector.submitCustomGeometry(pose, fill, (p, consumer) -> {
					KnifeGpu.queue(fill, KnifeGpu.mesh(knife), true, p.pose(), view, bones, light);
					placeholder(consumer, light);
				});
			}
		}
	}

	private static Matrix4f[] copy(Matrix4f[] skin) {
		if (skin == null) {
			return null;
		}
		Matrix4f[] out = new Matrix4f[skin.length];
		for (int i = 0; i < skin.length; i++) {
			out[i] = new Matrix4f(skin[i]);
		}
		return out;
	}

	/** One empty triangle so the batch exists; the knife itself is drawn from its GPU buffers. */
	private static void placeholder(VertexConsumer consumer, int light) {
		for (int k = 0; k < 3; k++) {
			consumer.addVertex(0f, 0f, 0f, 0xFFFFFFFF, 0f, 0f, OverlayTexture.NO_OVERLAY, light, 0f, 1f, 0f);
		}
	}

	/** CS2 view space (x forward, y left, z up, inches) to Minecraft view space (x right, y up, -z forward, blocks). */
	public static Matrix4f sourceToView(float scale) {
		return new Matrix4f(
			0f, 0f, -scale, 0f,
			-scale, 0f, 0f, 0f,
			0f, scale, 0f, 0f,
			0f, 0f, 0f, 1f
		);
	}

	/**
	 * Third person: the pose is the item frame set up by ItemInHandLayer. Places the knife
	 * where a handheld sword's grip sits, blade along the sword, plus the player's grip tweak.
	 */
	public static Matrix4f thirdPerson(String knifeId, HumanoidArm arm) {
		Matrix4f m = new Matrix4f();
		// Vanilla handheld (sword) third-person display transform.
		m.translate(0f, 4f / 16f, 0.5f / 16f);
		m.rotateXYZ(0f, (float) Math.toRadians(-90), (float) Math.toRadians(55));
		m.scale(0.85f);
		m.translate(-0.5f, -0.5f, -0.5f);
		// Sword grip in item model space, blade toward the top right of the texture.
		m.translate(4.5f / 16f, 4.5f / 16f, 0.5f);
		Vector3f z = new Vector3f(1, 1, 0).normalize();
		Vector3f y = new Vector3f(0, 0, 1);
		Vector3f x = new Vector3f(y).cross(z);
		Matrix4f basis = new Matrix4f(
			x.x, x.y, x.z, 0f,
			y.x, y.y, y.z, 0f,
			z.x, z.y, z.z, 0f,
			0f, 0f, 0f, 1f
		);
		m.mul(basis);
		applyGrip(m, knifeId, 0.065f);
		return m;
	}

	/** Base scale for third person (Source units to blocks). */
	public static void applyGrip(Matrix4f m, String knifeId, float baseScale) {
		m.scale(baseScale);
	}
}
