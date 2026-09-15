package dev.stray.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Hypixel chest menus pad empty cells with black stained glass. Skip drawing
 * those items so the themed well (or vanilla slot) shows through.
 */
public final class ChestFillers {
	private ChestFillers() {
	}

	public static boolean hide(Slot slot) {
		if (slot == null || slot.container instanceof Inventory) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		if (client == null || !(client.screen instanceof AbstractContainerScreen<?> screen)) {
			return false;
		}
		if (!(screen.getMenu() instanceof ChestMenu)) {
			return false;
		}
		ItemStack stack = slot.getItem();
		return !stack.isEmpty() && stack.is(Items.BLACK_STAINED_GLASS_PANE);
	}
}
