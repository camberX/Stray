package dev.stray.client.update;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.stray.client.config.StrayConfig;
import dev.stray.update.AutoUpdate;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/** {@code /stray update} checks once now. Auto close exits only after a newer jar is installed. */
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
		tell("Checking for an update.");
		Thread thread = new Thread(UpdateCommands::checkNow, "stray-manual-update");
		thread.setDaemon(true);
		thread.start();
		return Command.SINGLE_SUCCESS;
	}

	private static void checkNow() {
		boolean closing = false;
		try {
			AutoUpdate.UpdateOutcome outcome = AutoUpdate.updateNow();
			if (outcome.status() == AutoUpdate.UpdateStatus.UPDATED) {
				if (StrayConfig.get().updateAutoClose) {
					tell(outcome.message());
					tell("Closing.");
					closing = true;
					try {
						Thread.sleep(700L);
					} catch (InterruptedException ignored) {
						Thread.currentThread().interrupt();
					}
					AutoUpdate.quit();
				} else if (outcome.alreadyOnDisk()) {
					tell("Newer jar " + outcome.version() + " is already in mods. Restart to load it.");
				} else {
					tell("Updated to " + outcome.version() + ". Restart Minecraft to load it.");
				}
			} else if (outcome.status() == AutoUpdate.UpdateStatus.CURRENT) {
				tell(outcome.message());
			} else {
				tell(outcome.message() == null || outcome.message().isBlank() ? "Could not check for an update." : outcome.message());
				AutoUpdate.requestNextLaunch();
			}
		} catch (RuntimeException exception) {
			tell("Could not check for an update.");
		} finally {
			if (!closing) {
				RUNNING.set(false);
			}
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
