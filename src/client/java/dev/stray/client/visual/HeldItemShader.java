package dev.stray.client.visual;

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
import dev.stray.Stray;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.mixin.RenderSetupAccessor;
import dev.stray.client.mixin.RenderSetupTextureBindingAccessor;
import dev.stray.client.mixin.RenderTypeAccessor;
import dev.stray.client.render.MobGlowRenderer;
import dev.stray.client.render.NametagRenderer;
import dev.stray.client.render.StarMobEsp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.AbstractEndPortalRenderer;
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

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;

public final class HeldItemShader {
	private static final Identifier FILL_PIPELINE_ID = Stray.id("pipeline/held_item");
	private static final Identifier ESP_FILL_PIPELINE_ID = Stray.id("pipeline/held_item_esp");
	private static final Identifier MASK_PIPELINE_ID = Stray.id("pipeline/held_item_mask");
	private static final Identifier FILL_SHADER_ID = Stray.id("core/held_item");
	private static final Identifier SILHOUETTE_SHADER_ID = Stray.id("post/held_item_silhouette");
	private static final Identifier ROWDIST_SHADER_ID = Stray.id("post/held_item_rowdist");
	private static final Identifier ESP_BLIT_SHADER_ID = Stray.id("post/fill_esp_blit");
	private static final OutputTarget MASK_OUTPUT = new OutputTarget("stray_held_item_mask", HeldItemShader::maskTarget);
	private static final OutputTarget ESP_OUTPUT = new OutputTarget("stray_fill_esp", HeldItemShader::espTarget);
	private static final Function<Identifier, RenderType> FILL_TYPES = Util.memoize(HeldItemShader::createFillType);
	private static final Function<Identifier, RenderType> PLAYER_FILL_TYPES = Util.memoize(HeldItemShader::createPlayerFillType);
	private static final Function<Identifier, RenderType> ESP_FILL_TYPES = Util.memoize(HeldItemShader::createEspFillType);
	private static final Function<Identifier, RenderType> MASK_TYPES = Util.memoize(HeldItemShader::createMaskType);
	private static RenderPipeline fillPipeline;
	private static RenderPipeline espFillPipeline;
	private static RenderPipeline maskPipeline;
	private static RenderPipeline silhouettePipeline;
	private static RenderPipeline rowDistPipeline;
	private static RenderPipeline espBlitPipeline;
	private static RenderTarget maskTarget;
	private static RenderTarget rowTarget;
	private static RenderTarget espTarget;
	private static boolean maskThisFrame;
	private static boolean playerMaskThisFrame;
	private static boolean playerMaskDepthReady;
	private static boolean espThisFrame;
	private static int playerFillDepth;
	private static final Set<Object> FILL_ITEMS = Collections.newSetFromMap(new IdentityHashMap<>());

	private HeldItemShader() {
	}

	public static boolean active() {
		return StrayConfig.get().heldItemShaderEnabled;
	}

	public static boolean playerFillActive() {
		StrayConfig config = StrayConfig.get();
		return config.playerFillEsp || config.playerFillStarMobs;
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

	public static boolean playerFillThroughWalls() {
		StrayConfig config = StrayConfig.get();
		if (config.playerFillEsp && config.playerFillThroughWalls) {
			return true;
		}
		return config.playerFillStarMobs && config.starMobThroughWalls;
	}

	public static boolean shouldFillEntity(Entity entity) {
		if (!playerFillActive() || entity == null || entity.isSpectator()) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || entity == client.player) {
			return false;
		}
		StrayConfig config = StrayConfig.get();
		if (entity.getType() == EntityType.PLAYER) {
			if (config.playerFillStarMobs && StarMobEsp.marked(entity)) {
				return true;
			}
			return config.playerFillEsp && NametagRenderer.realAccount(entity);
		}
		if (config.playerFillStarMobs && StarMobEsp.marked(entity)) {
			return true;
		}
		return config.playerFillEsp && config.playerFillMobs && MobGlowRenderer.catalogOrNametag(entity);
	}

	public static boolean shouldFillThroughWalls(Entity entity) {
		return playerFillThroughWalls() && shouldFillEntity(entity);
	}

	public static boolean shouldFillPlayer(LivingEntityRenderState state) {
		return shouldFill(state);
	}

	public static boolean shouldFill(LivingEntityRenderState state) {
		return playerFillActive() && state instanceof FillEspMarker marker && marker.stray$fillEsp();
	}

	public static void markFillItem(Object submit) {
		if (playerFill() && submit != null) {
			FILL_ITEMS.add(submit);
		}
	}

	public static boolean isFillItem(Object submit) {
		return submit != null && !FILL_ITEMS.isEmpty() && FILL_ITEMS.contains(submit);
	}

	public static void pushPlayerFill() {
		playerFillDepth++;
	}

	public static void popPlayerFill() {
		playerFillDepth = Math.max(0, playerFillDepth - 1);
	}

	public static boolean isPlayerFillPipeline(RenderPipeline value) {
		return value != null && ESP_FILL_PIPELINE_ID.equals(value.getLocation());
	}

	public static boolean isFillPipeline(RenderPipeline value) {
		if (value == null) {
			return false;
		}
		Identifier location = value.getLocation();
		return FILL_PIPELINE_ID.equals(location) || ESP_FILL_PIPELINE_ID.equals(location);
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
				.withSampler("Sampler3")
				.withSampler("Sampler4")
				.withShaderDefine("ALPHA_CUTOUT", 0.1f)
				.withShaderDefine("PORTAL_LAYERS", 15)
				.withColorTargetState(ColorTargetState.DEFAULT)
				.withCull(false)
				.build()
		);
		espFillPipeline = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.ITEM_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(ESP_FILL_PIPELINE_ID)
				.withVertexShader(FILL_SHADER_ID)
				.withFragmentShader(FILL_SHADER_ID)
				.withSampler("Sampler1")
				.withSampler("Sampler3")
				.withSampler("Sampler4")
				.withShaderDefine("ALPHA_CUTOUT", 0.5f)
				.withShaderDefine("ESP_FILL")
				.withShaderDefine("PORTAL_LAYERS", 15)
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
				.withSampler("Sampler3")
				.withSampler("Sampler4")
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
			return fillType(atlas);
		}
		return FILL_TYPES.apply(atlas);
	}

	public static RenderType wrapPlayerItem(RenderType original, Iterable<BakedQuad> quads) {
		if (original == null || isPipeline(original.pipeline())) {
			return original;
		}
		return fillType(atlas(original, quads));
	}

	public static RenderType wrapSubmitted(RenderType original) {
		if (!playerFill() || original == null || original.isOutline() || isPipeline(original.pipeline())) {
			return original;
		}
		if (!compatibleLayer(original)) {
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
		if (original == null || original.isOutline() || isPipeline(original.pipeline()) || atlas == null) {
			return original;
		}
		return fillType(atlas);
	}

	private static RenderType fillType(Identifier atlas) {
		return playerFillThroughWalls() ? ESP_FILL_TYPES.apply(atlas) : PLAYER_FILL_TYPES.apply(atlas);
	}

	public static void submitArmMask(SubmitNodeCollector collector, PoseStack pose, int light, Identifier skin, ModelPart part) {
		if (!maskThisFrame || collector == null || pose == null || skin == null || part == null) {
			return;
		}
		ensureRegistered();
		collector.submitModelPart(part, pose, MASK_TYPES.apply(skin), light, OverlayTexture.NO_OVERLAY, null);
	}

	public static RenderType playerFillMask(RenderType original) {
		if (!playerMaskThisFrame || !playerFill() || original == null || isMaskPipeline(original.pipeline()) || !isFillPipeline(original.pipeline())) {
			return null;
		}
		Identifier atlas = sampler0(original);
		if (atlas == null) {
			return null;
		}
		ensureRegistered();
		return MASK_TYPES.apply(atlas);
	}

	public static void beginFillEsp() {
		FILL_ITEMS.clear();
		espThisFrame = false;
		beginPlayerMask();
		if (!playerFillThroughWalls()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		RenderTarget main = client.getMainRenderTarget();
		if (main == null || main.width <= 0 || main.height <= 0) {
			return;
		}
		if (espTarget == null) {
			espTarget = new TextureTarget("stray fill esp", main.width, main.height, true);
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
			() -> "stray fill esp blit",
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
		if (!prepareMaskTarget()) {
			return;
		}
		maskThisFrame = true;
	}

	public static void beginPlayerMask() {
		playerMaskThisFrame = false;
		playerMaskDepthReady = false;
		if (!playerFillActive()) {
			return;
		}
		if (!prepareMaskTarget()) {
			return;
		}
		playerMaskThisFrame = true;
	}

	public static void capturePlayerMaskDepth() {
		if (!playerMaskThisFrame || playerMaskDepthReady || playerFillThroughWalls()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		RenderTarget main = client.getMainRenderTarget();
		if (main == null || maskTarget == null || main.getDepthTexture() == null || maskTarget.getDepthTexture() == null) {
			return;
		}
		maskTarget.copyDepthFrom(main);
		playerMaskDepthReady = true;
	}

	private static boolean prepareMaskTarget() {
		Minecraft client = Minecraft.getInstance();
		RenderTarget main = client.getMainRenderTarget();
		if (main == null || main.width <= 0 || main.height <= 0) {
			return false;
		}
		if (maskTarget == null) {
			maskTarget = new TextureTarget("stray held item mask", main.width, main.height, true);
		} else if (maskTarget.width != main.width || maskTarget.height != main.height) {
			maskTarget.resize(main.width, main.height);
		}
		if (maskTarget.getColorTexture() == null) {
			return false;
		}
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		if (maskTarget.getDepthTexture() != null) {
			encoder.clearColorAndDepthTextures(maskTarget.getColorTexture(), 0, maskTarget.getDepthTexture(), 1.0);
		} else {
			encoder.clearColorTexture(maskTarget.getColorTexture(), 0);
		}
		return true;
	}

	public static void drawViewMask(
		MultiBufferSource.BufferSource buffers,
		PoseStack.Pose pose,
		Iterable<BakedQuad> quads,
		QuadInstance quadInstance
	) {
		if (!masking() || buffers == null || quads == null || pose == null || quadInstance == null) {
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
		runSilhouette("stray held item", outlineColorModulator(false), silhouetteThickness(false));
	}

	public static void compositePlayerSilhouette() {
		if (!playerMaskThisFrame) {
			return;
		}
		playerMaskThisFrame = false;
		runSilhouette("stray player fill", outlineColorModulator(true), silhouetteThickness(true));
	}

	public static boolean masking() {
		return maskThisFrame || playerMaskThisFrame;
	}

	private static void runSilhouette(String label, Vector4fc outline, float thickness) {
		Minecraft client = Minecraft.getInstance();
		RenderTarget main = client.getMainRenderTarget();
		if (maskTarget == null || main == null || maskTarget.getColorTextureView() == null || main.getColorTextureView() == null) {
			return;
		}
		if (rowTarget == null) {
			rowTarget = new TextureTarget("stray held item row distance", main.width, main.height, false);
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
			outline,
			new Vector3f(thickness, 0f, 0f),
			new Matrix4f()
		);
		GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		// The disc dilation is separable: a per-row nearest-distance pass, then a
		// vertical combine. 2 * (2r+1) reads per pixel instead of (2r+1)^2.
		try (RenderPass pass = encoder.createRenderPass(
			() -> label + " row distance",
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
			() -> label + " silhouette",
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
		return colorModulator(null);
	}

	public static Vector4fc colorModulator(RenderPipeline pipeline) {
		StrayConfig config = StrayConfig.get();
		if (playerFillUniforms(pipeline)) {
			return packColor(config.playerFillRgb, config.playerFillFill);
		}
		return packColor(config.heldItemShaderRgb, config.heldItemShaderFill);
	}

	private static Vector4f packColor(int rgb, float fill) {
		return new Vector4f(
			((rgb >> 16) & 0xFF) / 255f,
			((rgb >> 8) & 0xFF) / 255f,
			(rgb & 0xFF) / 255f,
			StrayConfig.clamp(fill, 0.08f, 0.85f)
		);
	}

	public static Vector4fc outlineColorModulator() {
		return outlineColorModulator(playerFill());
	}

	private static Vector4fc outlineColorModulator(boolean playerFill) {
		StrayConfig config = StrayConfig.get();
		int rgb = playerFill ? config.playerFillOutlineRgb : config.heldItemShaderOutlineRgb;
		return new Vector4f(
			((rgb >> 16) & 0xFF) / 255f,
			((rgb >> 8) & 0xFF) / 255f,
			(rgb & 0xFF) / 255f,
			1f
		);
	}

	private static float silhouetteThickness(boolean playerFill) {
		StrayConfig config = StrayConfig.get();
		if (playerFill) {
			return StrayConfig.clamp(config.playerFillOutline, 0.15f, 1.50f);
		}
		return StrayConfig.clamp(config.heldItemShaderOutline, 0.15f, 1.50f);
	}

	public static Vector3fc modelOffset() {
		return modelOffset(null);
	}

	public static Vector3fc modelOffset(RenderPipeline pipeline) {
		StrayConfig config = StrayConfig.get();
		if (playerFillUniforms(pipeline)) {
			return new Vector3f(
				StrayConfig.clamp(config.playerFillOutline, 0.15f, 1.50f),
				StrayConfig.clamp(config.playerFillSmoke, 0.10f, 1.50f),
				config.playerFillStyleIndex()
			);
		}
		return new Vector3f(
			StrayConfig.clamp(config.heldItemShaderOutline, 0.15f, 1.50f),
			StrayConfig.clamp(config.heldItemShaderSmoke, 0.10f, 1.50f),
			config.heldItemShaderStyleIndex()
		);
	}

	private static boolean playerFillUniforms(RenderPipeline pipeline) {
		return isPlayerFillPipeline(pipeline) || playerFill();
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

	private static boolean compatibleLayer(RenderType original) {
		RenderPipeline pipeline = original.pipeline();
		if (pipeline == null) {
			return false;
		}
		Identifier location = pipeline.getLocation();
		if (location != null) {
			String path = location.getPath();
			if (path.contains("glint") || path.contains("crumbling") || path.contains("shadow")
				|| path.contains("text") || path.contains("lines") || path.contains("particle")) {
				return false;
			}
		}
		String name = original.toString();
		if (name.contains("glint")) {
			return false;
		}
		ensureRegistered();
		return pipeline.getVertexFormat() == espFillPipeline.getVertexFormat()
			&& pipeline.getVertexFormatMode() == espFillPipeline.getVertexFormatMode();
	}

	private static Identifier sampler0(RenderType original) {
		RenderSetup setup = ((RenderTypeAccessor) (Object) original).stray$setup();
		if (setup == null) {
			return null;
		}
		var textures = ((RenderSetupAccessor) (Object) setup).stray$textures();
		if (textures == null) {
			return null;
		}
		Object sampler = textures.get("Sampler0");
		if (sampler == null) {
			return null;
		}
		return ((RenderSetupTextureBindingAccessor) sampler).stray$location();
	}

	private static RenderType createFillType(Identifier atlas) {
		ensureRegistered();
		return RenderType.create(
			"stray_held_item",
			RenderSetup.builder(fillPipeline)
				.withTexture("Sampler0", atlas)
				.withTexture("Sampler1", ItemFeatureRenderer.ENCHANTED_GLINT_ITEM)
				.withTexture("Sampler3", AbstractEndPortalRenderer.END_SKY_LOCATION)
				.withTexture("Sampler4", AbstractEndPortalRenderer.END_PORTAL_LOCATION)
				.useLightmap()
				.affectsCrumbling()
				.setOutline(RenderSetup.OutlineProperty.NONE)
				.createRenderSetup()
		);
	}

	private static RenderType createPlayerFillType(Identifier atlas) {
		ensureRegistered();
		return RenderType.create(
			"stray_player_fill",
			RenderSetup.builder(espFillPipeline)
				.withTexture("Sampler0", atlas)
				.withTexture("Sampler1", ItemFeatureRenderer.ENCHANTED_GLINT_ITEM)
				.withTexture("Sampler3", AbstractEndPortalRenderer.END_SKY_LOCATION)
				.withTexture("Sampler4", AbstractEndPortalRenderer.END_PORTAL_LOCATION)
				.useLightmap()
				.affectsCrumbling()
				.setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
				.createRenderSetup()
		);
	}

	private static RenderType createEspFillType(Identifier atlas) {
		ensureRegistered();
		return RenderType.create(
			"stray_fill_esp",
			RenderSetup.builder(espFillPipeline)
				.withTexture("Sampler0", atlas)
				.withTexture("Sampler1", ItemFeatureRenderer.ENCHANTED_GLINT_ITEM)
				.withTexture("Sampler3", AbstractEndPortalRenderer.END_SKY_LOCATION)
				.withTexture("Sampler4", AbstractEndPortalRenderer.END_PORTAL_LOCATION)
				.useLightmap()
				.affectsCrumbling()
				.setOutputTarget(ESP_OUTPUT)
				.setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
				.createRenderSetup()
		);
	}

	private static RenderType createMaskType(Identifier atlas) {
		ensureRegistered();
		return RenderType.create(
			"stray_held_item_mask",
			RenderSetup.builder(maskPipeline)
				.withTexture("Sampler0", atlas)
				.withTexture("Sampler1", ItemFeatureRenderer.ENCHANTED_GLINT_ITEM)
				.withTexture("Sampler3", AbstractEndPortalRenderer.END_SKY_LOCATION)
				.withTexture("Sampler4", AbstractEndPortalRenderer.END_PORTAL_LOCATION)
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
			.withLocation(Stray.id("pipeline/held_item_rowdist"))
			.withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
			.withFragmentShader(ROWDIST_SHADER_ID)
			.withSampler("InSampler")
			.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
			.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
			.withColorTargetState(new ColorTargetState(Optional.empty(), ColorTargetState.WRITE_ALL))
			.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
			.build();
		silhouettePipeline = RenderPipeline.builder()
			.withLocation(Stray.id("pipeline/held_item_silhouette"))
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
			.withLocation(Stray.id("pipeline/fill_esp_blit"))
			.withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
			.withFragmentShader(ESP_BLIT_SHADER_ID)
			.withSampler("InSampler")
			.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
			.build();
	}
}
