package dev.voidmark.client.visual;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.voidmark.Voidmark;
import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.mixin.RenderSetupAccessor;
import dev.voidmark.client.mixin.RenderSetupTextureBindingAccessor;
import dev.voidmark.client.mixin.RenderTypeAccessor;
import dev.voidmark.client.render.MobGlowRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;

public final class HeldItemShader {
	private static final Identifier FILL_PIPELINE_ID = Voidmark.id("pipeline/held_item");
	private static final Identifier MASK_PIPELINE_ID = Voidmark.id("pipeline/held_item_mask");
	private static final Identifier FILL_SHADER_ID = Voidmark.id("core/held_item");
	private static final Identifier SILHOUETTE_SHADER_ID = Voidmark.id("post/held_item_silhouette");
	private static final Identifier ROWDIST_SHADER_ID = Voidmark.id("post/held_item_rowdist");
	private static final Identifier ESP_BLIT_SHADER_ID = Voidmark.id("post/fill_esp_blit");
	private static final OutputTarget MASK_OUTPUT = new OutputTarget("voidmark_held_item_mask", HeldItemShader::maskTarget);
	private static final OutputTarget ESP_OUTPUT = new OutputTarget("voidmark_fill_esp", HeldItemShader::espTarget);
	private static final Function<Identifier, RenderType> FILL_TYPES = Util.memoize(HeldItemShader::createFillType);
	private static final Function<Identifier, RenderType> ESP_FILL_TYPES = Util.memoize(HeldItemShader::createEspFillType);
	private static final Function<Identifier, RenderType> MASK_TYPES = Util.memoize(HeldItemShader::createMaskType);
	private static RenderPipeline fillPipeline;
	private static RenderPipeline maskPipeline;
	private static RenderPipeline silhouettePipeline;
	private static RenderPipeline rowDistPipeline;
	private static RenderPipeline espBlitPipeline;
	private static RenderTarget maskTarget;
	private static RenderTarget rowTarget;
	private static RenderTarget espTarget;
	private static boolean maskThisFrame;
	private static boolean espThisFrame;
	private static int playerFillDepth;

	private HeldItemShader() {
	}

	public static boolean active() {
		return VoidmarkConfig.get().heldItemShaderEnabled;
	}

	public static boolean playerFillActive() {
		return VoidmarkConfig.get().playerFillEsp;
	}

	public static boolean appliesFill(ItemDisplayContext context) {
		if (context == null) {
			return false;
		}
		if (context.firstPerson()) {
			return active();
		}
		return playerFill() && (context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
			|| context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
	}

	public static boolean appliesOutline(ItemDisplayContext context) {
		return active() && context != null && context.firstPerson();
	}

	public static boolean applies(ItemDisplayContext context) {
		return appliesFill(context);
	}

	public static boolean playerFill() {
		return playerFillDepth > 0;
	}

	public static boolean shouldFillEntity(Entity entity) {
		if (!playerFillActive() || entity == null || entity.isSpectator()) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || entity == client.player) {
			return false;
		}
		if (entity.getType() == EntityType.PLAYER) {
			return true;
		}
		return MobGlowRenderer.listed(entity);
	}

	public static boolean shouldFillPlayer(LivingEntityRenderState state) {
		return shouldFill(state);
	}

	public static boolean shouldFill(LivingEntityRenderState state) {
		return playerFillActive() && state instanceof FillEspMarker marker && marker.voidmark$fillEsp();
	}

	public static void pushPlayerFill() {
		playerFillDepth++;
	}

	public static void popPlayerFill() {
		playerFillDepth = Math.max(0, playerFillDepth - 1);
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
				.withColorTargetState(ColorTargetState.DEFAULT)
				.withCull(false)
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
				.withCull(false)
				.build()
		);
	}

	public static RenderType wrap(RenderType original, Iterable<BakedQuad> quads) {
		if (original == null || isPipeline(original.pipeline())) {
			return original;
		}
		Identifier atlas = atlas(original, quads);
		if (playerFill()) {
			return ESP_FILL_TYPES.apply(atlas);
		}
		return FILL_TYPES.apply(atlas);
	}

	public static RenderType wrapSubmitted(RenderType original) {
		if (!playerFill() || original == null || original.isOutline() || isPipeline(original.pipeline())) {
			return original;
		}
		Identifier atlas = sampler0(original);
		if (atlas == null) {
			return original;
		}
		return wrapFill(original, atlas);
	}

	public static RenderType wrapArm(RenderType original, Identifier skin) {
		if (!active() || original == null || isPipeline(original.pipeline()) || skin == null) {
			return original;
		}
		return FILL_TYPES.apply(skin);
	}

	public static RenderType wrapFill(RenderType original, Identifier atlas) {
		if (original == null || isPipeline(original.pipeline()) || atlas == null) {
			return original;
		}
		return ESP_FILL_TYPES.apply(atlas);
	}

	public static void submitArmMask(SubmitNodeCollector collector, PoseStack pose, int light, Identifier skin, ModelPart part) {
		if (!maskThisFrame || collector == null || pose == null || skin == null || part == null) {
			return;
		}
		ensureRegistered();
		collector.submitModelPart(part, pose, MASK_TYPES.apply(skin), light, OverlayTexture.NO_OVERLAY, null);
	}

	public static void beginFillEsp() {
		espThisFrame = false;
		if (!playerFillActive()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		RenderTarget main = client.getMainRenderTarget();
		if (main == null || main.width <= 0 || main.height <= 0) {
			return;
		}
		if (espTarget == null) {
			espTarget = new TextureTarget("voidmark fill esp", main.width, main.height, true);
		} else if (espTarget.width != main.width || espTarget.height != main.height) {
			espTarget.resize(main.width, main.height);
		}
		if (espTarget.getColorTexture() == null) {
			return;
		}
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		if (espTarget.getDepthTexture() != null) {
			encoder.clearColorAndDepthTextures(espTarget.getColorTexture(), 0, espTarget.getDepthTexture(), 1.0);
		} else {
			encoder.clearColorTexture(espTarget.getColorTexture(), 0);
		}
		espThisFrame = true;
	}

	public static void compositeFillEsp() {
		if (!espThisFrame) {
			return;
		}
		espThisFrame = false;
		Minecraft client = Minecraft.getInstance();
		RenderTarget main = client.getMainRenderTarget();
		if (espTarget == null || main == null || espTarget.getColorTextureView() == null || main.getColorTextureView() == null) {
			return;
		}
		ensureEspBlitPipeline();
		GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		try (RenderPass pass = encoder.createRenderPass(
			() -> "voidmark fill esp blit",
			main.getColorTextureView(),
			OptionalInt.empty()
		)) {
			pass.setPipeline(espBlitPipeline);
			RenderSystem.bindDefaultUniforms(pass);
			pass.bindTexture("InSampler", espTarget.getColorTextureView(), nearest);
			pass.draw(0, 3);
		}
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
		if (maskTarget.getDepthTexture() != null) {
			encoder.clearColorAndDepthTextures(maskTarget.getColorTexture(), 0, maskTarget.getDepthTexture(), 1.0);
		} else {
			encoder.clearColorTexture(maskTarget.getColorTexture(), 0);
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
		if (rowTarget == null) {
			rowTarget = new TextureTarget("voidmark held item row distance", main.width, main.height, false);
		} else if (rowTarget.width != main.width || rowTarget.height != main.height) {
			rowTarget.resize(main.width, main.height);
		}
		if (rowTarget.getColorTextureView() == null) {
			return;
		}
		ensureSilhouettePipeline();
		// Mapping a UBO is illegal while a RenderPass is open.
		var transforms = RenderSystem.getDynamicUniforms().writeTransform(
			new Matrix4f(),
			outlineColorModulator(),
			modelOffset(),
			new Matrix4f()
		);
		GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		// The disc dilation is separable: a per-row nearest-distance pass, then a
		// vertical combine. 2 * (2r+1) reads per pixel instead of (2r+1)^2.
		try (RenderPass pass = encoder.createRenderPass(
			() -> "voidmark held item row distance",
			rowTarget.getColorTextureView(),
			OptionalInt.empty()
		)) {
			pass.setPipeline(rowDistPipeline);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transforms);
			pass.bindTexture("InSampler", maskTarget.getColorTextureView(), nearest);
			pass.draw(0, 3);
		}
		try (RenderPass pass = encoder.createRenderPass(
			() -> "voidmark held item silhouette",
			main.getColorTextureView(),
			OptionalInt.empty()
		)) {
			pass.setPipeline(silhouettePipeline);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transforms);
			pass.bindTexture("InSampler", maskTarget.getColorTextureView(), nearest);
			pass.bindTexture("RowSampler", rowTarget.getColorTextureView(), nearest);
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

	private static RenderTarget espTarget() {
		return espTarget;
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

	private static RenderType createEspFillType(Identifier atlas) {
		ensureRegistered();
		return RenderType.create(
			"voidmark_fill_esp",
			RenderSetup.builder(fillPipeline)
				.withTexture("Sampler0", atlas)
				.withTexture("Sampler1", ItemFeatureRenderer.ENCHANTED_GLINT_ITEM)
				.useLightmap()
				.affectsCrumbling()
				.setOutputTarget(ESP_OUTPUT)
				.setOutline(RenderSetup.OutlineProperty.NONE)
				.createRenderSetup()
		);
	}

	private static Identifier sampler0(RenderType original) {
		if (original == null) {
			return null;
		}
		RenderSetup setup = ((RenderTypeAccessor) (Object) original).voidmark$setup();
		if (setup == null) {
			return null;
		}
		var textures = ((RenderSetupAccessor) (Object) setup).voidmark$textures();
		if (textures == null) {
			return null;
		}
		Object sampler = textures.get("Sampler0");
		if (sampler == null) {
			return null;
		}
		return ((RenderSetupTextureBindingAccessor) sampler).voidmark$location();
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
		rowDistPipeline = RenderPipeline.builder()
			.withLocation(Voidmark.id("pipeline/held_item_rowdist"))
			.withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
			.withFragmentShader(ROWDIST_SHADER_ID)
			.withSampler("InSampler")
			.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
			.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
			.withColorTargetState(new ColorTargetState(Optional.empty(), ColorTargetState.WRITE_ALL))
			.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
			.build();
		silhouettePipeline = RenderPipeline.builder()
			.withLocation(Voidmark.id("pipeline/held_item_silhouette"))
			.withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
			.withFragmentShader(SILHOUETTE_SHADER_ID)
			.withSampler("InSampler")
			.withSampler("RowSampler")
			.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
			.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
			.withColorTargetState(new ColorTargetState(BlendFunction.ENTITY_OUTLINE_BLIT))
			.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
			.build();
	}

	private static synchronized void ensureEspBlitPipeline() {
		if (espBlitPipeline != null) {
			return;
		}
		espBlitPipeline = RenderPipeline.builder()
			.withLocation(Voidmark.id("pipeline/fill_esp_blit"))
			.withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
			.withFragmentShader(ESP_BLIT_SHADER_ID)
			.withSampler("InSampler")
			.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
			.build();
	}
}
