package dev.stray.client.render;

/**
 * Full blocks from 2 above the player's block position, within 6 blocks, are
 * discarded while the top-down picture is taken. The uniform is cleared
 * afterwards so the main view is unchanged.
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

	/**
	 * {@code uniform.x} is the camera-relative Y where the block two above the
	 * player starts. {@code floor} drops that whole block and everything above
	 * it. The top face of the block below sits on the plane and stays.
	 */
	private static String discard(String uniform) {
		return "if (" + uniform + ".z > 0.5 && dot(strayRel.xz, strayRel.xz) <= " + uniform + ".y * " + uniform + ".y && floor(strayRel.y - " + uniform + ".x - 0.001) >= 0.0) discard;";
	}

	public static String patchSodiumVertex(String source) {
		if (source == null || source.contains("strayRel") || !source.contains("vec3 position = _vert_position + translation;")) {
			return source;
		}
		return source
			.replace("out vec2 v_TexCoord;", "out vec2 v_TexCoord;\nout vec3 strayRel;")
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
			"in vec2 v_TexCoord;\nin vec3 strayRel;"
		);
		return StrayUniforms.insertBlock(withIn).replace(
			"void main() {",
			"void main() {\n    " + discard("u_StrayCut")
		);
	}

	public static String patchSource(String source) {
		if (source == null || source.contains("strayRel")) {
			return source;
		}
		if (source.contains("ChunkPosition") && source.contains("vec3 pos =")) {
			return source
				.replace("out vec2 texCoord0;", "out vec2 texCoord0;\nout vec3 strayRel;")
				.replace("texCoord0 = UV0;", "texCoord0 = UV0;\n    strayRel = pos;");
		}
		if (source.contains("ChunkVisibility") && source.contains("in vec2 texCoord0;") && source.contains("void main() {")) {
			String withIn = source.replace(
				"in vec2 texCoord0;",
				"in vec2 texCoord0;\nin vec3 strayRel;"
			);
			return StrayUniforms.insertBlock(withIn).replace(
				"void main() {",
				"void main() {\n    " + discard("u_StrayCut") + "\n"
			);
		}
		return source;
	}
}
