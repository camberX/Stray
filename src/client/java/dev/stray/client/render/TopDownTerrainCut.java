package dev.stray.client.render;

import com.mojang.blaze3d.opengl.GlRenderPipeline;
import com.mojang.blaze3d.opengl.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.util.HashMap;
import java.util.Map;

/**
 * Terrain fragments above the player, within 6 blocks, are discarded while the
 * top-down picture is taken. The uniform is cleared afterwards so the main view
 * is unchanged.
 */
public final class TopDownTerrainCut {
	private static final Map<Integer, Integer> locations = new HashMap<>();

	private TopDownTerrainCut() {
	}

	public static void bind(GlRenderPipeline pipeline) {
		if (!TopDownCapture.capturing() || pipeline == null || pipeline.program() == null) {
			return;
		}
		int program = pipeline.program().getProgramId();
		if (program <= 0) {
			return;
		}
		int location = locations.computeIfAbsent(program, TopDownTerrainCut::find);
		if (location < 0) {
			return;
		}
		uniform(program, location, TopDownCapture.cutThreshold(), 6f, 1f);
	}

	public static void clear() {
		for (Map.Entry<Integer, Integer> entry : locations.entrySet()) {
			if (entry.getKey() > 0 && entry.getValue() >= 0) {
				uniform(entry.getKey(), entry.getValue(), 0f, 0f, 0f);
			}
		}
	}

	private static void uniform(int program, int location, float x, float y, float z) {
		int previous = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
		GL20.glUseProgram(program);
		GL20.glUniform3f(location, x, y, z);
		GL20.glUseProgram(previous);
	}

	private static int find(int program) {
		return GlStateManager._glGetUniformLocation(program, "StrayCut");
	}

	public static String patchSource(String source) {
		if (source == null || source.contains("strayRel")) {
			return source;
		}
		if (source.contains("in vec3 Position") && source.contains("fog_spherical_distance")) {
			return source
				.replace("out vec2 texCoord0;", "out vec2 texCoord0;\nout vec3 strayRel;")
				.replace("texCoord0 = UV0;", "texCoord0 = UV0;\n    strayRel = pos;");
		}
		if (source.contains("fragColor") && source.contains("apply_fog")) {
			return source
				.replace("in vec2 texCoord0;", "in vec2 texCoord0;\nin vec3 strayRel;\nuniform vec3 StrayCut;")
				.replace(
					"void main() {",
					"void main() {\n    if (StrayCut.z > 0.5 && dot(strayRel.xz, strayRel.xz) <= StrayCut.y * StrayCut.y && strayRel.y > StrayCut.x) discard;\n"
				);
		}
		return source;
	}
}
