package dev.stray.client.render;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.ui.ControlChrome;
import dev.stray.client.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class HudChrome {
	private static boolean hudPass;

	private HudChrome() {
	}

	public static void beginHud() {
		hudPass = true;
	}

	public static void endHud() {
		hudPass = false;
	}

	public static boolean vanilla() {
		return StrayConfig.get().hudStyleVanilla();
	}

	public static boolean vanillaInk() {
		return hudPass && vanilla();
	}

	public static void panel(
		GuiGraphicsExtractor graphics,
		float x,
		float y,
		float w,
		float h,
		float radius,
		int fill,
		int outline,
		int accent
	) {
		if (vanilla()) {
			tooltip(graphics, x, y, w, h);
			return;
		}
		if (StrayConfig.get().guiDesignControl()) {
			float r = Math.min(Math.max(6f, radius), Math.min(w, h) / 2f);
			GuiFrostBlur.blitWindow(graphics, x, y, w, h, r);
			ControlChrome.glass(graphics, x, y, w, h, r, ControlChrome.hudFill());
			if (StrayConfig.get().hudStarfield && w >= 72f && h >= 52f) {
				Starfield.drawHud(graphics, x, y, w, h, Math.max(8f, radius));
			}
			return;
		}
		boolean right = accentTowardRight(graphics, x, y, w);
		GuiDraw.panel(graphics, x, y, w, h, radius, Theme.HUD_WINDOW, Theme.HUD_LINE, accent, right);
		if (StrayConfig.get().hudStarfield && w >= 72f && h >= 52f) {
			Starfield.drawHud(graphics, x, y, w, h, radius);
		}
	}

	public static void panel(
		GuiGraphicsExtractor graphics,
		float x,
		float y,
		float w,
		float h,
		float radius,
		int fill,
		int outline
	) {
		panel(graphics, x, y, w, h, radius, fill, outline, 0);
	}

	/** Minecraft item-tooltip chrome: dark fill and the purple gradient rim. */
	private static void tooltip(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		if (w < 4f || h < 4f) {
			return;
		}
		float opacity = StrayConfig.clamp(StrayConfig.get().hudOpacity, 0.20f, 1f);
		int background = Theme.withAlpha(0x100010, Math.round(240 * opacity));
		int start = Theme.withAlpha(0x5000FF, Math.round(80 * opacity));
		int end = Theme.withAlpha(0x28007F, Math.round(80 * opacity));
		GuiDraw.fill(graphics, x + 1, y, w - 2, h, background);
		GuiDraw.fill(graphics, x, y + 1, 1, h - 2, background);
		GuiDraw.fill(graphics, x + w - 1, y + 1, 1, h - 2, background);
		GuiDraw.fill(graphics, x + 1, y + 1, w - 2, 1, start);
		GuiDraw.fill(graphics, x + 1, y + h - 2, w - 2, 1, end);
		float side = Math.max(1f, h - 4f);
		GuiDraw.fillGradient(graphics, x + 1, y + 2, 1, side, start, end);
		GuiDraw.fillGradient(graphics, x + w - 2, y + 2, 1, side, start, end);
	}

	/** Rail sits on the side closer to the screen edge. */
	private static boolean accentTowardRight(GuiGraphicsExtractor graphics, float x, float y, float w) {
		var pose = graphics.pose();
		float left = pose.m00() * x + pose.m10() * y + pose.m20();
		float right = pose.m00() * (x + w) + pose.m10() * y + pose.m20();
		return (left + right) * 0.5f > graphics.guiWidth() * 0.5f;
	}
}
