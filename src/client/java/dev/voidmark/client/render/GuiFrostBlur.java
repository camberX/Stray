package dev.voidmark.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
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
import dev.voidmark.Voidmark;
import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.ui.VoidmarkScreen;
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
	private static final Identifier BLUR_SHADER = Voidmark.id("post/frost_blur");
	private static final int ROUNDS = 3;
	private static final float REGION_PAD_GUI = 3f;
	private static final int MAX_REGIONS = 24;
	private static final Vector2f UV_A = new Vector2f();
	private static final Vector2f UV_B = new Vector2f();

	private static TextureTarget frost;
	private static TextureTarget swap;
	private static boolean haveFrost;
	private static float lastFrost = -1f;
	private static float writtenRadius = -1f;
	private static RenderPipeline blurPipeline;
	private static GpuBuffer configH;
	private static GpuBuffer configV;

	/**
	 * GUI-space rectangles of every glass blit recorded since the last capture.
	 * GUI extraction runs before {@code GameRenderer.render}, so at capture time
	 * these are exactly the panes about to be drawn this frame.
	 */
	private static final List<float[]> BLITS = new ArrayList<>();
	private static boolean blitOverflow;

	private GuiFrostBlur() {
	}

	public static void captureAfterWorld() {
		Minecraft client = Minecraft.getInstance();
		List<Region> regions = takeRegions(client);
		if (client == null || !VoidmarkConfig.get().guiDesignControl()) {
			haveFrost = false;
			lastFrost = -1f;
			return;
		}
		boolean menu = client.screen instanceof VoidmarkScreen;
		boolean hud = client.level != null && (client.options == null || !client.options.hideGui);
		if (!menu && !hud) {
			haveFrost = false;
			lastFrost = -1f;
			return;
		}
		if (regions != null && regions.isEmpty()) {
			// Nothing drew glass this frame, so there is nothing to blur under.
			haveFrost = false;
			lastFrost = -1f;
			return;
		}
		capture(VoidmarkConfig.get().controlFrost, regions);
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
			float radius = 2.5f + VoidmarkConfig.clamp(frost01, 0f, 1f) * 29.5f;
			ensureBlurPipeline();
			writeConfigs(radius);
			blur(main.getColorTextureView(), regions, main.width, main.height, Math.round(radius));
			haveFrost = true;
			lastFrost = frost01;
		} catch (RuntimeException ignored) {
			haveFrost = false;
		}
	}

	public static void blitWindow(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius) {
		noteBlit(graphics, x, y, w, h);
		if (!haveFrost || frost == null) {
			return;
		}
		GpuTextureView view = frost.getColorTextureView();
		if (view == null) {
			return;
		}
		float r = Math.max(0.75f, Math.min(radius, Math.min(w, h) / 2f));
		float hw = w * 0.5f;
		float hh = h * 0.5f;
		// Four analytic quadrants: the shader samples the frost by screen position
		// and clips it to the same anti-aliased silhouette as the glass on top.
		GuiShapes.frostQuadrant(graphics, view, x, y, hw, hh, r, GuiShapes.Corner.TOP_LEFT);
		GuiShapes.frostQuadrant(graphics, view, x + hw, y, w - hw, hh, r, GuiShapes.Corner.TOP_RIGHT);
		GuiShapes.frostQuadrant(graphics, view, x + hw, y + hh, w - hw, h - hh, r, GuiShapes.Corner.BOTTOM_RIGHT);
		GuiShapes.frostQuadrant(graphics, view, x, y + hh, hw, h - hh, r, GuiShapes.Corner.BOTTOM_LEFT);
	}

	/**
	 * @param regions framebuffer rectangles that will be sampled, or {@code null}
	 *                to blur the whole screen
	 */
	private static void blur(GpuTextureView source, List<Region> regions, int width, int height, int radius) {
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
			pass(encoder, swap.getColorTextureView(), frost.getColorTextureView(), configV, linear, scissors);
			in = frost.getColorTextureView();
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
		try (RenderPass pass = encoder.createRenderPass(() -> "voidmark control frost", out, OptionalInt.empty())) {
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
			configH = RenderSystem.getDevice().createBuffer(() -> "voidmark frost blur h", usage, size);
			configV = RenderSystem.getDevice().createBuffer(() -> "voidmark frost blur v", usage, size);
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
	private static List<Region> takeRegions(Minecraft client) {
		boolean overflow = blitOverflow;
		blitOverflow = false;
		if (client == null || overflow) {
			BLITS.clear();
			return null;
		}
		RenderTarget main = client.getMainRenderTarget();
		if (main == null) {
			BLITS.clear();
			return null;
		}
		double scale = client.getWindow().getGuiScale();
		int fbH = main.height;
		List<Region> out = new ArrayList<>(BLITS.size());
		for (float[] rect : BLITS) {
			int left = (int) Math.floor((rect[0] - REGION_PAD_GUI) * scale);
			int top = (int) Math.floor((rect[1] - REGION_PAD_GUI) * scale);
			int right = (int) Math.ceil((rect[2] + REGION_PAD_GUI) * scale);
			int bottom = (int) Math.ceil((rect[3] + REGION_PAD_GUI) * scale);
			out.add(new Region(left, fbH - bottom, right - left, bottom - top));
		}
		BLITS.clear();
		return out;
	}

	private static void noteBlit(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		if (blitOverflow) {
			return;
		}
		if (BLITS.size() >= MAX_REGIONS) {
			blitOverflow = true;
			BLITS.clear();
			return;
		}
		graphics.pose().transformPosition(x, y, UV_A);
		graphics.pose().transformPosition(x + w, y + h, UV_B);
		BLITS.add(new float[]{
			Math.min(UV_A.x, UV_B.x),
			Math.min(UV_A.y, UV_B.y),
			Math.max(UV_A.x, UV_B.x),
			Math.max(UV_A.y, UV_B.y)
		});
	}

	private static synchronized void ensureBlurPipeline() {
		if (blurPipeline != null) {
			return;
		}
		blurPipeline = RenderPipeline.builder()
			.withLocation(Voidmark.id("pipeline/frost_blur"))
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
		haveFrost = false;
		frost = new TextureTarget("voidmark control frost", width, height, false);
		swap = new TextureTarget("voidmark control frost swap", width, height, false);
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
