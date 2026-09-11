package dev.stray.client.visual;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
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
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Procedural galaxy, starfield, and Gargantua over the End skybox. The sky
 * shader is expensive (noise stacks plus a geodesic march), so it renders into a
 * half-resolution target and is upscaled onto the frame with premultiplied alpha.
 */
public final class EndSkyDecor {
	private static final float RADIUS = 100f;
	private static final int DOWNSCALE = 2;
	private static final Identifier BLIT_SHADER = Stray.id("post/end_sky_blit");
	private static RenderPipeline pipeline;
	private static RenderPipeline blitPipeline;
	private static TextureTarget lowRes;
	private static GpuBuffer cube;
	private static int indices;

	private EndSkyDecor() {
	}

	public static void init() {
		ensurePipeline();
	}

	public static void render(float time) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return;
		}
		ensure();
		if (pipeline == null || blitPipeline == null || cube == null || indices < 6) {
			return;
		}
		RenderTarget main = client.getMainRenderTarget();
		if (main == null || main.getColorTextureView() == null || main.width <= 0 || main.height <= 0) {
			return;
		}
		int w = Math.max(1, main.width / DOWNSCALE);
		int h = Math.max(1, main.height / DOWNSCALE);
		if (lowRes == null) {
			lowRes = new TextureTarget("stray end sky", w, h, false);
		} else if (lowRes.width != w || lowRes.height != h) {
			lowRes.resize(w, h);
		}
		if (lowRes.getColorTexture() == null || lowRes.getColorTextureView() == null) {
			return;
		}

		PoseStack pose = new PoseStack();
		pose.mulPose(Axis.YP.rotation(time * 0.0009f));
		pose.mulPose(Axis.XP.rotation(0.42f));
		float pulse = 0.92f + 0.08f * (0.5f + 0.5f * Mth.sin(time * 0.03f));
		Vector3f worldHole = new Vector3f(0.18f, 0.86f, 0.48f).normalize();
		Vector3f localHole = new Matrix4f(pose.last().pose()).invert().transformDirection(worldHole, new Vector3f());
		if (!Float.isFinite(localHole.x) || localHole.lengthSquared() < 1.0e-6f) {
			localHole.set(worldHole);
		} else {
			localHole.normalize();
		}
		var indexBuf = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
		Matrix4fStack modelView = RenderSystem.getModelViewStack();
		modelView.pushMatrix();
		modelView.mul(pose.last().pose());
		GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(
			modelView,
			new Vector4f(pulse, pulse, pulse, time * 0.012f),
			localHole,
			new Matrix4f()
		);
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		try {
			encoder.clearColorTexture(lowRes.getColorTexture(), 0);
			try (RenderPass pass = encoder.createRenderPass(
				() -> "stray end galaxy",
				lowRes.getColorTextureView(),
				OptionalInt.empty()
			)) {
				pass.setPipeline(pipeline);
				RenderSystem.bindDefaultUniforms(pass);
				pass.setUniform("DynamicTransforms", transform);
				pass.setVertexBuffer(0, cube);
				pass.setIndexBuffer(indexBuf.getBuffer(indices), indexBuf.type());
				pass.drawIndexed(0, 0, indices, 1);
			}
		} finally {
			modelView.popMatrix();
		}

		GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		try (RenderPass pass = encoder.createRenderPass(
			() -> "stray end galaxy blit",
			main.getColorTextureView(),
			OptionalInt.empty(),
			main.getDepthTextureView(),
			OptionalDouble.empty()
		)) {
			pass.setPipeline(blitPipeline);
			RenderSystem.bindDefaultUniforms(pass);
			pass.bindTexture("InSampler", lowRes.getColorTextureView(), linear);
			pass.draw(0, 3);
		}
	}

	private static void ensure() {
		ensurePipeline();
		if (cube == null) {
			cube = bakeCube();
		}
	}

	private static synchronized void ensurePipeline() {
		if (pipeline == null) {
			pipeline = RenderPipelines.register(
				RenderPipeline.builder()
					.withLocation(Stray.id("pipeline/end_galaxy"))
					.withVertexShader(Stray.id("core/end_galaxy"))
					.withFragmentShader(Stray.id("core/end_galaxy"))
					.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
					.withUniform("Projection", UniformType.UNIFORM_BUFFER)
					.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
					.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
					.withCull(false)
					.withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
					.build()
			);
		}
		if (blitPipeline == null) {
			blitPipeline = RenderPipelines.register(
				RenderPipeline.builder()
					.withLocation(Stray.id("pipeline/end_sky_blit"))
					.withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
					.withFragmentShader(BLIT_SHADER)
					.withSampler("InSampler")
					.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
					.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
					.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
					.build()
			);
		}
	}

	private static GpuBuffer bakeCube() {
		try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(24 * DefaultVertexFormat.POSITION.getVertexSize())) {
			BufferBuilder buf = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
			face(buf, 1f, 0f, 0f);
			face(buf, -1f, 0f, 0f);
			face(buf, 0f, 1f, 0f);
			face(buf, 0f, -1f, 0f);
			face(buf, 0f, 0f, 1f);
			face(buf, 0f, 0f, -1f);
			try (MeshData mesh = buf.buildOrThrow()) {
				indices = mesh.drawState().indexCount();
				return RenderSystem.getDevice().createBuffer(
					() -> "stray end galaxy cube",
					GpuBuffer.USAGE_VERTEX,
					mesh.vertexBuffer()
				);
			}
		}
	}

	private static void face(BufferBuilder buf, float nx, float ny, float nz) {
		Vector3f n = new Vector3f(nx, ny, nz);
		Vector3f t = Math.abs(ny) > 0.5f ? new Vector3f(1f, 0f, 0f) : new Vector3f(0f, 1f, 0f);
		Vector3f b = new Vector3f(n).cross(t).normalize();
		t = new Vector3f(b).cross(n).normalize();
		vert(buf, n, t, b, -1f, -1f);
		vert(buf, n, t, b, -1f, 1f);
		vert(buf, n, t, b, 1f, 1f);
		vert(buf, n, t, b, 1f, -1f);
	}

	private static void vert(BufferBuilder buf, Vector3f n, Vector3f t, Vector3f b, float u, float v) {
		buf.addVertex(
			(n.x + t.x * u + b.x * v) * RADIUS,
			(n.y + t.y * u + b.y * v) * RADIUS,
			(n.z + t.z * u + b.z * v) * RADIUS
		);
	}
}
