package dev.stray.client.visual;

//? if <26.3 {
/*import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.Optional;
import java.util.OptionalDouble;*///?}
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import dev.stray.Stray;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Space Roof on the GPU: each room's geometry is uploaded once (again only after a rebake or a
 * colour or effect change) and drawn from there every frame, instead of being rebuilt vertex by
 * vertex each frame. Like the CS2 knives: a placeholder goes through Minecraft's queue so the
 * draw happens at the right moment with the right targets, and when Minecraft draws that batch
 * the cached buffers are drawn instead, moved to the camera through {@code ModelOffset}.
 */
public final class SpaceRoofGpu {
	private static final long STALE_NS = 1_000_000_000L;

	/** One uploaded mesh: positions relative to its origin. */
	static final class Mesh {
		final GpuBuffer vertices;
		final GpuBuffer indices;
		final int indexCount;
		final double ox;
		final double oy;
		final double oz;

		Mesh(GpuBuffer vertices, GpuBuffer indices, int indexCount, double ox, double oy, double oz) {
			this.vertices = vertices;
			this.indices = indices;
			this.indexCount = indexCount;
			this.ox = ox;
			this.oy = oy;
			this.oz = oz;
		}

		void close() {
			vertices.close();
			indices.close();
		}
	}

	/** Writes quads, four vertices each: camera-relative position, UV, colour. */
	public interface Sink {
		void vertex(float x, float y, float z, float u, float v, int argb);
	}

	/** A layer of cached meshes, with its render type. */
	record Layer(RenderType type, List<Mesh> meshes) {
	}

	/** Quads made fresh each frame (stardust, debris, ripples), with their render type. */
	record Live(RenderType type, java.util.function.Consumer<Sink> writer) {
	}

	/** A draw: cached buffers, or (vertices null) quads in the batch's own vertices from {@code firstQuad} on. */
	private record Item(GpuBufferSlice vertices, GpuBuffer indices, int indexCount, GpuBufferSlice transform, int firstQuad) {
	}

	private record Group(PreparedRenderType prepared, List<Item> items) {
	}

	private static final class Pending {
		final List<Group> groups = new ArrayList<>();
		long queuedNs = System.nanoTime();
	}


	/** Our render types, the batch Minecraft prepared last for each, and the draws waiting on those batches. */
	private static final Set<RenderType> OURS = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
	private static final Map<RenderType, PreparedRenderType> PREPARED = new java.util.IdentityHashMap<>();
	private static final Map<PreparedRenderType, Pending> QUEUE = new HashMap<>();
	private static boolean warned;
	/** Frames queued without our draw ever running, and whether it ever ran. */
	private static int queuedWithoutDraw;
	private static boolean drawn;
	private static boolean gaveUp;

	private SpaceRoofGpu() {
	}

	static void own(RenderType type) {
		OURS.add(type);
	}

	/**
	 * Uploads quads: {@code floatsPerQuad} floats per quad, x, y, z first in each vertex, then
	 * {@code uvAt} the two UV values (or, for the sky's 4 floats per vertex, its inside value with
	 * {@code fixedV} as the second). Every vertex gets {@code argb}.
	 */
	static Mesh upload(String name, List<float[]> arrays, List<Integer> counts, int floatsPerQuad, float fixedV, int argb) {
		int quads = 0;
		for (int c : counts) {
			quads += c;
		}
		if (quads == 0) {
			return null;
		}
		int perVertex = floatsPerQuad / 4;
		double ox = Double.MAX_VALUE;
		double oz = Double.MAX_VALUE;
		for (float[] a : arrays) {
			if (a.length > 0) {
				ox = Math.min(ox, Math.floor(a[0]));
				oz = Math.min(oz, Math.floor(a[2]));
			}
		}
		VertexFormat format = DefaultVertexFormat.POSITION_TEX_COLOR;
		int stride = format.getVertexSize();
		int oPos = format.getElement("Position").offset();
		int oUv = format.getElement("UV0").offset();
		int oColor = format.getElement("Color").offset();
		ByteBuffer v = ByteBuffer.allocateDirect(quads * 4 * stride).order(ByteOrder.nativeOrder());
		byte a = (byte) (argb >>> 24);
		byte r = (byte) (argb >> 16);
		byte g = (byte) (argb >> 8);
		byte b = (byte) argb;
		int vertex = 0;
		for (int i = 0; i < arrays.size(); i++) {
			float[] q = arrays.get(i);
			int floats = counts.get(i) * floatsPerQuad;
			for (int o = 0; o < floats; o += perVertex) {
				int at = vertex++ * stride;
				v.putFloat(at + oPos, (float) (q[o] - ox));
				v.putFloat(at + oPos + 4, q[o + 1]);
				v.putFloat(at + oPos + 8, (float) (q[o + 2] - oz));
				v.putFloat(at + oUv, q[o + 3]);
				v.putFloat(at + oUv + 4, perVertex >= 5 ? q[o + 4] : fixedV);
				v.put(at + oColor, r);
				v.put(at + oColor + 1, g);
				v.put(at + oColor + 2, b);
				v.put(at + oColor + 3, a);
			}
		}
		int indexCount = quads * 6;
		ByteBuffer idx = ByteBuffer.allocateDirect(indexCount * 4).order(ByteOrder.nativeOrder());
		for (int quad = 0; quad < quads; quad++) {
			int base = quad * 4;
			idx.putInt(base).putInt(base + 1).putInt(base + 2).putInt(base + 2).putInt(base + 3).putInt(base);
		}
		idx.flip();
		GpuDevice device = RenderSystem.getDevice();
		GpuBuffer vb = device.createBuffer(() -> "Stray space roof " + name, GpuBuffer.USAGE_VERTEX, v);
		GpuBuffer ib = device.createBuffer(() -> "Stray space roof " + name + " indices", GpuBuffer.USAGE_INDEX, idx);
		return new Mesh(vb, ib, indexCount, ox, 0.0, oz);
	}

	/** Called (from a mixin) for every prepared render type; remembers ours. */
	public static void prepared(RenderType type, PreparedRenderType prepared) {
		if (OURS.contains(type)) {
			PREPARED.put(type, prepared);
		}
	}

	/**
	 * From our one custom geometry, right after Minecraft prepared its batch: every layer, in this
	 * order, with its prepared render type and its camera offsets (written now, before any render
	 * pass), then the fresh quads; and the placeholder. One batch instead of one per layer: at
	 * high frame rates each batch's own setup costs about as much as its drawing.
	 */
	static void queueAll(RenderType carrier, List<Layer> layers, List<Live> lives, Vec3 camera, VertexConsumer consumer) {
		// The placeholder: one quad with nothing in it, so the batch gets drawn.
		for (int i = 0; i < 4; i++) {
			consumer.addVertex(0f, 0f, 0f).setUv(0f, 0f).setColor(0);
		}
		// The fresh quads go into the batch's own vertices right after it (Minecraft uploads those
		// once for the whole frame anyway), each kind drawn later with its own shader.
		int[] vertex = {0};
		Sink sink = (x, y, z, u, v, argb) -> {
			consumer.addVertex(x, y, z).setUv(u, v).setColor(argb);
			vertex[0]++;
		};
		int[] firstQuad = new int[lives.size()];
		int[] quads = new int[lives.size()];
		int written = 1;
		for (int i = 0; i < lives.size(); i++) {
			vertex[0] = 0;
			lives.get(i).writer.accept(sink);
			// Whole quads only (pad a broken one with empty corners).
			while (vertex[0] % 4 != 0) {
				consumer.addVertex(0f, 0f, 0f).setUv(0f, 0f).setColor(0);
				vertex[0]++;
			}
			firstQuad[i] = written;
			quads[i] = vertex[0] / 4;
			written += quads[i];
		}
		PreparedRenderType batch = PREPARED.get(carrier);
		if (batch == null) {
			return;
		}
		long now = System.nanoTime();
		for (Iterator<Pending> it = QUEUE.values().iterator(); it.hasNext(); ) {
			if (now - it.next().queuedNs > STALE_NS) {
				it.remove();
			}
		}
		Pending pending = QUEUE.computeIfAbsent(batch, k -> new Pending());
		pending.queuedNs = now;
		if (!drawn && ++queuedWithoutDraw > 120 && !gaveUp) {
			gaveUp = true;
			Stray.LOGGER.warn("Space roof: the cached GPU draw never ran (another mod may draw batches its own way); sending the geometry each frame instead");
		}
		try {
			Matrix4f view = RenderSystem.getModelViewMatrixCopy();
			Vector4f white = new Vector4f(1f, 1f, 1f, 1f);
			for (Layer layer : layers) {
				if (layer.meshes.isEmpty()) {
					continue;
				}
				List<Item> items = new ArrayList<>();
				for (Mesh mesh : layer.meshes) {
					Vector3f offset = new Vector3f((float) (mesh.ox - camera.x), (float) (mesh.oy - camera.y), (float) (mesh.oz - camera.z));
					GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(view, white, offset, new Matrix4f());
					items.add(new Item(mesh.vertices.slice(), mesh.indices, mesh.indexCount, transform, 0));
				}
				pending.groups.add(new Group(layer.type == carrier ? batch : layer.type.prepare(), items));
			}
			for (int i = 0; i < lives.size(); i++) {
				if (quads[i] == 0) {
					continue;
				}
				RenderType type = lives.get(i).type;
				PreparedRenderType prepared = type == carrier ? batch : type.prepare();
				pending.groups.add(new Group(prepared, List.of(new Item(null, null, quads[i] * 6, prepared.dynamicTransforms(), firstQuad[i]))));
			}
		} catch (RuntimeException e) {
			warn(e);
		}
	}

	/** {@code /stray debug dev roof stream}: send the geometry each frame instead (to compare). */
	private static boolean forceStream;

	/** The cached draw never ran (or streaming is forced): Space Roof sends its geometry each frame instead. */
	static boolean broken() {
		return gaveUp || forceStream;
	}

	public static String toggleStream() {
		forceStream = !forceStream;
		return forceStream ? "sending the geometry each frame (the old way). Run it again for the GPU cache." : "drawing from the GPU cache again.";
	}

	static String status() {
		return forceStream ? "forced streaming" : gaveUp ? "fallback (geometry sent each frame)" : drawn ? "cached on the GPU" : "waiting for the first draw (" + queuedWithoutDraw + " frames)";
	}

	/** Whether this prepared batch is ours, to draw from the cached meshes instead of its placeholder. */
	public static boolean handles(PreparedRenderType batch) {
		Pending pending = QUEUE.get(batch);
		return pending != null;
	}

	//? if >=26.3 {
	public static void draw(PreparedRenderType batch, StagedVertexBuffer.ExecuteInfo info, RenderPass pass) {
	//?} else {
	/*public static void draw(PreparedRenderType batch, StagedVertexBuffer.ExecuteInfo info) {*///?}
		Pending pending = QUEUE.remove(batch);
		if (pending == null) {
			return;
		}
		drawn = true;
		if (pending.groups.isEmpty()) {
			return;
		}
		//? if >=26.3 {
		try {
		//?} else {
		/*RenderTarget target = batch.outputTarget().getRenderTarget();
		GpuTextureView color = RenderSystem.outputColorTextureOverride != null ? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
		GpuTextureView depth = target.useDepth
			? (RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView())
			: null;
		try (RenderPass pass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(() -> "Stray space roof", color, Optional.empty(), depth, OptionalDouble.empty())) {*///?}
			for (Group group : pending.groups) {
				PreparedRenderType prepared = group.prepared;
				//? if >=26.3 {
				pass.setPipeline(RenderSystem.getCompiledPipeline(prepared.pipeline()));
				//?} else {
				/*pass.setPipeline(prepared.pipeline());*///?}
				if (prepared.scissorState().enabled()) {
					pass.enableScissor(prepared.scissorState().x(), prepared.scissorState().y(), prepared.scissorState().width(), prepared.scissorState().height());
				//? if <26.3 {
				/*} else {
					pass.disableScissor();*///?}
				}
				RenderSystem.bindDefaultUniforms(pass);
				for (PreparedRenderType.Texture texture : prepared.textures()) {
					//? if >=26.3 {
					pass.setUniform(texture.name(), texture.textureView(), texture.sampler());
					//?} else {
					/*pass.bindTexture(texture.name(), texture.textureView(), texture.sampler());*///?}
				}
				for (Item item : group.items) {
					pass.setUniform("DynamicTransforms", item.transform);
					if (item.vertices == null) {
						// Quads in the batch's own vertices.
						pass.setVertexBuffer(0, info.vertexBuffer().slice());
						pass.setIndexBuffer(info.indexBuffer(), info.indexType());
						pass.drawIndexed(item.indexCount, 1, info.firstIndex() + item.firstQuad * 6, info.baseVertex(), 0);
						continue;
					}
					pass.setVertexBuffer(0, item.vertices);
					pass.setIndexBuffer(item.indices, IndexType.INT);
					pass.drawIndexed(item.indexCount, 1, 0, 0, 0);
				}
			}
		} catch (RuntimeException e) {
			warn(e);
		}
	}

	private static void warn(RuntimeException e) {
		if (!warned) {
			warned = true;
			Stray.LOGGER.warn("Space roof: GPU draw failed (further failures are not logged)", e);
		}
	}
}
