package dev.stray.client.cs2.skin;

import dev.stray.client.cs2.s2.Resource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One finish: the paint material's parameters plus the seed ranges from its composite material. */
public final class PaintKit {
	public final String name;
	public final int style;
	public final Map<String, Float> floats = new HashMap<>();
	public final Map<String, Integer> ints = new HashMap<>();
	public final Map<String, float[]> vectors = new HashMap<>();
	public final Map<String, String> textures = new HashMap<>();
	/** Seed-rolled variables: name -> {minX, maxX, minY, maxY}; missing means the paint's fixed value. */
	public final Map<String, float[]> ranges = new HashMap<>();
	public float wearMin = 0f;
	public float wearMax = 1f;

	@SuppressWarnings("unchecked")
	public PaintKit(String name, Map<String, Object> vmat, Map<String, Object> vcompmat) {
		this.name = name;
		for (Object o : (List<Object>) vmat.getOrDefault("m_intParams", List.of())) {
			Map<String, Object> m = (Map<String, Object>) o;
			ints.put((String) m.get("m_name"), ((Number) m.get("m_nValue")).intValue());
		}
		for (Object o : (List<Object>) vmat.getOrDefault("m_floatParams", List.of())) {
			Map<String, Object> m = (Map<String, Object>) o;
			floats.put((String) m.get("m_name"), ((Number) m.get("m_flValue")).floatValue());
		}
		for (Object o : (List<Object>) vmat.getOrDefault("m_vectorParams", List.of())) {
			Map<String, Object> m = (Map<String, Object>) o;
			vectors.put((String) m.get("m_name"), toFloats(m.get("m_value")));
		}
		for (Object o : (List<Object>) vmat.getOrDefault("m_textureParams", List.of())) {
			Map<String, Object> m = (Map<String, Object>) o;
			textures.put((String) m.get("m_name"), String.valueOf(m.get("m_pValue")));
		}
		style = ints.getOrDefault("F_PAINT_STYLE", 0);
		if (vcompmat != null) {
			collectRanges(vcompmat);
		}
	}

	@SuppressWarnings("unchecked")
	private void collectRanges(Object node) {
		if (node instanceof Map<?, ?> map) {
			Object vars = map.get("m_vecLooseVariables");
			if (vars instanceof List<?> list) {
				for (Object v : list) {
					Map<String, Object> var = (Map<String, Object>) v;
					if (!Boolean.TRUE.equals(var.get("m_bHasFloatBounds"))) {
						continue;
					}
					String n = (String) var.get("m_strName");
					ranges.put(n, new float[]{
						f(var.get("m_flValueFloatX_Min")), f(var.get("m_flValueFloatX_Max")),
						f(var.get("m_flValueFloatY_Min")), f(var.get("m_flValueFloatY_Max"))
					});
				}
			}
			for (Object v : map.values()) {
				collectRanges(v);
			}
		} else if (node instanceof List<?> list) {
			for (Object v : list) {
				collectRanges(v);
			}
		}
	}

	private static float f(Object o) {
		return o instanceof Number n ? n.floatValue() : 0f;
	}

	static float[] toFloats(Object o) {
		if (o instanceof float[] f) {
			return f;
		}
		if (o instanceof double[] d) {
			float[] f = new float[d.length];
			for (int i = 0; i < d.length; i++) {
				f[i] = (float) d[i];
			}
			return f;
		}
		if (o instanceof List<?> l) {
			float[] f = new float[l.size()];
			for (int i = 0; i < f.length; i++) {
				f[i] = ((Number) l.get(i)).floatValue();
			}
			return f;
		}
		return new float[4];
	}

	public float fl(String key, float def) {
		return floats.getOrDefault(key, def);
	}

	public float[] vec(String key) {
		float[] v = vectors.get(key);
		return v == null ? new float[]{0, 0, 0, 0} : v;
	}

	public static PaintKit load(Assets assets, String name) {
		Resource vmat = assets.resource("materials/models/weapons/customization/paints/vmats/" + name + ".vmat");
		if (vmat == null) {
			return null;
		}
		@SuppressWarnings("unchecked")
		Map<String, Object> vm = (Map<String, Object>) vmat.kv3("DATA");
		Resource comp = assets.resource("weapons/paints/legacy/" + name + ".vcompmat");
		@SuppressWarnings("unchecked")
		Map<String, Object> cm = comp == null ? null : (Map<String, Object>) comp.kv3("DATA");
		return new PaintKit(name, vm, cm);
	}

	/** Seed-rolled values in CS2's order (same as CS:GO): pattern offset XY, pattern rotation, then wear and grunge transforms. */
	public static final class Roll {
		public float patternOffsetX;
		public float patternOffsetY;
		public float patternRotation;
		public float wearScale;
		public float wearOffsetX;
		public float wearOffsetY;
		public float wearRotation;
		public float grungeScale;
		public float grungeOffsetX;
		public float grungeOffsetY;
		public float grungeRotation;
	}

	public Roll roll(int seed) {
		ValveRandom r = new ValveRandom(seed);
		Roll o = new Roll();
		float[] off = vec("g_vPatternTexCoordOffset");
		float[] po = ranges.get("g_vPatternTexCoordOffset");
		o.patternOffsetX = draw(r, po, 0, off[0]);
		o.patternOffsetY = draw(r, po, 2, off[1]);
		o.patternRotation = draw(r, ranges.get("g_flPatternTexCoordRotation"), 0, fl("g_flPatternTexCoordRotation", 0f));
		o.wearScale = r.nextFloat(1.6f, 1.8f);
		o.wearOffsetX = r.nextFloat(0f, 1f);
		o.wearOffsetY = r.nextFloat(0f, 1f);
		o.wearRotation = r.nextFloat(0f, 360f);
		o.grungeScale = r.nextFloat(1.6f, 1.8f);
		o.grungeOffsetX = r.nextFloat(0f, 1f);
		o.grungeOffsetY = r.nextFloat(0f, 1f);
		o.grungeRotation = r.nextFloat(0f, 360f);
		return o;
	}

	/** Always consumes one draw so later values line up with the game, rolled or not. */
	private static float draw(ValveRandom r, float[] range, int index, float fixed) {
		if (range == null) {
			r.nextFloat(fixed, fixed);
			return fixed;
		}
		return r.nextFloat(range[index], range[index + 1]);
	}
}
