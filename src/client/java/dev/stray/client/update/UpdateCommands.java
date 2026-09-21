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

/** {@code /stray update} schedules an update check for the next launch. */
public final class UpdateCommands {
	private static final AtomicBoolean RUNNING = new AtomicBoolean();

	private UpdateCommands() {
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
		return ClientCommands.literal("update").executes(context -> run());
	}

	private static int run() {
		if (!RUNNING.compareAndSet(false, true)) {
			tell("Already scheduling an update.");
			return Command.SINGLE_SUCCESS;
		}
		boolean closing = false;
		try {
			if (!AutoUpdate.requestNextLaunch()) {
				tell("Could not schedule the update.");
				return 0;
			}
			if (StrayConfig.get().updateAutoClose) {
				tell("Stray will check for an update the next time Minecraft starts. Closing.");
				closing = true;
				Thread thread = new Thread(UpdateCommands::closeSoon, "stray-manual-update");
				thread.setDaemon(true);
				thread.start();
				return Command.SINGLE_SUCCESS;
			}
			tell("Stray will check for an update the next time Minecraft starts. The current jar stays until then.");
			return Command.SINGLE_SUCCESS;
		} catch (RuntimeException exception) {
			tell("Could not schedule the update.");
			return 0;
		} finally {
			if (!closing) {
				RUNNING.set(false);
			}
		}
	}

	private static void closeSoon() {
		try {
			Thread.sleep(700L);
		} catch (InterruptedException ignored) {
			Thread.currentThread().interrupt();
		} finally {
			RUNNING.set(false);
		}
		AutoUpdate.quit();
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
