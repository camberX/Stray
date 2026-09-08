package dev.voidmark.client.visual;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.voidmark.Voidmark;
import dev.voidmark.client.config.VoidmarkConfig;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import java.util.function.Function;

public final class HeldItemShader {
	private static final Identifier PIPELINE_ID = Voidmark.id("pipeline/held_item");
	private static final Identifier SHADER_ID = Voidmark.id("core/held_item");
	private static RenderPipeline pipeline;
	private static final Function<Identifier, RenderType> TYPES = Util.memoize(HeldItemShader::createType);

	private HeldItemShader() {
	}

	public static boolean active() {
		return VoidmarkConfig.get().heldItemShaderEnabled;
	}

	public static boolean applies(ItemDisplayContext context) {
		return active() && context != null && context.firstPerson();
	}

	public static RenderPipeline pipeline() {
		ensureRegistered();
		return pipeline;
	}

	public static boolean isPipeline(RenderPipeline value) {
		return value != null && PIPELINE_ID.equals(value.getLocation());
	}

	public static synchronized void ensureRegistered() {
		if (pipeline != null) {
			return;
		}
		pipeline = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.ITEM_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(PIPELINE_ID)
				.withVertexShader(SHADER_ID)
				.withFragmentShader(SHADER_ID)
				.withSampler("Sampler1")
				.withShaderDefine("ALPHA_CUTOUT", 0.1f)
				.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
				.build()
		);
	}

	public static RenderType wrap(RenderType original, Iterable<BakedQuad> quads) {
		if (original == null || isPipeline(original.pipeline())) {
			return original;
		}
		Identifier atlas = TextureAtlas.LOCATION_ITEMS;
		if (quads != null) {
			for (BakedQuad quad : quads) {
				if (quad.materialInfo().itemRenderType() == original) {
					atlas = quad.materialInfo().sprite().atlasLocation();
					break;
				}
			}
		}
		return TYPES.apply(atlas);
	}

	public static Vector4fc colorModulator() {
		VoidmarkConfig config = VoidmarkConfig.get();
		int rgb = config.heldItemShaderRgb;
		return new Vector4f(
			((rgb >> 16) & 0xFF) / 255f,
			((rgb >> 8) & 0xFF) / 255f,
			(rgb & 0xFF) / 255f,
			VoidmarkConfig.clamp(config.heldItemShaderFill, 0.08f, 0.85f)
		);
	}

	public static Vector3fc modelOffset() {
		VoidmarkConfig config = VoidmarkConfig.get();
		return new Vector3f(
			VoidmarkConfig.clamp(config.heldItemShaderOutline, 0.15f, 1.50f),
			VoidmarkConfig.clamp(config.heldItemShaderSmoke, 0.10f, 1.50f),
			config.heldItemShaderStyleIndex()
		);
	}

	private static RenderType createType(Identifier atlas) {
		ensureRegistered();
		return RenderType.create(
			"voidmark_held_item",
			RenderSetup.builder(pipeline())
				.withTexture("Sampler0", atlas)
				.withTexture("Sampler1", ItemFeatureRenderer.ENCHANTED_GLINT_ITEM)
				.useLightmap()
				.affectsCrumbling()
				.sortOnUpload()
				.setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
				.createRenderSetup()
		);
	}
}
