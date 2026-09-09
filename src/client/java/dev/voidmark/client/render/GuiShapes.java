package dev.voidmark.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.voidmark.Voidmark;
import dev.voidmark.client.mixin.GuiGraphicsExtractorInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

import java.util.function.UnaryOperator;

/**
 * Analytic rounded shapes for GUI chrome. Every piece is one quad whose UVs
 * measure the distance from its outer edges in corner radii; the
 * {@code gui_round} fragment shader turns that into anti-aliased coverage.
 */
public final class GuiShapes {
	private static final Identifier SHADER = Voidmark.id("core/gui_round");
	private static final Identifier DUMMY_TEXTURE = Voidmark.id("textures/gui/white.png");
	private static RenderPipeline fill;
	private static RenderPipeline invert;
	private static RenderPipeline frost;
	private static RenderPipeline ring;
	private static RenderPipeline hairline;
	private static int ringScale = Integer.MIN_VALUE;

	private GuiShapes() {
	}

	/** Which corner of a piece is the "outer" one (where both UVs are zero). */
	public enum Corner {
		TOP_LEFT(false, false),
		TOP_RIGHT(true, false),
		BOTTOM_RIGHT(true, true),
		BOTTOM_LEFT(false, true);

		final boolean right;
		final boolean bottom;

		Corner(boolean right, boolean bottom) {
			this.right = right;
			this.bottom = bottom;
		}
	}

	/**
	 * Fill the rectangle {@code (x, y, w, h)} as the {@code corner} quadrant of a
	 * rounded shape with the given radius. The two edges touching that corner
	 * are the silhouette; the other two are interior seams.
	 */
	public static void quadrant(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, Corner corner, int color) {
		piece(graphics, fillPipeline(), dummyView(), nearest(), x, y, w, h, radius, corner, 0f, 0f, color);
	}

	/** {@link #quadrant} for a piece that starts {@code skipX}/{@code skipY} GUI pixels in from the silhouette edges. */
	public static void quadrant(
		GuiGraphicsExtractor graphics,
		float x,
		float y,
		float w,
		float h,
		float radius,
		Corner corner,
		float skipX,
		float skipY,
		int color
	) {
		piece(graphics, fillPipeline(), dummyView(), nearest(), x, y, w, h, radius, corner, skipX, skipY, color);
	}

	/** Paint what lies <em>outside</em> the rounded corner: the "ear" of a square. */
	public static void ear(GuiGraphicsExtractor graphics, float x, float y, float radius, Corner corner, int color) {
		piece(graphics, invertPipeline(), dummyView(), nearest(), x, y, radius, radius, radius, corner, 0f, 0f, color);
	}

	/** A band of GUI pixels inside the silhouette edge (1px, or a 0.5px hairline). */
	public static void ringQuadrant(
		GuiGraphicsExtractor graphics,
		float x,
		float y,
		float w,
		float h,
		float radius,
		Corner corner,
		float skipX,
		float skipY,
		boolean thin,
		int color
	) {
		piece(graphics, thin ? hairlinePipeline() : ringPipeline(), dummyView(), nearest(), x, y, w, h, radius, corner, skipX, skipY, color);
	}

	/** Frost quadrant: RGB sampled from {@code view} by screen position, clipped to the rounded silhouette. */
	public static void frostQuadrant(
		GuiGraphicsExtractor graphics,
		GpuTextureView view,
		float x,
		float y,
		float w,
		float h,
		float radius,
		Corner corner
	) {
		piece(graphics, frostPipeline(), view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR), x, y, w, h, radius, corner, 0f, 0f, 0xFFFFFFFF);
	}

	/**
	 * @param skipX GUI distance from the outer vertical edge to this piece's near
	 *              edge (0 when the piece touches the silhouette); lets edge
	 *              strips start past the corner square
	 * @param skipY same for the outer horizontal edge
	 */
	private static void piece(
		GuiGraphicsExtractor graphics,
		RenderPipeline pipeline,
		GpuTextureView view,
		GpuSampler sampler,
		float x,
		float y,
		float w,
		float h,
		float radius,
		Corner corner,
		float skipX,
		float skipY,
		int color
	) {
		if (w <= 0f || h <= 0f || radius <= 0f || (color >>> 24) == 0 || view == null) {
			return;
		}
		float uNear = skipX / radius;
		float uFar = (skipX + w) / radius;
		float vNear = skipY / radius;
		float vFar = (skipY + h) / radius;
		float u0 = corner.right ? uFar : uNear;
		float u1 = corner.right ? uNear : uFar;
		float v0 = corner.bottom ? vFar : vNear;
		float v1 = corner.bottom ? vNear : vFar;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(w, h);
		((GuiGraphicsExtractorInvoker) graphics).voidmark$innerBlit(pipeline, view, sampler, 0, 0, 1, 1, u0, u1, v0, v1, color);
		graphics.pose().popMatrix();
	}

	private static GpuSampler nearest() {
		return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
	}

	/** The flat variants ignore Sampler0, but the GUI pipeline still wants a bound texture. */
	private static GpuTextureView dummyView() {
		AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(DUMMY_TEXTURE);
		return texture == null ? null : texture.getTextureView();
	}

	private static int guiScale() {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.getWindow() == null) {
			return 1;
		}
		return Math.max(1, client.getWindow().getGuiScale());
	}

	private static synchronized RenderPipeline fillPipeline() {
		if (fill == null) {
			fill = build("gui_round_fill", builder -> builder);
		}
		return fill;
	}

	private static synchronized RenderPipeline invertPipeline() {
		if (invert == null) {
			invert = build("gui_round_invert", builder -> builder.withShaderDefine("INVERT"));
		}
		return invert;
	}

	private static synchronized RenderPipeline frostPipeline() {
		if (frost == null) {
			frost = build("gui_round_frost", builder -> builder.withShaderDefine("FROST"));
		}
		return frost;
	}

	private static synchronized RenderPipeline ringPipeline() {
		ensureRingPipelines();
		return ring;
	}

	private static synchronized RenderPipeline hairlinePipeline() {
		ensureRingPipelines();
		return hairline;
	}

	/**
	 * {@code RING_GUI_PX} is framebuffer pixels. Multiplying the define by the
	 * current GUI scale makes a 1 GUI-pixel rim one screen pixel at scale 1 and
	 * two at scale 2, matching the old hairline. Rebuilt if the scale changes.
	 */
	private static void ensureRingPipelines() {
		int scale = guiScale();
		if (ring != null && scale == ringScale) {
			return;
		}
		ringScale = scale;
		float px = scale;
		ring = build("gui_round_ring_" + scale, builder -> builder.withShaderDefine("RING_GUI_PX", px));
		hairline = build("gui_round_hairline_" + scale, builder -> builder.withShaderDefine("RING_GUI_PX", px * 0.5f));
	}

	/** Same shape as vanilla GUI_TEXTURED, with only the uniforms that shader actually reads. */
	private static RenderPipeline build(String name, UnaryOperator<RenderPipeline.Builder> extra) {
		RenderPipeline.Builder builder = RenderPipeline.builder()
			.withLocation(Voidmark.id("pipeline/" + name))
			.withVertexShader(Identifier.withDefaultNamespace("core/position_tex_color"))
			.withFragmentShader(SHADER)
			.withSampler("Sampler0")
			.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
			.withUniform("Projection", UniformType.UNIFORM_BUFFER)
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS);
		return RenderPipelines.register(extra.apply(builder).build());
	}
}
