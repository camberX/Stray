package dev.stray.client.fairy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/** Found fairy souls. Written whenever a new one is marked. */
public final class FairySoulProgress {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("stray-fairy-souls.json");
	private static final Set<String> FOUND = new LinkedHashSet<>();
	private static boolean loaded;

	private FairySoulProgress() {
	}

	public static boolean found(FairySouls.Soul soul) {
		ensureLoaded();
		return soul != null && FOUND.contains(soul.key());
	}

	public static boolean mark(FairySouls.Soul soul) {
		if (soul == null) {
			return false;
		}
		ensureLoaded();
		if (!FOUND.add(soul.key())) {
			return false;
		}
		save();
		return true;
	}

	public static int reset() {
		ensureLoaded();
		int n = FOUND.size();
		FOUND.clear();
		save();
		return n;
	}

	public static int count() {
		ensureLoaded();
		return FOUND.size();
	}

	private static void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;
		if (!Files.isRegularFile(PATH)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(PATH)) {
			JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
			JsonArray found = json.getAsJsonArray("found");
			if (found == null) {
				return;
			}
			for (JsonElement element : found) {
				if (element.isJsonPrimitive()) {
					String key = element.getAsString().trim();
					if (!key.isEmpty()) {
						FOUND.add(key);
					}
				}
			}
		} catch (Exception ignored) {
		}
	}

	private static void save() {
		JsonObject json = new JsonObject();
		JsonArray found = new JsonArray();
		for (String key : FOUND) {
			found.add(key);
		}
		json.add("found", found);
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(json, writer);
			}
		} catch (Exception ignored) {
		}
	}
}
