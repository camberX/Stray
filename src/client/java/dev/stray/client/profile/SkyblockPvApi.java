package dev.stray.client.profile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stray.Stray;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/**
 * Same backend as <a href="https://github.com/meowdding/skyblock-pv">skyblock-pv</a>:
 * a Minecraft-session-auth proxy in front of Hypixel {@code /skyblock/profiles}.
 * One cached response has stats and bag NBT, so {@code /pv} does not wait on
 * Soopy or the odtheking dump.
 */
final class SkyblockPvApi {
	private static final String BASE = "https://skyblock-pv.thatgravyboat.tech";
	private static final String JOIN = "https://sessionserver.mojang.com/session/minecraft/join";
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL)
		.connectTimeout(Duration.ofSeconds(8))
		.build();
	private static final Object LOCK = new Object();
	private static volatile String key;
	private static volatile boolean failed;

	private SkyblockPvApi() {
	}

	static JsonObject profiles(Minecraft client, UUID uuid) {
		if (uuid == null || !ensureAuth(client)) {
			return null;
		}
		JsonObject json = get("/profiles/" + uuid, "profile", false);
		if (json == null && key != null) {
			json = get("/profiles/" + uuid, "profile", true);
		}
		return json;
	}

	private static boolean ensureAuth(Minecraft client) {
		if (key != null && !key.isBlank() && !failed) {
			return true;
		}
		synchronized (LOCK) {
			if (key != null && !key.isBlank() && !failed) {
				return true;
			}
			if (!authenticate(client)) {
				failed = true;
				return false;
			}
			failed = false;
			return true;
		}
	}

	private static boolean authenticate(Minecraft client) {
		if (client == null) {
			return false;
		}
		User user = client.getUser();
		if (user == null || user.getAccessToken() == null || user.getAccessToken().isBlank()) {
			return false;
		}
		String server = UUID.randomUUID().toString();
		if (!joinServer(user, server)) {
			Stray.LOGGER.warn("Profile viewer Mojang join for PV API failed");
			return false;
		}
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + "/authenticate"))
				.timeout(Duration.ofSeconds(8))
				.header("User-Agent", userAgent())
				.header("x-minecraft-username", user.getName())
				.header("x-minecraft-server", server)
				.GET()
				.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				Stray.LOGGER.warn("Profile viewer PV API auth returned HTTP {}", response.statusCode());
				key = null;
				return false;
			}
			String body = response.body() == null ? "" : response.body().trim();
			if (body.isBlank() || body.startsWith("{") || body.startsWith("<")) {
				Stray.LOGGER.warn("Profile viewer PV API auth returned an empty key");
				key = null;
				return false;
			}
			key = body;
			return true;
		} catch (Exception exception) {
			Stray.LOGGER.warn("Profile viewer PV API auth failed", exception);
			key = null;
			return false;
		}
	}

	private static boolean joinServer(User user, String server) {
		try {
			JsonObject body = new JsonObject();
			body.addProperty("accessToken", user.getAccessToken());
			body.addProperty("selectedProfile", user.getProfileId().toString());
			body.addProperty("serverId", server);
			HttpRequest request = HttpRequest.newBuilder(URI.create(JOIN))
				.timeout(Duration.ofSeconds(8))
				.header("Content-Type", "application/json")
				.header("User-Agent", userAgent())
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
				.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			return response.statusCode() == 204 || response.statusCode() == 200;
		} catch (Exception exception) {
			return false;
		}
	}

	private static JsonObject get(String path, String intent, boolean reauth) {
		String token = key;
		if (token == null || token.isBlank()) {
			return null;
		}
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + path))
				.timeout(Duration.ofSeconds(12))
				.header("User-Agent", userAgent())
				.header("Authorization", token)
				.header("X-Intent", intent == null ? "profile" : intent)
				.GET()
				.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 401) {
				key = null;
				if (!reauth) {
					failed = false;
					if (ensureAuth(Minecraft.getInstance())) {
						return get(path, intent, true);
					}
				}
				return null;
			}
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				Stray.LOGGER.warn("Profile viewer PV API {} returned HTTP {}", path, response.statusCode());
				return null;
			}
			return JsonParser.parseString(response.body()).getAsJsonObject();
		} catch (Exception exception) {
			Stray.LOGGER.warn("Profile viewer PV API {} failed", path, exception);
			return null;
		}
	}

	private static String userAgent() {
		String version = "1.4.9";
		try {
			version = net.fabricmc.loader.api.FabricLoader.getInstance()
				.getModContainer(Stray.MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse(version);
		} catch (Exception ignored) {
		}
		return "SkyBlockPV/" + version + "/" + SharedConstants.getCurrentVersion().name();
	}
}
