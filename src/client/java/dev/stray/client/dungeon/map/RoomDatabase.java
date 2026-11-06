package dev.stray.client.dungeon.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Known dungeon rooms by core hash and by name (NoammAddons' "rooms-modern.json").
 * The file is not shipped with NoammAddons' source (it downloads it at start), so this
 * looks for it in order: config/stray/rooms-modern.json, NoammAddons' own data folder
 * (when that mod is installed too), then one bundled in the jar. Without it the map still
 * works from the map item, just without room names.
 */
public final class RoomDatabase {
	private static final String FILE = "rooms-modern.json";
	private static Map<Integer, RoomData> byCore;
	private static Map<String, RoomData> byName;
	private static String source = "none";

	private RoomDatabase() {
	}

	public static RoomData byCore(int hash) {
		ensure();
		return byCore.get(hash);
	}

	public static RoomData byName(String name) {
		ensure();
		return byName.get(name);
	}

	public static int size() {
		ensure();
		return byName.size();
	}

	/** Where the room data came from, for the debug command. */
	public static String source() {
		ensure();
		return source;
	}

	private static synchronized void ensure() {
		if (byCore != null) {
			return;
		}
		byCore = new HashMap<>();
		byName = new HashMap<>();
		Path config = FabricLoader.getInstance().getConfigDir();
		Path[] candidates = {
			config.resolve("stray").resolve(FILE),
			config.resolve("NoammAddons").resolve("data").resolve(FILE),
			config.resolve("noammaddons").resolve("data").resolve(FILE),
			config.resolve("Noammaddons").resolve("data").resolve(FILE)
		};
		for (Path path : candidates) {
			if (Files.isRegularFile(path)) {
				try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
					if (load(reader)) {
						source = path.toString();
						return;
					}
				} catch (Exception ignored) {
				}
			}
		}
		try (InputStream in = RoomDatabase.class.getResourceAsStream("/assets/stray/dungeon/" + FILE)) {
			if (in != null && load(new InputStreamReader(in, StandardCharsets.UTF_8))) {
				source = "bundled";
			}
		} catch (Exception ignored) {
		}
	}

	private static boolean load(Reader reader) {
		JsonElement root = JsonParser.parseReader(reader);
		if (!root.isJsonArray()) {
			return false;
		}
		Map<Integer, RoomData> cores = new HashMap<>();
		Map<String, RoomData> names = new HashMap<>();
		for (JsonElement element : root.getAsJsonArray()) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject obj = element.getAsJsonObject();
			String name = string(obj, "name", "Unknown");
			List<Integer> coreList = new ArrayList<>();
			if (obj.has("cores") && obj.get("cores").isJsonArray()) {
				JsonArray array = obj.getAsJsonArray("cores");
				for (JsonElement core : array) {
					coreList.add(core.getAsInt());
				}
			}
			RoomData data = new RoomData(
				name,
				RoomType.parse(string(obj, "type", "NORMAL")),
				RoomShape.parse(string(obj, "shape", "Unknown")),
				List.copyOf(coreList),
				integer(obj, "secrets"),
				integer(obj, "crypts"),
				integer(obj, "trappedChests")
			);
			for (int core : coreList) {
				cores.put(core, data);
			}
			names.put(name, data);
		}
		if (names.isEmpty()) {
			return false;
		}
		byCore = cores;
		byName = names;
		return true;
	}

	private static String string(JsonObject obj, String key, String fallback) {
		JsonElement value = obj.get(key);
		return value == null || value.isJsonNull() ? fallback : value.getAsString();
	}

	private static int integer(JsonObject obj, String key) {
		JsonElement value = obj.get(key);
		return value == null || value.isJsonNull() ? 0 : value.getAsInt();
	}
}
