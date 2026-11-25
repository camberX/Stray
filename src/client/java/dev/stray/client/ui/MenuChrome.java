package dev.stray.client.ui;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.render.GuiDraw;
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
		// Minecraft: vanilla screens stay exactly as the game draws them.
		if (screen == null || Looks.minecraft()) {
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
		if (Looks.panels()) {
			// Panels: no frost, the world simply dimmed.
			GuiDraw.fill(graphics, 0, 0, width, height, 0x99000000);
			return;
		}
		// Skeet: no frost, just the world darkened like the cheat menus do.
		GuiDraw.fill(graphics, 0, 0, width, height, 0x90070707);
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
		look(graphics, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(), hover, widget.active, alpha, false);
	}

	public static void field(GuiGraphicsExtractor graphics, AbstractWidget widget) {
		float alpha = widget.getAlpha();
		boolean focus = widget.isFocused();
		look(graphics, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(), widget.isHovered(), true, alpha, true);
		if (focus && Looks.panels()) {
			GuiDraw.alphaScale = alpha;
			GuiDraw.hairline(graphics, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(), Math.min(4f, widget.getHeight() * 0.5f), PanelLook.accent());
			GuiDraw.alphaScale = 1f;
		} else if (focus && Looks.uid()) {
			GuiDraw.fillSmooth(graphics, widget.getX() + 1, widget.getY() + widget.getHeight() - 1, widget.getWidth() - 2, 1, UidMenu.accent());
		}
	}

	public static void slider(GuiGraphicsExtractor graphics, AbstractWidget widget, double value) {
		float alpha = widget.getAlpha();
		int x = widget.getX();
		int y = widget.getY();
		int w = widget.getWidth();
		int h = widget.getHeight();
		boolean hover = widget.active && widget.isHoveredOrFocused();
		float tv = (float) Math.max(0d, Math.min(1d, value));
		if (Looks.panels()) {
			// Panels: a grey box, a thin accent track along the bottom up to the value, a white notch.
			look(graphics, x, y, w, h, hover, widget.active, alpha, false);
			GuiDraw.alphaScale = alpha;
			float tw = w - 8f;
			GuiDraw.fillSmooth(graphics, x + 4f, y + h - 4f, tw, 1.5f, PanelLook.EDGE);
			GuiDraw.fillSmooth(graphics, x + 4f, y + h - 4f, tw * tv, 1.5f, PanelLook.accent());
			GuiDraw.fillSmooth(graphics, x + 4f + (tw - 2f) * tv, y + 3f, 2f, h - 6f, PanelLook.WHITE);
			GuiDraw.alphaScale = 1f;
			return;
		}
		if (Looks.uid()) {
			// A framed dark box filled with the menu colour up to the value, a light notch at it.
			look(graphics, x, y, w, h, hover, widget.active, alpha, false);
			float p = GuiDraw.pixel();
			float fill = (w - 2 * p) * tv;
			GuiDraw.fillGradient(graphics, x + p, y + p, fill, h - 2 * p, fade(Theme.withAlpha(UidMenu.accent(), 0x90), alpha), fade(Theme.withAlpha(UidMenu.accent(), 0x50), alpha));
			GuiDraw.fillSmooth(graphics, x + p + fill - p, y + p, 2 * p, h - 2 * p, fade(0xC8FFFFFF, alpha));
			return;
		}
		look(graphics, x, y, w, h, hover, widget.active, alpha, false);
	}

	/** A button box in the look in use, for screens that draw their own (the theme picker). */
	public static void box(GuiGraphicsExtractor graphics, float x, float y, float w, float h, boolean hover, boolean active) {
		look(graphics, x, y, w, h, hover, active, 1f, false);
	}

	/** A vanilla button, field or slider box in the look in use. */
	private static void look(GuiGraphicsExtractor graphics, float x, float y, float w, float h, boolean hover, boolean active, float alpha, boolean field) {
		GuiDraw.alphaScale = alpha;
		try {
			if (Looks.minecraft()) {
				if (field) {
					MinecraftLook.field(graphics, x, y, w, h, false);
				} else {
					MinecraftLook.button(graphics, x, y, w, h, hover, active);
				}
				return;
			}
			if (Looks.panels()) {
				float r = Math.min(4f, h * 0.5f);
				GuiDraw.roundedFine(graphics, x, y, w, h, r, !active ? PanelLook.G1 : field ? PanelLook.G1 : hover ? PanelLook.G3 : PanelLook.G2);
				GuiDraw.hairline(graphics, x, y, w, h, r, hover && active ? PanelLook.DGREY : PanelLook.EDGE);
				return;
			}
			if (Looks.uid()) {
				UidLook.control(graphics, x, y, w, h, hover && active, false);
				if (!active) {
					GuiDraw.fillSmooth(graphics, x, y, w, h, 0x66000000);
				}
				return;
			}
		} finally {
			GuiDraw.alphaScale = 1f;
		}
	}

	public static void listSeparators(GuiGraphicsExtractor graphics, int x, int y, int w, int bottom) {
		// None in any look: lists sit in their panel.
	}

	public static void selection(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		if (Looks.panels()) {
			GuiDraw.roundedFine(graphics, x, y, w, h, 3f, PanelLook.G3);
			PanelLook.onBar(graphics, x + 1f, y, h);
			return;
		}
		GuiDraw.fillSmooth(graphics, x, y, w, h, 0xFF2A2A2A);
		GuiDraw.fillSmooth(graphics, x, y, 2 * GuiDraw.pixel(), h, UidMenu.accent());
	}

	public static void scrollbar(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int thumbY, int thumbH, boolean active) {
		if (Looks.panels()) {
			if (active && thumbH > 0) {
				GuiDraw.roundedFine(graphics, x + 1, thumbY, Math.max(2, w - 2), thumbH, 1f, PanelLook.DGREY);
			}
			return;
		}
		GuiDraw.fill(graphics, x, y, w, h, 0xFF282828);
		if (active && thumbH > 0) {
			GuiDraw.fill(graphics, x + 1, thumbY, Math.max(1, w - 2), thumbH, 0xFF414141);
		}
	}

	private static int fade(int color, float alpha) {
		int a = Math.round(((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
		return Theme.withAlpha(color, a);
	}
}
