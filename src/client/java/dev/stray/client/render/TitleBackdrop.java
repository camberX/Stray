package dev.stray.client.render;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import dev.stray.Stray;
import dev.stray.client.ui.Theme;
import dev.stray.client.render.RenderCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;

import java.util.Optional;

/**
 * Looping liquid-marble backdrop for the title screen and out-of-world menus.
 * The teal stone stays put. Veins, splashes, and flecks use the accent color.
 */
public final class TitleBackdrop {
	private static final Identifier SHADER = Stray.id("post/title_marble");
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
	/** A failed draw waits this long before trying again (shaders may still be loading). */
	private static long failedUntil;
	private static boolean pending;
	private static boolean ready;
	private static int pendingW;
	private static int pendingH;
	private static int pendingAccent;

	private TitleBackdrop() {
	}

	public static void draw(GuiGraphicsExtractor graphics, int width, int height) {
		if (graphics == null || width <= 0 || height <= 0) {
			return;
		}
		Theme.refresh();
		if (dev.stray.client.ui.Looks.panels()) {
			// Panels: plain neutral greys, the accent as one thin line along the top.
			GuiDraw.fillGradient(graphics, 0, 0, width, height, 0xFF181818, 0xFF0E0E0E);
			GuiDraw.fill(graphics, 0, 0, width, 1, dev.stray.client.ui.PanelLook.accent());
			return;
		}
		if (dev.stray.client.ui.Looks.uid()) {
			// uid?: the cheat-menu backdrop, near-black with its colour strip along the top.
			GuiDraw.fillGradient(graphics, 0, 0, width, height, 0xFF141414, 0xFF0B0B0B);
			float half = width / 2f;
			dev.stray.client.ui.UidLook.horizontal(graphics, 0, 0, half, 2, 0xFF37B1DA, 0xFFCC46CD);
			dev.stray.client.ui.UidLook.horizontal(graphics, half, 0, width - half, 2, 0xFFCC46CD, 0xFFCCE335);
			return;
		}
		if (!paint(graphics, width, height)) {
			// Plain dark until the marble shader is ready, so no other background flashes first.
			GuiDraw.fill(graphics, 0, 0, width, height, 0xFF050709);
		}
		GuiDraw.fill(graphics, 0, 0, width, height, Theme.withAlpha(0x000000, 56));
		if (dev.stray.client.ui.Looks.ex()) {
			// Expressive: the marble (tinted by the primary colour) under a tonal scrim with two soft
			// colour blooms, like a Material wallpaper.
			GuiDraw.fill(graphics, 0, 0, width, height, dev.stray.client.ui.ExColors.alpha(dev.stray.client.ui.ExColors.surfaceDim, 0.62f));
			dev.stray.client.ui.ShadowLook.glow(graphics, width * 0.08f, height * 0.62f, width * 0.32f, height * 0.30f, 80f, Math.round(Math.min(width, height) * 0.35f), dev.stray.client.ui.ExColors.alpha(dev.stray.client.ui.ExColors.primaryContainer, 0.55f));
			dev.stray.client.ui.ShadowLook.glow(graphics, width * 0.62f, height * 0.08f, width * 0.30f, height * 0.26f, 80f, Math.round(Math.min(width, height) * 0.30f), dev.stray.client.ui.ExColors.alpha(dev.stray.client.ui.ExColors.tertiaryContainer, 0.45f));
		}
	}

	private static boolean paint(GuiGraphicsExtractor graphics, int width, int height) {
		if (System.nanoTime() < failedUntil || !StrayShaders.ready()) {
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
				// GUI extraction runs before the frame's GPU work starts; Vulkan
				// can't record a pass here, so the draw waits for flush().
				pending = true;
				pendingW = rw;
				pendingH = rh;
				pendingAccent = accent;
				renderedBucket = bucket;
				renderedAccent = accent;
			}
			if (!ready) {
				return false;
			}
			GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
			graphics.pose().pushMatrix();
			graphics.pose().scale(width, height);
			graphics.blit(target.getColorTextureView(), sampler, 0, 0, 1, 1, 0f, 1f, 1f, 0f);
			graphics.pose().popMatrix();
			return true;
		} catch (RuntimeException ex) {
			failedUntil = System.nanoTime() + 3_000_000_000L;
			return false;
		}
	}

	/** Called during the frame's render phase, before the GUI is drawn. */
	public static void flush() {
		if (!pending || System.nanoTime() < failedUntil || target == null || pipeline == null) {
			pending = false;
			return;
		}
		pending = false;
		if (target.width != pendingW || target.height != pendingH) {
			return;
		}
		try {
			render(pendingW, pendingH, pendingAccent);
			ready = true;
		} catch (RuntimeException ex) {
			failedUntil = System.nanoTime() + 3_000_000_000L;
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
			Optional.empty()
		)) {
			//? if >=26.3 {
			pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
			//?} else {
			/*pass.setPipeline(pipeline);*///?}
			pass.setUniform("MarbleConfig", config);
			pass.draw(3, 1, 0, 0);
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
		target = RenderCompat.colorTarget("stray title marble", width, height);
		ready = false;
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
		pipeline = RenderCompat.screenQuad(Stray.id("pipeline/title_marble"), SHADER)
			.withBindGroupLayout(RenderCompat.uniform("MarbleConfig"))
			.withColorTargetState(RenderCompat.opaqueTarget())
			.build();
	}
}
