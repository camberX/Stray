package dev.voidmark.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.voidmark.client.config.VoidmarkConfig;
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

/**
 * Control frost: blur a copy of the world, restore {@code minecraft:main},
 * then blit that copy only inside the rounded menu.
 */
public final class GuiFrostBlur {
	private static final Identifier BOX_BLUR = Identifier.withDefaultNamespace("post/box_blur");
	private static final IdentityHashMap<PostPass, Vector2f> DIRECTIONS = new IdentityHashMap<>();
	private static final Vector2f UV_A = new Vector2f();
	private static final Vector2f UV_B = new Vector2f();

	private static boolean capturing;
	private static float captureRadius;
	private static TextureTarget frost;
	private static TextureTarget backup;
	private static boolean haveFrost;
	private static float lastFrost = -1f;

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

	public static void capture(float frost01) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || frost01 < 0.01f) {
			haveFrost = false;
			lastFrost = -1f;
			return;
		}
		RenderTarget main = client.getMainRenderTarget();
		if (main == null || main.getColorTexture() == null || main.width <= 0 || main.height <= 0) {
			haveFrost = false;
			return;
		}
		ensure(main.width, main.height);
		if (frost == null || backup == null || frost.getColorTexture() == null || backup.getColorTexture() == null) {
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
			if (main.getColorTexture() != null && frost.getColorTexture() != null) {
				copy(main.getColorTexture(), frost.getColorTexture(), main.width, main.height);
			}
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
		if (!haveFrost || frost == null || backup == null) {
			return;
		}
		GpuTextureView view = frost.getColorTextureView();
		GpuTextureView sharp = backup.getColorTextureView();
		if (view == null || sharp == null) {
			return;
		}
		GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		blitRegion(graphics, view, sampler, x, y, w, h);
		coverEars(graphics, sharp, sampler, x, y, w, h, radius);
	}

	private static void coverEars(
		GuiGraphicsExtractor graphics,
		GpuTextureView sharp,
		GpuSampler sampler,
		float x,
		float y,
		float w,
		float h,
		float radius
	) {
		float r = Math.min(radius, Math.min(w, h) / 2f);
		if (r < 0.75f) {
			return;
		}
		int rows = Math.max(10, Math.round(r));
		float rowH = r / rows;
		for (int i = 0; i < rows; i++) {
			float ly = i * rowH;
			float dy = r - (ly + rowH * 0.5f);
			float chord = (float) Math.sqrt(Math.max(0f, r * r - dy * dy));
			float ear = r - chord;
			if (ear <= 0.02f) {
				continue;
			}
			float top = y + ly;
			float bottom = y + h - ly - rowH;
			float strip = rowH + 0.2f;
			blitRegion(graphics, sharp, sampler, x, top, ear, strip);
			blitRegion(graphics, sharp, sampler, x + w - ear, top, ear, strip);
			blitRegion(graphics, sharp, sampler, x, bottom, ear, strip);
			blitRegion(graphics, sharp, sampler, x + w - ear, bottom, ear, strip);
		}
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
