package dev.voidmark.client.visual;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
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
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
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
	private static final Identifier FILL_SHADER_ID = Voidmark.id("core/held_item");
	private static final Identifier SILHOUETTE_SHADER_ID = Voidmark.id("post/held_item_silhouette");
	private static RenderPipeline fillPipeline;
	private static RenderPipeline silhouettePipeline;
	private static final Function<Identifier, RenderType> FILL_TYPES = Util.memoize(HeldItemShader::createFillType);
	private static boolean maskThisFrame;

	private HeldItemShader() {
	}

	public static boolean active() {
		return VoidmarkConfig.get().heldItemShaderEnabled;
	}

	public static boolean applies(ItemDisplayContext context) {
		return active() && context != null && context.firstPerson();
	}

	public static boolean isPipeline(RenderPipeline value) {
		return value != null && FILL_PIPELINE_ID.equals(value.getLocation());
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
		RenderTarget target = outlineTarget();
		if (target == null || target.getColorTexture() == null) {
			return;
		}
		RenderSystem.getDevice().createCommandEncoder().clearColorTexture(target.getColorTexture(), 0);
		maskThisFrame = true;
	}

	public static void drawViewMask(
		OutlineBufferSource outlines,
		PoseStack.Pose pose,
		Iterable<BakedQuad> quads,
		QuadInstance quadInstance
	) {
		if (!maskThisFrame || outlines == null || quads == null) {
			return;
		}
		outlines.setColor(0xFFFFFFFF);
		for (BakedQuad quad : quads) {
			Identifier atlas = TextureAtlas.LOCATION_ITEMS;
			if (quad != null) {
				atlas = quad.materialInfo().sprite().atlasLocation();
			}
			outlines.getBuffer(RenderTypes.outline(atlas)).putBakedQuad(pose, quad, quadInstance);
		}
	}

	public static void compositeSilhouette() {
		if (!maskThisFrame) {
			return;
		}
		maskThisFrame = false;
		Minecraft client = Minecraft.getInstance();
		if (client.levelRenderer == null) {
			return;
		}
		client.renderBuffers().outlineBufferSource().endOutlineBatch();
		RenderTarget mask = outlineTarget();
		RenderTarget main = client.getMainRenderTarget();
		if (mask == null || main == null || mask.getColorTextureView() == null || main.getColorTextureView() == null) {
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
				mask.getColorTextureView(),
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

	public static Vector3fc modelOffset() {
		VoidmarkConfig config = VoidmarkConfig.get();
		return new Vector3f(
			VoidmarkConfig.clamp(config.heldItemShaderOutline, 0.15f, 1.50f),
			VoidmarkConfig.clamp(config.heldItemShaderSmoke, 0.10f, 1.50f),
			config.heldItemShaderStyleIndex()
		);
	}

	private static RenderTarget outlineTarget() {
		Minecraft client = Minecraft.getInstance();
		if (client.levelRenderer == null) {
			return null;
		}
		return client.levelRenderer.entityOutlineTarget();
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
