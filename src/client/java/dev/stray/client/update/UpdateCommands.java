package dev.stray.client.update;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.stray.update.AutoUpdate;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/** {@code /stray update} downloads the newest jar and closes the game. */
public final class UpdateCommands {
	private static final AtomicBoolean RUNNING = new AtomicBoolean();

	private UpdateCommands() {
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
		return ClientCommands.literal("update").executes(context -> run());
	}

	private static int run() {
		if (!RUNNING.compareAndSet(false, true)) {
			tell("Already checking for an update.");
			return Command.SINGLE_SUCCESS;
		}
		tell("Checking for an update…");
		Thread thread = new Thread(UpdateCommands::download, "stray-manual-update");
		thread.setDaemon(true);
		thread.start();
		return Command.SINGLE_SUCCESS;
	}

	private static void download() {
		try {
			AutoUpdate.UpdateOutcome outcome = AutoUpdate.updateNow();
			Minecraft.getInstance().execute(() -> tell(outcome.message()));
			if (outcome.status() != AutoUpdate.UpdateStatus.UPDATED) {
				return;
			}
			try {
				Thread.sleep(700L);
			} catch (InterruptedException ignored) {
				Thread.currentThread().interrupt();
			}
			AutoUpdate.quit();
		} finally {
			RUNNING.set(false);
		}
	}

	private static void tell(String text) {
		Minecraft client = Minecraft.getInstance();
		Component line = Component.literal("Stray | ").withStyle(ChatFormatting.AQUA)
			.append(Component.literal(text == null ? "" : text).withStyle(ChatFormatting.GRAY));
		if (client.player != null) {
			client.player.sendSystemMessage(line);
			return;
		}
		client.gui.getChat().addClientSystemMessage(line);
	}
}
