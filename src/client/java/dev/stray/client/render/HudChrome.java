package dev.stray.client.render;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class HudChrome {
	private static final float STROKE = 0.5f;

	private static boolean hudPass;
	/** This draw skips accent strokes. Inventory HUD minimal uses it. */
	private static boolean neutralOutline;

	private HudChrome() {
	}

	public static void beginHud() {
		if (win98()) {
			dev.stray.client.ui.Win98.sync();
		}
		hudPass = true;
		element = null;
	}

	public static void endHud() {
		hudPass = false;
		element = null;
	}

	/** The HUD element being drawn, so its background toggle applies. */
	private static HudLayout.Id element;

	public static void element(HudLayout.Id id) {
		element = id;
	}

	/** False when the element being drawn has its background turned off. */
	public static boolean background() {
		return element == null || HudLayout.background(element);
	}

	/** The Windows 98 look: bevelled grey HUD panels. */
	public static boolean win98() {
		return dev.stray.client.ui.Looks.win98();
	}

	/** HUD text is re-inked dark for the grey Windows 98 face while the HUD draws. */
	public static boolean win98Ink() {
		return hudPass && win98() && background();
	}

	/** HUD text drawn in the Shadow style (Google Sans Flex, no shadow): true while the HUD draws. */
	public static boolean shadowInk() {
		return hudPass;
	}

	public static void neutralOutline(boolean neutral) {
		neutralOutline = neutral;
	}

	/** Black, or the accent color when accent outlines are on. */
	public static int outline() {
		if (neutralOutline) {
			return 0xFF000000;
		}
		return StrayConfig.get().accentOutlines ? Theme.ACCENT : 0xFF000000;
	}

	private static int line() {
		return neutralOutline ? Theme.HUD_LINE_NEUTRAL : Theme.HUD_LINE;
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
		if (!background()) {
			return;
		}
		if (win98()) {
			dev.stray.client.ui.Win98.raised(graphics, x, y, w, h);
			return;
		}
		dev.stray.client.ui.ShadowLook.panel(graphics, x, y, w, h, dev.stray.client.ui.ShadowLook.HUD_R, Theme.HUD_WINDOW);
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

	/** Sharp transparent-black box with a half-pixel outline, matching the click GUI. */
	public static void box(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int fill) {
		if (w < 1f || h < 1f) {
			return;
		}
		if (win98()) {
			dev.stray.client.ui.Win98.raised(graphics, x, y, w, h);
			return;
		}
		if (dev.stray.client.ui.Looks.panels()) {
			dev.stray.client.ui.PanelLook.panel(graphics, x, y, w, h, Theme.withAlpha(0x1C1C1C, Math.max(0xC8, (fill >>> 24) & 0xFF)));
			return;
		}
		if (dev.stray.client.ui.Looks.minecraft()) {
			// Minecraft: the chat box's black, square, like the vanilla scoreboard.
			GuiDraw.fill(graphics, x, y, w, h, Theme.HUD_WINDOW);
			return;
		}
		// Skeet: a near-black edge around the dark grey.
		float p = GuiDraw.pixel() / GuiDraw.poseScale(graphics);
		GuiDraw.fillSmooth(graphics, x, y, w, h, 0xFF0C0C0C);
		GuiDraw.fillSmooth(graphics, x + p, y + p, w - 2 * p, h - 2 * p, Theme.withAlpha(0x1A1A1A, Math.max(0xC0, (fill >>> 24) & 0xFF)));
	}

	/** Progress bars and chips. */
	public static void rounded(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, int color) {
		GuiDraw.rounded(graphics, x, y, w, h, radius, color);
	}

	public static void slot(GuiGraphicsExtractor graphics, float x, float y, float size, boolean selected) {
		if (dev.stray.client.ui.Looks.panels()) {
			dev.stray.client.ui.PanelLook.control(graphics, x, y, size, size, false, selected);
			return;
		}
		if (win98()) {
			if (selected) {
				dev.stray.client.ui.Win98.pressed(graphics, x, y, size, size);
				GuiDraw.border(graphics, x - 1, y - 1, size + 2, size + 2, dev.stray.client.ui.Win98.NAVY, 1f);
			} else {
				dev.stray.client.ui.Win98.sunken(graphics, x, y, size, size, dev.stray.client.ui.Win98.FACE);
			}
			return;
		}
		if (dev.stray.client.ui.Looks.minecraft()) {
			GuiDraw.fill(graphics, x, y, size, size, selected ? 0x40FFFFFF : 0x18FFFFFF);
			if (selected) {
				dev.stray.client.ui.MinecraftLook.outline(graphics, x, y, size, size, 0xFFFFFFFF);
			}
			return;
		}
		dev.stray.client.ui.UidLook.control(graphics, x, y, size, size, false, selected);
		if (selected) {
			GuiDraw.fillSmooth(graphics, x, y + size - 2 * GuiDraw.pixel(), size, 2 * GuiDraw.pixel(), dev.stray.client.ui.UidMenu.accent());
		}
	}
}
