package dev.stray.client.render;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.UniformType;
import java.nio.ByteBuffer;
//? if >=26.3 {
import net.minecraft.client.renderer.DynamicGpuDataStorage;
import net.minecraft.client.renderer.DynamicGpuDataStorageMapped;
//?} else {
/*import net.minecraft.client.renderer.DynamicUniformStorage;*///?}

/**
 * Focus, world tint, fog density, and the top-down cut share one std140 block.
 * Values go through the same per-frame mapped ring buffer vanilla uses for its
 * dynamic transforms, so writing works mid-pass on both OpenGL and Vulkan.
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

	private static final int BLOCK_BYTES = 48;
	private static BindGroupLayout layout;
	//? if >=26.3 {
	private static DynamicGpuDataStorageMapped<Block> storage;
	//?} else {
	/*private static DynamicUniformStorage<Block> storage;*///?}
	private static float cutY;
	private static float cutRadius;
	private static float cutEnabled;
	private static float tintRed;
	private static float tintGreen;
	private static float tintBlue;
	private static float tintStrength;
	private static float focus;
	private static float fog;

	private StrayUniforms() {
	}

	private record Block(
		float cutY, float cutRadius, float cutEnabled,
		float tintRed, float tintGreen, float tintBlue, float tintStrength,
		float focus, float fog
	//? if >=26.3 {
	) implements DynamicGpuDataStorage.DynamicGpuData {
	//?} else {
	/*) implements DynamicUniformStorage.DynamicUniform {*///?}
		@Override
		public void write(ByteBuffer buffer) {
			int base = buffer.position();
			buffer.putFloat(base, cutY);
			buffer.putFloat(base + 4, cutRadius);
			buffer.putFloat(base + 8, cutEnabled);
			buffer.putFloat(base + 12, 0f);
			buffer.putFloat(base + 16, tintRed);
			buffer.putFloat(base + 20, tintGreen);
			buffer.putFloat(base + 24, tintBlue);
			buffer.putFloat(base + 28, tintStrength);
			buffer.putFloat(base + 32, focus);
			buffer.putFloat(base + 36, fog);
			buffer.putFloat(base + 40, 0f);
			buffer.putFloat(base + 44, 0f);
		}
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
		cutY = y;
		cutRadius = radius;
		cutEnabled = enabled;
	}

	public static void tint(float red, float green, float blue, float strength) {
		tintRed = red;
		tintGreen = green;
		tintBlue = blue;
		tintStrength = strength;
	}

	public static void focus(float enabled) {
		focus = enabled;
	}

	public static void fog(float enabled) {
		fog = enabled;
	}

	/** Writes the current values (deduplicated within the frame) and returns their slice. */
	public static GpuBufferSlice upload() {
		if (storage == null) {
			//? if >=26.3 {
			storage = new DynamicGpuDataStorageMapped<>("Stray block UBO", BLOCK_BYTES, 128, 64);
			//?} else {
			/*storage = new DynamicUniformStorage<>("Stray block UBO", BLOCK_BYTES, 64);*///?}
		}
		//? if >=26.3 {
		return storage.writeData(new Block(cutY, cutRadius, cutEnabled, tintRed, tintGreen, tintBlue, tintStrength, focus, fog));
		//?} else {
		/*return storage.writeUniform(new Block(cutY, cutRadius, cutEnabled, tintRed, tintGreen, tintBlue, tintStrength, focus, fog));*///?}
	}

	/** Called once per frame together with vanilla's dynamic uniforms. */
	public static void endFrame() {
		if (storage != null) {
			storage.endFrame();
		}
	}
}
