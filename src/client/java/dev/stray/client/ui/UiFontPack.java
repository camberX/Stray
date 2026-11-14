package dev.stray.client.ui;

import dev.stray.Stray;
import dev.stray.client.config.StrayConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;

/**
 * Folder resource pack that feeds the selected system TTF to Minecraft's font loader.
 */
public final class UiFontPack {
	public static final String FOLDER = "stray-ui";
	public static final String PACK_ID = "file/stray-ui";

	private static volatile boolean busy;
	private static volatile boolean reloading;
	private static volatile boolean bootstrapped;

	private UiFontPack() {
	}

	public static boolean loaded() {
		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			return false;
		}
		return client.getResourceManager().getResource(Stray.id("font/ui.json")).isPresent();
	}

	public static void tick(Minecraft client) {
		if (client == null || busy || reloading) {
			return;
		}
		if (!bootstrapped) {
			bootstrapped = true;
			disable(client);
		}
	}

	public static void apply(String family) {
		Minecraft client = Minecraft.getInstance();
		if (client == null || busy || reloading) {
			return;
		}
		StrayConfig.get().uiFont = MenuFont.MINECRAFT_FAMILY;
		disable(client);
	}

	private static void disable(Minecraft client) {
		PackRepository repo = client.getResourcePackRepository();
		if (!repo.getSelectedIds().contains(PACK_ID)) {
			return;
		}
		repo.removePack(PACK_ID);
		client.options.updateResourcePacks(repo);
		client.options.save();
		reload(client);
	}

	private static void reload(Minecraft client) {
		reloading = true;
		client.reloadResourcePacks().whenComplete((unused, error) -> reloading = false);
	}

}
