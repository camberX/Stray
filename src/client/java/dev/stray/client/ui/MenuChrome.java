package dev.stray.client.ui;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.GuiFrostBlur;
import dev.stray.client.render.TitleBackdrop;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

/**
 * Stray look for vanilla menus (world list, server list, options, pause, …).
 * Inventory, chat, and Stray's own screens stay as they are.
 */
public final class MenuChrome {
	private MenuChrome() {
	}

	public static boolean enabled() {
		Minecraft client = Minecraft.getInstance();
		return client != null && applies(client.gui.screen());
	}

	public static boolean applies(Screen screen) {
		if (screen == null) {
			return false;
		}
		if (screen instanceof StrayTitleScreen
			|| screen instanceof ThemePickerScreen
			|| screen instanceof StrayScreen
			|| screen instanceof ItemEditScreen
			|| screen instanceof HudEditorScreen
			|| screen instanceof CapeCreatorScreen
			|| dev.stray.client.EditionGate.loadoutsScreen(screen)
			|| screen instanceof CommandShortcutScreen
			|| screen instanceof CustomTitleScreen) {
			return false;
		}
		if (screen.isInGameUi() || screen instanceof AbstractContainerScreen || screen instanceof ChatScreen) {
			return false;
		}
		return true;
	}

	public static boolean outOfWorld() {
		Minecraft client = Minecraft.getInstance();
		return client != null && client.level == null;
	}

	public static void sky(GuiGraphicsExtractor graphics, int width, int height) {
		if (outOfWorld()) {
			TitleBackdrop.draw(graphics, width, height);
			return;
		}
		Theme.refresh();
		if (Looks.uid()) {
			// uid?: no frost, just the world darkened like the cheat menus do.
			GuiDraw.fill(graphics, 0, 0, width, height, 0x90070707);
			return;
		}
		// Shadow: the world frosted and dimmed behind in-game menus; Expressive tints it tonal.
		GuiFrostBlur.blitWindow(graphics, 0, 0, width, height, 0f);
		GuiDraw.fill(graphics, 0, 0, width, height, Looks.ex() ? ExColors.alpha(ExColors.surfaceDim, 0.55f) : 0x60050709);
	}

	public static Component bodyLabel(Component message, boolean active) {
		Theme.refresh();
		int color = (active ? Theme.TEXT : Theme.MUTED) & 0xFFFFFF;
		return message.copy().withStyle(MenuFont.bodyLook().withColor(color));
	}

	public static Component titleLabel(Component message) {
		Theme.refresh();
		return message.copy().withStyle(MenuFont.bodyLook().withColor(Theme.HEADER & 0xFFFFFF));
	}

	public static void button(GuiGraphicsExtractor graphics, AbstractWidget widget) {
		Theme.refresh();
		float alpha = widget.getAlpha();
		boolean hover = widget.active && widget.isHoveredOrFocused();
		if (!Looks.shadow()) {
			look(graphics, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(), hover, widget.active, alpha, false);
			return;
		}
		int fill = Theme.withAlpha(ClickLook.FILL, Math.round(136 * alpha));
		int outline = hover || StrayConfig.get().accentOutlines ? Theme.ACCENT : ClickLook.OUTLINE;
		ClickLook.solid(graphics, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(), fill, outline);
	}

	public static void field(GuiGraphicsExtractor graphics, AbstractWidget widget) {
		float alpha = widget.getAlpha();
		boolean focus = widget.isFocused();
		if (!Looks.shadow()) {
			look(graphics, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(), widget.isHovered(), true, alpha, true);
			if (focus && Looks.ex()) {
				GuiDraw.alphaScale = alpha;
				ExLook.field(graphics, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(), true, 0f);
				GuiDraw.alphaScale = 1f;
			} else if (focus) {
				GuiDraw.fillSmooth(graphics, widget.getX() + 1, widget.getY() + widget.getHeight() - 1, widget.getWidth() - 2, 1, UidMenu.accent());
			}
			return;
		}
		int fill = Theme.withAlpha(ClickLook.FILL, Math.round(136 * alpha));
		int outline = focus || StrayConfig.get().accentOutlines ? Theme.ACCENT : ClickLook.OUTLINE;
		ClickLook.solid(graphics, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(), fill, outline);
	}

	public static void slider(GuiGraphicsExtractor graphics, AbstractWidget widget, double value) {
		float alpha = widget.getAlpha();
		int x = widget.getX();
		int y = widget.getY();
		int w = widget.getWidth();
		int h = widget.getHeight();
		boolean hover = widget.active && widget.isHoveredOrFocused();
		float tv = (float) Math.max(0d, Math.min(1d, value));
		if (Looks.uid()) {
			// A framed dark box filled with the menu colour up to the value, a light notch at it.
			look(graphics, x, y, w, h, hover, widget.active, alpha, false);
			float p = GuiDraw.pixel();
			float fill = (w - 2 * p) * tv;
			GuiDraw.fillGradient(graphics, x + p, y + p, fill, h - 2 * p, fade(Theme.withAlpha(UidMenu.accent(), 0x90), alpha), fade(Theme.withAlpha(UidMenu.accent(), 0x50), alpha));
			GuiDraw.fillSmooth(graphics, x + p + fill - p, y + p, 2 * p, h - 2 * p, fade(0xC8FFFFFF, alpha));
			return;
		}
		if (Looks.ex()) {
			// A tonal pill, filled with the primary container up to the value, and a bar handle.
			GuiDraw.alphaScale = alpha;
			float r = h * 0.5f;
			ExLook.rounded(graphics, x, y, w, h, r, ExColors.surfaceHigh);
			float hx = x + 4 + (w - 8 - 4) * tv;
			if (hx - x > 6) {
				ExLook.sides(graphics, x, y, hx - x - 2, h, r, 4f, ExColors.primaryContainer);
			}
			ExLook.state(graphics, x, y, w, h, r, hover ? 1f : 0f, false, ExColors.onSurface);
			ExLook.rounded(graphics, hx, y + 2, 4, h - 4, 2, ExColors.primary);
			GuiDraw.alphaScale = 1f;
			return;
		}
		ClickLook.solid(
			graphics,
			x,
			y,
			w,
			h,
			Theme.withAlpha(ClickLook.FILL, Math.round(136 * alpha)),
			hover || StrayConfig.get().accentOutlines ? Theme.ACCENT : ClickLook.OUTLINE
		);
		float t = (float) Math.max(0d, Math.min(1d, value));
		float trackX = x + 4;
		float trackW = Math.max(8, w - 8);
		float trackY = y + h * 0.5f - 2f;
		GuiDraw.fillSmooth(graphics, trackX, trackY, trackW, 4, fade(0xFF222222, alpha));
		float filled = trackW * t;
		if (filled > 1f) {
			GuiDraw.fillSmooth(graphics, trackX, trackY, filled, 4, fade(Theme.ACCENT, alpha));
		}
		float handleW = 4f;
		float hx = trackX + t * (trackW - handleW);
		GuiDraw.fillSmooth(graphics, hx, y + 3, handleW, h - 6, fade(0xFFFFFFFF, alpha));
	}

	/** A button box in the look in use, for screens that draw their own (the theme picker). */
	public static void box(GuiGraphicsExtractor graphics, float x, float y, float w, float h, boolean hover, boolean active) {
		if (!Looks.shadow()) {
			look(graphics, x, y, w, h, hover, active, 1f, false);
			return;
		}
		ClickLook.solid(graphics, x, y, w, h, Theme.withAlpha(ClickLook.FILL, 136), hover ? Theme.ACCENT : ClickLook.OUTLINE);
	}

	/**
	 * A vanilla button, field or slider box in uid? (a framed dark grey box) or Expressive (a
	 * tonal pill; fields are squarer, like Material's filled text fields).
	 */
	private static void look(GuiGraphicsExtractor graphics, float x, float y, float w, float h, boolean hover, boolean active, float alpha, boolean field) {
		GuiDraw.alphaScale = alpha;
		try {
			if (Looks.uid()) {
				UidLook.control(graphics, x, y, w, h, hover && active, false);
				if (!active) {
					GuiDraw.fillSmooth(graphics, x, y, w, h, 0x66000000);
				}
				return;
			}
			if (field) {
				ExLook.field(graphics, x, y, w, h, false, hover ? 1f : 0f);
				return;
			}
			float r = h * 0.5f;
			ExLook.rounded(graphics, x, y, w, h, r, active ? ExColors.secondaryContainer : ExColors.alpha(ExColors.surfaceHighest, 0.6f));
			if (active) {
				ExLook.state(graphics, x, y, w, h, r, hover ? 1f : 0f, false, ExColors.onSecondaryContainer);
			}
		} finally {
			GuiDraw.alphaScale = 1f;
		}
	}

	public static void listPanel(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		if (Looks.uid()) {
			UidLook.frame(graphics, x, y, w, h, 0xE0);
			return;
		}
		if (Looks.ex()) {
			ExLook.rounded(graphics, x, y, w, h, 18f, ExColors.alpha(ExColors.surfaceLow, 0.92f));
			return;
		}
		ClickLook.outside(graphics, x, y, w, h, 8f, ClickLook.PANEL, Theme.LINE, 0);
	}

	public static void listSeparators(GuiGraphicsExtractor graphics, int x, int y, int w, int bottom) {
		if (!Looks.shadow()) {
			return;
		}
		GuiDraw.fill(graphics, x, y - 1, w, 1, Theme.withAlpha(Theme.ACCENT, 70));
		GuiDraw.fill(graphics, x, bottom, w, 1, Theme.LINE);
	}

	public static void selection(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		if (Looks.uid()) {
			GuiDraw.fillSmooth(graphics, x, y, w, h, 0xFF2A2A2A);
			GuiDraw.fillSmooth(graphics, x, y, 2 * GuiDraw.pixel(), h, UidMenu.accent());
			return;
		}
		if (Looks.ex()) {
			ExLook.rounded(graphics, x, y, w, h, Math.min(14f, h * 0.5f), ExColors.secondaryContainer);
			return;
		}
		ClickLook.outside(
			graphics,
			x,
			y,
			w,
			h,
			5f,
			Theme.CARD_HOVER,
			Theme.ACCENT
		);
	}

	public static void scrollbar(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int thumbY, int thumbH, boolean active) {
		if (Looks.uid()) {
			GuiDraw.fill(graphics, x, y, w, h, 0xFF282828);
			if (active && thumbH > 0) {
				GuiDraw.fill(graphics, x + 1, thumbY, Math.max(1, w - 2), thumbH, 0xFF414141);
			}
			return;
		}
		if (Looks.ex()) {
			if (active && thumbH > 0) {
				ExLook.rounded(graphics, x + 1, thumbY, Math.max(2, w - 2), thumbH, w * 0.5f, ExColors.alpha(ExColors.onSurfaceVariant, 0.6f));
			}
			return;
		}
		GuiDraw.rounded(graphics, x, y, w, h, 3f, Theme.TRACK);
		if (active && thumbH > 0) {
			GuiDraw.rounded(graphics, x + 1, thumbY, Math.max(2, w - 2), thumbH, 3f, Theme.ACCENT);
		}
	}

	private static int fade(int color, float alpha) {
		int a = Math.round(((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
		return Theme.withAlpha(color, a);
	}
}
