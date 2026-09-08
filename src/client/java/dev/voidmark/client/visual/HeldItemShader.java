package dev.voidmark.client.visual;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.voidmark.Voidmark;
import dev.voidmark.client.config.VoidmarkConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.OutputTarget;
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

import java.util.OptionalInt;
import java.util.function.Function;

public final class HeldItemShader {
	private static final Identifier FILL_PIPELINE_ID = Voidmark.id("pipeline/held_item");
	private static final Identifier MASK_PIPELINE_ID = Voidmark.id("pipeline/held_item_mask");
	private static final Identifier FILL_SHADER_ID = Voidmark.id("core/held_item");
	private static final Identifier SILHOUETTE_SHADER_ID = Voidmark.id("post/held_item_silhouette");
	private static final OutputTarget MASK_OUTPUT = new OutputTarget("voidmark_held_item_mask", HeldItemShader::maskTarget);
	private static final Function<Identifier, RenderType> FILL_TYPES = Util.memoize(HeldItemShader::createFillType);
	private static final Function<Identifier, RenderType> MASK_TYPES = Util.memoize(HeldItemShader::createMaskType);
	private static RenderPipeline fillPipeline;
	private static RenderPipeline maskPipeline;
	private static RenderPipeline silhouettePipeline;
	private static RenderTarget maskTarget;
	private static boolean maskThisFrame;

	private HeldItemShader() {
	}

	public static boolean active() {
		return VoidmarkConfig.get().heldItemShaderEnabled;
	}

	public static boolean applies(ItemDisplayContext context) {
		return active() && context != null && context.firstPerson();
	}

	public static boolean isFillPipeline(RenderPipeline value) {
		return value != null && FILL_PIPELINE_ID.equals(value.getLocation());
	}

	public static boolean isMaskPipeline(RenderPipeline value) {
		return value != null && MASK_PIPELINE_ID.equals(value.getLocation());
	}

	public static boolean isPipeline(RenderPipeline value) {
		return isFillPipeline(value) || isMaskPipeline(value);
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
		maskPipeline = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.ITEM_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(MASK_PIPELINE_ID)
				.withVertexShader(FILL_SHADER_ID)
				.withFragmentShader(FILL_SHADER_ID)
				.withSampler("Sampler1")
				.withShaderDefine("ALPHA_CUTOUT", 0.1f)
				.withShaderDefine("COVERAGE_MASK")
				.withColorTargetState(ColorTargetState.DEFAULT)
				.build()
		);
	}

	public static RenderType wrap(RenderType original, Iterable<BakedQuad> quads) {
		if (original == null || isPipeline(original.pipeline())) {
			return original;
		}
		return FILL_TYPES.apply(atlas(original, quads));
	}

	public static void beginMask() {
		maskThisFrame = false;
		if (!active()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		RenderTarget main = client.getMainRenderTarget();
		if (main == null || main.width <= 0 || main.height <= 0) {
			return;
		}
		if (maskTarget == null) {
			maskTarget = new TextureTarget("voidmark held item mask", main.width, main.height, true);
		} else if (maskTarget.width != main.width || maskTarget.height != main.height) {
			maskTarget.resize(main.width, main.height);
		}
		if (maskTarget.getColorTexture() == null) {
			return;
		}
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		encoder.clearColorTexture(maskTarget.getColorTexture(), 0);
		if (maskTarget.getDepthTexture() != null) {
			encoder.clearDepthTexture(maskTarget.getDepthTexture(), 1.0);
		}
		maskThisFrame = true;
	}

	public static void drawViewMask(
		MultiBufferSource.BufferSource buffers,
		PoseStack.Pose pose,
		Iterable<BakedQuad> quads,
		QuadInstance quadInstance
	) {
		if (!maskThisFrame || buffers == null || quads == null || pose == null || quadInstance == null) {
			return;
		}
		ensureRegistered();
		for (BakedQuad quad : quads) {
			Identifier atlas = TextureAtlas.LOCATION_ITEMS;
			if (quad != null) {
				atlas = quad.materialInfo().sprite().atlasLocation();
			}
			buffers.getBuffer(MASK_TYPES.apply(atlas)).putBakedQuad(pose, quad, quadInstance);
		}
	}

	public static void compositeSilhouette() {
		if (!maskThisFrame) {
			return;
		}
		maskThisFrame = false;
		Minecraft client = Minecraft.getInstance();
		RenderTarget main = client.getMainRenderTarget();
		if (maskTarget == null || main == null || maskTarget.getColorTextureView() == null || main.getColorTextureView() == null) {
			return;
		}
		ensureSilhouettePipeline();
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
			() -> "voidmark held item silhouette",
			main.getColorTextureView(),
			OptionalInt.empty()
		)) {
			pass.setPipeline(silhouettePipeline);
			RenderSystem.bindDefaultUniforms(pass);
			pass.bindTexture(
				"InSampler",
				maskTarget.getColorTextureView(),
				RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
			);
			pass.draw(0, 3);
		}
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

	public static Vector4fc outlineColorModulator() {
		Vector4fc fill = colorModulator();
		return new Vector4f(
			fill.x() + (1f - fill.x()) * 0.62f,
			fill.y() + (1f - fill.y()) * 0.62f,
			fill.z() + (1f - fill.z()) * 0.62f,
			1f
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

	private static RenderTarget maskTarget() {
		return maskTarget;
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
				.setOutline(RenderSetup.OutlineProperty.NONE)
				.createRenderSetup()
		);
	}

	private static RenderType createMaskType(Identifier atlas) {
		ensureRegistered();
		return RenderType.create(
			"voidmark_held_item_mask",
			RenderSetup.builder(maskPipeline)
				.withTexture("Sampler0", atlas)
				.withTexture("Sampler1", ItemFeatureRenderer.ENCHANTED_GLINT_ITEM)
				.useLightmap()
				.setOutputTarget(MASK_OUTPUT)
				.setOutline(RenderSetup.OutlineProperty.NONE)
				.createRenderSetup()
		);
	}

	private static synchronized void ensureSilhouettePipeline() {
		if (silhouettePipeline != null) {
			return;
		}
		silhouettePipeline = RenderPipeline.builder()
			.withLocation(Voidmark.id("pipeline/held_item_silhouette"))
			.withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
			.withFragmentShader(SILHOUETTE_SHADER_ID)
			.withSampler("InSampler")
			.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
			.withColorTargetState(new ColorTargetState(BlendFunction.ENTITY_OUTLINE_BLIT))
			.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
			.build();
	}
}
