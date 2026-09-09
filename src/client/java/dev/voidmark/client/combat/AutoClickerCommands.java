package dev.voidmark.client.combat;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.voidmark.client.config.VoidmarkConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * OdinClient /autoclicker add|remove|clear|list, using the same held() key.
 */
public final class AutoClickerCommands {
	private AutoClickerCommands() {
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
		return ClientCommands.literal("autoclicker")
			.then(ClientCommands.literal("add")
				.then(ClientCommands.literal("left").executes(context -> add(true)))
				.then(ClientCommands.literal("right").executes(context -> add(false))))
			.then(ClientCommands.literal("remove")
				.then(ClientCommands.literal("left").executes(context -> remove(true)))
				.then(ClientCommands.literal("right").executes(context -> remove(false))))
			.then(ClientCommands.literal("clear")
				.then(ClientCommands.literal("left").executes(context -> clear(true, false)))
				.then(ClientCommands.literal("right").executes(context -> clear(false, true)))
				.then(ClientCommands.literal("all").executes(context -> clear(true, true))))
			.then(ClientCommands.literal("list").executes(context -> list()));
	}

	private static int add(boolean left) {
		String item = AutoClicker.held();
		if (item == null) {
			tell("Hold an item to whitelist.");
			return 0;
		}
		List<String> whitelist = whitelist(left);
		if (whitelist.contains(item)) {
			tell("\"" + item + "\" is already in the " + side(left) + " whitelist.");
			return Command.SINGLE_SUCCESS;
		}
		whitelist.add(item);
		VoidmarkConfig.get().save();
		tell("Added \"" + item + "\" to the " + side(left) + " whitelist.");
		return Command.SINGLE_SUCCESS;
	}

	private static int remove(boolean left) {
		String item = AutoClicker.held();
		if (item == null) {
			tell("Hold an item to remove from whitelist.");
			return 0;
		}
		List<String> whitelist = whitelist(left);
		if (!whitelist.contains(item)) {
			tell("\"" + item + "\" isn't in the " + side(left) + " whitelist.");
			return Command.SINGLE_SUCCESS;
		}
		whitelist.remove(item);
		VoidmarkConfig.get().save();
		tell("Removed \"" + item + "\" from the " + side(left) + " whitelist.");
		return Command.SINGLE_SUCCESS;
	}

	private static int clear(boolean left, boolean right) {
		if (left) {
			VoidmarkConfig.get().autoClickerLeftWhitelist.clear();
		}
		if (right) {
			VoidmarkConfig.get().autoClickerRightWhitelist.clear();
		}
		VoidmarkConfig.get().save();
		if (left && right) {
			tell("All whitelists cleared.");
		} else if (left) {
			tell("Left whitelist cleared.");
		} else {
			tell("Right whitelist cleared.");
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int list() {
		String left = String.join(", ", VoidmarkConfig.get().autoClickerLeftWhitelist);
		String right = String.join(", ", VoidmarkConfig.get().autoClickerRightWhitelist);
		if (left.isEmpty()) {
			left = "empty";
		}
		if (right.isEmpty()) {
			right = "empty";
		}
		tell("Autoclicker whitelist:");
		tell("Left: " + left);
		tell("--------------------");
		tell("Right: " + right);
		return Command.SINGLE_SUCCESS;
	}

	private static List<String> whitelist(boolean left) {
		return left
			? VoidmarkConfig.get().autoClickerLeftWhitelist
			: VoidmarkConfig.get().autoClickerRightWhitelist;
	}

	private static String side(boolean left) {
		return left ? "left" : "right";
	}

	private static void tell(String text) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			client.player.sendSystemMessage(Component.literal(text));
		}
	}
}
