package dev.stray.client.render;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.ui.ControlChrome;
import dev.stray.client.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class HudChrome {
	private HudChrome() {
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
		if (StrayConfig.get().guiDesignControl()) {
			float r = Math.min(Math.max(6f, radius), Math.min(w, h) * 0.5f);
			if (h >= 40f) {
				GuiFrostBlur.blitWindow(graphics, x, y, w, h, Math.max(8f, radius));
				ControlChrome.glass(graphics, x, y, w, h, Math.max(8f, radius), ControlChrome.hudFill());
			} else {
				GuiDraw.roundedFine(graphics, x, y, w, h, r, ControlChrome.hudFill());
			}
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

	/** Rail sits on the side closer to the screen edge. */
	private static boolean accentTowardRight(GuiGraphicsExtractor graphics, float x, float y, float w) {
		var pose = graphics.pose();
		float left = pose.m00() * x + pose.m10() * y + pose.m20();
		float right = pose.m00() * (x + w) + pose.m10() * y + pose.m20();
		return (left + right) * 0.5f > graphics.guiWidth() * 0.5f;
	}
}
