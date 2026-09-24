package dev.stray.client.update;

import dev.stray.Stray;
import dev.stray.client.config.StrayConfig;
import dev.stray.update.UpdateMeta;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

public final class UpdateNotifier {
	private UpdateNotifier() {
	}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			ScreenEvents.afterExtract(screen).register((opened, graphics, mouseX, mouseY, tickProgress) ->
				UpdateToast.extract(graphics)
			);
			ScreenMouseEvents.allowMouseClick(screen).register((opened, event) -> {
				if (event.button() != 0) {
					return true;
				}
				return !UpdateToast.mouseClicked(event);
			});
		});
	}

	public static void tick() {
		UpdateToast.tickMouse(Minecraft.getInstance());
	}

	/** One card after the startup check. Never polls while the game is open. */
	public static void announce(String remote) {
		if (remote == null || remote.isBlank() || !StrayConfig.get().updateNotify) {
			return;
		}
		String installed = installedVersion();
		StrayConfig config = StrayConfig.get();
		String seen = config.updateNotifiedVersion == null ? "" : config.updateNotifiedVersion;
		if (!seen.isEmpty() && UpdateMeta.compare(remote, seen) <= 0) {
			return;
		}
		config.updateNotifiedVersion = remote;
		config.save();
		Minecraft.getInstance().execute(() -> UpdateToast.show(remote, installed));
		Stray.LOGGER.info("Update {} is out (installed {}).", remote, installed);
	}

	private static String installedVersion() {
		return FabricLoader.getInstance()
			.getModContainer(Stray.MOD_ID)
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("");
	}
}
