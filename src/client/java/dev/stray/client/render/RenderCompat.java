package dev.stray.client.render;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import java.util.Optional;

/**
 * Shared pieces of the 26.3 renderpearl pipeline API.
 */
public final class RenderCompat {
	public static final Vector4fc CLEAR_TRANSPARENT = new Vector4f(0f, 0f, 0f, 0f);

	private RenderCompat() {
	}

	public static void setPipeline(RenderPass pass, RenderPipeline pipeline) {
		pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
	}

	public static TextureTarget colorTarget(String name, int width, int height) {
		return new TextureTarget(name, width, height, GpuFormat.RGBA8_UNORM, null);
	}

	public static TextureTarget colorDepthTarget(String name, int width, int height) {
		return new TextureTarget(name, width, height, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
	}

	public static ColorTargetState opaqueTarget() {
		return new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL);
	}

	public static BindGroupLayout sampler(String name) {
		return BindGroupLayout.builder().withUniform(name, UniformType.COMBINED_IMAGE_SAMPLER).build();
	}

	public static BindGroupLayout uniform(String name) {
		return BindGroupLayout.builder().withUniform(name, UniformType.UNIFORM_BUFFER).build();
	}

	public static RenderPipeline.Builder guiBlit(Identifier location, Identifier shader, BlendFunction blend) {
		return RenderPipeline.builder()
			.withLocation(location)
			.withVertexShader(shader)
			.withFragmentShader(shader)
			.withBindGroupLayout(BindGroupLayouts.PROJECTION)
			.withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
			.withBindGroupLayout(BindGroupLayouts.SAMPLER0)
			.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
			.withPrimitiveTopology(PrimitiveTopology.QUADS)
			.withColorTargetState(new ColorTargetState(blend));
	}

	public static RenderPipeline.Builder screenQuad(Identifier location, Identifier fragment) {
		return RenderPipeline.builder()
			.withLocation(location)
			.withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
			.withFragmentShader(fragment)
			.withPrimitiveTopology(PrimitiveTopology.TRIANGLES);
	}
}
