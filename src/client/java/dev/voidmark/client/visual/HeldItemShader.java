package dev.voidmark.client.visual;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import dev.voidmark.Voidmark;
import dev.voidmark.client.config.VoidmarkConfig;
import net.minecraft.client.renderer.MultiBufferSource;
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
	private static final int[] OUTLINE_OFFSETS = {
		packOffset(-1, -1), packOffset(-1, 0), packOffset(-1, 1),
		packOffset(0, -1), packOffset(0, 1),
		packOffset(1, -1), packOffset(1, 0), packOffset(1, 1)
	};
	private static final Identifier FILL_PIPELINE_ID = Voidmark.id("pipeline/held_item");
	private static final Identifier OUTLINE_PIPELINE_ID = Voidmark.id("pipeline/held_item_outline");
	private static final Identifier FILL_SHADER_ID = Voidmark.id("core/held_item");
	private static final Identifier OUTLINE_SHADER_ID = Voidmark.id("core/held_item_outline");
	private static RenderPipeline fillPipeline;
	private static RenderPipeline outlinePipeline;
	private static final Function<Identifier, RenderType> FILL_TYPES = Util.memoize(HeldItemShader::createFillType);
	private static final Function<Identifier, RenderType> OUTLINE_TYPES = Util.memoize(HeldItemShader::createOutlineType);

	private HeldItemShader() {
	}

	public static boolean active() {
		return VoidmarkConfig.get().heldItemShaderEnabled;
	}

	public static boolean applies(ItemDisplayContext context) {
		return active() && context != null && context.firstPerson();
	}

	public static boolean isPipeline(RenderPipeline value) {
		return value != null && (FILL_PIPELINE_ID.equals(value.getLocation()) || OUTLINE_PIPELINE_ID.equals(value.getLocation()));
	}

	public static synchronized void ensureRegistered() {
		if (fillPipeline != null) {
			return;
		}
		fillPipeline = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.ITEM_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(FILL_PIPELINE_ID)
				.withVertexShader(FILL_SHADER_ID)
				.withFragmentShader(FILL_SHADER_ID)
				.withSampler("Sampler1")
				.withShaderDefine("ALPHA_CUTOUT", 0.1f)
				.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
				.build()
		);
		outlinePipeline = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.ITEM_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(OUTLINE_PIPELINE_ID)
				.withVertexShader(OUTLINE_SHADER_ID)
				.withFragmentShader(OUTLINE_SHADER_ID)
				.withShaderDefine("ALPHA_CUTOUT", 0.1f)
				.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
				.build()
		);
	}

	public static void drawPixelOutline(
		MultiBufferSource.BufferSource bufferSource,
		PoseStack.Pose pose,
		Iterable<BakedQuad> quads,
		QuadInstance quadInstance
	) {
		if (quads == null) {
			return;
		}
		for (int color : OUTLINE_OFFSETS) {
			quadInstance.setColor(color);
			for (BakedQuad quad : quads) {
				bufferSource.getBuffer(outlineType(quad)).putBakedQuad(pose, quad, quadInstance);
			}
		}
	}

	private static int packOffset(int dx, int dy) {
		int red = (dx + 1) * 127;
		int green = (dy + 1) * 127;
		return 0xFF000000 | (red << 16) | (green << 8) | 0xFF;
	}

	public static RenderType wrap(RenderType original, Iterable<BakedQuad> quads) {
		if (original == null || isPipeline(original.pipeline())) {
			return original;
		}
		return FILL_TYPES.apply(atlas(original, quads));
	}

	public static RenderType outlineType(BakedQuad quad) {
		Identifier atlas = TextureAtlas.LOCATION_ITEMS;
		if (quad != null) {
			atlas = quad.materialInfo().sprite().atlasLocation();
		}
		return OUTLINE_TYPES.apply(atlas);
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

	private static Identifier atlas(RenderType original, Iterable<BakedQuad> quads) {
		Identifier atlas = TextureAtlas.LOCATION_ITEMS;
		if (quads != null) {
			for (BakedQuad quad : quads) {
				if (quad.materialInfo().itemRenderType() == original) {
					return quad.materialInfo().sprite().atlasLocation();
				}
			}
		}
		return atlas;
	}

	private static RenderType createFillType(Identifier atlas) {
		ensureRegistered();
		return RenderType.create(
			"voidmark_held_item",
			RenderSetup.builder(fillPipeline)
				.withTexture("Sampler0", atlas)
				.withTexture("Sampler1", ItemFeatureRenderer.ENCHANTED_GLINT_ITEM)
				.useLightmap()
				.affectsCrumbling()
				.setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
				.createRenderSetup()
		);
	}

	private static RenderType createOutlineType(Identifier atlas) {
		ensureRegistered();
		return RenderType.create(
			"voidmark_held_item_outline",
			RenderSetup.builder(outlinePipeline)
				.withTexture("Sampler0", atlas)
				.useLightmap()
				.affectsCrumbling()
				.setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
				.createRenderSetup()
		);
	}
}
