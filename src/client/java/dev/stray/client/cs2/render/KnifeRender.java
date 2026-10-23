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
		collector.submitCustomGeometry(pose, type(), (p, consumer) -> emit(p, consumer, model, toView, skin, light, null));
		if (StrayConfig.get().cs2BladeShader) {
			RenderType fill = HeldItemShader.bladeTypeFor(Cs2Knives.BLADE);
			if (fill != null) {
				int[][] blade = knife.bladeTriangles();
				collector.submitCustomGeometry(pose, fill, (p, consumer) -> emit(p, consumer, model, toView, skin, light, blade));
			}
		}
	}

	// Reused between frames (rendering is single threaded).
	private static float[] posBuf = new float[0];
	private static float[] nrmBuf = new float[0];
	private static Matrix4f[] boneBuf = new Matrix4f[0];
	private static org.joml.Matrix3f[] boneNrm = new org.joml.Matrix3f[0];

	/**
	 * Skins every vertex once (one combined matrix per bone, pose included), then writes the
	 * triangles with the bulk vertex call. {@code triangles} null = all triangles as TRIANGLES;
	 * otherwise only those triangles, each as a degenerate quad (for the QUADS fill pass).
	 */
	private static void emit(PoseStack.Pose p, VertexConsumer consumer, S2Model model, Matrix4f toView, Matrix4f[] skin, int light, int[][] triangles) {
		int bones = skin == null ? 1 : Math.max(1, skin.length);
		if (boneBuf.length < bones) {
			boneBuf = new Matrix4f[bones];
			boneNrm = new org.joml.Matrix3f[bones];
			for (int b = 0; b < bones; b++) {
				boneBuf[b] = new Matrix4f();
				boneNrm[b] = new org.joml.Matrix3f();
			}
		}
		Matrix4f poseM = p.pose();
		org.joml.Matrix3f poseN = p.normal();
		for (int b = 0; b < bones; b++) {
			Matrix4f m = boneBuf[b].set(poseM).mul(toView);
			if (skin != null) {
				m.mul(skin[b]);
			}
			// Normals: pose normal matrix times the bone's rotation (uniform scale, renormalized below).
			boneNrm[b].set(poseN).mul(new org.joml.Matrix3f().set(toView).mul(skin == null ? new org.joml.Matrix3f() : new org.joml.Matrix3f().set(skin[b])));
		}
		boolean quads = triangles != null;
		for (int pi = 0; pi < model.parts.size(); pi++) {
			S2Model.Part part = model.parts.get(pi);
			int count = part.vertexCount;
			if (posBuf.length < count * 3) {
				posBuf = new float[count * 3];
				nrmBuf = new float[count * 3];
			}
			float[] px = posBuf;
			float[] nx = nrmBuf;
			float[] pos = part.pos;
			float[] nor = part.normal;
			int[] bonesOf = part.bones;
			for (int i = 0; i < count; i++) {
				int bone = bonesOf == null ? 0 : bonesOf[i * 4];
				if (bone >= bones) {
					bone = 0;
				}
				Matrix4f m = boneBuf[bone];
				float x = pos[i * 3];
				float y = pos[i * 3 + 1];
				float z = pos[i * 3 + 2];
				px[i * 3] = m.m00() * x + m.m10() * y + m.m20() * z + m.m30();
				px[i * 3 + 1] = m.m01() * x + m.m11() * y + m.m21() * z + m.m31();
				px[i * 3 + 2] = m.m02() * x + m.m12() * y + m.m22() * z + m.m32();
				float a;
				float b;
				float c;
				if (nor != null) {
					org.joml.Matrix3f nm = boneNrm[bone];
					float ox = nor[i * 3];
					float oy = nor[i * 3 + 1];
					float oz = nor[i * 3 + 2];
					a = nm.m00 * ox + nm.m10 * oy + nm.m20 * oz;
					b = nm.m01 * ox + nm.m11 * oy + nm.m21 * oz;
					c = nm.m02 * ox + nm.m12 * oy + nm.m22 * oz;
					float len = (float) Math.sqrt(a * a + b * b + c * c);
					if (len > 1e-8f) {
						a /= len;
						b /= len;
						c /= len;
					}
				} else {
					a = poseN.m10;
					b = poseN.m11;
					c = poseN.m12;
				}
				nx[i * 3] = a;
				nx[i * 3 + 1] = b;
				nx[i * 3 + 2] = c;
			}
			int[] idx = part.indices;
			float[] uv = part.uv;
			int overlay = OverlayTexture.NO_OVERLAY;
			if (!quads) {
				int end = idx.length - idx.length % 3;
				for (int t = 0; t < end; t++) {
					put(consumer, px, nx, uv, idx[t], light, overlay);
				}
			} else {
				int[] list = pi < triangles.length ? triangles[pi] : null;
				if (list == null) {
					continue;
				}
				for (int t : list) {
					put(consumer, px, nx, uv, idx[t], light, overlay);
					put(consumer, px, nx, uv, idx[t + 1], light, overlay);
					put(consumer, px, nx, uv, idx[t + 2], light, overlay);
					put(consumer, px, nx, uv, idx[t + 2], light, overlay);
				}
			}
		}
	}

	private static void put(VertexConsumer consumer, float[] px, float[] nx, float[] uv, int i, int light, int overlay) {
		int k = i * 3;
		consumer.addVertex(px[k], px[k + 1], px[k + 2], 0xFFFFFFFF,
			uv == null ? 0f : uv[i * 2], uv == null ? 0f : uv[i * 2 + 1],
			overlay, light, nx[k], nx[k + 1], nx[k + 2]);
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
