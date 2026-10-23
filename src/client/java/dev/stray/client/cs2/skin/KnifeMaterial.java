package dev.stray.client.cs2.skin;

import dev.stray.client.cs2.s2.Resource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The knife's own material plus its composite inputs (masks, AO, position and surface maps). */
public final class KnifeMaterial {
	public final String material;
	public final Map<String, String> base = new HashMap<>();
	public final Map<String, String> inputs = new HashMap<>();
	public float uvScale = 1f;
	public float weaponLength = 10f;

	@SuppressWarnings("unchecked")
	public KnifeMaterial(Assets assets, String material) {
		this.material = material;
		Resource r = assets.resource(material);
		if (r == null) {
			return;
		}
		Map<String, Object> d = (Map<String, Object>) r.kv3("DATA");
		textures(d, base);
		String inputsPath = null;
		for (Object o : (List<Object>) d.getOrDefault("m_stringAttributes", List.of())) {
			Map<String, Object> m = (Map<String, Object>) o;
			if ("composite_inputs".equals(m.get("m_name"))) {
				inputsPath = String.valueOf(m.get("m_value"));
			}
		}
		if (inputsPath != null) {
			Resource ir = assets.resource(inputsPath);
			if (ir != null) {
				Map<String, Object> id = (Map<String, Object>) ir.kv3("DATA");
				textures(id, inputs);
				for (Object o : (List<Object>) id.getOrDefault("m_floatParams", List.of())) {
					Map<String, Object> m = (Map<String, Object>) o;
					float v = ((Number) m.get("m_flValue")).floatValue();
					if ("g_flUvScale1".equals(m.get("m_name"))) {
						uvScale = v;
					} else if ("g_flWeaponLength1".equals(m.get("m_name"))) {
						weaponLength = v;
					}
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static void textures(Map<String, Object> d, Map<String, String> out) {
		for (Object o : (List<Object>) d.getOrDefault("m_textureParams", List.of())) {
			Map<String, Object> m = (Map<String, Object>) o;
			out.put((String) m.get("m_name"), String.valueOf(m.get("m_pValue")));
		}
	}

	public String input(String key) {
		String v = inputs.get(key);
		return v != null ? v : base.get(key);
	}
}
