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
		S2Model model = knife.model;
		collector.submitCustomGeometry(pose, type(), (p, consumer) -> emit(p, consumer, model, toView, skin, light, false));
		if (StrayConfig.get().cs2BladeShader) {
			RenderType fill = HeldItemShader.fillTypeFor(Cs2Knives.BLADE);
			if (fill != null) {
				collector.submitCustomGeometry(pose, fill, (p, consumer) -> emit(p, consumer, model, toView, skin, light, true));
			}
		}
	}

	private static void emit(PoseStack.Pose p, VertexConsumer consumer, S2Model model, Matrix4f toView, Matrix4f[] skin, int light, boolean quads) {
		Matrix4f m = new Matrix4f();
		Vector3f v = new Vector3f();
		Vector3f n = new Vector3f();
		for (S2Model.Part part : model.parts) {
			int count = part.vertexCount;
			float[] px = new float[count * 3];
			float[] nx = new float[count * 3];
			for (int i = 0; i < count; i++) {
				int bone = part.bones == null ? 0 : part.bones[i * 4];
				if (skin != null && bone < skin.length) {
					m.set(toView).mul(skin[bone]);
				} else {
					m.set(toView);
				}
				v.set(part.pos[i * 3], part.pos[i * 3 + 1], part.pos[i * 3 + 2]);
				m.transformPosition(v);
				px[i * 3] = v.x;
				px[i * 3 + 1] = v.y;
				px[i * 3 + 2] = v.z;
				if (part.normal != null) {
					n.set(part.normal[i * 3], part.normal[i * 3 + 1], part.normal[i * 3 + 2]);
					m.transformDirection(n).normalize();
				} else {
					n.set(0, 1, 0);
				}
				nx[i * 3] = n.x;
				nx[i * 3 + 1] = n.y;
				nx[i * 3 + 2] = n.z;
			}
			int[] idx = part.indices;
			for (int t = 0; t + 2 < idx.length; t += 3) {
				for (int k = 0; k < (quads ? 4 : 3); k++) {
					int i = idx[t + Math.min(k, 2)];
					float u = part.uv == null ? 0f : part.uv[i * 2];
					float w = part.uv == null ? 0f : part.uv[i * 2 + 1];
					consumer.addVertex(p, px[i * 3], px[i * 3 + 1], px[i * 3 + 2])
						.setColor(0xFFFFFFFF)
						.setUv(u, w)
						.setOverlay(OverlayTexture.NO_OVERLAY)
						.setLight(light)
						.setNormal(p, nx[i * 3], nx[i * 3 + 1], nx[i * 3 + 2]);
				}
			}
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
