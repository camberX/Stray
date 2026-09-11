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
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Procedural galaxy, starfield, and Gargantua over the End skybox, baked into an
 * animated six-face sky texture. The heavy shader only runs when a face is
 * (re)baked: static faces once, faces the black hole touches every few frames
 * for the disk animation. Each frame just draws six textured quads.
 */
public final class EndSkyDecor {
	private static final float RADIUS = 100f;
	private static final int FACE_SIZE = 1280;
	private static final int ANIMATED_REBAKE_FRAMES = 3;
	private static final float TILT = 0.42f;
	private static final float CONE_COS = 0.45f;
	private static final float FACE_HALF_DIAGONAL = 0.9553f;
	private static final Vector3f WORLD_HOLE = new Vector3f(0.18f, 0.86f, 0.48f).normalize();

	private static final Face[] FACES = {
		new Face(new Vector3f(1f, 0f, 0f), new Vector3f(0f, 1f, 0f)),
		new Face(new Vector3f(-1f, 0f, 0f), new Vector3f(0f, 1f, 0f)),
		new Face(new Vector3f(0f, 1f, 0f), new Vector3f(0f, 0f, -1f)),
		new Face(new Vector3f(0f, -1f, 0f), new Vector3f(0f, 0f, 1f)),
		new Face(new Vector3f(0f, 0f, 1f), new Vector3f(0f, 1f, 0f)),
		new Face(new Vector3f(0f, 0f, -1f), new Vector3f(0f, 1f, 0f))
	};

	private static RenderPipeline bakePipeline;
	private static RenderPipeline drawPipeline;
	private static GpuBuffer bakeCube;
	private static int bakeIndices;
	private static GpuBuffer drawQuads;
	private static final TextureTarget[] TARGETS = new TextureTarget[6];
	private static final boolean[] BAKED = new boolean[6];
	private static final boolean[] ANIMATED = new boolean[6];
	private static Vector3f localHole;
	private static int frame;

	private EndSkyDecor() {
	}

	public static void init() {
		ensurePipelines();
	}

	public static void render(float time) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return;
		}
		ensurePipelines();
		ensureMeshes();
		if (bakePipeline == null || drawPipeline == null || bakeCube == null || drawQuads == null) {
			return;
		}
		RenderTarget main = client.getMainRenderTarget();
		if (main == null || main.getColorTextureView() == null) {
			return;
		}
		ensureTargets();
		frame++;
		boolean animateNow = frame % ANIMATED_REBAKE_FRAMES == 0;
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		for (int i = 0; i < 6; i++) {
			if (!BAKED[i] || (ANIMATED[i] && animateNow)) {
				bakeFace(encoder, i, time);
				BAKED[i] = true;
			}
		}
		draw(encoder, main, time);
	}

	private static void bakeFace(CommandEncoder encoder, int index, float time) {
		TextureTarget target = TARGETS[index];
		if (target == null || target.getColorTexture() == null || target.getColorTextureView() == null) {
			return;
		}
		Face face = FACES[index];
		Matrix4f view = new Matrix4f().setLookAlong(face.normal, face.up);
		Matrix4f proj = new Matrix4f().setPerspective(Mth.HALF_PI, 1f, 1f, 400f);
		Matrix4f viewProj = proj.mul(view);
		float pulse = 0.92f + 0.08f * (0.5f + 0.5f * Mth.sin(time * 0.03f));
		GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(
			viewProj,
			new Vector4f(pulse, pulse, pulse, time * 0.012f),
			localHole,
			new Matrix4f()
		);
		var indexBuf = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
		encoder.clearColorTexture(target.getColorTexture(), 0);
		try (RenderPass pass = encoder.createRenderPass(
			() -> "stray end sky bake",
			target.getColorTextureView(),
			OptionalInt.empty()
		)) {
			pass.setPipeline(bakePipeline);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transform);
			pass.setVertexBuffer(0, bakeCube);
			pass.setIndexBuffer(indexBuf.getBuffer(bakeIndices), indexBuf.type());
			pass.drawIndexed(0, 0, bakeIndices, 1);
		}
	}

	private static void draw(CommandEncoder encoder, RenderTarget main, float time) {
		PoseStack pose = new PoseStack();
		pose.mulPose(Axis.YP.rotation(time * 0.0009f));
		pose.mulPose(Axis.XP.rotation(TILT));
		Matrix4fStack modelView = RenderSystem.getModelViewStack();
		modelView.pushMatrix();
		modelView.mul(pose.last().pose());
		GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(
			modelView,
			new Vector4f(1f, 1f, 1f, 1f),
			new Vector3f(),
			new Matrix4f()
		);
		var indexBuf = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
		GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		try (RenderPass pass = encoder.createRenderPass(
			() -> "stray end sky",
			main.getColorTextureView(),
			OptionalInt.empty(),
			main.getDepthTextureView(),
			OptionalDouble.empty()
		)) {
			pass.setPipeline(drawPipeline);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transform);
			pass.setVertexBuffer(0, drawQuads);
			pass.setIndexBuffer(indexBuf.getBuffer(36), indexBuf.type());
			for (int i = 0; i < 6; i++) {
				TextureTarget target = TARGETS[i];
				if (target == null || target.getColorTextureView() == null) {
					continue;
				}
				pass.bindTexture("Sampler0", target.getColorTextureView(), linear);
				pass.drawIndexed(0, i * 6, 6, 1);
			}
		} finally {
			modelView.popMatrix();
		}
	}

	private static void ensureTargets() {
		if (localHole == null) {
			// The sky spins about Y after the X tilt, so a hole fixed in cube space
			// keeps a constant elevation as it circles the zenith.
			localHole = Axis.XP.rotation(-TILT).transform(new Vector3f(WORLD_HOLE)).normalize();
			for (int i = 0; i < 6; i++) {
				float cos = FACES[i].normal.dot(localHole);
				float angle = (float) Math.acos(Mth.clamp(cos, -1f, 1f));
				ANIMATED[i] = angle < (float) Math.acos(CONE_COS) + FACE_HALF_DIAGONAL;
			}
		}
		for (int i = 0; i < 6; i++) {
			if (TARGETS[i] == null) {
				TARGETS[i] = new TextureTarget("stray end sky face " + i, FACE_SIZE, FACE_SIZE, false);
				BAKED[i] = false;
			}
		}
	}

	private static void ensureMeshes() {
		if (bakeCube == null) {
			bakeCube = bakeCubeMesh();
		}
		if (drawQuads == null) {
			drawQuads = bakeDrawQuads();
		}
	}

	private static synchronized void ensurePipelines() {
		if (bakePipeline == null) {
			bakePipeline = RenderPipelines.register(
				RenderPipeline.builder()
					.withLocation(Stray.id("pipeline/end_galaxy_bake"))
					.withVertexShader(Stray.id("core/end_galaxy_bake"))
					.withFragmentShader(Stray.id("core/end_galaxy"))
					.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
					.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
					.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
					.withCull(false)
					.withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
					.build()
			);
		}
		if (drawPipeline == null) {
			drawPipeline = RenderPipelines.register(
				RenderPipeline.builder()
					.withLocation(Stray.id("pipeline/end_sky_cube"))
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
	}

	private static GpuBuffer bakeCubeMesh() {
		try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(24 * DefaultVertexFormat.POSITION.getVertexSize())) {
			BufferBuilder buf = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
			for (Face face : FACES) {
				Vector3f right = face.right();
				corner(buf, face, right, -1f, -1f);
				corner(buf, face, right, 1f, -1f);
				corner(buf, face, right, 1f, 1f);
				corner(buf, face, right, -1f, 1f);
			}
			try (MeshData mesh = buf.buildOrThrow()) {
				bakeIndices = mesh.drawState().indexCount();
				return RenderSystem.getDevice().createBuffer(
					() -> "stray end sky bake cube",
					GpuBuffer.USAGE_VERTEX,
					mesh.vertexBuffer()
				);
			}
		}
	}

	private static GpuBuffer bakeDrawQuads() {
		try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(24 * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize())) {
			BufferBuilder buf = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
			for (Face face : FACES) {
				Vector3f right = face.right();
				texCorner(buf, face, right, -1f, -1f, 0f, 0f);
				texCorner(buf, face, right, 1f, -1f, 1f, 0f);
				texCorner(buf, face, right, 1f, 1f, 1f, 1f);
				texCorner(buf, face, right, -1f, 1f, 0f, 1f);
			}
			try (MeshData mesh = buf.buildOrThrow()) {
				return RenderSystem.getDevice().createBuffer(
					() -> "stray end sky quads",
					GpuBuffer.USAGE_VERTEX,
					mesh.vertexBuffer()
				);
			}
		}
	}

	private static void corner(BufferBuilder buf, Face face, Vector3f right, float x, float y) {
		Vector3f p = point(face, right, x, y);
		buf.addVertex(p.x, p.y, p.z);
	}

	private static void texCorner(BufferBuilder buf, Face face, Vector3f right, float x, float y, float u, float v) {
		Vector3f p = point(face, right, x, y);
		buf.addVertex(p.x, p.y, p.z).setUv(u, v).setColor(0xFFFFFFFF);
	}

	private static Vector3f point(Face face, Vector3f right, float x, float y) {
		return new Vector3f(face.normal)
			.add(new Vector3f(right).mul(x))
			.add(new Vector3f(face.up).mul(y))
			.mul(RADIUS);
	}

	private record Face(Vector3f normal, Vector3f up) {
		Vector3f right() {
			// Same convention as JOML lookAlong: right = forward x up maps to +x.
			return new Vector3f(normal).cross(up).normalize();
		}
	}
}
