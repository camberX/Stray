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
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.voidmark.Voidmark;
import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.ui.VoidmarkScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.UniformValue;
import net.minecraft.resources.Identifier;
import org.joml.Vector2f;
import org.lwjgl.system.MemoryStack;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Blur a copy of the world after it is drawn, restore {@code minecraft:main},
 * and blit that copy as opaque RGB only inside the Control pane.
 */
public final class GuiFrostBlur {
	private static final Identifier BOX_BLUR = Identifier.withDefaultNamespace("post/box_blur");
	private static final Identifier COPY_SHADER = Voidmark.id("post/gui_frost");
	private static final IdentityHashMap<PostPass, Vector2f> DIRECTIONS = new IdentityHashMap<>();
	private static final Vector2f UV_A = new Vector2f();
	private static final Vector2f UV_B = new Vector2f();

	private static boolean capturing;
	private static float captureRadius;
	private static TextureTarget frost;
	private static TextureTarget backup;
	private static boolean haveFrost;
	private static float lastFrost = -1f;
	private static RenderPipeline copyPipeline;

	private GuiFrostBlur() {
	}

	public static void register(PostPass pass, RenderPipeline pipeline, Map<String, List<UniformValue>> uniforms) {
		if (pipeline == null || !BOX_BLUR.equals(pipeline.getFragmentShader()) || uniforms == null) {
			return;
		}
		Vector2f dir = new Vector2f(1f, 0f);
		List<UniformValue> values = uniforms.get("BlurConfig");
		if (values != null) {
			for (UniformValue value : values) {
				if (value instanceof UniformValue.Vec2Uniform vec) {
					dir.set(vec.value());
				}
			}
		}
		DIRECTIONS.put(pass, dir);
	}

	public static void unregister(PostPass pass) {
		DIRECTIONS.remove(pass);
	}

	public static void apply(PostPass pass, Map<String, GpuBuffer> customUniforms) {
		Vector2f dir = DIRECTIONS.get(pass);
		if (dir == null || customUniforms == null) {
			return;
		}
		GpuBuffer buffer = customUniforms.get("BlurConfig");
		if (buffer == null || buffer.isClosed()) {
			return;
		}
		float radius = capturing ? captureRadius : 0f;
		int size = new Std140SizeCalculator().putVec2().putFloat().get();
		if (buffer.size() < size) {
			return;
		}
		try (MemoryStack stack = MemoryStack.stackPush()) {
			RenderSystem.getDevice().createCommandEncoder().writeToBuffer(
				buffer.slice(),
				Std140Builder.onStack(stack, size)
					.putVec2(dir.x, dir.y)
					.putFloat(radius)
					.get()
			);
		}
	}

	public static void captureAfterWorld() {
		Minecraft client = Minecraft.getInstance();
		if (!(client.screen instanceof VoidmarkScreen) || !VoidmarkConfig.get().guiDesignControl()) {
			haveFrost = false;
			lastFrost = -1f;
			return;
		}
		capture(VoidmarkConfig.get().controlFrost);
	}

	public static void capture(float frost01) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || frost01 < 0.01f) {
			haveFrost = false;
			lastFrost = -1f;
			return;
		}
		RenderTarget main = client.getMainRenderTarget();
		if (main == null || main.getColorTexture() == null || main.getColorTextureView() == null || main.width <= 0 || main.height <= 0) {
			haveFrost = false;
			return;
		}
		ensure(main.width, main.height);
		if (frost == null || backup == null || frost.getColorTexture() == null || backup.getColorTexture() == null || frost.getColorTextureView() == null) {
			haveFrost = false;
			return;
		}
		if (haveFrost && Math.abs(lastFrost - frost01) < 0.002f && client.isPaused()) {
			return;
		}
		try {
			copy(main.getColorTexture(), backup.getColorTexture(), main.width, main.height);
			capturing = true;
			captureRadius = 2.5f + VoidmarkConfig.clamp(frost01, 0f, 1f) * 13.5f;
			try {
				client.gameRenderer.processBlurEffect();
			} finally {
				capturing = false;
			}
			flatten(main.getColorTextureView(), frost.getColorTextureView());
			if (backup.getColorTexture() != null && main.getColorTexture() != null) {
				copy(backup.getColorTexture(), main.getColorTexture(), main.width, main.height);
			}
			haveFrost = true;
			lastFrost = frost01;
		} catch (RuntimeException ignored) {
			haveFrost = false;
			capturing = false;
		}
	}

	public static void blitWindow(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius) {
		if (!haveFrost || frost == null) {
			return;
		}
		GpuTextureView view = frost.getColorTextureView();
		if (view == null) {
			return;
		}
		GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		float r = Math.min(radius, Math.min(w, h) / 2f);
		blitRegion(graphics, view, sampler, x + r, y, w - 2f * r, h);
		blitRegion(graphics, view, sampler, x, y + r, r, h - 2f * r);
		blitRegion(graphics, view, sampler, x + w - r, y + r, r, h - 2f * r);
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

	private static void flatten(GpuTextureView source, GpuTextureView dest) {
		ensureCopyPipeline();
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
			() -> "voidmark control frost",
			dest,
			OptionalInt.empty()
		)) {
			pass.setPipeline(copyPipeline);
			pass.bindTexture(
				"InSampler",
				source,
				RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
			);
			pass.draw(0, 3);
		}
	}

	private static synchronized void ensureCopyPipeline() {
		if (copyPipeline != null) {
			return;
		}
		copyPipeline = RenderPipeline.builder()
			.withLocation(Voidmark.id("pipeline/gui_frost"))
			.withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
			.withFragmentShader(COPY_SHADER)
			.withSampler("InSampler")
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
		if (backup != null) {
			backup.destroyBuffers();
			backup = null;
		}
		haveFrost = false;
		frost = new TextureTarget("voidmark control frost", width, height, false);
		backup = new TextureTarget("voidmark control backup", width, height, false);
	}

	private static void copy(GpuTexture src, GpuTexture dest, int width, int height) {
		RenderSystem.getDevice().createCommandEncoder()
			.copyTextureToTexture(src, dest, 0, 0, 0, 0, 0, width, height);
	}
}
