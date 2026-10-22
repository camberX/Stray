package dev.stray.client.ui;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.stray.client.item.LoadoutsMenus;
import dev.stray.client.location.SkyblockLocation;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;

public final class LoadoutsCommands {
	private LoadoutsCommands() {
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
		return ClientCommands.literal("loadouts").executes(context -> open());
	}

	public static int open() {
		if (!send()) {
			return 0;
		}
		LoadoutsScreen.allowReopen();
		Minecraft client = Minecraft.getInstance();
		if (LoadoutsMenus.enabled()
			&& LoadoutsScreen.hasCache()
			&& !(client.screen instanceof LoadoutsScreen)) {
			client.setScreen(LoadoutsScreen.fromCache());
		}
		return Command.SINGLE_SUCCESS;
	}

	/** Asks Hypixel for the loadouts chest without opening the Stray menu. */
	public static void openHidden() {
		send();
	}

	private static boolean send() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.player.connection == null) {
			return false;
		}
		if (!SkyblockLocation.inSkyblock && !SkyblockLocation.onHypixel) {
			client.gui.getChat().addClientSystemMessage(
				Component.literal("Stray | Join Skyblock, then /loadouts opens the custom menu.")
			);
		}
		try {
			client.player.connection.send(new ServerboundChatCommandPacket("loadouts"));
		} catch (RuntimeException ignored) {
			try {
				client.player.connection.sendCommand("loadouts");
			} catch (RuntimeException ignoredToo) {
			}
		}
		return true;
	}
}
