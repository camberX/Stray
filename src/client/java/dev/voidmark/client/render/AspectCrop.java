package dev.voidmark.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.voidmark.Voidmark;
import dev.voidmark.client.config.VoidmarkConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Pillarbox the world to the chosen aspect. The projection already matches that
 * ratio; this scissor stops the GPU from drawing the unseen sides, then paints
 * black bars over any leftover sky or post-process.
 */
public final class AspectCrop {
	private static boolean cropping;

	private AspectCrop() {
	}

	public static void init() {
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.HOTBAR,
			Voidmark.id("aspect_bars"),
			(graphics, delta) -> extract(graphics)
		);
	}

	public static boolean active() {
		VoidmarkConfig config = VoidmarkConfig.get();
		return config.aspectEnabled && config.aspectRatio < 0.995f;
	}

	public static void beginWorld() {
		cropping = false;
		if (!active()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		RenderTarget main = client.getMainRenderTarget();
		if (main == null) {
			return;
		}
		int visW = Math.max(1, Math.round(main.width * ratio()));
		int x = Math.max(0, (main.width - visW) / 2);
		RenderSystem.enableScissorForRenderTypeDraws(x, 0, visW, main.height);
		cropping = true;
	}

	public static void endWorld() {
		if (!cropping) {
			return;
		}
		cropping = false;
		RenderSystem.disableScissorForRenderTypeDraws();
	}

	private static void extract(GuiGraphicsExtractor graphics) {
		if (!active()) {
			return;
		}
		float guiW = graphics.guiWidth();
		float guiH = graphics.guiHeight();
		float visW = guiW * ratio();
		float bar = (guiW - visW) * 0.5f;
		if (bar < 0.5f) {
			return;
		}
		GuiDraw.fill(graphics, 0, 0, bar, guiH, 0xFF000000);
		GuiDraw.fill(graphics, guiW - bar, 0, bar, guiH, 0xFF000000);
	}

	private static float ratio() {
		return VoidmarkConfig.clamp(VoidmarkConfig.get().aspectRatio, 0.50f, 1f);
	}
}
