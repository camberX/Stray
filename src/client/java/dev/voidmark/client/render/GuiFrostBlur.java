package dev.voidmark.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.ui.VoidmarkScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.UniformValue;
import net.minecraft.resources.Identifier;
import org.joml.Vector2f;
import org.lwjgl.system.MemoryStack;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes the Control frost radius into vanilla {@code minecraft:post/box_blur}.
 * JSON Radius is 0, so the shader otherwise uses the small MenuBlurRadius.
 */
public final class GuiFrostBlur {
	public static final float MIN = 3f;
	public static final float MAX = 28f;
	private static final Identifier SHADER = Identifier.fromNamespaceAndPath("minecraft", "post/box_blur");
	private static final IdentityHashMap<PostPass, Vector2f> DIRECTIONS = new IdentityHashMap<>();

	private GuiFrostBlur() {
	}

	public static boolean isBoxBlur(RenderPipeline pipeline) {
		return pipeline != null && SHADER.equals(pipeline.getFragmentShader());
	}

	public static void register(PostPass pass, RenderPipeline pipeline, Map<String, List<UniformValue>> uniforms) {
		if (!isBoxBlur(pipeline) || uniforms == null) {
			return;
		}
		Vector2f dir = new Vector2f(1f, 0f);
		List<UniformValue> values = uniforms.get("BlurConfig");
		if (values != null) {
			for (UniformValue value : values) {
				if (value instanceof UniformValue.Vec2Uniform vec) {
					dir.set(vec.value());
				}
			}
		}
		DIRECTIONS.put(pass, dir);
	}

	public static void unregister(PostPass pass) {
		DIRECTIONS.remove(pass);
	}

	public static float radius() {
		return MIN + VoidmarkConfig.clamp(VoidmarkConfig.get().controlFrost, 0f, 1f) * (MAX - MIN);
	}

	public static void apply(PostPass pass, Map<String, GpuBuffer> customUniforms) {
		Vector2f dir = DIRECTIONS.get(pass);
		if (dir == null || customUniforms == null) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client == null || !(client.screen instanceof VoidmarkScreen) || !VoidmarkConfig.get().guiDesignControl()) {
			return;
		}
		GpuBuffer buffer = customUniforms.get("BlurConfig");
		if (buffer == null || buffer.isClosed()) {
			return;
		}
		int size = new Std140SizeCalculator().putVec2().putFloat().get();
		if (buffer.size() < size) {
			return;
		}
		try (MemoryStack stack = MemoryStack.stackPush()) {
			RenderSystem.getDevice().createCommandEncoder().writeToBuffer(
				buffer.slice(),
				Std140Builder.onStack(stack, size)
					.putVec2(dir.x, dir.y)
					.putFloat(radius())
					.get()
			);
		}
	}
}
