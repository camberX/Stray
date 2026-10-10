package dev.stray.client.render;

/**
 * Terrain fragments above the player, within 6 blocks, are discarded while the
 * top-down picture is taken. The uniform is cleared afterwards so the main view
 * is unchanged.
 */
public final class TopDownTerrainCut {
	private TopDownTerrainCut() {
	}

	public static void bind() {
		if (TopDownCapture.capturing()) {
			StrayUniforms.cut(TopDownCapture.cutThreshold(), 6f, 1f);
		} else {
			StrayUniforms.cut(0f, 0f, 0f);
		}
	}

	public static void clear() {
		StrayUniforms.cut(0f, 0f, 0f);
	}

	public static String patchSodiumVertex(String source) {
		if (source == null || source.contains("strayRel") || !source.contains("vec3 position = _vert_position + translation;")) {
			return source;
		}
		return source
			.replace("out vec2 v_TexCoord;", "out vec2 v_TexCoord;\nlayout(location = 7) out vec3 strayRel;")
			.replace(
				"vec3 position = _vert_position + translation;",
				"vec3 position = _vert_position + translation;\n    strayRel = position;"
			);
	}

	public static String patchSodiumFragment(String source) {
		if (source == null || source.contains("u_StrayCut.z") || !source.contains("in vec2 v_TexCoord;") || !source.contains("color *= v_Color;")) {
			return source;
		}
		String withIn = source.replace(
			"in vec2 v_TexCoord;",
			"in vec2 v_TexCoord;\nlayout(location = 7) in vec3 strayRel;"
		);
		return StrayUniforms.insertBlock(withIn).replace(
			"void main() {",
			"void main() {\n    if (u_StrayCut.z > 0.5 && dot(strayRel.xz, strayRel.xz) <= u_StrayCut.y * u_StrayCut.y && strayRel.y > u_StrayCut.x) discard;"
		);
	}

	public static String patchSource(String source) {
		if (source == null || source.contains("strayRel")) {
			return source;
		}
		if (source.contains("ChunkPosition") && source.contains("vec3 pos =")) {
			return source
				.replace("out vec2 texCoord0;", "out vec2 texCoord0;\nlayout(location = 7) out vec3 strayRel;")
				.replace("texCoord0 = UV0;", "texCoord0 = UV0;\n    strayRel = pos;");
		}
		if (source.contains("chunkVisibility") && source.contains("in vec2 texCoord0;") && source.contains("void main() {")) {
			String withIn = source.replace(
				"in vec2 texCoord0;",
				"in vec2 texCoord0;\nlayout(location = 7) in vec3 strayRel;"
			);
			return StrayUniforms.insertBlock(withIn).replace(
				"void main() {",
				"void main() {\n    if (u_StrayCut.z > 0.5 && dot(strayRel.xz, strayRel.xz) <= u_StrayCut.y * u_StrayCut.y && strayRel.y > u_StrayCut.x) discard;"
			);
		}
		return source;
	}
}
