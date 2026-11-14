package dev.stray.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Messages Stray shows in your own chat (never sent to the server). */
public final class Chat {
	private Chat() {
	}

	/** Shows a message in your own chat. */
	public static void tell(Component message) {
		Minecraft client = Minecraft.getInstance();
		if (client.gui != null) {
			client.gui.hud.getChat().addClientSystemMessage(message);
		}
	}
}
