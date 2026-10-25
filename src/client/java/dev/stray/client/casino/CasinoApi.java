package dev.stray.client.casino;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stray.Stray;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Talks to the casino on the Stray Worker. Play money only: the balance and every result
 * are decided by the server; this sends what you want to do and returns what happened.
 * Logging in proves the Minecraft account to Mojang (the same check servers use).
 */
public final class CasinoApi {
	private static final String BASE = "https://stray.gay/api/casino";
	private static final String JOIN = "https://sessionserver.mojang.com/session/minecraft/join";
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
	private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "stray-casino");
		t.setDaemon(true);
		return t;
	});
	private static volatile String token;

	private CasinoApi() {
	}

	/** A call's result: the JSON body, or an error message for the player. */
	public record Result(JsonObject body, String error) {
		public boolean ok() {
			return error == null;
		}
	}

	public static CompletableFuture<Result> login() {
		return CompletableFuture.supplyAsync(CasinoApi::loginNow, POOL);
	}

	public static CompletableFuture<Result> call(String path, JsonObject body) {
		return CompletableFuture.supplyAsync(() -> {
			if (token == null) {
				Result login = loginNow();
				if (!login.ok()) {
					return login;
				}
			}
			Result result = send(path, body);
			if (result.error != null && "auth".equals(result.error)) {
				Result login = loginNow();
				if (!login.ok()) {
					return login;
				}
				result = send(path, body);
			}
			if ("auth".equals(result.error)) {
				return new Result(null, "Could not log in to the casino.");
			}
			return result;
		}, POOL);
	}

	private static Result loginNow() {
		Minecraft client = Minecraft.getInstance();
		User user = client.getUser();
		if (user == null || user.getAccessToken() == null || user.getAccessToken().isBlank()) {
			return new Result(null, "You need to be logged in to a Minecraft account.");
		}
		String server = UUID.randomUUID().toString().replace("-", "");
		boolean joined = join(user, server);
		// Second proof for when the Worker can't reach Mojang: the chat signing key Mojang
		// issued for this account signs the login.
		JsonObject proof = keyProof(client, user, server);
		if (!joined && proof == null) {
			return new Result(null, "Mojang refused the login. Try again in a moment.");
		}
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + "/login"))
				.timeout(Duration.ofSeconds(20))
				.header("User-Agent", "Stray")
				.header("Content-Type", "application/json")
				.header("x-minecraft-username", user.getName())
				.header("x-minecraft-server", server)
				.POST(HttpRequest.BodyPublishers.ofString(proof == null ? "{}" : proof.toString()))
				.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			JsonObject body = parse(response.body());
			if (response.statusCode() != 200 || body == null || !body.has("token")) {
				return new Result(null, message(body, "Casino login failed (" + response.statusCode() + ")."));
			}
			token = body.get("token").getAsString();
			return new Result(body, null);
		} catch (Exception e) {
			Stray.LOGGER.warn("Casino login failed", e);
			return new Result(null, "Can't reach the casino.");
		}
	}

	private static Result send(String path, JsonObject body) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + path))
				.timeout(Duration.ofSeconds(12))
				.header("User-Agent", "Stray")
				.header("Content-Type", "application/json")
				.header("Authorization", "Bearer " + token)
				.POST(HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body.toString()))
				.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			JsonObject json = parse(response.body());
			if (response.statusCode() == 401) {
				token = null;
				return new Result(null, "auth");
			}
			if (response.statusCode() != 200) {
				return new Result(json, message(json, "Casino error (" + response.statusCode() + ")."));
			}
			return new Result(json, null);
		} catch (Exception e) {
			Stray.LOGGER.warn("Casino call {} failed", path, e);
			return new Result(null, "Can't reach the casino.");
		}
	}

	private static JsonObject keyProof(Minecraft client, User user, String server) {
		try {
			var pair = client.getProfileKeyPairManager().prepareKeyPair().get(10, java.util.concurrent.TimeUnit.SECONDS);
			if (pair.isEmpty()) {
				return null;
			}
			var data = pair.get().publicKey().data();
			java.util.Base64.Encoder b64 = java.util.Base64.getEncoder();
			String uuid = user.getProfileId().toString();
			long time = System.currentTimeMillis();
			java.security.Signature sign = java.security.Signature.getInstance("SHA256withRSA");
			sign.initSign(pair.get().privateKey());
			sign.update(("stray-casino|" + user.getName() + "|" + server + "|" + time + "|" + uuid).getBytes(java.nio.charset.StandardCharsets.UTF_8));
			JsonObject cert = new JsonObject();
			cert.addProperty("key", b64.encodeToString(data.key().getEncoded()));
			cert.addProperty("expires", data.expiresAt().toEpochMilli());
			cert.addProperty("sig", b64.encodeToString(data.keySignature()));
			JsonObject body = new JsonObject();
			body.addProperty("uuid", uuid);
			body.addProperty("time", time);
			body.addProperty("proof", b64.encodeToString(sign.sign()));
			body.add("cert", cert);
			return body;
		} catch (Exception e) {
			Stray.LOGGER.warn("Casino key proof unavailable", e);
			return null;
		}
	}

	private static boolean join(User user, String server) {
		try {
			JsonObject body = new JsonObject();
			body.addProperty("accessToken", user.getAccessToken());
			body.addProperty("selectedProfile", user.getProfileId().toString().replace("-", ""));
			body.addProperty("serverId", server);
			HttpRequest request = HttpRequest.newBuilder(URI.create(JOIN))
				.timeout(Duration.ofSeconds(8))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
				.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			return response.statusCode() == 204 || response.statusCode() == 200;
		} catch (Exception e) {
			return false;
		}
	}

	private static JsonObject parse(String raw) {
		try {
			return raw == null || raw.isBlank() ? null : JsonParser.parseString(raw).getAsJsonObject();
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static String message(JsonObject body, String fallback) {
		if (body != null && body.has("error") && body.get("error").isJsonPrimitive()) {
			return body.get("error").getAsString();
		}
		return fallback;
	}
}
