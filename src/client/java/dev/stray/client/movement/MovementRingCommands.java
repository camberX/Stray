package dev.stray.client.movement;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.stray.client.config.IslandSaves;
import dev.stray.client.config.StrayConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** {@code /stray move 2} — place, list, or clear movement recorder rings. */
public final class MovementRingCommands {
	private MovementRingCommands() {
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
		return ClientCommands.literal("move")
			.executes(context -> {
				tell(MovementRings.place(2f), ChatFormatting.GREEN);
				return Command.SINGLE_SUCCESS;
			})
			.then(ClientCommands.literal("clear").executes(context -> {
				int n = MovementRings.count();
				MovementRings.clear();
				tell(n == 0 ? "No rings to clear." : "Cleared " + n + " ring" + (n == 1 ? "." : "s."), ChatFormatting.YELLOW);
				return Command.SINGLE_SUCCESS;
			}))
			.then(ClientCommands.literal("list").executes(context -> list()))
			.then(ClientCommands.literal("remove")
				.then(ClientCommands.argument("index", IntegerArgumentType.integer(1))
					.executes(context -> {
						int index = IntegerArgumentType.getInteger(context, "index");
						tell(MovementRings.remove(index) ? "Removed ring " + index + "." : "No ring " + index + ".", ChatFormatting.YELLOW);
						return Command.SINGLE_SUCCESS;
					})))
			.then(ClientCommands.argument("size", FloatArgumentType.floatArg(0.5f, 16f))
				.executes(context -> {
					tell(MovementRings.place(FloatArgumentType.getFloat(context, "size")), ChatFormatting.GREEN);
					return Command.SINGLE_SUCCESS;
				}));
	}

	private static int list() {
		if (!StrayConfig.get().movementRingsEnabled) {
			tell("Turn on Movement rings in Misc → Tools first, or place one anyway to enable it.", ChatFormatting.YELLOW);
		}
		int n = MovementRings.count();
		tell("Movement rings on " + IslandSaves.label() + " (" + n + ")", ChatFormatting.AQUA);
		int i = 1;
		for (MovementRings.Ring ring : MovementRings.rings()) {
			String rec = ring.ticks() == 0 ? "empty" : String.format(java.util.Locale.ROOT, "%.1fs", ring.ticks() / 20f);
			tell("  " + i + ". " + rec + "  " + String.format(java.util.Locale.ROOT, "%.1fm", ring.radius), ChatFormatting.YELLOW);
			i++;
		}
		if (n == 0) {
			tell("  None. /stray move 2", ChatFormatting.GRAY);
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
