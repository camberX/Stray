package dev.stray.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stray.Stray;
import dev.stray.client.location.SkyblockLocation;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Shared Skyblock-island key and JSON helpers for paths, block marks, and
 * command rings. Public islands use the tab {@code Area:} name so Hub marks
 * never load in the Hollows.
 */
public final class IslandSaves {
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("stray");

	private IslandSaves() {
	}

	public static String key() {
		if (SkyblockLocation.inSkyblock) {
			String area = SkyblockLocation.area == null ? "" : SkyblockLocation.area.trim();
			if (!area.isEmpty()) {
				return area.toLowerCase(Locale.ROOT);
			}
			return "skyblock";
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level != null) {
			return client.level.dimension().identifier().toString();
		}
		return "unknown";
	}

	public static String label() {
		String key = key();
		if (key.contains(":")) {
			key = key.substring(key.indexOf(':') + 1).replace('_', ' ');
		}
		if (key.isEmpty()) {
			return "Unknown";
		}
		return Character.toUpperCase(key.charAt(0)) + key.substring(1);
	}

	public static JsonObject readIslands(Path file) {
		if (!Files.isRegularFile(file)) {
			return new JsonObject();
		}
		try (Reader reader = Files.newBufferedReader(file)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			JsonObject islands = root.getAsJsonObject("islands");
			return islands == null ? new JsonObject() : islands;
		} catch (Exception exception) {
			Stray.LOGGER.warn("Could not read {}", file.getFileName(), exception);
			return new JsonObject();
		}
	}

	public static boolean writeIslands(Path file, JsonObject islands) {
		JsonObject root = new JsonObject();
		root.add("islands", islands);
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file)) {
				GSON.toJson(root, writer);
			}
			return true;
		} catch (IOException exception) {
			Stray.LOGGER.warn("Could not write {}", file.getFileName(), exception);
			return false;
		}
	}

	public static double coord(double value) {
		return Math.round(value * 100.0) / 100.0;
	}
}
