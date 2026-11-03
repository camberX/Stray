package dev.stray.client.visual;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stray.Stray;
import dev.stray.client.casino.CasinoApi;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Your own cosmetics on stray.gay: read them, and change them as you click. Every change
 * shows on your screen right away; the server copy follows a moment later. Changes made in
 * quick succession (dragging a slider) are merged and sent as one request, at most one in
 * flight, and when the server says to slow down the latest values are sent after the wait.
 */
public final class CosmeticsApi {
	private static final String BASE = "https://stray.gay";
	private static final long DEBOUNCE_MS = 300L;
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
	private static final ScheduledExecutorService POOL = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "stray-cosmetics");
		t.setDaemon(true);
		return t;
	});

	/** What the server last said about your cosmetics. */
	public record Mine(
		boolean whitelisted, boolean hasCape, int capeRetry,
		boolean wingsGranted, String wingsStyle, String wingsRgb, boolean wingsHidden,
		boolean chestGranted, float chestSize, float chestY, boolean chestHidden,
		boolean skinHas, boolean skinSlim, int skinRetry, int skinLeft
	) {
	}

	private static volatile Mine mine;
	private static volatile String status = "";
	private static volatile boolean loading;
	private static final Object LOCK = new Object();
	private static JsonObject pending = new JsonObject();
	private static boolean scheduled;
	private static boolean sending;
	private static long notBefore;

	private CosmeticsApi() {
	}

	public static Mine mine() {
		return mine;
	}

	public static String status() {
		return status;
	}

	public static boolean loading() {
		return loading;
	}

	public static void refresh() {
		if (loading) {
			return;
		}
		loading = true;
		status = mine == null ? "Logging in…" : status;
		POOL.execute(() -> {
			try {
				HttpResponse<String> response = authed("GET", "/api/cosmetics", null, null);
				if (response == null) {
					status = "Could not log in to stray.gay.";
					return;
				}
				if (response.statusCode() == 200) {
					accept(response.body());
					status = "";
				} else {
					status = error(response);
				}
			} finally {
				loading = false;
			}
		});
	}

	/** One setting changed, e.g. change("chest", "size", 1.2): applied now, sent shortly. */
	public static void change(String part, String key, JsonElement value) {
		synchronized (LOCK) {
			JsonObject group = pending.has(part) ? pending.getAsJsonObject(part) : new JsonObject();
			group.add(key, value);
			pending.add(part, group);
			applyLocal();
			schedule(DEBOUNCE_MS);
		}
		status = "Saving…";
	}

	public static void uploadSkin(byte[] png, boolean slim) {
		UUID self = self();
		if (self != null) {
			ShopSkin.setLocal(self, png, "local" + System.nanoTime(), slim);
		}
		status = "Uploading skin…";
		POOL.execute(() -> {
			HttpResponse<String> response = authed("PUT", "/api/skin?slim=" + (slim ? "1" : "0"), png, "image/png");
			if (response == null) {
				status = "Could not log in to stray.gay.";
			} else if (response.statusCode() == 200) {
				accept(response.body());
				status = "Skin is live for everyone with Stray.";
			} else {
				status = error(response);
			}
		});
	}

	public static void removeSkin() {
		UUID self = self();
		if (self != null) {
			ShopSkin.set(self, "", false);
		}
		status = "Removing skin…";
		POOL.execute(() -> {
			HttpResponse<String> response = authed("DELETE", "/api/skin", null, null);
			if (response != null && response.statusCode() == 200) {
				accept(response.body());
				status = "Skin removed.";
			} else {
				status = response == null ? "Could not log in to stray.gay." : error(response);
			}
		});
	}

	// ------------------------------------------------------------------ sending

	private static void schedule(long delay) {
		if (scheduled || sending) {
			return;
		}
		scheduled = true;
		long wait = Math.max(delay, notBefore - System.currentTimeMillis());
		POOL.schedule(CosmeticsApi::flush, Math.max(0L, wait), TimeUnit.MILLISECONDS);
	}

	private static void flush() {
		JsonObject body;
		synchronized (LOCK) {
			scheduled = false;
			if (pending.size() == 0) {
				return;
			}
			body = pending;
			pending = new JsonObject();
			sending = true;
		}
		HttpResponse<String> response = authed("POST", "/api/cosmetics", body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), "application/json");
		synchronized (LOCK) {
			sending = false;
			if (response != null && response.statusCode() == 429) {
				// Put the unsent values back under anything newer, and try after the wait.
				for (String part : body.keySet()) {
					JsonObject sent = body.getAsJsonObject(part);
					JsonObject newer = pending.has(part) ? pending.getAsJsonObject(part) : new JsonObject();
					for (String key : sent.keySet()) {
						if (!newer.has(key)) {
							newer.add(key, sent.get(key));
						}
					}
					pending.add(part, newer);
				}
				notBefore = System.currentTimeMillis() + retryMs(response);
				status = "Saving… (waiting for the server)";
				schedule(0L);
				return;
			}
			if (response == null) {
				status = "Could not reach stray.gay.";
			} else if (response.statusCode() == 200) {
				if (pending.size() == 0) {
					accept(response.body());
					status = "Saved. Everyone with Stray sees it now.";
				}
			} else {
				status = error(response);
				refresh();
			}
			notBefore = System.currentTimeMillis() + 750L;
			if (pending.size() > 0) {
				schedule(0L);
			}
		}
	}

	private static HttpResponse<String> authed(String method, String path, byte[] body, String type) {
		for (int attempt = 0; attempt < 2; attempt++) {
			String token = CasinoApi.authToken();
			if (token == null) {
				return null;
			}
			try {
				HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BASE + path))
					.timeout(Duration.ofSeconds(15))
					.header("User-Agent", "Stray")
					.header("Authorization", "Bearer " + token);
				if (type != null) {
					builder.header("Content-Type", type);
				}
				HttpRequest.BodyPublisher publisher = body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body);
				HttpResponse<String> response = HTTP.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() == 401) {
					CasinoApi.dropToken();
					continue;
				}
				return response;
			} catch (Exception e) {
				Stray.LOGGER.debug("Cosmetics {} {} failed", method, path, e);
				return null;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ local state

	private static void accept(String body) {
		try {
			JsonObject json = JsonParser.parseString(body).getAsJsonObject();
			JsonObject cape = json.getAsJsonObject("cape");
			JsonObject wings = json.getAsJsonObject("wings");
			JsonObject chest = json.getAsJsonObject("chest");
			JsonObject skin = json.getAsJsonObject("skin");
			Mine next = new Mine(
				bool(cape, "whitelisted"), bool(cape, "has"), integer(cape, "retryIn"),
				bool(wings, "granted"), text(wings, "style"), text(wings, "rgb"), bool(wings, "hidden"),
				bool(chest, "granted"), number(chest, "size", 1f), number(chest, "y", 0f), bool(chest, "hidden"),
				bool(skin, "has"), bool(skin, "slim"), integer(skin, "retryIn"), integer(skin, "left")
			);
			mine = next;
			synchronized (LOCK) {
				applyLocal();
			}
		} catch (Exception e) {
			Stray.LOGGER.debug("Cosmetics: bad answer", e);
		}
	}

	/** Your own player shows the server state with any unsent changes on top. */
	private static void applyLocal() {
		Mine m = mine;
		UUID self = self();
		if (m == null || self == null) {
			return;
		}
		JsonObject wings = pending.has("wings") ? pending.getAsJsonObject("wings") : new JsonObject();
		JsonObject chest = pending.has("chest") ? pending.getAsJsonObject("chest") : new JsonObject();
		JsonObject skin = pending.has("skin") ? pending.getAsJsonObject("skin") : new JsonObject();
		if (m.wingsGranted()) {
			boolean hidden = wings.has("hidden") ? wings.get("hidden").getAsBoolean() : m.wingsHidden();
			String style = wings.has("style") ? wings.get("style").getAsString() : m.wingsStyle();
			String rgb = wings.has("rgb") ? wings.get("rgb").getAsString() : m.wingsRgb();
			ShopWings.set(self, hidden ? "" : style, rgb);
		}
		if (m.chestGranted()) {
			boolean hidden = chest.has("hidden") ? chest.get("hidden").getAsBoolean() : m.chestHidden();
			float size = chest.has("size") ? chest.get("size").getAsFloat() : m.chestSize();
			float y = chest.has("y") ? chest.get("y").getAsFloat() : m.chestY();
			ShopChest.set(self, !hidden, size, y);
		}
		if (skin.has("slim")) {
			ShopSkin.setSlim(self, skin.get("slim").getAsBoolean());
		}
	}

	/** The value the window should show: unsent change first, then the server's. */
	public static JsonElement current(String part, String key) {
		synchronized (LOCK) {
			if (pending.has(part) && pending.getAsJsonObject(part).has(key)) {
				return pending.getAsJsonObject(part).get(key);
			}
		}
		return null;
	}

	private static UUID self() {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			return client.player.getUUID();
		}
		try {
			return client.getGameProfile().id();
		} catch (Exception e) {
			return null;
		}
	}

	private static long retryMs(HttpResponse<String> response) {
		try {
			JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
			if (json.has("retryMs")) {
				return Math.max(250L, json.get("retryMs").getAsLong());
			}
			if (json.has("retryIn")) {
				return Math.max(250L, json.get("retryIn").getAsLong() * 1000L);
			}
		} catch (Exception ignored) {
		}
		return 1500L;
	}

	private static String error(HttpResponse<String> response) {
		try {
			JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
			if (json.has("error")) {
				String message = json.get("error").getAsString();
				if (response.statusCode() == 429 && json.has("retryIn")) {
					return message + " Try again in " + json.get("retryIn").getAsInt() + "s.";
				}
				return message;
			}
		} catch (Exception ignored) {
		}
		return "stray.gay answered " + response.statusCode() + ".";
	}

	private static boolean bool(JsonObject o, String k) {
		return o != null && o.has(k) && o.get(k).isJsonPrimitive() && o.get(k).getAsBoolean();
	}

	private static int integer(JsonObject o, String k) {
		return o != null && o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsInt() : 0;
	}

	private static float number(JsonObject o, String k, float fallback) {
		return o != null && o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsFloat() : fallback;
	}

	private static String text(JsonObject o, String k) {
		return o != null && o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
	}
}
