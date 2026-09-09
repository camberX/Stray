package dev.stray.client.ui;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.stray.client.config.StrayConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class ProfileCommands {
	private ProfileCommands() {
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
		return ClientCommands.literal("pv")
			.executes(context -> open(""))
			.then(ClientCommands.argument("player", StringArgumentType.word())
				.executes(context -> open(StringArgumentType.getString(context, "player"))));
	}

	public static int open(String name) {
		Minecraft client = Minecraft.getInstance();
		if (!StrayConfig.get().profileViewerEnabled) {
			client.gui.getChat().addClientSystemMessage(
				Component.literal("Stray | Turn on Profile viewer in Menus.")
			);
			return 0;
		}
		client.execute(() -> {
			if (client.screen instanceof ProfileViewerScreen screen) {
				screen.lookup(name);
				return;
			}
			client.setScreen(new ProfileViewerScreen(name));
		});
		return Command.SINGLE_SUCCESS;
	}
}
