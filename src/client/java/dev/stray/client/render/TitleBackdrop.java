package dev.stray.client.render;

import dev.stray.client.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The backdrop of the title screen and the out-of-world menus, in the look in use. */
public final class TitleBackdrop {
	private TitleBackdrop() {
	}

	public static void draw(GuiGraphicsExtractor graphics, int width, int height) {
		if (graphics == null || width <= 0 || height <= 0) {
			return;
		}
		Theme.refresh();
		if (dev.stray.client.ui.Looks.minecraft()) {
			// Minecraft: the dark grey of the game's own menu backgrounds.
			GuiDraw.fillGradient(graphics, 0, 0, width, height, 0xFF2A2A2A, 0xFF141414);
			return;
		}
		if (dev.stray.client.ui.Looks.signature()) {
			// Stray: the menu's near-black, a touch darker at the bottom.
			GuiDraw.fillGradient(graphics, 0, 0, width, height, 0xFF0F0F0F, 0xFF090909);
			return;
		}
		if (dev.stray.client.ui.Looks.panels()) {
			// Modern: plain neutral greys, the accent as one thin line along the top.
			GuiDraw.fillGradient(graphics, 0, 0, width, height, 0xFF181818, 0xFF0E0E0E);
			GuiDraw.fill(graphics, 0, 0, width, 1, dev.stray.client.ui.PanelLook.accent());
			return;
		}
		// Skeet: the cheat-menu backdrop, near-black with its colour strip along the top.
		GuiDraw.fillGradient(graphics, 0, 0, width, height, 0xFF141414, 0xFF0B0B0B);
		float half = width / 2f;
		dev.stray.client.ui.UidLook.horizontal(graphics, 0, 0, half, 2, 0xFF37B1DA, 0xFFCC46CD);
		dev.stray.client.ui.UidLook.horizontal(graphics, half, 0, width - half, 2, 0xFFCC46CD, 0xFFCCE335);
	}
}
