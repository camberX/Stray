package dev.stray.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.stray.Stray;
import dev.stray.client.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Looping liquid-marble backdrop for the title screen and out-of-world menus.
 * The teal stone stays put. Veins, splashes, and flecks use the accent color.
 */
public final class TitleBackdrop {
	private static final Identifier FALLBACK = Stray.id("textures/gui/title_marble.png");
	private static final Identifier SHADER = Stray.id("post/title_marble");
	private static final int FALLBACK_W = 1920;
	private static final int FALLBACK_H = 1080;
	private static final int MAX_W = 2560;
	private static final int MAX_H = 1440;
	private static final float LOOP = 30f;
	private static final long FRAME_NS = 33_333_333L;

	private static RenderPipeline pipeline;
	private static TextureTarget target;
	private static GpuBuffer config;
	private static int configSize;
	private static int targetW;
	private static int targetH;
	private static long renderedBucket = Long.MIN_VALUE;
	private static int renderedAccent = -1;
	private static boolean failed;

	private TitleBackdrop() {
	}

	public static void draw(GuiGraphicsExtractor graphics, int width, int height) {
		if (graphics == null || width <= 0 || height <= 0) {
			return;
		}
		Theme.refresh();
		if (!paint(graphics, width, height)) {
			float scale = Math.max(width / (float) FALLBACK_W, height / (float) FALLBACK_H) * 1.18f;
			float dw = FALLBACK_W * scale;
			float dh = FALLBACK_H * scale;
			GuiDraw.blit(
				graphics,
				FALLBACK,
				(width - dw) * 0.5f,
				(height - dh) * 0.5f,
				dw,
				dh,
				0f,
				0f,
				FALLBACK_W,
				FALLBACK_H,
				FALLBACK_W,
				FALLBACK_H,
				Theme.ACCENT
			);
		}
		GuiDraw.fill(graphics, 0, 0, width, height, Theme.withAlpha(0x000000, 56));
	}

	private static boolean paint(GuiGraphicsExtractor graphics, int width, int height) {
		if (failed) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.getWindow() == null) {
			return false;
		}
		int rw = Math.max(16, client.getWindow().getWidth());
		int rh = Math.max(16, client.getWindow().getHeight());
		float limit = Math.min(1f, Math.min(MAX_W / (float) rw, MAX_H / (float) rh));
		rw = Math.max(16, Math.round(rw * limit));
		rh = Math.max(16, Math.round(rh * limit));
		try {
			ensurePipeline();
			ensureTarget(rw, rh);
			int accent = Theme.ACCENT;
			long bucket = System.nanoTime() / FRAME_NS;
			if (bucket != renderedBucket || accent != renderedAccent) {
				render(rw, rh, accent);
				renderedBucket = bucket;
				renderedAccent = accent;
			}
			GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
			graphics.pose().pushMatrix();
			graphics.pose().scale(width, height);
			graphics.blit(target.getColorTextureView(), sampler, 0, 0, 1, 1, 0f, 1f, 1f, 0f);
			graphics.pose().popMatrix();
			return true;
		} catch (RuntimeException ex) {
			failed = true;
			return false;
		}
	}

	private static void render(int width, int height, int accent) {
		float seconds = ((System.nanoTime() / 1_000_000L) % 30_000L) / 1000f;
		float r = ((accent >> 16) & 0xFF) / 255f;
		float g = ((accent >> 8) & 0xFF) / 255f;
		float b = (accent & 0xFF) / 255f;
		var encoder = RenderSystem.getDevice().createCommandEncoder();
		try (MemoryStack stack = MemoryStack.stackPush()) {
			encoder.writeToBuffer(
				config.slice(),
				Std140Builder.onStack(stack, configSize)
					.putVec2(width, height)
					.putFloat(seconds)
					.putFloat(LOOP)
					.putVec4(r, g, b, 1f)
					.get()
			);
		}
		try (RenderPass pass = encoder.createRenderPass(
			() -> "stray title marble",
			target.getColorTextureView(),
			OptionalInt.empty()
		)) {
			pass.setPipeline(pipeline);
			pass.setUniform("MarbleConfig", config);
			pass.draw(0, 3);
		}
	}

	private static void ensureTarget(int width, int height) {
		if (target != null && targetW == width && targetH == height) {
			return;
		}
		if (target != null) {
			target.destroyBuffers();
			target = null;
		}
		target = new TextureTarget("stray title marble", width, height, false);
		targetW = width;
		targetH = height;
		renderedBucket = Long.MIN_VALUE;
	}

	private static synchronized void ensurePipeline() {
		if (pipeline != null) {
			return;
		}
		configSize = new Std140SizeCalculator().putVec2().putFloat().putFloat().putVec4().get();
		config = RenderSystem.getDevice().createBuffer(
			() -> "stray title marble",
			GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
			configSize
		);
		pipeline = RenderPipeline.builder()
			.withLocation(Stray.id("pipeline/title_marble"))
			.withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
			.withFragmentShader(SHADER)
			.withUniform("MarbleConfig", UniformType.UNIFORM_BUFFER)
			.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
			.withColorTargetState(new ColorTargetState(Optional.empty(), ColorTargetState.WRITE_ALL))
			.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
			.build();
	}
}
