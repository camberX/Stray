package dev.stray.client.visual;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import dev.stray.Stray;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.render.RenderCompat;
import dev.stray.client.render.StrayShaders;
import net.minecraft.client.Minecraft;
import org.lwjgl.system.MemoryStack;

import java.util.Optional;

/**
 * Monochrome's "Whole screen" option: after the GUI is drawn, the finished frame goes black and
 * white too, so items, menus, mobs, the sky and every resource pack texture match the world.
 * Pixels close to the splash colour become the accent (same luminance, so they keep their
 * detail), and anything already in the accent colour stays. Stray's own menu is left alone so
 * colour pickers show real colours.
 */
public final class MonochromeScreen {
	private static final int CONFIG_SIZE = new Std140SizeCalculator().putVec4().putVec4().get();
	private static RenderPipeline pipeline;
	private static TextureTarget copy;
	private static GpuBuffer config;

	private MonochromeScreen() {
	}

	/** True when the pass runs this frame (the block shader then leaves the contrast to it). */
	public static boolean wanted(Minecraft client) {
		StrayConfig c = StrayConfig.get();
		return c.monochrome && c.monochromeScreen && client.level != null
			&& !(client.gui.screen() instanceof dev.stray.client.ui.StrayScreen);
	}

	/** Render thread, right after the GUI drew into the main target. */
	public static void apply() {
		Minecraft client = Minecraft.getInstance();
		if (!wanted(client)) {
			release();
			return;
		}
		if (!StrayShaders.ready()) {
			return;
		}
		RenderTarget main = client.gameRenderer.mainRenderTarget();
		if (main == null || main.getColorTexture() == null || main.getColorTextureView() == null || main.width <= 0 || main.height <= 0) {
			return;
		}
		try {
			ensure(main.width, main.height);
			CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
			encoder.copyTextureToTexture(main.getColorTexture(), copy.getColorTexture(), 0, 0, 0, 0, 0, main.width, main.height);
			writeConfig(encoder);
			GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
			try (RenderPass pass = encoder.createRenderPass(() -> "stray monochrome", main.getColorTextureView(), Optional.empty())) {
				RenderCompat.setPipeline(pass, pipeline);
				pass.setUniform("MonoConfig", config);
				//? if >=26.3 {
				pass.setUniform("InSampler", copy.getColorTextureView(), nearest);
				//?} else {
				/*pass.bindTexture("InSampler", copy.getColorTextureView(), nearest);*///?}
				pass.draw(3, 1, 0, 0);
			}
		} catch (RuntimeException exception) {
			Stray.LOGGER.warn("Monochrome screen pass failed; turning it off", exception);
			StrayConfig.get().monochromeScreen = false;
		}
	}

	private static void writeConfig(CommandEncoder encoder) {
		StrayConfig c = StrayConfig.get();
		int rgb = c.monochromeRgb & 0xFFFFFF;
		float accentHue = WorldTint.rgbToHsv(rgb)[0] / 360f;
		float splashHue = Monochrome.splashHueDegrees(c) / 360f;
		float width = StrayConfig.clamp(c.monochromeSplashWidth, 5f, 90f) / 360f;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			encoder.writeToBuffer(config.slice(), Std140Builder.onStack(stack, CONFIG_SIZE)
				.putVec4(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, c.monochromeSplash ? 1f : 0f)
				.putVec4(splashHue, width, StrayConfig.clamp(c.monochromeContrast, 0.5f, 1.5f), accentHue)
				.get());
		}
	}

	private static void ensure(int width, int height) {
		if (pipeline == null) {
			pipeline = RenderCompat.screenQuad(Stray.id("pipeline/monochrome_screen"), Stray.id("post/monochrome_screen"))
				.withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.IN_SAMPLER)
				.withBindGroupLayout(RenderCompat.uniform("MonoConfig"))
				.withColorTargetState(RenderCompat.opaqueTarget())
				.build();
		}
		if (config == null || config.isClosed()) {
			config = RenderSystem.getDevice().createBuffer(() -> "stray monochrome config", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, CONFIG_SIZE);
		}
		if (copy == null || copy.width != width || copy.height != height) {
			if (copy != null) {
				copy.destroyBuffers();
			}
			copy = RenderCompat.colorTarget("stray monochrome copy", width, height);
		}
	}

	/** Frees the full-screen copy while the option is off. */
	private static void release() {
		if (copy != null) {
			copy.destroyBuffers();
			copy = null;
		}
	}
}
