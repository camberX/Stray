package dev.stray.client.render;

import com.mojang.blaze3d.opengl.GlRenderPipeline;
import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL41;

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
		Identifier fragment = pipeline.info().getFragmentShader();
		if (fragment == null || !fragment.getPath().contains("terrain")) {
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
		GL41.glProgramUniform3f(program, location, TopDownCapture.cutThreshold(), 6f, 1f);
	}

	public static void clear() {
		for (Map.Entry<Integer, Integer> entry : locations.entrySet()) {
			if (entry.getKey() > 0 && entry.getValue() >= 0) {
				GL41.glProgramUniform3f(entry.getKey(), entry.getValue(), 0f, 0f, 0f);
			}
		}
	}

	private static int find(int program) {
		return GlStateManager._glGetUniformLocation(program, "StrayCut");
	}
}
