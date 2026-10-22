package dev.stray.client.menu;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.mixin.AbstractContainerScreenAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Middle-click an item in a sack to run {@code /recipe}.
 * Sack of Sacks is the bag picker, so it is left alone.
 */
public final class SackRecipe {
	private SackRecipe() {
	}

	public static boolean click(AbstractContainerScreen<?> screen, Slot slot, int button, ContainerInput type) {
		boolean middle = button == 2 || type == ContainerInput.CLONE;
		if (!middle || !StrayConfig.get().sackRecipe || !sack(screen) || !sackSlot(screen, slot)) {
			return false;
		}
		ItemStack stack = slot.getItem();
		String name = itemName(stack);
		if (name.isBlank()) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.player.connection == null) {
			return false;
		}
		client.player.connection.sendCommand("recipe " + name);
		return true;
	}

	public static List<Component> withLore(ItemStack stack, List<Component> lines) {
		if (!StrayConfig.get().sackRecipe || stack == null || stack.isEmpty()) {
			return null;
		}
		Minecraft client = Minecraft.getInstance();
		if (!(client.screen instanceof AbstractContainerScreen<?> screen) || !sack(screen)) {
			return null;
		}
		Slot hovered = ((AbstractContainerScreenAccessor) screen).stray$hoveredSlot();
		if (!sackSlot(screen, hovered) || hovered.getItem() != stack || itemName(stack).isBlank()) {
			return null;
		}
		List<Component> next = new ArrayList<>(lines == null ? List.of() : lines);
		next.add(Component.literal("Middle click for recipe").withStyle(ChatFormatting.DARK_GRAY));
		return next;
	}

	private static boolean sack(AbstractContainerScreen<?> screen) {
		if (screen == null) {
			return false;
		}
		String title = plain(screen.getTitle().getString()).toLowerCase(Locale.ROOT);
		return title.contains("sack") && !title.contains("sack of sacks");
	}

	private static boolean sackSlot(AbstractContainerScreen<?> screen, Slot slot) {
		if (screen == null || slot == null || slot.getItem() == null || slot.getItem().isEmpty()) {
			return false;
		}
		int chest = Math.max(0, screen.getMenu().slots.size() - 36);
		return slot.index >= 0 && slot.index < chest;
	}

	private static String itemName(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "";
		}
		return plain(stack.getHoverName().getString());
	}

	private static String plain(String text) {
		if (text == null) {
			return "";
		}
		return text.replaceAll("§.", "").trim();
	}
}
