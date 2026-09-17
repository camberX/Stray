package dev.stray.client.render;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.pip.PipCapture;
import dev.stray.client.ui.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

public final class PipHudRenderer {
	public static final float WIDTH = 240f;
	public static final float CHROME = 16f;

	private PipHudRenderer() {
	}

	public static void init() {
	}

	public static boolean showing() {
		return StrayConfig.get().pipEnabled && (PipCapture.hasFrame() || HudLayout.editorOpen());
	}

	public static float drawWidth() {
		return WIDTH + 8f;
	}

	public static float drawHeight() {
		if (!StrayConfig.get().pipEnabled && !HudLayout.editorOpen()) {
			return 0f;
		}
		return CHROME + WIDTH / PipCapture.aspect() + 4f;
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.player == null || client.options.hideGui) {
			return;
		}
		StrayConfig config = StrayConfig.get();
		if (!config.pipEnabled && !HudLayout.editorOpen()) {
			return;
		}
		PipCapture.upload();
		HudLayout.apply(graphics, client.font, HudLayout.Id.PIP, () -> draw(graphics, client.font, 0, 0));
	}

	public static void draw(GuiGraphicsExtractor graphics, Font font, float x, float y) {
		float w = drawWidth();
		float h = drawHeight();
		HudChrome.panel(graphics, x, y, w, h, 6, Theme.HUD_WINDOW, Theme.HUD_LINE, Theme.ACCENT);
		GuiDraw.small(graphics, font, clip(font, PipCapture.status(), w - 12f), x + 6, y + 3, Theme.ACCENT);
		float viewX = x + 4;
		float viewY = y + CHROME - 2;
		float viewW = w - 8;
		float viewH = h - CHROME - 2;
		if (PipCapture.hasFrame()) {
			int alpha = Mth.clamp(Math.round(StrayConfig.get().pipOpacity * 255f), 32, 255);
			GuiDraw.blit(
				graphics,
				PipCapture.TEXTURE,
				viewX,
				viewY,
				viewW,
				viewH,
				0f,
				0f,
				PipCapture.texWidth(),
				PipCapture.texHeight(),
				PipCapture.texWidth(),
				PipCapture.texHeight(),
				Theme.withAlpha(0xFFFFFF, alpha)
			);
		} else {
			GuiDraw.rounded(graphics, viewX, viewY, viewW, viewH, 4, Theme.withAlpha(Theme.HUD_CARD, 180));
			String hint = HudLayout.editorOpen() ? "Select a window in HUD" : PipCapture.supported() ? "Select a window" : "Windows only";
			GuiDraw.small(graphics, font, hint, viewX + 6, viewY + viewH * 0.5f - 4, Theme.MUTED);
		}
	}

	private static String clip(Font font, String text, float max) {
		if (text == null || text.isBlank()) {
			return "PiP";
		}
		if (GuiDraw.smallWidth(font, text) <= max) {
			return text;
		}
		String cut = text;
		while (cut.length() > 1 && GuiDraw.smallWidth(font, cut + "…") > max) {
			cut = cut.substring(0, cut.length() - 1);
		}
		return cut + "…";
	}
}
