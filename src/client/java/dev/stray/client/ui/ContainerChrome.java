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
	private static final int SLOT = 16;
	private static final float SLOT_R = 4f;
	private static final int PAD = 8;
	private static final int VANILLA_LABEL = 0xFF404040;
	private static final int PLAYER_X1 = 26;
	private static final int PLAYER_Y1 = 8;
	private static final int PLAYER_X2 = 75;
	private static final int PLAYER_Y2 = 78;
	private static final int PLAYER_SCALE = 30;

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
		float x = box.stray$leftPos() - PAD;
		float y = box.stray$topPos() - PAD;
		float w = box.stray$imageWidth() + PAD * 2;
		float h = box.stray$imageHeight() + PAD * 2;
		float radius = Math.min(12f, Math.min(w, h) * 0.08f);
		GuiFrostBlur.blitWindow(graphics, x, y, w, h, radius);
		GuiDraw.roundedFine(graphics, x, y, w, h, radius, ControlChrome.windowFill());
		GuiDraw.roundedOutline(graphics, x, y, w, h, radius, Theme.LINE, 1f);
		int left = box.stray$leftPos();
		int top = box.stray$topPos();
		for (Slot slot : container.getMenu().slots) {
			if (slot == null || !slot.isActive()) {
				continue;
			}
			int sx = left + slot.x;
			int sy = top + slot.y;
			GuiDraw.rounded(graphics, sx, sy, SLOT, SLOT, SLOT_R, Theme.PANEL);
			GuiDraw.roundedOutline(graphics, sx, sy, SLOT, SLOT, SLOT_R, Theme.LINE, 1f);
		}
		player(graphics, container, box);
	}

	public static void hover(GuiGraphicsExtractor graphics, Slot hovered) {
		if (hovered == null || !hovered.isHighlightable()) {
			return;
		}
		Theme.refresh();
		GuiDraw.roundedOutline(graphics, hovered.x, hovered.y, SLOT, SLOT, SLOT_R, Theme.ACCENT, 1f);
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
