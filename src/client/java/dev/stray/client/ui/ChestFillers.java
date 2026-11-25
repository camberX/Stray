package dev.stray.client.ui;

import net.minecraft.world.item.DyeColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Hypixel chest menus pad empty cells with black stained glass. Skip drawing
 * those items so the themed well shows through. Part of Themed GUIs.
 */
public final class ChestFillers {
	private ChestFillers() {
	}

	/** Only with Themed GUIs on (off by default): with them off, chests look like vanilla. */
	public static boolean hide(Slot slot) {
		if (!ContainerChrome.enabled() || slot == null || slot.container instanceof Inventory) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		if (client == null || !(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
			return false;
		}
		if (!(screen.getMenu() instanceof ChestMenu)) {
			return false;
		}
		ItemStack stack = slot.getItem();
		return !stack.isEmpty() && stack.is(Items.STAINED_GLASS_PANE.pick(DyeColor.BLACK));
	}
}
