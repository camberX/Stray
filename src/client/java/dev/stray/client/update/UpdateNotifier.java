package dev.stray.client.update;

import dev.stray.Stray;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.ui.Theme;
import dev.stray.update.UpdateMeta;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.net.URI;

public final class UpdateNotifier {
	private static final int MAX_TRIES = 3;
	private static volatile String pending;
	private static volatile boolean checking;
	private static volatile boolean done;
	private static int attempts;
	private static long nextAt;

	private UpdateNotifier() {
	}

	public static void tick() {
		if (!StrayConfig.get().updateNotify) {
			return;
		}
		if (done && pending == null) {
			return;
		}
		String remote = pending;
		if (remote != null) {
			if (tell(remote)) {
				pending = null;
				done = true;
			}
			return;
		}
		if (done || checking || attempts >= MAX_TRIES) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now < nextAt) {
			return;
		}
		checking = true;
		attempts++;
		Thread thread = new Thread(UpdateNotifier::check, "stray-update-notify");
		thread.setDaemon(true);
		thread.start();
	}

	private static void check() {
		try {
			String remote = UpdateMeta.latestVersion();
			if (remote == null) {
				nextAt = System.currentTimeMillis() + 30_000L;
				return;
			}
			String installed = FabricLoader.getInstance()
				.getModContainer(Stray.MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("");
			if (UpdateMeta.compare(remote, installed) > 0) {
				pending = remote;
			} else {
				done = true;
			}
		} catch (Exception ignored) {
			nextAt = System.currentTimeMillis() + 30_000L;
		} finally {
			checking = false;
		}
	}

	private static boolean tell(String remote) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.gui == null) {
			return false;
		}
		String installed = FabricLoader.getInstance()
			.getModContainer(Stray.MOD_ID)
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("this version");
		MutableComponent line = Component.literal("STRAY").withStyle(style(Theme.ACCENT).withBold(true))
			.append(Component.literal(" | ").withStyle(style(Theme.MUTED)))
			.append(Component.literal("UPDATE").withStyle(style(Theme.ACCENT).withBold(true)))
			.append(Component.literal(" " + remote + " is out (you have " + installed + "). ").withStyle(style(Theme.TEXT)))
			.append(Component.literal("Open stray.gay").withStyle(
				style(Theme.ACCENT)
					.withUnderlined(true)
					.withClickEvent(new ClickEvent.OpenUrl(URI.create(UpdateMeta.SHOP)))
					.withHoverEvent(new HoverEvent.ShowText(Component.literal(UpdateMeta.SHOP)))
			));
		client.gui.getChat().addClientSystemMessage(line);
		return true;
	}

	private static Style style(int color) {
		return Style.EMPTY.withColor(color & 0xFFFFFF);
	}
}
