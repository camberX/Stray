package dev.stray.client.visual;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import dev.stray.Stray;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Stars and galaxy dust for the End skybox. Vanilla star draw uses overlay
 * blend, which vanishes on the End texture, so this uses the End sky
 * translucent pass with soft circle sprites.
 */
public final class EndSkyDecor {
	private static final Identifier SPRITE = Stray.id("textures/gui/circle.png");
	private static final float RADIUS = 82f;
	private static final int[] STAR_COLORS = {
		0xFFFFFFFF,
		0xFFE8F2FF,
		0xFFC8E6FF,
		0xFFFFF0D4,
		0xFFFFD0E8,
		0xFFB8FFF0
	};
	private static final int[] GALAXY_COLORS = {
		0x88C070FF,
		0x7780A8FF,
		0x90FF6BA8,
		0x66FFE08A,
		0x80A070FF,
		0x70FF9AD4,
		0x5C6EC8FF
	};

	private static GpuBuffer stars;
	private static GpuBuffer galaxy;
	private static int starIndices;
	private static int galaxyIndices;

	private EndSkyDecor() {
	}

	public static void render(float time) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return;
		}
		ensure();
		if (stars == null || galaxy == null) {
			return;
		}
		float twinkle = 0.88f + 0.12f * (0.5f + 0.5f * Mth.sin(time * 0.045f));
		PoseStack pose = new PoseStack();
		pose.mulPose(Axis.YP.rotation(time * 0.0014f));
		pose.mulPose(Axis.XP.rotation(0.48f + time * 0.00022f));
		draw(galaxy, galaxyIndices, pose, 1f);
		pose.pushPose();
		pose.mulPose(Axis.YP.rotation(1.85f));
		pose.mulPose(Axis.XP.rotation(-0.55f));
		draw(galaxy, galaxyIndices, pose, 0.72f);
		pose.popPose();
		pose.mulPose(Axis.ZP.rotation(0.18f));
		draw(stars, starIndices, pose, twinkle);
	}

	private static void ensure() {
		if (stars != null && galaxy != null) {
			return;
		}
		Mesh starsMesh = bakeStars();
		Mesh galaxyMesh = bakeGalaxy();
		if (starsMesh == null || galaxyMesh == null) {
			return;
		}
		stars = starsMesh.buffer();
		starIndices = starsMesh.indices();
		galaxy = galaxyMesh.buffer();
		galaxyIndices = galaxyMesh.indices();
	}

	private static Mesh bakeStars() {
		RandomSource random = RandomSource.create(0x51A4F1E1L);
		int want = 1100;
		try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(want * 4 * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize())) {
			BufferBuilder buf = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
			int made = 0;
			int attempts = 0;
			while (made < want && attempts < want * 8) {
				attempts++;
				float x = random.nextFloat() * 2f - 1f;
				float y = random.nextFloat() * 2f - 1f;
				float z = random.nextFloat() * 2f - 1f;
				if (Mth.lengthSquared(x, y, z) < 0.08f || Mth.lengthSquared(x, y, z) > 1f) {
					continue;
				}
				float size = 0.55f + random.nextFloat() * 2.1f;
				if (random.nextFloat() < 0.08f) {
					size *= 1.8f;
				}
				quad(buf, x, y, z, size, size, random.nextFloat() * Mth.TWO_PI, STAR_COLORS[random.nextInt(STAR_COLORS.length)]);
				made++;
			}
			return upload(buf, "stray end sky stars");
		}
	}

	private static Mesh bakeGalaxy() {
		RandomSource random = RandomSource.create(0xC0FFEE11L);
		int want = 220;
		try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(want * 4 * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize())) {
			BufferBuilder buf = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
			for (int i = 0; i < want; i++) {
				float t = i / (float) want;
				float a = t * Mth.TWO_PI + random.nextFloat() * 0.18f;
				float wobble = (random.nextFloat() - 0.5f) * 0.38f;
				float x = Mth.cos(a);
				float y = Mth.sin(a) * 0.28f + wobble;
				float z = Mth.sin(a);
				float wide = 9.5f + random.nextFloat() * 20f;
				float tall = 2.8f + random.nextFloat() * 5.5f;
				if (i % 11 == 0) {
					wide *= 1.6f;
					tall *= 1.35f;
				}
				quad(buf, x, y, z, wide, tall, a + 1.2f, GALAXY_COLORS[random.nextInt(GALAXY_COLORS.length)]);
			}
			return upload(buf, "stray end sky galaxy");
		}
	}

	private static void quad(BufferBuilder buf, float x, float y, float z, float rx, float ry, float roll, int argb) {
		Vector3f dir = new Vector3f(x, y, z).normalize(RADIUS);
		Matrix3f basis = new Matrix3f()
			.rotateTowards(new Vector3f(dir).negate(), new Vector3f(0f, 1f, 0f))
			.rotateZ(-roll);
		vert(buf, basis, dir, rx, -ry, 1f, 0f, argb);
		vert(buf, basis, dir, rx, ry, 1f, 1f, argb);
		vert(buf, basis, dir, -rx, ry, 0f, 1f, argb);
		vert(buf, basis, dir, -rx, -ry, 0f, 0f, argb);
	}

	private static void vert(BufferBuilder buf, Matrix3f basis, Vector3f dir, float ox, float oy, float u, float v, int argb) {
		Vector3f at = new Vector3f(ox, oy, 0f).mul(basis).add(dir);
		buf.addVertex(at).setUv(u, v).setColor(argb);
	}

	private static Mesh upload(BufferBuilder buf, String label) {
		try (MeshData mesh = buf.buildOrThrow()) {
			int indices = mesh.drawState().indexCount();
			GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> label, GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
			return new Mesh(buffer, indices);
		}
	}

	private static void draw(GpuBuffer buffer, int indices, PoseStack pose, float brightness) {
		if (indices < 6) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		AbstractTexture texture = client.getTextureManager().getTexture(SPRITE);
		if (texture == null || texture.getTextureView() == null) {
			return;
		}
		RenderTarget target = client.getMainRenderTarget();
		if (target == null || target.getColorTextureView() == null) {
			return;
		}
		var indicesBuf = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
		Matrix4fStack modelView = RenderSystem.getModelViewStack();
		modelView.pushMatrix();
		modelView.mul(pose.last().pose());
		GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(
			modelView,
			new Vector4f(brightness, brightness, brightness, brightness),
			new Vector3f(),
			new Matrix4f()
		);
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
			() -> "stray end sky decor",
			target.getColorTextureView(),
			java.util.OptionalInt.empty(),
			target.getDepthTextureView(),
			java.util.OptionalDouble.empty()
		)) {
			pass.setPipeline(RenderPipelines.END_SKY);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transform);
			pass.bindTexture("Sampler0", texture.getTextureView(), texture.getSampler());
			pass.setVertexBuffer(0, buffer);
			pass.setIndexBuffer(indicesBuf.getBuffer(indices), indicesBuf.type());
			pass.drawIndexed(0, 0, indices, 1);
		} finally {
			modelView.popMatrix();
		}
	}

	private record Mesh(GpuBuffer buffer, int indices) {
	}
}
