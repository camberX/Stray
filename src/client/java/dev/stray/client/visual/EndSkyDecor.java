package dev.stray.client.visual;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
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
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Procedural galaxy + starfield over the End skybox. A fragment shader paints
 * noise nebula and hashed stars on a sky cube so it does not look like GUI
 * circles stamped on the dome.
 */
public final class EndSkyDecor {
	private static final float RADIUS = 100f;
	private static RenderPipeline pipeline;
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
		if (pipeline == null || cube == null || indices < 6) {
			return;
		}
		RenderTarget target = client.getMainRenderTarget();
		if (target == null || target.getColorTextureView() == null) {
			return;
		}
		PoseStack pose = new PoseStack();
		pose.mulPose(Axis.YP.rotation(time * 0.0009f));
		pose.mulPose(Axis.XP.rotation(0.42f));
		float pulse = 0.92f + 0.08f * (0.5f + 0.5f * Mth.sin(time * 0.03f));
		var indexBuf = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
		Matrix4fStack modelView = RenderSystem.getModelViewStack();
		modelView.pushMatrix();
		modelView.mul(pose.last().pose());
		GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(
			modelView,
			new Vector4f(pulse, pulse, pulse, 1f),
			new Vector3f(time * 0.012f, 0f, 0f),
			new Matrix4f()
		);
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
			() -> "stray end galaxy",
			target.getColorTextureView(),
			java.util.OptionalInt.empty(),
			target.getDepthTextureView(),
			java.util.OptionalDouble.empty()
		)) {
			pass.setPipeline(pipeline);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transform);
			pass.setVertexBuffer(0, cube);
			pass.setIndexBuffer(indexBuf.getBuffer(indices), indexBuf.type());
			pass.drawIndexed(0, 0, indices, 1);
		} finally {
			modelView.popMatrix();
		}
	}

	private static void ensure() {
		ensurePipeline();
		if (cube == null) {
			cube = bakeCube();
		}
	}

	private static synchronized void ensurePipeline() {
		if (pipeline != null) {
			return;
		}
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
