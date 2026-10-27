package dev.stray.client.cs2.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import dev.stray.Stray;
import dev.stray.client.cs2.Cs2Knives;
import dev.stray.client.cs2.s2.S2Model;
import dev.stray.client.visual.HeldItemShader;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * GPU skinning for CS2 knives. Each knife mesh is uploaded once in its bind pose (bone index
 * in the vertex color); every frame only the few bone matrices are sent. The knife is still
 * submitted through the normal render queue (as a single empty triangle) so it draws at the
 * right moment with the right targets; when Minecraft draws that batch, the knife's own
 * buffers are drawn instead.
 *
 * <p>26.3: batches are prepared (uniforms written, textures resolved) before any render pass
 * opens and drawn later inside a shared pass, so bone matrices are written when the knife is
 * queued and the knife is drawn from {@link PreparedRenderType#drawFromBuffer}.
 */
public final class KnifeGpu {
	/** Bones per draw (the knife models use at most 8). */
	public static final int MAX_BONES = 16;
	private static final int BLOCK_SIZE = MAX_BONES * 64 + 16;
	private static final int SLOTS = 64;
	/** Queued knives whose batch never drew (culled, menu closed) are dropped after this long. */
	private static final long STALE_NS = 1_000_000_000L;

	/** {@code blade}: the prepared blade shader pass to draw right after this knife (null for none). */
	private record Request(Mesh mesh, GpuBufferSlice bones, PreparedRenderType blade) {
	}

	/** First-person knives to add to the held item silhouette mask this frame. */
	private record MaskRequest(Mesh mesh, GpuBufferSlice bones, PreparedRenderType mask) {
	}

	private static final List<MaskRequest> MASKS = new ArrayList<>();

	private static final class Pending {
		final List<Request> requests = new ArrayList<>();
		long queuedNs = System.nanoTime();
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

	/** Keyed by the prepared knife batch (a record, so equal batches share requests like Minecraft merges them). */
	private static final Map<PreparedRenderType, Pending> QUEUE = new HashMap<>();
	private static final Map<Cs2Knives.Knife, Mesh> MESHES = new IdentityHashMap<>();
	/** The knife batch Minecraft prepared last: the one the knife's placeholder vertices go into. */
	private static PreparedRenderType lastPrepared;
	private static GpuBuffer bones;
	private static int slotSize;
	private static int slot;
	private static boolean warned;
	private static final ByteBuffer STAGING = ByteBuffer.allocateDirect(BLOCK_SIZE).order(ByteOrder.nativeOrder());

	private KnifeGpu() {
	}

	/** The GPU mesh for this knife, built on first use. */
	public static Mesh mesh(Cs2Knives.Knife knife) {
		return MESHES.computeIfAbsent(knife, KnifeGpu::build);
	}

	/** Frees a knife's GPU buffers (when it leaves the knife cache). */
	public static void release(Cs2Knives.Knife knife) {
		Mesh m = MESHES.remove(knife);
		if (m != null) {
			m.close();
		}
		KnifeRender.forget(knife);
	}

	private static Mesh build(Cs2Knives.Knife knife) {
		S2Model model = knife.model;
		VertexFormat format = DefaultVertexFormat.ENTITY;
		int stride = format.getVertexSize();
		int oPos = format.getElement("Position").offset();
		int oColor = format.getElement("Color").offset();
		int oUv = format.getElement("UV0").offset();
		int oUv1 = format.getElement("UV1").offset();
		int oUv2 = format.getElement("UV2").offset();
		int oNormal = format.getElement("Normal").offset();
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

	/** Called (from a mixin) for every prepared render type; remembers knife batches. */
	public static void prepared(RenderType type, PreparedRenderType prepared) {
		if (KnifeRender.isKnife(type)) {
			lastPrepared = prepared;
		}
	}

	public static void queue(RenderType type, Mesh mesh, boolean blade, Matrix4f pose, Matrix4f toView, Matrix4f[] skin, int light) {
		queue(type, mesh, blade, pose, toView, skin, light, null, null);
	}

	/**
	 * Records a knife draw for the batch being built (called from the knife's custom geometry, right
	 * after Minecraft prepared its batch). Bone matrices are written now, before the render pass.
	 */
	public static void queue(RenderType type, Mesh mesh, boolean blade, Matrix4f pose, Matrix4f toView, Matrix4f[] skin, int light, RenderType bladeType, Identifier albedo) {
		PreparedRenderType batch = lastPrepared;
		if (batch == null) {
			return;
		}
		long now = System.nanoTime();
		for (Iterator<Pending> it = QUEUE.values().iterator(); it.hasNext(); ) {
			if (now - it.next().queuedNs > STALE_NS) {
				it.remove();
			}
		}
		GpuBufferSlice bonesSlice;
		try {
			bonesSlice = writeBones(pose, toView, skin, light);
		} catch (RuntimeException e) {
			warn(e);
			return;
		}
		// The blade shader goes right after its knife, in the same draw: as its own batch it could be
		// drawn before the knife (batch order is not fixed) and the knife would cover it.
		PreparedRenderType bladeBatch = bladeType == null ? null : bladeType.prepare();
		Pending pending = QUEUE.computeIfAbsent(batch, k -> new Pending());
		pending.queuedNs = now;
		pending.requests.add(new Request(mesh, bonesSlice, bladeBatch));
		PreparedRenderType maskBatch = HeldItemShader.knifeMaskBatch(albedo);
		if (maskBatch != null && mesh != null) {
			MASKS.add(new MaskRequest(mesh, bonesSlice, maskBatch));
		}
	}

	public static void clearMasks() {
		MASKS.clear();
	}

	public static boolean hasMasks() {
		return !MASKS.isEmpty();
	}

	/** Draws the queued knife masks into the (open) silhouette mask pass. */
	public static void drawMasks(RenderPass pass, RenderPipeline maskPipeline) {
		List<MaskRequest> pending = List.copyOf(MASKS);
		MASKS.clear();
		try {
			for (MaskRequest req : pending) {
				pass.setPipeline(RenderSystem.getCompiledPipeline(maskPipeline));
				RenderSystem.bindDefaultUniforms(pass);
				pass.setUniform("DynamicTransforms", req.mask.dynamicTransforms());
				for (PreparedRenderType.Texture texture : req.mask.textures()) {
					pass.setUniform(texture.name(), texture.textureView(), texture.sampler());
				}
				drawMesh(pass, new Request(req.mesh, req.bones, null), false);
			}
		} catch (RuntimeException e) {
			warn(e);
		}
	}

	private static GpuBufferSlice writeBones(Matrix4f pose, Matrix4f toView, Matrix4f[] skin, int light) {
		GpuDevice device = RenderSystem.getDevice();
		if (bones == null || bones.isClosed()) {
			int align = Math.max(16, device.getDeviceInfo().limits().minUniformOffsetAlignment());
			slotSize = (BLOCK_SIZE + align - 1) / align * align;
			bones = device.createBuffer(() -> "Stray CS2 knife bones", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, (long) slotSize * SLOTS);
		}
		int n = skin == null ? 1 : Math.min(MAX_BONES, skin.length);
		ByteBuffer data = STAGING;
		data.clear();
		Matrix4f m = new Matrix4f();
		for (int b = 0; b < MAX_BONES; b++) {
			m.set(pose).mul(toView);
			if (skin != null) {
				m.mul(skin[b < n ? b : 0]);
			}
			m.get(b * 64, data);
		}
		data.putInt(MAX_BONES * 64, light & 0xFFFF);
		data.putInt(MAX_BONES * 64 + 4, (light >> 16) & 0xFFFF);
		data.putInt(MAX_BONES * 64 + 8, 0);
		data.putInt(MAX_BONES * 64 + 12, 0);
		data.position(0).limit(BLOCK_SIZE);
		GpuBufferSlice slice = bones.slice((long) slot * slotSize, BLOCK_SIZE);
		slot = (slot + 1) % SLOTS;
		device.createCommandEncoder().writeToBuffer(slice, data);
		return slice;
	}

	/** Whether this prepared batch is a knife batch to draw from the GPU mesh instead of its (empty) vertices. */
	public static boolean handles(PreparedRenderType batch) {
		Pending pending = QUEUE.get(batch);
		return pending != null && !pending.requests.isEmpty();
	}

	/** Draws every queued knife for this batch inside Minecraft's render pass. */
	public static void draw(PreparedRenderType batch, RenderPass pass) {
		Pending pending = QUEUE.remove(batch);
		if (pending == null || pending.requests.isEmpty()) {
			return;
		}
		try {
			bind(batch, pass);
			for (Request req : pending.requests) {
				drawMesh(pass, req, false);
			}
			for (Request req : pending.requests) {
				if (req.blade == null) {
					continue;
				}
				bind(req.blade, pass);
				drawMesh(pass, req, true);
			}
		} catch (RuntimeException e) {
			warn(e);
		}
	}

	private static void bind(PreparedRenderType batch, RenderPass pass) {
		pass.setPipeline(RenderSystem.getCompiledPipeline(batch.pipeline()));
		if (batch.scissorState().enabled()) {
			pass.enableScissor(batch.scissorState().x(), batch.scissorState().y(), batch.scissorState().width(), batch.scissorState().height());
		}
		RenderSystem.bindDefaultUniforms(pass);
		pass.setUniform("DynamicTransforms", batch.dynamicTransforms());
		for (PreparedRenderType.Texture texture : batch.textures()) {
			pass.setUniform(texture.name(), texture.textureView(), texture.sampler());
		}
	}

	private static void drawMesh(RenderPass pass, Request req, boolean blade) {
		GpuBuffer indices = blade ? req.mesh.bladeIndices : req.mesh.indices;
		int count = blade ? req.mesh.bladeCount : req.mesh.indexCount;
		if (indices == null || count == 0) {
			return;
		}
		pass.setUniform("KnifeBones", req.bones);
		pass.setVertexBuffer(0, req.mesh.vertices.slice());
		pass.setIndexBuffer(indices, IndexType.INT);
		pass.drawIndexed(count, 1, 0, 0, 0);
	}

	private static void warn(RuntimeException e) {
		if (!warned) {
			warned = true;
			Stray.LOGGER.warn("CS2 knives: GPU draw failed (further failures are not logged)", e);
		}
	}
}
