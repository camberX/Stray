package dev.stray.client.render;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

	/**
	 * Matches Sodium's camera-relative position line in any version, e.g.
	 * {@code vec3 position = _vert_position + translation;} (0.9.3-beta.1) and
	 * {@code vec3 position = (_vert_position + translation) - u_CameraOffset;}
	 * (0.9.3-beta.3).
	 */
	private static final Pattern SODIUM_POSITION = Pattern.compile("\\bvec3 position = [^;\\n]+;");

	/**
	 * The fragment patch reads {@code strayRel}, so whenever it can apply the
	 * vertex shader must write it, or the pipeline fails to link and the game
	 * crashes. The output is always declared and set to a value that never
	 * discards; the real position is used when Sodium's position line is found.
	 */
	public static String patchSodiumVertex(String source) {
		if (source == null || source.contains("strayRel") || !source.contains("_vert_position")
			|| !source.contains("out vec2 v_TexCoord;") || !source.contains("void main() {")) {
			return source;
		}
		String patched = source
			//? if >=26.3 {
			.replace("out vec2 v_TexCoord;", "out vec2 v_TexCoord;\nlayout(location = 14) out vec3 strayRel;")
			//?} else {
			/*.replace("out vec2 v_TexCoord;", "out vec2 v_TexCoord;\nout vec3 strayRel;")*///?}
			.replace("void main() {", "void main() {\n    strayRel = vec3(0.0, -1.0e9, 0.0);");
		Matcher position = SODIUM_POSITION.matcher(patched);
		if (!position.find()) {
			return patched;
		}
		return patched.substring(0, position.end()) + "\n    strayRel = position;" + patched.substring(position.end());
	}

	public static String patchSodiumFragment(String source) {
		if (source == null || source.contains("u_StrayCut.z") || !source.contains("in vec2 v_TexCoord;") || !source.contains("color *= v_Color;")) {
			return source;
		}
		String withIn = source.replace(
			"in vec2 v_TexCoord;",
			//? if >=26.3 {
			"in vec2 v_TexCoord;\nlayout(location = 14) in vec3 strayRel;"
			//?} else {
			/*"in vec2 v_TexCoord;\nin vec3 strayRel;"*///?}
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
				//? if >=26.3 {
				.replace("out vec2 texCoord0;", "out vec2 texCoord0;\nlayout(location = 14) out vec3 strayRel;")
				//?} else {
				/*.replace("out vec2 texCoord0;", "out vec2 texCoord0;\nout vec3 strayRel;")*///?}
				.replace("texCoord0 = UV0;", "texCoord0 = UV0;\n    strayRel = pos;");
		}
		//? if >=26.3 {
		if (source.contains("chunkVisibility") && source.contains("in vec2 texCoord0;") && source.contains("void main() {")) {
		//?} else {
		/*if (source.contains("ChunkVisibility") && source.contains("in vec2 texCoord0;") && source.contains("void main() {")) {*///?}
			String withIn = source.replace(
				"in vec2 texCoord0;",
				//? if >=26.3 {
				"in vec2 texCoord0;\nlayout(location = 14) in vec3 strayRel;"
				//?} else {
				/*"in vec2 texCoord0;\nin vec3 strayRel;"*///?}
			);
			return StrayUniforms.insertBlock(withIn).replace(
				"void main() {",
				"void main() {\n    " + discard("u_StrayCut") + "\n"
			);
		}
		return source;
	}
}
