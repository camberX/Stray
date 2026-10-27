package dev.stray.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.BindGroupLayouts;
import dev.stray.Stray;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;

/**
 * Rounded rectangles drawn as one quad with the shape computed per pixel (gui_round_rect),
 * so the fill can be a real gradient: each corner of the quad has its own color and the GPU
 * blends between them, with smooth anti-aliased rounded edges at any size and GUI scale.
 *
 * <p>The quad's UV carries the shape: (x, y) in half-height units times V, where V is
 * 1 + radius/(h/2) for fills and 3 + that for 1px outlines. The shader reads the corner
 * extents back from the interpolated UV, so no uniforms are needed.
 */
public final class RoundRect {
	private static RenderPipeline pipeline;

	private RoundRect() {
	}

	/** Top-to-bottom gradient. */
	public static void vertical(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int top, int bottom) {
		draw(g, x, y, w, h, radius, top, top, bottom, bottom, false);
	}

	/** Left-to-right gradient. */
	public static void horizontal(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int left, int right) {
		draw(g, x, y, w, h, radius, left, right, right, left, false);
	}

	/** Diagonal gradient from the top-left corner to the bottom-right one. */
	public static void diagonal(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int from, int to) {
		int mid = mix(from, to, 0.5f);
		draw(g, x, y, w, h, radius, from, mid, to, mid, false);
	}

	public static void fill(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int color) {
		draw(g, x, y, w, h, radius, color, color, color, color, false);
	}

	/** A 1 screen-pixel outline, top color to bottom color. */
	public static void outline(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int top, int bottom) {
		draw(g, x, y, w, h, radius, top, top, bottom, bottom, true);
	}

	/**
	 * Corner colors clockwise from the top-left. Falls back to a plain rounded fill when
	 * the shape can't go through the custom state.
	 */
	public static void draw(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int tl, int tr, int br, int bl, boolean ring) {
		if (w <= 0f || h <= 0f || ((tl | tr | br | bl) >>> 24) == 0) {
			return;
		}
		float half = h * 0.5f;
		float rr = Math.max(0f, Math.min(radius, Math.min(w, h) * 0.5f)) / half;
		float v = (ring ? 3f : 1f) + Math.min(1f, rr);
		float u = (w / h) * v;
		Matrix3x2f pose = new Matrix3x2f(g.pose());
		ScreenRectangle scissor = g.scissorStack.peek();
		ScreenRectangle bounds = new ScreenRectangle((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(w + 1), (int) Math.ceil(h + 1)).transformMaxBounds(pose);
		if (scissor != null) {
			bounds = scissor.intersection(bounds);
			if (bounds == null) {
				return;
			}
		}
		ensurePipeline();
		g.guiRenderState.addGuiElement(new State(pipeline, pose, x, y, x + w, y + h, u, v, tl, tr, br, bl, scissor, bounds));
	}

	/**
	 * A texture tinted by a gradient: the GUI's textured pipeline multiplies the texture by the
	 * vertex colors, so a white logo takes on whatever two-tone gradient the corners get.
	 */
	public static void texture(GuiGraphicsExtractor g, net.minecraft.resources.Identifier id, float x, float y, float w, float h, int tl, int tr, int br, int bl) {
		net.minecraft.client.renderer.texture.AbstractTexture tex = net.minecraft.client.Minecraft.getInstance().getTextureManager().getTexture(id);
		com.mojang.blaze3d.textures.GpuTextureView view = tex == null ? null : tex.getTextureView();
		if (view == null || w <= 0f || h <= 0f) {
			return;
		}
		Matrix3x2f pose = new Matrix3x2f(g.pose());
		ScreenRectangle scissor = g.scissorStack.peek();
		ScreenRectangle bounds = new ScreenRectangle((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(w + 1), (int) Math.ceil(h + 1)).transformMaxBounds(pose);
		if (scissor != null) {
			bounds = scissor.intersection(bounds);
			if (bounds == null) {
				return;
			}
		}
		com.mojang.blaze3d.textures.GpuSampler sampler = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache()
			.getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR);
		g.guiRenderState.addGuiElement(new Textured(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, TextureSetup.singleTexture(view, sampler), pose, x, y, x + w, y + h, tl, tr, br, bl, scissor, bounds));
	}

	private record Textured(
		RenderPipeline pipeline,
		TextureSetup textureSetup,
		Matrix3x2f pose,
		float x0,
		float y0,
		float x1,
		float y1,
		int tl,
		int tr,
		int br,
		int bl,
		ScreenRectangle scissorArea,
		ScreenRectangle bounds
	) implements GuiElementRenderState {
		@Override
		public void buildVertices(VertexConsumer out) {
			out.addVertexWith2DPose(pose, x0, y0).setUv(0f, 0f).setColor(tl);
			out.addVertexWith2DPose(pose, x0, y1).setUv(0f, 1f).setColor(bl);
			out.addVertexWith2DPose(pose, x1, y1).setUv(1f, 1f).setColor(br);
			out.addVertexWith2DPose(pose, x1, y0).setUv(1f, 0f).setColor(tr);
		}
	}

	private record State(
		RenderPipeline pipeline,
		Matrix3x2f pose,
		float x0,
		float y0,
		float x1,
		float y1,
		float u,
		float v,
		int tl,
		int tr,
		int br,
		int bl,
		ScreenRectangle scissorArea,
		ScreenRectangle bounds
	) implements GuiElementRenderState {
		@Override
		public void buildVertices(VertexConsumer out) {
			out.addVertexWith2DPose(pose, x0, y0).setUv(-u, -v).setColor(tl);
			out.addVertexWith2DPose(pose, x0, y1).setUv(-u, v).setColor(bl);
			out.addVertexWith2DPose(pose, x1, y1).setUv(u, v).setColor(br);
			out.addVertexWith2DPose(pose, x1, y0).setUv(u, -v).setColor(tr);
		}

		@Override
		public TextureSetup textureSetup() {
			return TextureSetup.noTexture();
		}
	}

	private static synchronized void ensurePipeline() {
		if (pipeline != null) {
			return;
		}
		pipeline = RenderPipeline.builder()
			.withLocation(Stray.id("pipeline/gui_round_rect"))
			.withVertexShader(Stray.id("core/gui_round_rect"))
			.withFragmentShader(Stray.id("core/gui_round_rect"))
			.withBindGroupLayout(BindGroupLayouts.PROJECTION)
			.withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
			.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
			.withPrimitiveTopology(PrimitiveTopology.QUADS)
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.build();
	}

	public static int mix(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int aa = (a >>> 24) & 0xFF;
		int ar = (a >> 16) & 0xFF;
		int ag = (a >> 8) & 0xFF;
		int ab = a & 0xFF;
		int ba = (b >>> 24) & 0xFF;
		int br = (b >> 16) & 0xFF;
		int bg = (b >> 8) & 0xFF;
		int bb = b & 0xFF;
		return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16) | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
	}
}
