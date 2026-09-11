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
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Pre-rendered End sky: six static cube-face textures plus a short frame
 * sequence for the black hole patch, cross-faded for motion. Everything is
 * baked offline; at runtime this is textured quads through vanilla's
 * position_tex_color shader.
 */
public final class EndSkyDecor {
	private static final float RADIUS = 100f;
	private static final int FRAMES = 8;
	private static final float FRAME_TICKS = 5f;
	private static final float TILT = 0.42f;
	private static final float CONE_COS = 0.574f;
	private static final Vector3f WORLD_HOLE = new Vector3f(0.18f, 0.86f, 0.48f).normalize();

	private static final Face[] FACES = {
		new Face(new Vector3f(1f, 0f, 0f), new Vector3f(0f, 1f, 0f)),
		new Face(new Vector3f(-1f, 0f, 0f), new Vector3f(0f, 1f, 0f)),
		new Face(new Vector3f(0f, 1f, 0f), new Vector3f(0f, 0f, -1f)),
		new Face(new Vector3f(0f, -1f, 0f), new Vector3f(0f, 0f, 1f)),
		new Face(new Vector3f(0f, 0f, 1f), new Vector3f(0f, 1f, 0f)),
		new Face(new Vector3f(0f, 0f, -1f), new Vector3f(0f, 1f, 0f))
	};
	private static final Identifier[] FACE_TEXTURES = new Identifier[6];
	private static final Identifier[] HOLE_TEXTURES = new Identifier[FRAMES];

	static {
		for (int i = 0; i < 6; i++) {
			FACE_TEXTURES[i] = Stray.id("textures/sky/end/face_" + i + ".png");
		}
		for (int i = 0; i < FRAMES; i++) {
			HOLE_TEXTURES[i] = Stray.id("textures/sky/end/hole_" + i + ".png");
		}
	}

	private static RenderPipeline pipeline;
	private static GpuBuffer quads;

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
		ensurePipeline();
		if (quads == null) {
			quads = bakeQuads();
		}
		if (pipeline == null || quads == null) {
			return;
		}
		RenderTarget main = client.getMainRenderTarget();
		if (main == null || main.getColorTextureView() == null) {
			return;
		}

		PoseStack pose = new PoseStack();
		pose.mulPose(Axis.YP.rotation(time * 0.0009f));
		pose.mulPose(Axis.XP.rotation(TILT));
		Matrix4fStack modelView = RenderSystem.getModelViewStack();
		modelView.pushMatrix();
		modelView.mul(pose.last().pose());
		GpuBufferSlice opaque = RenderSystem.getDynamicUniforms().writeTransform(
			modelView,
			new Vector4f(1f, 1f, 1f, 1f),
			new Vector3f(),
			new Matrix4f()
		);

		// Ping-pong through the hole frames and cross-fade neighbours.
		float phase = time / FRAME_TICKS;
		int span = FRAMES - 1;
		float cycle = phase % (span * 2);
		if (cycle < 0f) {
			cycle += span * 2;
		}
		float pos = cycle <= span ? cycle : span * 2 - cycle;
		int a = Mth.clamp((int) Math.floor(pos), 0, span);
		int b = Math.min(a + 1, span);
		float blend = Mth.clamp(pos - a, 0f, 1f);
		GpuBufferSlice fadeB = RenderSystem.getDynamicUniforms().writeTransform(
			modelView,
			new Vector4f(blend, blend, blend, blend),
			new Vector3f(),
			new Matrix4f()
		);

		var indexBuf = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
		GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
			() -> "stray end sky",
			main.getColorTextureView(),
			OptionalInt.empty(),
			main.getDepthTextureView(),
			OptionalDouble.empty()
		)) {
			pass.setPipeline(pipeline);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setVertexBuffer(0, quads);
			pass.setIndexBuffer(indexBuf.getBuffer(7 * 6), indexBuf.type());
			pass.setUniform("DynamicTransforms", opaque);
			for (int i = 0; i < 6; i++) {
				if (bind(client, pass, FACE_TEXTURES[i], linear)) {
					pass.drawIndexed(0, i * 6, 6, 1);
				}
			}
			if (bind(client, pass, HOLE_TEXTURES[a], linear)) {
				pass.drawIndexed(0, 6 * 6, 6, 1);
			}
			if (b != a && blend > 0.002f && bind(client, pass, HOLE_TEXTURES[b], linear)) {
				pass.setUniform("DynamicTransforms", fadeB);
				pass.drawIndexed(0, 6 * 6, 6, 1);
			}
		} finally {
			modelView.popMatrix();
		}
	}

	private static boolean bind(Minecraft client, RenderPass pass, Identifier id, GpuSampler sampler) {
		AbstractTexture texture = client.getTextureManager().getTexture(id);
		if (texture == null || texture.getTextureView() == null) {
			return false;
		}
		pass.bindTexture("Sampler0", texture.getTextureView(), sampler);
		return true;
	}

	private static synchronized void ensurePipeline() {
		if (pipeline != null) {
			return;
		}
		pipeline = RenderPipelines.register(
			RenderPipeline.builder()
				.withLocation(Stray.id("pipeline/end_sky_textured"))
				.withVertexShader("core/position_tex_color")
				.withFragmentShader("core/position_tex_color")
				.withSampler("Sampler0")
				.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
				.withUniform("Projection", UniformType.UNIFORM_BUFFER)
				.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
				.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
				.withCull(false)
				.withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
				.build()
		);
	}

	/** Six cube faces followed by the black hole patch quad. */
	private static GpuBuffer bakeQuads() {
		try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(28 * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize())) {
			BufferBuilder buf = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
			for (Face face : FACES) {
				Vector3f right = face.right();
				// Image row 0 is the top of the face (+up), matching the offline bake.
				quad(buf, face.normal, right, face.up, 1f, 1f);
			}
			Vector3f hole = Axis.XP.rotation(-TILT).transform(new Vector3f(WORLD_HOLE)).normalize();
			Vector3f holeUp = new Vector3f(0f, 1f, 0f);
			if (Math.abs(holeUp.dot(hole)) > 0.94f) {
				holeUp.set(0f, 0f, 1f);
			}
			holeUp.sub(new Vector3f(hole).mul(holeUp.dot(hole))).normalize();
			Vector3f holeX = new Vector3f(holeUp).cross(hole).normalize();
			Vector3f holeY = new Vector3f(hole).cross(holeX);
			float tanC = (float) (Math.sqrt(1.0 - CONE_COS * CONE_COS) / CONE_COS);
			quad(buf, hole, holeX, holeY, tanC, tanC);
			try (MeshData mesh = buf.buildOrThrow()) {
				return RenderSystem.getDevice().createBuffer(
					() -> "stray end sky quads",
					GpuBuffer.USAGE_VERTEX,
					mesh.vertexBuffer()
				);
			}
		}
	}

	private static void quad(BufferBuilder buf, Vector3f center, Vector3f right, Vector3f up, float halfW, float halfH) {
		corner(buf, center, right, up, -halfW, halfH, 0f, 0f);
		corner(buf, center, right, up, halfW, halfH, 1f, 0f);
		corner(buf, center, right, up, halfW, -halfH, 1f, 1f);
		corner(buf, center, right, up, -halfW, -halfH, 0f, 1f);
	}

	private static void corner(BufferBuilder buf, Vector3f center, Vector3f right, Vector3f up, float x, float y, float u, float v) {
		Vector3f p = new Vector3f(center)
			.add(new Vector3f(right).mul(x))
			.add(new Vector3f(up).mul(y))
			.mul(RADIUS);
		buf.addVertex(p.x, p.y, p.z).setUv(u, v).setColor(0xFFFFFFFF);
	}

	private record Face(Vector3f normal, Vector3f up) {
		Vector3f right() {
			return new Vector3f(normal).cross(up).normalize();
		}
	}
}
