package dev.stray.client.render;

import dev.stray.client.ui.MenuFont;
import dev.stray.client.ui.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class HeldItemHudRenderer {
	public static final float HEIGHT = 16;
	private static final float MIN_W = 80;
	private static final Component PLACEHOLDER = MenuFont.applyBody(Component.literal("Held item"));

	private static ItemStack labelStack;
	private static int labelTick = Integer.MIN_VALUE;
	private static Component labelName = PLACEHOLDER;
	private static float labelWidth = MIN_W;

	private HeldItemHudRenderer() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		LocalPlayer player = Minecraft.getInstance().player;
		ItemStack stack = player == null ? ItemStack.EMPTY : player.getInventory().getSelectedItem();
		boolean empty = stack == null || stack.isEmpty();
		if (empty && !HudLayout.editorOpen()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		label(font, stack, player);
		Component name = labelName;
		float w = labelWidth;
		HudLayout.apply(graphics, font, HudLayout.Id.HELD_ITEM, () -> {
			HudChrome.panel(graphics, 0, 0, w, HEIGHT, 5, Theme.WINDOW, Theme.LINE);
			GuiDraw.hud(graphics, font, name, 8, 3, 0xFFFFFFFF);
		});
	}

	public static float drawWidth(Font font) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return MIN_W;
		}
		label(font, player.getInventory().getSelectedItem(), player);
		return labelWidth;
	}

	/** Hover names rebuild a styled Component each call, so keep one per stack per tick. */
	private static void label(Font font, ItemStack stack, LocalPlayer player) {
		int tick = player == null ? -1 : player.tickCount;
		if (stack == labelStack && tick == labelTick) {
			return;
		}
		labelStack = stack;
		labelTick = tick;
		if (stack == null || stack.isEmpty()) {
			labelName = PLACEHOLDER;
			labelWidth = MIN_W;
			return;
		}
		labelName = MenuFont.applyBody(stack.getStyledHoverName());
		labelWidth = Math.max(MIN_W, GuiDraw.hudWidth(font, labelName) + 16);
	}
}
