package dev.stray.client.fairy;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.stray.client.location.SkyblockLocation;
import dev.stray.client.ui.Theme;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public final class FairySoulCommands {
	private FairySoulCommands() {
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
		return ClientCommands.literal("fairysouls")
			.executes(context -> status())
			.then(ClientCommands.literal("reset").executes(context -> reset()))
			.then(ClientCommands.literal("clear").executes(context -> reset()));
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> shortCommand() {
		return ClientCommands.literal("fairy")
			.executes(context -> status())
			.then(ClientCommands.literal("reset").executes(context -> reset()))
			.then(ClientCommands.literal("clear").executes(context -> reset()));
	}

	private static int status() {
		String island = FairySouls.currentIsland();
		int total = FairySouls.current().size();
		int left = FairySoulTracker.visible().size();
		int foundHere = Math.max(0, total - left);
		int found = FairySoulProgress.count();
		String where = island.isEmpty() ? "unknown island" : island;
		tell(brand()
			.append(sep())
			.append(Component.literal("FAIRY SOULS").withStyle(style(Theme.ACCENT).withBold(true)))
			.append(Component.literal(" " + where + " " + foundHere + "/" + total).withStyle(style(Theme.TEXT)))
			.append(Component.literal(left > 0 ? "  " + left + " left here" : "  none left here").withStyle(style(Theme.MUTED))));
		tell(Component.literal("area " + empty(SkyblockLocation.area) + "  poi " + empty(SkyblockLocation.poi)
			+ "  " + found + " saved").withStyle(style(Theme.MUTED)));
		tell(Component.literal("/stray fairysouls reset shows them all again.").withStyle(style(Theme.MUTED)));
		return Command.SINGLE_SUCCESS;
	}

	private static String empty(String value) {
		return value == null || value.isBlank() ? "-" : value;
	}

	private static int reset() {
		int n = FairySoulProgress.reset();
		tell(brand()
			.append(sep())
			.append(Component.literal("FAIRY SOULS").withStyle(style(Theme.ACCENT).withBold(true)))
			.append(Component.literal(" reset").withStyle(style(Theme.TEXT)))
			.append(Component.literal(" (" + n + " cleared)").withStyle(style(Theme.MUTED))));
		return Command.SINGLE_SUCCESS;
	}

	private static MutableComponent brand() {
		return Component.literal("STRAY").withStyle(style(Theme.ACCENT).withBold(true));
	}

	private static MutableComponent sep() {
		return Component.literal(" | ").withStyle(style(Theme.MUTED));
	}

	private static Style style(int color) {
		return Style.EMPTY.withColor(color & 0xFFFFFF);
	}

	private static void tell(Component message) {
		Minecraft client = Minecraft.getInstance();
		if (client.gui != null) {
			client.gui.getChat().addClientSystemMessage(message);
		}
	}
}
