package dev.stray.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
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
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.stray.Stray;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.mixin.GuiGraphicsExtractorInvoker;
import dev.stray.client.ui.ContainerChrome;
import dev.stray.client.ui.StrayScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.joml.Vector2f;
import org.lwjgl.system.MemoryStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Blur a copy of the world after it is drawn and blit that copy as opaque RGB
 * inside Control panes and HUD glass.
 * <p>
 * The blur is vanilla's menu box blur (three horizontal + vertical rounds at the
 * same radius), run through our own pipeline so {@code minecraft:main} never has
 * to be copied, blurred in place, and restored. When only the HUD is showing,
 * the passes are scissored to the union of the HUD boxes (padded by the full
 * kernel reach), which keeps the result pixel-identical under the glass while
 * skipping the rest of the screen.
 */
public final class GuiFrostBlur {
	private static final Identifier BLUR_SHADER = Stray.id("post/frost_blur");
	private static final Identifier ROUNDED_BLIT_SHADER = Stray.id("core/gui_rounded_blit");
	private static final Identifier LIQUID_BLIT_SHADER = Stray.id("core/gui_liquid_glass");
	private static final int ROUNDS = 3;
	private static final float REGION_PAD_GUI = 3f;
	/** CSS blur(6px) on the liquid-glass backdrop, before the displacement. */
	private static final float LIQUID_BLUR = 4f;
	/** The lens reads about 21px outside the pane, plus the blur kernel. */
	private static final float LIQUID_PAD_GUI = 32f;
	private static final int MAX_REGIONS = 256;
	private static final Vector2f UV_A = new Vector2f();
	private static final Vector2f UV_B = new Vector2f();

	private static TextureTarget frost;
	private static TextureTarget glass;
	private static TextureTarget swap;
	private static boolean haveFrost;
	private static boolean haveGlass;
	private static float lastFrost = -1f;
	private static float writtenRadius = -1f;
	private static RenderPipeline blurPipeline;
	private static RenderPipeline roundedBlitPipeline;
	private static RenderPipeline liquidBlitPipeline;
	private static GpuBuffer configH;
	private static GpuBuffer configV;

	/**
	 * GUI-space rectangles of every glass blit recorded since the last capture.
	 * GUI extraction runs before {@code GameRenderer.render}, so at capture time
	 * these are exactly the panes about to be drawn this frame.
	 */
	private static final List<float[]> BLITS = new ArrayList<>();
	private static final List<float[]> LIQUID = new ArrayList<>();
	private static boolean blitOverflow;
	private static boolean liquidOverflow;

	private GuiFrostBlur() {
	}

	public static void captureAfterWorld() {
		Minecraft client = Minecraft.getInstance();
		List<Region> regions = takeRegions(client, BLITS, blitOverflow, REGION_PAD_GUI);
		blitOverflow = false;
		List<Region> liquid = takeRegions(client, LIQUID, liquidOverflow, LIQUID_PAD_GUI);
		liquidOverflow = false;
		if (client == null || !StrayConfig.get().guiDesignControl()) {
			haveFrost = false;
			haveGlass = false;
			lastFrost = -1f;
			return;
		}
		boolean menu = client.screen instanceof StrayScreen || ContainerChrome.applies(client.screen);
		boolean hud = client.level != null && (client.options == null || !client.options.hideGui);
		if (!menu && !hud) {
			haveFrost = false;
			haveGlass = false;
			lastFrost = -1f;
			return;
		}
		boolean frostWanted = regions == null || !regions.isEmpty();
		boolean glassWanted = liquid == null || !liquid.isEmpty();
		if (!frostWanted) {
			haveFrost = false;
			lastFrost = -1f;
		}
		if (!glassWanted) {
			haveGlass = false;
		}
		if (frostWanted) {
			capture(StrayConfig.get().controlFrost, regions);
		}
		if (glassWanted) {
			captureLiquid(liquid);
		}
	}

	public static void capture(float frost01) {
		capture(frost01, null);
	}

	private static void capture(float frost01, List<Region> regions) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || frost01 < 0.01f) {
			haveFrost = false;
			lastFrost = -1f;
			return;
		}
		RenderTarget main = client.getMainRenderTarget();
		if (main == null || main.getColorTextureView() == null || main.width <= 0 || main.height <= 0) {
			haveFrost = false;
			return;
		}
		ensure(main.width, main.height);
		if (frost == null || swap == null || frost.getColorTextureView() == null || swap.getColorTextureView() == null) {
			haveFrost = false;
			return;
		}
		// A paused world is static, so a full-screen frost can be reused; scissored
		// frost cannot, because the panes may have moved since it was made.
		if (regions == null && haveFrost && Math.abs(lastFrost - frost01) < 0.002f && client.isPaused()) {
			return;
		}
		try {
			float radius = 2.5f + StrayConfig.clamp(frost01, 0f, 1f) * 29.5f;
			ensureBlurPipeline();
			writeConfigs(radius);
			blur(main.getColorTextureView(), frost, regions, main.width, main.height, Math.round(radius));
			haveFrost = true;
			lastFrost = frost01;
		} catch (RuntimeException ignored) {
			haveFrost = false;
		}
	}

	public static void blitWindow(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius) {
		noteBlit(graphics, x, y, w, h);
		if (!haveFrost || frost == null || w <= 0f || h <= 0f) {
			return;
		}
		GpuTextureView view = frost.getColorTextureView();
		if (view == null) {
			return;
		}
		GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		float r = Math.min(radius, Math.min(w, h) / 2f);
		if (r < 0.75f || !(graphics instanceof GuiGraphicsExtractorInvoker invoker)) {
			blitRegion(graphics, view, sampler, x, y, w, h);
			return;
		}
		ensureRoundedBlitPipeline();
		int ru = Math.max(1, Math.min(255, Math.round(r / w * 255f)));
		int rv = Math.max(1, Math.min(255, Math.round(r / h * 255f)));
		int color = 0xFF000000 | (ru << 16) | (rv << 8) | 0xFF;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(w, h);
		invoker.stray$innerBlit(roundedBlitPipeline, view, sampler, 0, 0, 1, 1, 0f, 1f, 0f, 1f, color);
		graphics.pose().popMatrix();
	}

	/**
	 * Standard liquid-glass lens: a 6px blur, then the red/blue displacement map
	 * at scale 70 with the library's chromatic split.
	 */
	public static void blitLiquid(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius) {
		blitLiquid(graphics, x, y, w, h, radius, 1f);
	}

	/**
	 * @param shine specular strength, 0 to 1. 1 matches the control-menu lens.
	 */
	public static void blitLiquid(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, float shine) {
		note(LIQUID, graphics, x, y, w, h, true);
		if (!haveGlass || glass == null || w <= 0f || h <= 0f) {
			return;
		}
		GpuTextureView view = glass.getColorTextureView();
		if (view == null) {
			return;
		}
		GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		float r = Math.min(radius, Math.min(w, h) / 2f);
		if (r < 0.75f || !(graphics instanceof GuiGraphicsExtractorInvoker invoker)) {
			blitRegion(graphics, view, sampler, x, y, w, h);
			return;
		}
		ensureLiquidBlitPipeline();
		int ru = Math.max(1, Math.min(255, Math.round(r / w * 255f)));
		int rv = Math.max(1, Math.min(255, Math.round(r / h * 255f)));
		int scaleByte = packScale(framebufferScale(graphics, x, y, w));
		float[] cursor = cursorInQuad(graphics, x, y, w, h);
		int mouse = packMouse((cursor[0] - 0.5f) * 100f, (cursor[1] - 0.5f) * 100f, shine);
		int color = (mouse << 24) | (ru << 16) | (rv << 8) | scaleByte;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(w, h);
		invoker.stray$innerBlit(liquidBlitPipeline, view, sampler, 0, 0, 1, 1, 0f, 1f, 0f, 1f, color);
		graphics.pose().popMatrix();
	}

	private static void captureLiquid(List<Region> regions) {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.level == null) {
			haveGlass = false;
			return;
		}
		RenderTarget main = client.getMainRenderTarget();
		if (main == null || main.getColorTextureView() == null || main.width <= 0 || main.height <= 0) {
			haveGlass = false;
			return;
		}
		ensure(main.width, main.height);
		if (glass == null || swap == null || glass.getColorTextureView() == null || swap.getColorTextureView() == null) {
			haveGlass = false;
			return;
		}
		try {
			ensureBlurPipeline();
			writeConfigs(LIQUID_BLUR);
			blur(main.getColorTextureView(), glass, regions, main.width, main.height, Math.round(LIQUID_BLUR));
			haveGlass = true;
		} catch (RuntimeException ignored) {
			haveGlass = false;
		}
	}

	/** Cursor in quad space. 0.5, 0.5 when it cannot be read. */
	private static float[] cursorInQuad(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		float[] out = {0.5f, 0.5f};
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.mouseHandler == null || client.getWindow() == null) {
			return out;
		}
		graphics.pose().transformPosition(x, y, UV_A);
		graphics.pose().transformPosition(x + w, y + h, UV_B);
		float left = Math.min(UV_A.x, UV_B.x);
		float right = Math.max(UV_A.x, UV_B.x);
		float top = Math.min(UV_A.y, UV_B.y);
		float bottom = Math.max(UV_A.y, UV_B.y);
		double mx = client.mouseHandler.getScaledXPos(client.getWindow());
		double my = client.mouseHandler.getScaledYPos(client.getWindow());
		out[0] = (float) ((mx - left) / Math.max(0.001f, right - left));
		out[1] = (float) ((my - top) / Math.max(0.001f, bottom - top));
		return out;
	}

	/** Framebuffer pixels per local GUI pixel, packed into one color byte. */
	private static int packScale(float scale) {
		return Math.max(1, Math.min(255, Math.round(scale * 40f)));
	}

	/**
	 * Alpha byte: shine 0–3 in the top two bits, mouse offset about -100..100
	 * in 3 bits per axis. Shine 3 is the full control-menu specular.
	 */
	private static int packMouse(float offsetX, float offsetY, float shine) {
		int level = Math.max(0, Math.min(3, Math.round(Math.max(0f, Math.min(1f, shine)) * 3f)));
		int mx = Math.max(0, Math.min(7, Math.round((offsetX + 100f) / 200f * 7f)));
		int my = Math.max(0, Math.min(7, Math.round((offsetY + 100f) / 200f * 7f)));
		return (level << 6) | (mx << 3) | my;
	}

	private static float framebufferScale(GuiGraphicsExtractor graphics, float x, float y, float w) {
		graphics.pose().transformPosition(x, y, UV_A);
		graphics.pose().transformPosition(x + Math.max(w, 1f), y, UV_B);
		float gui = Math.abs(UV_B.x - UV_A.x);
		Minecraft client = Minecraft.getInstance();
		double guiScale = client == null || client.getWindow() == null ? 1.0 : client.getWindow().getGuiScale();
		return (float) (gui * guiScale / Math.max(1f, w));
	}

	/**
	 * @param regions framebuffer rectangles that will be sampled, or {@code null}
	 *                to blur the whole screen
	 */
	private static void blur(GpuTextureView source, TextureTarget dest, List<Region> regions, int width, int height, int radius) {
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		List<Region> scissors = null;
		if (regions != null) {
			// Each H or V pass only trusts the previous pass one kernel reach into its
			// own scissor, so padding by the whole chain's reach keeps the glass exact.
			int pad = radius * ROUNDS + 2;
			scissors = new ArrayList<>(regions.size());
			for (Region region : regions) {
				Region padded = region.padded(pad, width, height);
				if (padded.w > 0 && padded.h > 0) {
					scissors.add(padded);
				}
			}
			scissors = merge(scissors);
			if (scissors.isEmpty()) {
				return;
			}
		}
		GpuTextureView in = source;
		for (int round = 0; round < ROUNDS; round++) {
			pass(encoder, in, swap.getColorTextureView(), configH, linear, scissors);
			pass(encoder, swap.getColorTextureView(), dest.getColorTextureView(), configV, linear, scissors);
			in = dest.getColorTextureView();
		}
	}

	private static void pass(
		CommandEncoder encoder,
		GpuTextureView in,
		GpuTextureView out,
		GpuBuffer config,
		GpuSampler sampler,
		List<Region> scissors
	) {
		try (RenderPass pass = encoder.createRenderPass(() -> "stray control frost", out, OptionalInt.empty())) {
			pass.setPipeline(blurPipeline);
			pass.setUniform("BlurConfig", config);
			pass.bindTexture("InSampler", in, sampler);
			if (scissors == null) {
				pass.draw(0, 3);
				return;
			}
			for (Region scissor : scissors) {
				pass.enableScissor(scissor.x, scissor.y, scissor.w, scissor.h);
				pass.draw(0, 3);
			}
			pass.disableScissor();
		}
	}

	/** Collapse overlapping scissors so shared pixels are not blurred twice. */
	private static List<Region> merge(List<Region> regions) {
		List<Region> out = new ArrayList<>(regions);
		boolean changed = true;
		while (changed) {
			changed = false;
			outer:
			for (int i = 0; i < out.size(); i++) {
				for (int j = i + 1; j < out.size(); j++) {
					Region a = out.get(i);
					Region b = out.get(j);
					if (a.intersects(b)) {
						out.set(i, a.union(b));
						out.remove(j);
						changed = true;
						break outer;
					}
				}
			}
		}
		return out;
	}

	private static void writeConfigs(float radius) {
		int size = new Std140SizeCalculator().putVec2().putFloat().get();
		if (configH == null || configH.isClosed() || configV == null || configV.isClosed()) {
			int usage = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
			configH = RenderSystem.getDevice().createBuffer(() -> "stray frost blur h", usage, size);
			configV = RenderSystem.getDevice().createBuffer(() -> "stray frost blur v", usage, size);
			writtenRadius = -1f;
		}
		if (writtenRadius == radius) {
			return;
		}
		writtenRadius = radius;
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		try (MemoryStack stack = MemoryStack.stackPush()) {
			encoder.writeToBuffer(configH.slice(), Std140Builder.onStack(stack, size).putVec2(1f, 0f).putFloat(radius).get());
		}
		try (MemoryStack stack = MemoryStack.stackPush()) {
			encoder.writeToBuffer(configV.slice(), Std140Builder.onStack(stack, size).putVec2(0f, 1f).putFloat(radius).get());
		}
	}

	/**
	 * Convert the glass rectangles recorded since the last capture into
	 * framebuffer scissors (GL convention) and reset the list for the next frame.
	 * Returns {@code null} when the whole screen must be blurred.
	 */
	private static List<Region> takeRegions(Minecraft client, List<float[]> rects, boolean overflow, float padGui) {
		if (client == null || overflow) {
			rects.clear();
			return null;
		}
		RenderTarget main = client.getMainRenderTarget();
		if (main == null) {
			rects.clear();
			return null;
		}
		double scale = client.getWindow().getGuiScale();
		int fbH = main.height;
		List<Region> out = new ArrayList<>(rects.size());
		for (float[] rect : rects) {
			int left = (int) Math.floor((rect[0] - padGui) * scale);
			int top = (int) Math.floor((rect[1] - padGui) * scale);
			int right = (int) Math.ceil((rect[2] + padGui) * scale);
			int bottom = (int) Math.ceil((rect[3] + padGui) * scale);
			out.add(new Region(left, fbH - bottom, right - left, bottom - top));
		}
		rects.clear();
		return out;
	}

	private static void noteBlit(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		note(BLITS, graphics, x, y, w, h, false);
	}

	private static void note(List<float[]> rects, GuiGraphicsExtractor graphics, float x, float y, float w, float h, boolean liquid) {
		if (liquid ? liquidOverflow : blitOverflow) {
			return;
		}
		if (rects.size() >= MAX_REGIONS) {
			if (liquid) {
				liquidOverflow = true;
			} else {
				blitOverflow = true;
			}
			rects.clear();
			return;
		}
		graphics.pose().transformPosition(x, y, UV_A);
		graphics.pose().transformPosition(x + w, y + h, UV_B);
		rects.add(new float[]{
			Math.min(UV_A.x, UV_B.x),
			Math.min(UV_A.y, UV_B.y),
			Math.max(UV_A.x, UV_B.x),
			Math.max(UV_A.y, UV_B.y)
		});
	}

	private static void blitRegion(
		GuiGraphicsExtractor graphics,
		GpuTextureView view,
		GpuSampler sampler,
		float x,
		float y,
		float w,
		float h
	) {
		if (w <= 0f || h <= 0f) {
			return;
		}
		graphics.pose().transformPosition(x, y, UV_A);
		graphics.pose().transformPosition(x + w, y + h, UV_B);
		Minecraft client = Minecraft.getInstance();
		float gw = Math.max(1, client.getWindow().getGuiScaledWidth());
		float gh = Math.max(1, client.getWindow().getGuiScaledHeight());
		float u0 = UV_A.x / gw;
		float u1 = UV_B.x / gw;
		float v0 = 1f - UV_A.y / gh;
		float v1 = 1f - UV_B.y / gh;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(w, h);
		graphics.blit(view, sampler, 0, 0, 1, 1, u0, u1, v0, v1);
		graphics.pose().popMatrix();
	}

	private static synchronized void ensureRoundedBlitPipeline() {
		if (roundedBlitPipeline != null) {
			return;
		}
		roundedBlitPipeline = RenderPipeline.builder()
			.withLocation(Stray.id("pipeline/gui_rounded_blit"))
			.withVertexShader(ROUNDED_BLIT_SHADER)
			.withFragmentShader(ROUNDED_BLIT_SHADER)
			.withSampler("Sampler0")
			.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
			.withUniform("Projection", UniformType.UNIFORM_BUFFER)
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
			.build();
	}

	private static synchronized void ensureLiquidBlitPipeline() {
		if (liquidBlitPipeline != null) {
			return;
		}
		liquidBlitPipeline = RenderPipeline.builder()
			.withLocation(Stray.id("pipeline/gui_liquid_glass"))
			.withVertexShader(ROUNDED_BLIT_SHADER)
			.withFragmentShader(LIQUID_BLIT_SHADER)
			.withSampler("Sampler0")
			.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
			.withUniform("Projection", UniformType.UNIFORM_BUFFER)
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
			.build();
	}

	private static synchronized void ensureBlurPipeline() {
		if (blurPipeline != null) {
			return;
		}
		blurPipeline = RenderPipeline.builder()
			.withLocation(Stray.id("pipeline/frost_blur"))
			.withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
			.withFragmentShader(BLUR_SHADER)
			.withSampler("InSampler")
			.withUniform("BlurConfig", UniformType.UNIFORM_BUFFER)
			.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
			.withColorTargetState(new ColorTargetState(Optional.empty(), ColorTargetState.WRITE_ALL))
			.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
			.build();
	}

	private static void ensure(int width, int height) {
		if (frost != null && frost.width == width && frost.height == height) {
			return;
		}
		if (frost != null) {
			frost.destroyBuffers();
			frost = null;
		}
		if (swap != null) {
			swap.destroyBuffers();
			swap = null;
		}
		if (glass != null) {
			glass.destroyBuffers();
			glass = null;
		}
		haveFrost = false;
		haveGlass = false;
		frost = new TextureTarget("stray control frost", width, height, false);
		glass = new TextureTarget("stray liquid glass", width, height, false);
		swap = new TextureTarget("stray control frost swap", width, height, false);
	}

	/** Framebuffer rectangle in GL scissor convention (origin bottom-left). */
	private record Region(int x, int y, int w, int h) {
		Region padded(int pad, int width, int height) {
			int left = Math.max(0, x - pad);
			int bottom = Math.max(0, y - pad);
			int right = Math.min(width, x + w + pad);
			int top = Math.min(height, y + h + pad);
			return new Region(left, bottom, Math.max(0, right - left), Math.max(0, top - bottom));
		}

		boolean intersects(Region other) {
			return x < other.x + other.w && other.x < x + w && y < other.y + other.h && other.y < y + h;
		}

		Region union(Region other) {
			int left = Math.min(x, other.x);
			int bottom = Math.min(y, other.y);
			int right = Math.max(x + w, other.x + other.w);
			int top = Math.max(y + h, other.y + other.h);
			return new Region(left, bottom, right - left, top - bottom);
		}
	}
}
