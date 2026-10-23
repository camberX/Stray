package dev.stray.client.cs2.anim;

import dev.stray.client.cs2.s2.Resource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A .vnmskel: bone names, parents and the parent-space reference pose. */
public final class NmSkeleton {
	public final String[] names;
	public final int[] parents;
	/** Per bone: x y z, qx qy qz qw, scale (parent space). */
	public final float[][] reference;
	private final Map<String, Integer> index = new HashMap<>();

	@SuppressWarnings("unchecked")
	public NmSkeleton(Resource res) {
		Map<String, Object> d = (Map<String, Object>) res.kv3("DATA");
		List<Object> ids = (List<Object>) d.get("m_boneIDs");
		names = new String[ids.size()];
		for (int i = 0; i < names.length; i++) {
			names[i] = String.valueOf(ids.get(i));
			index.put(names[i].toLowerCase(), i);
		}
		parents = toInts(d.get("m_parentIndices"), names.length);
		reference = new float[names.length][];
		List<Object> ref = (List<Object>) d.get("m_parentSpaceReferencePose");
		for (int i = 0; i < names.length; i++) {
			reference[i] = transform(ref == null || i >= ref.size() ? null : ref.get(i));
		}
	}

	public int bone(String name) {
		Integer i = index.get(name.toLowerCase());
		return i == null ? -1 : i;
	}

	/** Valve stores a transform as position xyz, uniform scale, quaternion xyzw (7 floats when scale is omitted). */
	static float[] transform(Object o) {
		float[] out = {0, 0, 0, 0, 0, 0, 1, 1};
		double[] v = null;
		if (o instanceof double[] d) {
			v = d;
		} else if (o instanceof float[] f) {
			v = new double[f.length];
			for (int i = 0; i < f.length; i++) {
				v[i] = f[i];
			}
		}
		if (v == null || v.length < 7) {
			return out;
		}
		out[0] = (float) v[0];
		out[1] = (float) v[1];
		out[2] = (float) v[2];
		int r = v.length == 7 ? 3 : 4;
		out[7] = v.length == 7 ? 1f : (float) v[3];
		out[3] = (float) v[r];
		out[4] = (float) v[r + 1];
		out[5] = (float) v[r + 2];
		out[6] = (float) v[r + 3];
		return out;
	}

	static int[] toInts(Object o, int n) {
		int[] r = new int[n];
		if (o instanceof int[] a) {
			System.arraycopy(a, 0, r, 0, Math.min(n, a.length));
		} else if (o instanceof long[] a) {
			for (int i = 0; i < Math.min(n, a.length); i++) {
				r[i] = (int) a[i];
			}
		} else if (o instanceof List<?> l) {
			for (int i = 0; i < Math.min(n, l.size()); i++) {
				r[i] = ((Number) l.get(i)).intValue();
			}
		}
		return r;
	}
}
