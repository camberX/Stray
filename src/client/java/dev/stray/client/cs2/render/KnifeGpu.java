package dev.stray.client.cs2.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import dev.stray.Stray;
import dev.stray.client.cs2.Cs2Knives;
import dev.stray.client.cs2.s2.S2Model;
import dev.stray.client.visual.HeldItemShader;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * GPU skinning for CS2 knives. Each knife mesh is uploaded once in its bind pose (bone index
 * in the vertex color); every frame only the few bone matrices are sent. The knife is still
 * submitted through the normal render queue (as a single empty triangle) so it draws at the
 * right moment with the right targets; when Minecraft draws that batch, the knife's own
 * buffers are drawn instead.
 */
public final class KnifeGpu {
	/** Bones per draw (the knife models use at most 8). */
	public static final int MAX_BONES = 16;
	private static final int BLOCK_SIZE = MAX_BONES * 64 + 16;
	private static final int SLOTS = 64;

	private record Request(Mesh mesh, boolean blade, Matrix4f[] bones, int light) {
	}

	/** One knife's buffers on the GPU. */
	public static final class Mesh {
		final GpuBuffer vertices;
		final GpuBuffer indices;
		final int indexCount;
		final GpuBuffer bladeIndices;
		final int bladeCount;

		Mesh(GpuBuffer vertices, GpuBuffer indices, int indexCount, GpuBuffer bladeIndices, int bladeCount) {
			this.vertices = vertices;
			this.indices = indices;
			this.indexCount = indexCount;
			this.bladeIndices = bladeIndices;
			this.bladeCount = bladeCount;
		}

		void close() {
			vertices.close();
			indices.close();
			if (bladeIndices != null) {
				bladeIndices.close();
			}
		}
	}

	private static final Map<RenderType, List<Request>> QUEUE = new IdentityHashMap<>();
	private static Cs2Knives.Knife meshKnife;
	private static Mesh mesh;
	private static GpuBuffer bones;
	private static int slotSize;
	private static int slot;
	private static boolean warned;
	private static final ByteBuffer STAGING = ByteBuffer.allocateDirect(BLOCK_SIZE).order(ByteOrder.nativeOrder());

	private KnifeGpu() {
	}

	/** The GPU mesh for this knife, built on first use (and the previous knife's freed). */
	public static Mesh mesh(Cs2Knives.Knife knife) {
		if (knife == meshKnife && mesh != null) {
			return mesh;
		}
		if (mesh != null) {
			mesh.close();
			mesh = null;
		}
		meshKnife = knife;
		mesh = build(knife);
		return mesh;
	}

	private static Mesh build(Cs2Knives.Knife knife) {
		S2Model model = knife.model;
		VertexFormat format = DefaultVertexFormat.ENTITY;
		int stride = format.getVertexSize();
		int oPos = format.getOffset(VertexFormatElement.POSITION);
		int oColor = format.getOffset(VertexFormatElement.COLOR);
		int oUv = format.getOffset(VertexFormatElement.UV0);
		int oUv1 = format.getOffset(VertexFormatElement.UV1);
		int oUv2 = format.getOffset(VertexFormatElement.UV2);
		int oNormal = format.getOffset(VertexFormatElement.NORMAL);
		int total = 0;
		int indexTotal = 0;
		for (S2Model.Part part : model.parts) {
			total += part.vertexCount;
			indexTotal += part.indices.length - part.indices.length % 3;
		}
		ByteBuffer v = ByteBuffer.allocateDirect(Math.max(1, total) * stride).order(ByteOrder.nativeOrder());
		ByteBuffer idx = ByteBuffer.allocateDirect(Math.max(1, indexTotal) * 4).order(ByteOrder.nativeOrder());
		int[][] bladeTris = knife.bladeTriangles();
		int bladeTotal = 0;
		for (int[] list : bladeTris) {
			bladeTotal += list == null ? 0 : list.length * 3;
		}
		ByteBuffer bladeIdx = ByteBuffer.allocateDirect(Math.max(1, bladeTotal) * 4).order(ByteOrder.nativeOrder());
		int base = 0;
		for (int pi = 0; pi < model.parts.size(); pi++) {
			S2Model.Part part = model.parts.get(pi);
			for (int i = 0; i < part.vertexCount; i++) {
				int at = (base + i) * stride;
				v.putFloat(at + oPos, part.pos[i * 3]);
				v.putFloat(at + oPos + 4, part.pos[i * 3 + 1]);
				v.putFloat(at + oPos + 8, part.pos[i * 3 + 2]);
				int bone = part.bones == null ? 0 : Math.min(MAX_BONES - 1, Math.max(0, part.bones[i * 4]));
				v.put(at + oColor, (byte) bone);
				v.put(at + oColor + 1, (byte) 255);
				v.put(at + oColor + 2, (byte) 255);
				v.put(at + oColor + 3, (byte) 255);
				v.putFloat(at + oUv, part.uv == null ? 0f : part.uv[i * 2]);
				v.putFloat(at + oUv + 4, part.uv == null ? 0f : part.uv[i * 2 + 1]);
				v.putShort(at + oUv1, (short) 0);
				v.putShort(at + oUv1 + 2, (short) 10);
				v.putShort(at + oUv2, (short) 0);
				v.putShort(at + oUv2 + 2, (short) 0);
				float nx = part.normal == null ? 0f : part.normal[i * 3];
				float ny = part.normal == null ? 1f : part.normal[i * 3 + 1];
				float nz = part.normal == null ? 0f : part.normal[i * 3 + 2];
				v.put(at + oNormal, normalByte(nx));
				v.put(at + oNormal + 1, normalByte(ny));
				v.put(at + oNormal + 2, normalByte(nz));
			}
			int end = part.indices.length - part.indices.length % 3;
			for (int t = 0; t < end; t++) {
				idx.putInt(base + part.indices[t]);
			}
			int[] list = pi < bladeTris.length ? bladeTris[pi] : null;
			if (list != null) {
				for (int t : list) {
					bladeIdx.putInt(base + part.indices[t]);
					bladeIdx.putInt(base + part.indices[t + 1]);
					bladeIdx.putInt(base + part.indices[t + 2]);
				}
			}
			base += part.vertexCount;
		}
		idx.flip();
		bladeIdx.flip();
		GpuDevice device = RenderSystem.getDevice();
		GpuBuffer vb = device.createBuffer(() -> "Stray CS2 knife vertices", GpuBuffer.USAGE_VERTEX, v);
		GpuBuffer ib = device.createBuffer(() -> "Stray CS2 knife indices", GpuBuffer.USAGE_INDEX, idx);
		GpuBuffer bb = bladeTotal == 0 ? null : device.createBuffer(() -> "Stray CS2 blade indices", GpuBuffer.USAGE_INDEX, bladeIdx);
		return new Mesh(vb, ib, indexTotal, bb, bladeTotal);
	}

	private static byte normalByte(float f) {
		return (byte) Math.round(Math.max(-1f, Math.min(1f, f)) * 127f);
	}

	/** Records a knife draw for the batch of {@code type}; called while that batch is built. */
	public static void queue(RenderType type, Mesh mesh, boolean blade, Matrix4f pose, Matrix4f toView, Matrix4f[] skin, int light) {
		int n = skin == null ? 1 : Math.min(MAX_BONES, skin.length);
		Matrix4f[] out = new Matrix4f[n];
		for (int b = 0; b < n; b++) {
			out[b] = new Matrix4f(pose).mul(toView);
			if (skin != null) {
				out[b].mul(skin[b]);
			}
		}
		QUEUE.computeIfAbsent(type, k -> new ArrayList<>()).add(new Request(mesh, blade, out, light));
	}

	/** Whether this batch is a knife batch to draw from the GPU mesh instead of its (empty) vertices. */
	public static boolean handles(RenderType type) {
		List<Request> list = QUEUE.get(type);
		return list != null && !list.isEmpty();
	}

	/** Draws every queued knife for this batch. */
	public static void draw(RenderType type, RenderSetup setup) {
		List<Request> list = QUEUE.remove(type);
		if (list == null || list.isEmpty()) {
			return;
		}
		GpuDevice device = RenderSystem.getDevice();
		if (bones == null || bones.isClosed()) {
			int align = Math.max(16, device.getUniformOffsetAlignment());
			slotSize = (BLOCK_SIZE + align - 1) / align * align;
			bones = device.createBuffer(() -> "Stray CS2 knife bones", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, (long) slotSize * SLOTS);
		}
		CommandEncoder encoder = device.createCommandEncoder();
		// Uniform writes must happen before the render pass opens.
		GpuBufferSlice[] boneSlices = new GpuBufferSlice[list.size()];
		GpuBufferSlice[] transforms = new GpuBufferSlice[list.size()];
		ByteBuffer data = STAGING;
		Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
		for (int r = 0; r < list.size(); r++) {
			Request req = list.get(r);
			data.clear();
			for (int b = 0; b < MAX_BONES; b++) {
				Matrix4f m = b < req.bones.length ? req.bones[b] : req.bones[0];
				m.get(b * 64, data);
			}
			data.putInt(MAX_BONES * 64, req.light & 0xFFFF);
			data.putInt(MAX_BONES * 64 + 4, (req.light >> 16) & 0xFFFF);
			data.putInt(MAX_BONES * 64 + 8, 0);
			data.putInt(MAX_BONES * 64 + 12, 0);
			data.position(0).limit(BLOCK_SIZE);
			GpuBufferSlice slice = bones.slice((long) slot * slotSize, BLOCK_SIZE);
			slot = (slot + 1) % SLOTS;
			encoder.writeToBuffer(slice, data);
			boneSlices[r] = slice;
			Vector4f color = req.blade ? new Vector4f(HeldItemShader.colorModulator(type.pipeline())) : new Vector4f(1f, 1f, 1f, 1f);
			Vector3f offset = req.blade ? new Vector3f(HeldItemShader.modelOffset(type.pipeline())) : new Vector3f();
			transforms[r] = RenderSystem.getDynamicUniforms().writeTransform(modelView, color, offset, new Matrix4f());
		}
		// Textures may still need loading (an upload), which is not allowed inside a render pass.
		Map<String, RenderSetup.TextureAndSampler> textures = setup.getTextures();
		RenderTarget target = type.outputTarget().getRenderTarget();
		GpuTextureView colorView = RenderSystem.outputColorTextureOverride != null ? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
		GpuTextureView depthView = target.useDepth
			? (RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView())
			: null;
		try (RenderPass pass = encoder.createRenderPass(() -> "Stray CS2 knife", colorView, OptionalInt.empty(), depthView, OptionalDouble.empty())) {
			pass.setPipeline(type.pipeline());
			ScissorState scissor = RenderSystem.getScissorStateForRenderTypeDraws();
			if (scissor.enabled()) {
				pass.enableScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
			}
			RenderSystem.bindDefaultUniforms(pass);
			for (Map.Entry<String, RenderSetup.TextureAndSampler> e : textures.entrySet()) {
				pass.bindTexture(e.getKey(), e.getValue().textureView(), e.getValue().sampler());
			}
			for (int r = 0; r < list.size(); r++) {
				Request req = list.get(r);
				GpuBuffer indices = req.blade ? req.mesh.bladeIndices : req.mesh.indices;
				int count = req.blade ? req.mesh.bladeCount : req.mesh.indexCount;
				if (indices == null || count == 0) {
					continue;
				}
				pass.setUniform("DynamicTransforms", transforms[r]);
				pass.setUniform("KnifeBones", boneSlices[r]);
				pass.setVertexBuffer(0, req.mesh.vertices);
				pass.setIndexBuffer(indices, VertexFormat.IndexType.INT);
				pass.drawIndexed(0, 0, count, 1);
			}
		} catch (RuntimeException e) {
			if (!warned) {
				warned = true;
				Stray.LOGGER.warn("CS2 knives: GPU draw failed (further failures are not logged)", e);
			}
		}
	}

}
