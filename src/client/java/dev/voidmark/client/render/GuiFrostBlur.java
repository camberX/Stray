package dev.voidmark.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.UniformValue;

import java.util.List;
import java.util.Map;

/**
 * Control frost stays on the pane. Vanilla box_blur is full-screen, so we do
 * not write a larger Radius into it.
 */
public final class GuiFrostBlur {
	private GuiFrostBlur() {
	}

	public static void register(PostPass pass, RenderPipeline pipeline, Map<String, List<UniformValue>> uniforms) {
	}

	public static void unregister(PostPass pass) {
	}

	public static void apply(PostPass pass, Map<String, GpuBuffer> customUniforms) {
	}
}
