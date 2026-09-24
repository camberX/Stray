package dev.stray.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.stray.client.farming.TopDownView;
import dev.stray.client.ui.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class TopDownHudRenderer {
	private static final float PAD = 4f;

	private TopDownHudRenderer() {
	}

	public static float drawWidth() {
		return TopDownView.WINDOW + PAD * 2f;
	}

	public static float drawHeight() {
		return TopDownView.WINDOW + PAD * 2f;
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.player == null || client.options.hideGui) {
			return;
		}
		if (!TopDownView.showing()) {
			return;
		}
		HudLayout.apply(graphics, client.font, HudLayout.Id.TOP_DOWN, () -> draw(graphics, client.font, 0, 0));
	}

	public static void draw(GuiGraphicsExtractor graphics, Font font, float x, float y) {
		float w = drawWidth();
		float h = drawHeight();
		HudChrome.panel(graphics, x, y, w, h, 6, Theme.HUD_WINDOW, Theme.HUD_LINE, Theme.ACCENT);
		float view = TopDownView.WINDOW;
		float left = x + PAD;
		float top = y + PAD;
		GpuTextureView texture = TopDownCapture.colorView();
		if (texture == null) {
			HudChrome.rounded(graphics, left, top, view, view, 4, Theme.withAlpha(Theme.HUD_CARD, 180));
			return;
		}
		GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		blitView(graphics, texture, sampler, left, top, view);
		GpuTextureView cut = TopDownCapture.cutView();
		if (cut == null) {
			return;
		}
		float radius = TopDownCapture.cutFraction() * view;
		float cx = left + view * 0.5f;
		float cy = top + view * 0.5f;
		org.joml.Vector2f origin = new org.joml.Vector2f();
		org.joml.Vector2f extent = new org.joml.Vector2f();
		graphics.pose().transformPosition(cx - radius, cy - radius, origin);
		graphics.pose().transformPosition(cx + radius, cy + radius, extent);
		if (!GuiDraw.scissor(graphics, origin.x, origin.y, extent.x - origin.x, extent.y - origin.y)) {
			return;
		}
		blitView(graphics, cut, sampler, left, top, view);
		GuiDraw.disableScissor(graphics);
	}

	private static void blitView(GuiGraphicsExtractor graphics, GpuTextureView texture, GpuSampler sampler, float left, float top, float view) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(left, top);
		graphics.pose().scale(view, view);
		graphics.blit(texture, sampler, 0, 0, 1, 1, 0f, 1f, 1f, 0f);
		graphics.pose().popMatrix();
	}
}
