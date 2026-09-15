package dev.stray.client.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reads Prism / PolyMC / MultiMC {@code accounts.json} (formatVersion 3).
 */
final class PrismAccounts {
	static final String DEFAULT_CLIENT_ID = "c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb";

	private PrismAccounts() {
	}

	static Path file(Minecraft client) {
		List<Path> seen = new ArrayList<>();
		if (client != null && client.gameDirectory != null) {
			Path dir = client.gameDirectory.toPath().toAbsolutePath().normalize();
			for (int i = 0; i < 8 && dir != null; i++) {
				add(seen, dir.resolve("accounts.json"));
				dir = dir.getParent();
			}
		}
		String home = System.getProperty("user.home");
		String appdata = System.getenv("APPDATA");
		String local = System.getenv("LOCALAPPDATA");
		String xdg = System.getenv("XDG_DATA_HOME");
		for (String launcher : List.of("PrismLauncher", "prismlauncher", "PolyMC", "polymc", "MultiMC", "multimc")) {
			if (home != null) {
				add(seen, Path.of(home, ".local", "share", launcher, "accounts.json"));
				add(seen, Path.of(home, "Library", "Application Support", launcher, "accounts.json"));
				add(seen, Path.of(home, launcher, "accounts.json"));
			}
			if (xdg != null && !xdg.isBlank()) {
				add(seen, Path.of(xdg, launcher, "accounts.json"));
			}
			if (appdata != null && !appdata.isBlank()) {
				add(seen, Path.of(appdata, launcher, "accounts.json"));
			}
			if (local != null && !local.isBlank()) {
				add(seen, Path.of(local, launcher, "accounts.json"));
			}
		}
		for (Path path : seen) {
			if (Files.isRegularFile(path)) {
				return path;
			}
		}
		return null;
	}

	static List<Candidate> load(Path file) throws Exception {
		try (Reader reader = Files.newBufferedReader(file)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			JsonArray list = root.getAsJsonArray("accounts");
			if (list == null) {
				return List.of();
			}
			Map<UUID, Candidate> unique = new LinkedHashMap<>();
			for (JsonElement element : list) {
				if (!element.isJsonObject()) {
					continue;
				}
				Candidate candidate = parse(element.getAsJsonObject());
				if (candidate != null) {
					unique.put(candidate.uuid, candidate);
				}
			}
			return List.copyOf(unique.values());
		}
	}

	private static Candidate parse(JsonObject json) {
		String type = text(json, "type");
		if (type != null && !"MSA".equalsIgnoreCase(type)) {
			return null;
		}
		JsonObject profile = json.has("profile") && json.get("profile").isJsonObject()
			? json.getAsJsonObject("profile")
			: null;
		String name = profile == null ? "" : text(profile, "name");
		UUID uuid = profile == null ? null : uuid(text(profile, "id"));
		JsonObject msa = object(json, "msa");
		String refresh = token(msa, "refresh_token");
		if (refresh.isBlank() && msa != null) {
			refresh = token(object(msa, "extra"), "refresh_token");
		}
		String access = token(object(json, "ygg"), "token");
		if ("0".equals(access) || "offline".equalsIgnoreCase(access)) {
			access = "";
		}
		if (refresh.isBlank() && access.isBlank()) {
			return null;
		}
		if (uuid == null) {
			uuid = UUID.nameUUIDFromBytes(("prism:" + name + ":" + refresh + access).getBytes());
		}
		if (name == null || name.isBlank()) {
			name = "Microsoft";
		}
		String clientId = text(json, "msa-client-id");
		if (clientId == null || clientId.isBlank()) {
			clientId = DEFAULT_CLIENT_ID;
		}
		return new Candidate(name, uuid, refresh, access, clientId, json.has("active") && json.get("active").getAsBoolean());
	}

	private static void add(List<Path> seen, Path path) {
		Path normalized = path.toAbsolutePath().normalize();
		if (!seen.contains(normalized)) {
			seen.add(normalized);
		}
	}

	private static JsonObject object(JsonObject parent, String key) {
		if (parent == null || !parent.has(key) || !parent.get(key).isJsonObject()) {
			return null;
		}
		return parent.getAsJsonObject(key);
	}

	private static String token(JsonObject object, String key) {
		String value = text(object, key);
		return value == null ? "" : value;
	}

	private static String text(JsonObject object, String key) {
		if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
			return null;
		}
		try {
			return object.get(key).getAsString();
		} catch (Exception ignored) {
			return null;
		}
	}

	private static UUID uuid(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String value = raw.trim();
		try {
			if (value.length() == 32) {
				return UUID.fromString(
					value.substring(0, 8) + "-" + value.substring(8, 12) + "-" + value.substring(12, 16)
						+ "-" + value.substring(16, 20) + "-" + value.substring(20)
				);
			}
			return UUID.fromString(value);
		} catch (Exception ignored) {
			return null;
		}
	}

	record Candidate(String name, UUID uuid, String refreshToken, String accessToken, String clientId, boolean active) {
		boolean microsoft() {
			return refreshToken != null && !refreshToken.isBlank();
		}
	}
}
