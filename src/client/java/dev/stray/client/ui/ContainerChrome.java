package dev.stray.client.ui;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.mixin.AbstractContainerScreenAccessor;
import dev.stray.client.mixin.InventoryScreenAccessor;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.GuiFrostBlur;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.Slot;

/**
 * Optional Stray look for vanilla and Hypixel container screens (chests, inventory, …).
 */
public final class ContainerChrome {
	private static final int SLOT = 18;
	private static final int VANILLA_LABEL = 0xFF404040;
	private static final int PLAYER_X1 = 32;
	private static final int PLAYER_Y1 = 4;
	private static final int PLAYER_X2 = 80;
	private static final int PLAYER_Y2 = 82;
	private static final int PLAYER_SCALE = 38;

	private static boolean hideVanilla;

	private ContainerChrome() {
	}

	public static boolean enabled() {
		return StrayConfig.get().themedGuisEnabled;
	}

	public static boolean applies(Screen screen) {
		return enabled() && screen instanceof AbstractContainerScreen<?>;
	}

	public static void hideVanilla(boolean hide) {
		hideVanilla = hide;
	}

	public static boolean hideVanilla() {
		return hideVanilla;
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
		float radius = Math.min(ControlChrome.WINDOW_R, Math.min(w, h) * 0.14f);
		GuiFrostBlur.blitWindow(graphics, x, y, w, h, radius);
		GuiDraw.roundedFine(graphics, x, y, w, h, radius, ControlChrome.windowFill());
		GuiDraw.roundedOutline(graphics, x, y, w, h, radius, Theme.LINE, 1f);
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
		player(graphics, container, box);
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

	private static void player(
		GuiGraphicsExtractor graphics,
		AbstractContainerScreen<?> container,
		AbstractContainerScreenAccessor box
	) {
		if (!(container instanceof InventoryScreen inventory)) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client == null ? null : client.player;
		if (player == null) {
			return;
		}
		int left = box.stray$leftPos();
		int top = box.stray$topPos();
		InventoryScreenAccessor mouse = (InventoryScreenAccessor) inventory;
		InventoryScreen.extractEntityInInventoryFollowsMouse(
			graphics,
			left + PLAYER_X1,
			top + PLAYER_Y1,
			left + PLAYER_X2,
			top + PLAYER_Y2,
			PLAYER_SCALE,
			0.0625f,
			mouse.stray$xMouse(),
			mouse.stray$yMouse(),
			player
		);
	}

	private static boolean appliesCurrent() {
		Minecraft client = Minecraft.getInstance();
		return client != null && applies(client.screen);
	}
}
