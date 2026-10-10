package dev.stray.client.render;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.backend.opengl.GlRenderPipeline;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

/**
 * Focus, world tint, fog density, and the top-down cut share one std140 block.
 * Renderpearl compiles GLSL to SPIR-V, which rejects loose uniforms and inputs
 * without locations, so the values live here and are bound on each draw.
 */
public final class StrayUniforms {
	private static final String BLOCK = """
		#ifndef STRAY_BLOCK
		#define STRAY_BLOCK
		layout(std140) uniform StrayBlock {
		    vec4 u_StrayCut;
		    vec4 u_WorldTint;
		    vec4 u_StrayMisc;
		};
		#endif
		""";

	private static final ByteBuffer DATA = ByteBuffer.allocateDirect(48).order(ByteOrder.nativeOrder());
	private static final Map<Integer, Integer> BINDINGS = new HashMap<>();
	private static BindGroupLayout layout;
	private static int buffer;

	private StrayUniforms() {
	}

	public static BindGroupLayout layout() {
		BindGroupLayout current = layout;
		if (current == null) {
			current = BindGroupLayout.builder()
				.withUniform("StrayBlock", UniformType.UNIFORM_BUFFER)
				.build();
			layout = current;
		}
		return current;
	}

	public static String insertBlock(String source) {
		if (source == null || source.contains("STRAY_BLOCK")) {
			return source;
		}
		int version = source.indexOf("#version");
		if (version < 0) {
			return BLOCK + "\n" + source;
		}
		int at = source.indexOf('\n', version);
		if (at < 0) {
			return source + "\n" + BLOCK;
		}
		at++;
		while (source.startsWith("#extension", at)) {
			int next = source.indexOf('\n', at);
			if (next < 0) {
				break;
			}
			at = next + 1;
		}
		return source.substring(0, at) + BLOCK + "\n" + source.substring(at);
	}

	public static void cut(float y, float radius, float enabled) {
		DATA.putFloat(0, y);
		DATA.putFloat(4, radius);
		DATA.putFloat(8, enabled);
	}

	public static void tint(float red, float green, float blue, float strength) {
		DATA.putFloat(16, red);
		DATA.putFloat(20, green);
		DATA.putFloat(24, blue);
		DATA.putFloat(28, strength);
	}

	public static void focus(float enabled) {
		DATA.putFloat(32, enabled);
	}

	public static void fog(float enabled) {
		DATA.putFloat(36, enabled);
	}

	public static void apply(GlRenderPipeline pipeline) {
		if (pipeline == null || pipeline.program() == null) {
			return;
		}
		int program = pipeline.program().getProgramId();
		if (program <= 0) {
			return;
		}
		int binding = BINDINGS.computeIfAbsent(program, StrayUniforms::binding);
		if (binding < 0) {
			return;
		}
		int id = buffer();
		DATA.position(0);
		DATA.limit(48);
		GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, id);
		GL15.glBufferSubData(GL31.GL_UNIFORM_BUFFER, 0L, DATA);
		GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, binding, id);
		GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, 0);
	}

	private static int buffer() {
		int id = buffer;
		if (id != 0) {
			return id;
		}
		id = GL15.glGenBuffers();
		GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, id);
		GL15.glBufferData(GL31.GL_UNIFORM_BUFFER, 48L, GL15.GL_DYNAMIC_DRAW);
		GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, 0);
		buffer = id;
		return id;
	}

	private static int binding(int program) {
		int index = GL31.glGetUniformBlockIndex(program, "StrayBlock");
		if (index < 0) {
			return -1;
		}
		int[] params = new int[1];
		GL31.glGetActiveUniformBlockiv(program, index, GL31.GL_UNIFORM_BLOCK_BINDING, params);
		return params[0];
	}
}
