package dev.stray.client.movement;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.stray.client.config.IslandSaves;
import dev.stray.client.config.StrayConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** {@code /stray cmd "warp hub" 2} — place, list, or clear command rings. */
public final class CommandRingCommands {
	private CommandRingCommands() {
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
		return ClientCommands.literal("cmd")
			.executes(context -> usage())
			.then(ClientCommands.literal("clear").executes(context -> {
				int n = CommandRings.count();
				CommandRings.clear();
				tell(n == 0 ? "No rings to clear." : "Cleared " + n + " ring" + (n == 1 ? "." : "s."), ChatFormatting.YELLOW);
				return Command.SINGLE_SUCCESS;
			}))
			.then(ClientCommands.literal("list").executes(context -> list()))
			.then(ClientCommands.literal("remove")
				.then(ClientCommands.argument("index", IntegerArgumentType.integer(1))
					.executes(context -> {
						int index = IntegerArgumentType.getInteger(context, "index");
						tell(CommandRings.remove(index) ? "Removed ring " + index + "." : "No ring " + index + ".", ChatFormatting.YELLOW);
						return Command.SINGLE_SUCCESS;
					})))
			.then(ClientCommands.argument("command", StringArgumentType.string())
				.then(ClientCommands.argument("size", FloatArgumentType.floatArg(0.5f, 16f))
					.executes(context -> {
						String command = StringArgumentType.getString(context, "command");
						float size = FloatArgumentType.getFloat(context, "size");
						tell(CommandRings.place(command, size), ChatFormatting.GREEN);
						return Command.SINGLE_SUCCESS;
					})));
	}

	private static int usage() {
		if (!StrayConfig.get().commandRingsEnabled) {
			tell("Turn on Command rings in Misc → Tools first, or place one anyway to enable it.", ChatFormatting.YELLOW);
		}
		tell("/stray cmd \"warp hub\" 2   place a ring at your feet (saved on this island)", ChatFormatting.AQUA);
		tell("/stray cmd list | remove <n> | clear", ChatFormatting.GRAY);
		return Command.SINGLE_SUCCESS;
	}

	private static int list() {
		int n = CommandRings.count();
		tell("Command rings on " + IslandSaves.label() + " (" + n + ")", ChatFormatting.AQUA);
		int i = 1;
		for (CommandRings.Ring ring : CommandRings.rings()) {
			tell("  " + i + ". /" + ring.command + "  " + String.format(java.util.Locale.ROOT, "%.1fm", ring.radius), ChatFormatting.YELLOW);
			i++;
		}
		if (n == 0) {
			tell("  None. /stray cmd \"warp hub\" 2", ChatFormatting.GRAY);
		}
		return Command.SINGLE_SUCCESS;
	}

	private static void tell(String text, ChatFormatting color) {
		Minecraft client = Minecraft.getInstance();
		if (client.gui != null) {
			client.gui.getChat().addClientSystemMessage(
				Component.literal("Stray | ").withStyle(ChatFormatting.AQUA)
					.append(Component.literal(text).withStyle(color))
			);
		}
	}
}
