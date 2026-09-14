package dev.stray.client.ui;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.mixin.AbstractContainerScreenAccessor;
import dev.stray.client.render.GuiDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

/**
 * Optional Stray look for vanilla and Hypixel container screens (chests, inventory, …).
 */
public final class ContainerChrome {
	private static final int SLOT = 18;
	private static final int VANILLA_LABEL = 0xFF404040;

	private ContainerChrome() {
	}

	public static boolean enabled() {
		return StrayConfig.get().themedGuisEnabled;
	}

	public static boolean applies(Screen screen) {
		return enabled() && screen instanceof AbstractContainerScreen<?>;
	}

	public static void cover(GuiGraphicsExtractor graphics, Screen screen) {
		if (!(screen instanceof AbstractContainerScreen<?> container)) {
			return;
		}
		Theme.refresh();
		AbstractContainerScreenAccessor box = (AbstractContainerScreenAccessor) container;
		float x = box.stray$leftPos();
		float y = box.stray$topPos();
		float w = box.stray$imageWidth();
		float h = box.stray$imageHeight();
		float radius = Math.min(12f, Math.min(w, h) * 0.08f);
		int pane = 0xFF000000 | ControlChrome.paneRgb();
		GuiDraw.roundedFine(graphics, x, y, w, h, radius, pane);
		ControlChrome.rim(graphics, x, y, w, h, radius);
		for (Slot slot : container.getMenu().slots) {
			if (slot == null || !slot.isActive()) {
				continue;
			}
			GuiDraw.well(
				graphics,
				x + slot.x - 1,
				y + slot.y - 1,
				SLOT,
				Theme.TRACK,
				Theme.LINE
			);
		}
	}

	public static void hover(GuiGraphicsExtractor graphics, Slot hovered) {
		if (hovered == null || !hovered.isHighlightable()) {
			return;
		}
		Theme.refresh();
		GuiDraw.wellBorder(graphics, hovered.x - 1, hovered.y - 1, SLOT, Theme.ACCENT);
	}

	public static int labelColor(int color) {
		if (!appliesCurrent()) {
			return color;
		}
		int rgb = color | 0xFF000000;
		if (rgb != VANILLA_LABEL) {
			return color;
		}
		Theme.refresh();
		return ControlChrome.text();
	}

	private static boolean appliesCurrent() {
		Minecraft client = Minecraft.getInstance();
		return client != null && applies(client.screen);
	}
}
