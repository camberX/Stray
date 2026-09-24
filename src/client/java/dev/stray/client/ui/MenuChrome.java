package dev.stray.client.ui;

import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.Starfield;
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
		return client != null && applies(client.screen);
	}

	public static boolean applies(Screen screen) {
		if (screen == null) {
			return false;
		}
		if (screen instanceof StrayTitleScreen
			|| screen instanceof StrayScreen
			|| screen instanceof ItemEditScreen
			|| screen instanceof HudEditorScreen
			|| screen instanceof CapeCreatorScreen
			|| screen instanceof LoadoutsScreen
			|| screen instanceof CommandShortcutScreen) {
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
		int top;
		int bot;
		if (ControlChrome.on()) {
			int pane = ControlChrome.paneRgb();
			top = 0xFF000000 | Theme.mix(0x101218, pane, 0.28f);
			bot = 0xFF000000 | Theme.mix(0x05070D, pane, 0.16f);
		} else {
			top = 0xFF05070D;
			bot = 0xFF000000 | Theme.mix(0x0B0E14, Theme.ACCENT & 0xFFFFFF, 0.06f);
		}
		GuiDraw.fillGradient(graphics, 0, 0, width, height, top, bot);
		try {
			Starfield.drawSky(graphics, width, height);
		} catch (Throwable ignored) {
		}
	}

	public static Component bodyLabel(Component message, boolean active) {
		Theme.refresh();
		int color = (active ? Theme.TEXT : Theme.MUTED) & 0xFFFFFF;
		return message.copy().withStyle(MenuFont.bodyStyle().withColor(color));
	}

	public static Component titleLabel(Component message) {
		Theme.refresh();
		return message.copy().withStyle(MenuFont.bodyStyle().withColor(Theme.HEADER & 0xFFFFFF));
	}

	public static void button(GuiGraphicsExtractor graphics, AbstractWidget widget) {
		Theme.refresh();
		float alpha = widget.getAlpha();
		boolean hover = widget.active && widget.isHoveredOrFocused();
		int fill = Theme.withAlpha(ClickLook.FILL, Math.round(136 * alpha));
		int outline = hover ? Theme.ACCENT : Theme.LINE;
		ClickLook.panel(
			graphics,
			widget.getX(),
			widget.getY(),
			widget.getWidth(),
			widget.getHeight(),
			0,
			fill,
			outline
		);
	}

	public static void field(GuiGraphicsExtractor graphics, AbstractWidget widget) {
		float alpha = widget.getAlpha();
		boolean focus = widget.isFocused();
		int fill = Theme.withAlpha(ClickLook.FILL, Math.round(136 * alpha));
		int outline = focus ? Theme.ACCENT : Theme.LINE;
		ClickLook.panel(
			graphics,
			widget.getX(),
			widget.getY(),
			widget.getWidth(),
			widget.getHeight(),
			0,
			fill,
			outline
		);
	}

	public static void slider(GuiGraphicsExtractor graphics, AbstractWidget widget, double value) {
		float alpha = widget.getAlpha();
		int x = widget.getX();
		int y = widget.getY();
		int w = widget.getWidth();
		int h = widget.getHeight();
		boolean hover = widget.active && widget.isHoveredOrFocused();
		ClickLook.panel(
			graphics,
			x,
			y,
			w,
			h,
			0,
			Theme.withAlpha(ClickLook.FILL, Math.round(136 * alpha)),
			hover ? Theme.ACCENT : Theme.LINE
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

	public static void listPanel(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		ClickLook.panel(graphics, x, y, w, h, 8f, ClickLook.PANEL, Theme.LINE, 0);
	}

	public static void listSeparators(GuiGraphicsExtractor graphics, int x, int y, int w, int bottom) {
		GuiDraw.fill(graphics, x, y - 1, w, 1, Theme.withAlpha(Theme.ACCENT, 70));
		GuiDraw.fill(graphics, x, bottom, w, 1, Theme.withAlpha(Theme.LINE, 200));
	}

	public static void selection(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		ClickLook.panel(
			graphics,
			x,
			y,
			w,
			h,
			5f,
			Theme.withAlpha(Theme.CARD_HOVER, 210),
			Theme.ACCENT
		);
	}

	public static void scrollbar(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int thumbY, int thumbH, boolean active) {
		GuiDraw.rounded(graphics, x, y, w, h, 3f, Theme.withAlpha(Theme.TRACK, 200));
		if (active && thumbH > 0) {
			GuiDraw.rounded(graphics, x + 1, thumbY, Math.max(2, w - 2), thumbH, 3f, Theme.ACCENT);
		}
	}

	private static int fade(int color, float alpha) {
		int a = Math.round(((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
		return Theme.withAlpha(color, a);
	}
}
