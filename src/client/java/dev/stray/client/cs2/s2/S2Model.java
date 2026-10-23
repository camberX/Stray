package dev.stray.client.cs2.s2;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Embedded meshes of a .vmdl_c: geometry per draw call plus the model skeleton. */
public final class S2Model {
	public static final class Part {
		public String material;
		public float[] pos;
		public float[] uv;
		public float[] normal;
		public int[] bones; // 4 per vertex (mesh-local indices remapped to model bones)
		public float[] weights; // 4 per vertex
		public int[] indices;
		public int vertexCount;
	}

	public static final class Bone {
		public String name;
		public int parent;
		public float[] pos = new float[3];
		public float[] quat = {0, 0, 0, 1};
	}

	public final String name;
	public final List<Part> parts = new ArrayList<>();
	public final List<Bone> bones = new ArrayList<>();
	public final List<String> refAnimGroups = new ArrayList<>();
	public final Map<String, Object> data;

	@SuppressWarnings("unchecked")
	public S2Model(Resource res) {
		data = (Map<String, Object>) res.kv3("DATA");
		name = (String) data.getOrDefault("m_name", "");
		readSkeleton();
		Map<String, Object> ctrl = (Map<String, Object>) res.kv3("CTRL");
		if (ctrl == null) {
			return;
		}
		List<Object> meshes = (List<Object>) ctrl.get("embedded_meshes");
		if (meshes == null) {
			return;
		}
		List<Object> remapTable = (List<Object>) null;
		int[] remap = intArray(data.get("m_remappingTable"));
		int[] remapStarts = intArray(data.get("m_remappingTableStarts"));
		int meshIdx = 0;
		for (Object o : meshes) {
			Map<String, Object> em = (Map<String, Object>) o;
			int meshIndex = num(em.getOrDefault("m_nMeshIndex", em.get("mesh_index")));
			int dataBlock = num(em.getOrDefault("m_nDataBlock", em.get("data_block")));
			Map<String, Object> mdat = (Map<String, Object>) res.kv3(res.list.get(dataBlock));
			List<Object> vbs = (List<Object>) em.get("m_vertexBuffers");
			List<Object> ibs = (List<Object>) em.get("m_indexBuffers");
			if (vbs == null || ibs == null) {
				continue;
			}
			byte[][] vData = new byte[vbs.size()][];
			for (int i = 0; i < vbs.size(); i++) {
				vData[i] = buffer(res, (Map<String, Object>) vbs.get(i), true);
			}
			int[][] iData = new int[ibs.size()][];
			for (int i = 0; i < ibs.size(); i++) {
				Map<String, Object> ib = (Map<String, Object>) ibs.get(i);
				byte[] raw = buffer(res, ib, false);
				int size = num(ib.get("m_nElementSizeInBytes"));
				int n = num(ib.get("m_nElementCount"));
				if (raw.length == n * 4 && size != 4 && isIntArray(raw, n)) {
					iData[i] = toInts(raw, n);
				} else {
					iData[i] = indices(raw, n, size);
				}
			}
			int remapStart = remapStarts != null && meshIndex < remapStarts.length ? remapStarts[meshIndex] : -1;
			List<Object> sceneObjects = (List<Object>) mdat.get("m_sceneObjects");
			for (Object so : sceneObjects) {
				for (Object dco : (List<Object>) ((Map<String, Object>) so).get("m_drawCalls")) {
					Map<String, Object> dc = (Map<String, Object>) dco;
					Part part = new Part();
					part.material = (String) dc.get("m_material");
					int baseVertex = num(dc.get("m_nBaseVertex"));
					int vcount = num(dc.get("m_nVertexCount"));
					int start = num(dc.get("m_nStartIndex"));
					int icount = num(dc.get("m_nIndexCount"));
					Map<String, Object> vbRef = (Map<String, Object>) ((List<Object>) dc.get("m_vertexBuffers")).get(0);
					int vbIndex = num(vbRef.get("m_hBuffer"));
					int ibIndex = num(((Map<String, Object>) dc.get("m_indexBuffer")).get("m_hBuffer"));
					Map<String, Object> vb = (Map<String, Object>) vbs.get(vbIndex);
					readVertices(part, vData[vbIndex], vb, baseVertex, vcount, remap, remapStart);
					int[] src = iData[ibIndex];
					part.indices = new int[icount];
					for (int i = 0; i < icount; i++) {
						part.indices[i] = src[start + i];
					}
					parts.add(part);
				}
			}
			meshIdx++;
		}
	}

	private static boolean isIntArray(byte[] raw, int n) {
		return false;
	}

	private static int[] toInts(byte[] raw, int n) {
		int[] out = new int[n];
		ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
		for (int i = 0; i < n; i++) {
			out[i] = b.getInt();
		}
		return out;
	}

	private static int[] indices(byte[] raw, int n, int size) {
		int[] out = new int[n];
		ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
		for (int i = 0; i < n; i++) {
			out[i] = size == 2 ? b.getShort() & 0xFFFF : b.getInt();
		}
		return out;
	}

	@SuppressWarnings("unchecked")
	private void readSkeleton() {
		Map<String, Object> skel = (Map<String, Object>) data.get("m_modelSkeleton");
		if (skel == null) {
			return;
		}
		List<Object> names = (List<Object>) skel.get("m_boneName");
		int[] parents = intArray(skel.get("m_nParent"));
		Object posObj = skel.get("m_bonePosParent");
		Object rotObj = skel.get("m_boneRotParent");
		if (names == null) {
			return;
		}
		for (int i = 0; i < names.size(); i++) {
			Bone bone = new Bone();
			bone.name = (String) names.get(i);
			bone.parent = parents == null ? -1 : parents[i];
			float[] p = vec(posObj, i);
			if (p != null) {
				bone.pos = p;
			}
			float[] q = vec(rotObj, i);
			if (q != null) {
				bone.quat = q;
			}
			bones.add(bone);
		}
	}

	@SuppressWarnings("unchecked")
	private static float[] vec(Object list, int i) {
		if (!(list instanceof List<?> l) || i >= l.size()) {
			return null;
		}
		Object v = l.get(i);
		if (v instanceof float[] f) {
			return f.clone();
		}
		if (v instanceof double[] d) {
			float[] f = new float[d.length];
			for (int k = 0; k < d.length; k++) {
				f[k] = (float) d[k];
			}
			return f;
		}
		if (v instanceof List<?> vl) {
			float[] f = new float[vl.size()];
			for (int k = 0; k < f.length; k++) {
				f[k] = ((Number) vl.get(k)).floatValue();
			}
			return f;
		}
		return null;
	}

	static int num(Object o) {
		return o == null ? 0 : ((Number) o).intValue();
	}

	static int[] intArray(Object o) {
		if (o instanceof int[] a) {
			return a;
		}
		if (o instanceof long[] a) {
			int[] r = new int[a.length];
			for (int i = 0; i < a.length; i++) {
				r[i] = (int) a[i];
			}
			return r;
		}
		if (o instanceof List<?> l) {
			int[] r = new int[l.size()];
			for (int i = 0; i < r.length; i++) {
				r[i] = ((Number) l.get(i)).intValue();
			}
			return r;
		}
		return null;
	}

	private static byte[] buffer(Resource res, Map<String, Object> buf, boolean vertex) {
		int count = num(buf.get("m_nElementCount"));
		int size = num(buf.get("m_nElementSizeInBytes"));
		Object inline = buf.get("m_pData");
		byte[] src;
		int off;
		int len;
		boolean meshopt;
		boolean zstd;
		if (inline instanceof byte[] b) {
			src = b;
			off = 0;
			len = b.length;
			meshopt = len != count * size;
			zstd = false;
		} else {
			Resource.Block block = res.list.get(num(buf.get("m_nBlockIndex")));
			src = res.data;
			off = block.offset();
			len = block.size();
			meshopt = Boolean.TRUE.equals(buf.get("m_bMeshoptCompressed")) || Long.valueOf(1).equals(buf.get("m_bMeshoptCompressed"));
			zstd = Boolean.TRUE.equals(buf.get("m_bCompressedZSTD")) || Long.valueOf(1).equals(buf.get("m_bCompressedZSTD"));
		}
		if (zstd) {
			byte[] d = Zstd.decompressUnknown(src, off, len, count * size + 4096);
			src = d;
			off = 0;
			len = d.length;
		}
		if (!meshopt) {
			byte[] out = new byte[len];
			System.arraycopy(src, off, out, 0, len);
			return out;
		}
		if (vertex) {
			return Meshopt.decodeVertices(count, size, src, off, len);
		}
		int[] idx = Meshopt.decodeIndices(count, src, off, len);
		ByteBuffer b = ByteBuffer.allocate(count * size).order(ByteOrder.LITTLE_ENDIAN);
		for (int i : idx) {
			if (size == 2) {
				b.putShort((short) i);
			} else {
				b.putInt(i);
			}
		}
		return b.array();
	}

	@SuppressWarnings("unchecked")
	private static void readVertices(Part part, byte[] vdata, Map<String, Object> vb, int base, int count, int[] remap, int remapStart) {
		int stride = num(vb.get("m_nElementSizeInBytes"));
		part.vertexCount = count;
		ByteBuffer b = ByteBuffer.wrap(vdata).order(ByteOrder.LITTLE_ENDIAN);
		for (Object fo : (List<Object>) vb.get("m_inputLayoutFields")) {
			Map<String, Object> f = (Map<String, Object>) fo;
			Object sn = f.get("m_pSemanticName");
			String sem = sn instanceof byte[] bytes ? new String(bytes).trim().replace("\0", "") : String.valueOf(sn);
			sem = sem.toUpperCase();
			int semIndex = num(f.get("m_nSemanticIndex"));
			int fmt = num(f.get("m_Format"));
			int off = num(f.get("m_nOffset"));
			switch (sem) {
				case "POSITION" -> {
					part.pos = new float[count * 3];
					for (int i = 0; i < count; i++) {
						int p = (base + i) * stride + off;
						for (int k = 0; k < 3; k++) {
							part.pos[i * 3 + k] = b.getFloat(p + k * 4);
						}
					}
				}
				case "TEXCOORD" -> {
					if (semIndex != 0 || part.uv != null) {
						break;
					}
					part.uv = new float[count * 2];
					for (int i = 0; i < count; i++) {
						int p = (base + i) * stride + off;
						for (int k = 0; k < 2; k++) {
							float v = switch (fmt) {
								case 16 -> b.getFloat(p + k * 4);
								case 34 -> Float.float16ToFloat(b.getShort(p + k * 2));
								case 35 -> (b.getShort(p + k * 2) & 0xFFFF) / 65535f;
								case 37 -> b.getShort(p + k * 2) / 32767f;
								default -> 0f;
							};
							part.uv[i * 2 + k] = v;
						}
					}
				}
				case "NORMAL" -> {
					part.normal = new float[count * 3];
					for (int i = 0; i < count; i++) {
						int p = (base + i) * stride + off;
						if (fmt == 6) {
							for (int k = 0; k < 3; k++) {
								part.normal[i * 3 + k] = b.getFloat(p + k * 4);
							}
						} else if (fmt == 42) {
							decodeFrame2(b.getInt(p), part.normal, i * 3);
						} else if (fmt == 28) {
							decodeFrame1(b.get(p) & 0xFF, b.get(p + 1) & 0xFF, part.normal, i * 3);
						}
					}
				}
				case "BLENDINDICES" -> {
					part.bones = new int[count * 4];
					for (int i = 0; i < count; i++) {
						int p = (base + i) * stride + off;
						for (int k = 0; k < 4; k++) {
							int v = switch (fmt) {
								case 30 -> b.get(p + k) & 0xFF;
								case 14 -> b.getShort(p + k * 2) & 0xFFFF;
								case 38 -> b.getShort(p + Math.min(k, 1) * 2) & 0xFFFF;
								default -> 0;
							};
							if (remap != null && remapStart >= 0 && remapStart + v < remap.length) {
								v = remap[remapStart + v];
							}
							part.bones[i * 4 + k] = v;
						}
					}
				}
				case "BLENDWEIGHT", "BLENDWEIGHTS" -> {
					part.weights = new float[count * 4];
					for (int i = 0; i < count; i++) {
						int p = (base + i) * stride + off;
						for (int k = 0; k < 4; k++) {
							part.weights[i * 4 + k] = switch (fmt) {
								case 28 -> (b.get(p + k) & 0xFF) / 255f;
								case 11 -> (b.getShort(p + k * 2) & 0xFFFF) / 65535f;
								case 35 -> k < 2 ? (b.getShort(p + k * 2) & 0xFFFF) / 65535f : 0f;
								default -> 0f;
							};
						}
					}
				}
				default -> {
				}
			}
		}
	}

	/** CS2 compressed tangent frame: octahedral normal in 20 bits. */
	private static void decodeFrame2(int packed, float[] out, int o) {
		float x = ((packed >>> 12) & 0x3FF) / 1023f * 2f - 1f;
		float y = ((packed >>> 22) & 0x3FF) / 1023f * 2f - 1f;
		float z = 1f - Math.abs(x) - Math.abs(y);
		float neg = Math.max(0f, Math.min(1f, -z));
		x += x >= 0 ? -neg : neg;
		y += y >= 0 ? -neg : neg;
		float l = (float) Math.sqrt(x * x + y * y + z * z);
		out[o] = x / l;
		out[o + 1] = y / l;
		out[o + 2] = z / l;
	}

	private static void decodeFrame1(int bx, int by, float[] out, int o) {
		float x = bx - 128f;
		float y = by - 128f;
		float zSign = x < 0 ? -1 : 1;
		float tSign = y < 0 ? -1 : 1;
		x = (Math.abs(x) - 64f) / 64f;
		y = (Math.abs(y) - 64f) / 64f;
		float z = 1f - Math.abs(x) - Math.abs(y);
		float l = (float) Math.sqrt(x * x + y * y + z * z);
		out[o] = x / l;
		out[o + 1] = y / l;
		out[o + 2] = z * zSign / l;
	}
}
