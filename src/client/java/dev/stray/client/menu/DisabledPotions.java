package dev.stray.client.menu;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.item.ItemAppearance;
import dev.stray.client.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.BitSet;
import java.util.List;
import java.util.Locale;

/**
 * Highlights slots whose lore contains {@code DISABLED} in Hypixel's
 * {@code Toggle Potion Effects} chests. Lore is read only from slot-update
 * packets, never polled each tick.
 */
public final class DisabledPotions {
	private static final int HIGHLIGHT = 0x80FF5555;
	private static final String MENU = "Toggle Potion Effects";

	private static int containerId = -1;
	private static int chestSlots = -1;
	private static final BitSet disabled = new BitSet();

	private DisabledPotions() {
	}

	public static void init() {
		ScreenEvents.BEFORE_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof AbstractContainerScreen<?>)) {
				return;
			}
			ScreenEvents.afterExtract(screen).register((opened, graphics, mouseX, mouseY, tickDelta) ->
				afterRender(opened, graphics)
			);
		});
	}

	public static void reset() {
		containerId = -1;
		chestSlots = -1;
		disabled.clear();
	}

	public static void onOpen(Screen screen) {
		if (!(screen instanceof AbstractContainerScreen<?> container)) {
			reset();
			return;
		}
		if (!isPotionMenu(screen.getTitle().getString())) {
			reset();
			return;
		}
		containerId = container.getMenu().containerId;
	}

	public static void onPacket(Packet<?> packet) {
		if (packet instanceof ClientboundOpenScreenPacket open) {
			if (isPotionMenu(open.getTitle().getString())) {
				containerId = open.getContainerId();
				chestSlots = -1;
				disabled.clear();
			} else if (containerId == open.getContainerId()) {
				reset();
			}
			return;
		}
		if (containerId < 0) {
			return;
		}
		if (packet instanceof ClientboundContainerSetSlotPacket setSlot) {
			if (setSlot.getContainerId() != containerId) {
				return;
			}
			applySlot(setSlot.getSlot(), setSlot.getItem());
			return;
		}
		if (packet instanceof ClientboundContainerSetContentPacket content) {
			if (content.containerId() != containerId) {
				return;
			}
			List<ItemStack> items = content.items();
			chestSlots = Math.max(0, items.size() - 36);
			disabled.clear();
			for (int slot = 0; slot < chestSlots; slot++) {
				if (loreDisabled(items.get(slot))) {
					disabled.set(slot);
				}
			}
		}
	}

	private static void applySlot(int slot, ItemStack stack) {
		if (slot < 0) {
			return;
		}
		if (chestSlots >= 0 && slot >= chestSlots) {
			return;
		}
		if (loreDisabled(stack)) {
			disabled.set(slot);
		} else {
			disabled.clear(slot);
		}
	}

	private static void afterRender(Screen screen, GuiGraphicsExtractor graphics) {
		if (!StrayConfig.get().disabledPotionsHighlight || disabled.isEmpty()) {
			return;
		}
		if (!(screen instanceof AbstractContainerScreen<?> container)) {
			return;
		}
		if (!isPotionMenu(screen.getTitle().getString())) {
			return;
		}
		int left = ((AbstractContainerScreenAccessor) container).stray$leftPos();
		int top = ((AbstractContainerScreenAccessor) container).stray$topPos();
		for (int slotId = disabled.nextSetBit(0); slotId >= 0; slotId = disabled.nextSetBit(slotId + 1)) {
			if (slotId >= container.getMenu().slots.size()) {
				continue;
			}
			Slot slot = container.getMenu().getSlot(slotId);
			int x = left + slot.x;
			int y = top + slot.y;
			graphics.fill(x, y, x + 16, y + 16, HIGHLIGHT);
		}
	}

	static boolean isPotionMenu(String title) {
		if (title == null) {
			return false;
		}
		String plain = ChatFormatting.stripFormatting(title).trim();
		return "(1/2) Toggle Potion Effects".equals(plain)
			|| "(2/2) Toggle Potion Effects".equals(plain)
			|| plain.endsWith(MENU);
	}

	private static boolean loreDisabled(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		boolean prior = ItemAppearance.suppress();
		try {
			ItemLore lore = stack.get(DataComponents.LORE);
			if (lore == null) {
				return false;
			}
			for (Component line : lore.lines()) {
				if (hasDisabled(line)) {
					return true;
				}
			}
			for (Component line : lore.styledLines()) {
				if (hasDisabled(line)) {
					return true;
				}
			}
			return false;
		} finally {
			ItemAppearance.resume(prior);
		}
	}

	private static boolean hasDisabled(Component line) {
		if (line == null) {
			return false;
		}
		String text = ChatFormatting.stripFormatting(line.getString());
		return text != null && text.toUpperCase(Locale.ROOT).contains("DISABLED");
	}
}
