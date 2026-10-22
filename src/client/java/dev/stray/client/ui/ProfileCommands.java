package dev.stray.client.ui;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.stray.client.config.StrayConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public final class ProfileCommands {
	private ProfileCommands() {
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
		return ClientCommands.literal("pv")
			.executes(context -> open(""))
			.then(ClientCommands.argument("player", StringArgumentType.greedyString())
				.executes(context -> open(StringArgumentType.getString(context, "player"))));
	}

	public static boolean handleTyped(String raw) {
		if (!StrayConfig.get().profileViewerEnabled) {
			return false;
		}
		String message = raw == null ? "" : raw.trim();
		if (message.startsWith("/")) {
			message = message.substring(1).trim();
		}
		if (message.isEmpty()) {
			return false;
		}
		int split = -1;
		for (int i = 0; i < message.length(); i++) {
			if (Character.isWhitespace(message.charAt(i))) {
				split = i;
				break;
			}
		}
		String cmd = (split < 0 ? message : message.substring(0, split)).toLowerCase(Locale.ROOT);
		if (!"pv".equals(cmd) && !"profile".equals(cmd)) {
			return false;
		}
		String name = split < 0 ? "" : message.substring(split).trim();
		if (!name.isEmpty()) {
			int end = 0;
			while (end < name.length() && !Character.isWhitespace(name.charAt(end))) {
				end++;
			}
			name = name.substring(0, end);
		}
		open(name);
		return true;
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
