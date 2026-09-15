package dev.stray.client.ui;

import dev.stray.client.combat.OdinClicks;
import dev.stray.client.config.StrayConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.util.Mth;

/**
 * Patcher 1.8.9 chat peek: hold a keybind to show the focused chat history
 * without opening the chat box.
 */
public final class ChatPeek {
	private static boolean wasHolding;

	private ChatPeek() {
	}

	public static boolean holding() {
		boolean hold = active();
		if (wasHolding && !hold) {
			Minecraft client = Minecraft.getInstance();
			if (client != null && client.gui != null) {
				client.gui.getChat().resetChatScroll();
			}
		}
		wasHolding = hold;
		return hold;
	}

	public static boolean mouseScrolled(double yOffset) {
		if (!holding()) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.gui == null) {
			return true;
		}
		double amount = Mth.clamp(yOffset, -1.0, 1.0);
		if (!client.hasShiftDown()) {
			amount *= 7.0;
		}
		int ticks = (int) amount;
		if (ticks != 0) {
			client.gui.getChat().scrollChat(ticks);
		}
		return true;
	}

	private static boolean active() {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.player == null) {
			return false;
		}
		if (client.screen instanceof ChatScreen) {
			return false;
		}
		if (client.screen != null || client.options.hideGui) {
			return false;
		}
		return OdinClicks.isPressed(OdinClicks.parseKey(StrayConfig.get().chatPeekKey));
	}
}
