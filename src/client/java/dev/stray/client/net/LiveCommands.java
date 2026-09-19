package dev.stray.client.net;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** {@code /st irc} and {@code /st ping} on the Stray live websocket. */
public final class LiveCommands {
	private LiveCommands() {
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> irc() {
		return ClientCommands.literal("irc")
			.then(ClientCommands.literal("users")
				.executes(context -> {
					StrayLive.requestUsers();
					return Command.SINGLE_SUCCESS;
				}))
			.then(ClientCommands.argument("message", StringArgumentType.greedyString())
				.executes(context -> {
					StrayLive.sendIrc(StringArgumentType.getString(context, "message"));
					return Command.SINGLE_SUCCESS;
				}));
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> ping() {
		return ClientCommands.literal("ping")
			.executes(context -> ping(""))
			.then(ClientCommands.argument("label", StringArgumentType.greedyString())
				.executes(context -> ping(StringArgumentType.getString(context, "label"))));
	}

	public static int ping(String label) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return 0;
		}
		String name = client.player.getGameProfile().name();
		if (LobbyPings.aimingAtOwn(name == null ? "You" : name)) {
			StrayLive.clearPing();
			return Command.SINGLE_SUCCESS;
		}
		BlockPos pos = LobbyPings.lookPos();
		if (pos == null) {
			StrayLive.tell("Look at a spot to ping it.", ChatFormatting.GRAY);
			return 0;
		}
		StrayLive.sendPing(pos.getX(), pos.getY(), pos.getZ(), label);
		return Command.SINGLE_SUCCESS;
	}
}
