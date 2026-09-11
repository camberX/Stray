package dev.stray.client.update;

import dev.stray.Stray;
import dev.stray.client.config.StrayConfig;
import dev.stray.update.UpdateMeta;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

public final class UpdateNotifier {
	private static final long RETRY_MS = 30_000L;
	private static final long POLL_MS = 180_000L;
	private static volatile boolean checking;
	private static long nextAt;

	private UpdateNotifier() {
	}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			ScreenEvents.afterExtract(screen).register((opened, graphics, mouseX, mouseY, tickProgress) -> {
				if (client.level != null) {
					return;
				}
				UpdateToast.extract(graphics);
			});
			ScreenMouseEvents.allowMouseClick(screen).register((opened, event) -> {
				if (event.button() != 0) {
					return true;
				}
				return !UpdateToast.mouseClicked(event);
			});
		});
	}

	public static void tick() {
		Minecraft client = Minecraft.getInstance();
		UpdateToast.tickMouse(client);
		if (!StrayConfig.get().updateNotify) {
			return;
		}
		if (checking) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now < nextAt) {
			return;
		}
		checking = true;
		Thread thread = new Thread(UpdateNotifier::check, "stray-update-notify");
		thread.setDaemon(true);
		thread.start();
	}

	private static void check() {
		try {
			String remote = UpdateMeta.latestVersion();
			if (remote == null) {
				nextAt = System.currentTimeMillis() + RETRY_MS;
				return;
			}
			nextAt = System.currentTimeMillis() + POLL_MS;
			String installed = installedVersion();
			StrayConfig config = StrayConfig.get();
			String seen = config.updateNotifiedVersion == null ? "" : config.updateNotifiedVersion;
			if (UpdateMeta.compare(remote, installed) <= 0) {
				return;
			}
			if (!seen.isEmpty() && UpdateMeta.compare(remote, seen) <= 0) {
				return;
			}
			config.updateNotifiedVersion = remote;
			config.save();
			Minecraft.getInstance().execute(() -> UpdateToast.show(remote, installed));
			Stray.LOGGER.info("Update {} is out (installed {}).", remote, installed);
		} catch (Exception ignored) {
			nextAt = System.currentTimeMillis() + RETRY_MS;
		} finally {
			checking = false;
		}
	}

	private static String installedVersion() {
		return FabricLoader.getInstance()
			.getModContainer(Stray.MOD_ID)
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("");
	}
}
