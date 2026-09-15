package dev.stray.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.stray.Stray;
import dev.stray.client.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Accent-tinted liquid marble for the title screen and out-of-world menus.
 * The texture is grayscale so {@link Theme#ACCENT} recolors it. UVs warp over
 * time so the surface keeps moving.
 */
public final class TitleBackdrop {
	private static final Identifier TEXTURE = Stray.id("textures/gui/title_marble.png");
	private static final Identifier SHADER = Stray.id("core/gui_title_marble");
	private static final int TEX_W = 1920;
	private static final int TEX_H = 1080;
	private static RenderPipeline pipeline;
	private static boolean flowing;
	private static float flowSeconds;

	private TitleBackdrop() {
	}

	public static float flowTime() {
		return flowing ? flowSeconds : 0f;
	}

	public static void draw(GuiGraphicsExtractor graphics, int width, int height) {
		if (graphics == null || width <= 0 || height <= 0) {
			return;
		}
		Theme.refresh();
		float scale = Math.max(width / (float) TEX_W, height / (float) TEX_H) * 1.08f;
		float dw = TEX_W * scale;
		float dh = TEX_H * scale;
		float x = (width - dw) * 0.5f;
		float y = (height - dh) * 0.5f;
		flowSeconds = System.nanoTime() / 1_000_000_000f;
		flowing = true;
		try {
			GuiDraw.blit(graphics, pipeline(), TEXTURE, x, y, dw, dh, 0f, 0f, TEX_W, TEX_H, TEX_W, TEX_H, Theme.ACCENT);
		} catch (Throwable ignored) {
			flowing = false;
			GuiDraw.blit(graphics, TEXTURE, x, y, dw, dh, 0f, 0f, TEX_W, TEX_H, TEX_W, TEX_H, Theme.ACCENT);
		} finally {
			flowing = false;
		}
		GuiDraw.fill(graphics, 0, 0, width, height, Theme.withAlpha(0x000000, 72));
	}

	private static synchronized RenderPipeline pipeline() {
		if (pipeline == null) {
			pipeline = RenderPipeline.builder()
				.withLocation(Stray.id("pipeline/gui_title_marble"))
				.withVertexShader(SHADER)
				.withFragmentShader(SHADER)
				.withSampler("Sampler0")
				.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
				.withUniform("Projection", UniformType.UNIFORM_BUFFER)
				.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
				.withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
				.build();
		}
		return pipeline;
	}
}
