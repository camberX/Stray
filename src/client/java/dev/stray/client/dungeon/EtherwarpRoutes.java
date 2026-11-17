package dev.stray.client.dungeon;

import com.google.gson.JsonArray;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Shared etherwarp routes on stray.gay: the list, one route's rooms, sharing yours and taking
 * yours down. Everything runs off the render thread; answers come back on it.
 */
public final class EtherwarpRoutes {
	private static final String BASE = "https://stray.gay";
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
	private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "stray-routes");
		thread.setDaemon(true);
		return thread;
	});
	/** Rooms of routes already fetched, by id. */
	private static final Map<String, Detail> DETAILS = new ConcurrentHashMap<>();

	private EtherwarpRoutes() {
	}

	public record Summary(String id, String name, String description, String author, String uuid, int rooms, int points, long at) {
		public boolean mine() {
			Minecraft client = Minecraft.getInstance();
			return client.player != null && client.player.getUUID().toString().equalsIgnoreCase(uuid);
		}
	}

	/** A route's rooms: name to points (room coordinates). */
	public record Detail(String id, JsonObject rooms) {
		public List<String> roomNames() {
			List<String> names = new ArrayList<>(rooms.keySet());
			names.sort(String.CASE_INSENSITIVE_ORDER);
			return names;
		}
	}

	/** The answer to a request: what came back, or an error to show. */
	public record Result<T>(T value, String error) {
	}

	public static Detail cached(String id) {
		return DETAILS.get(id);
	}

	public static void list(Consumer<Result<List<Summary>>> done) {
		POOL.execute(() -> {
			HttpResponse<String> response = send("GET", "/api/routes", null, false);
			if (response == null || response.statusCode() != 200) {
				deliver(done, new Result<>(null, error(response, "Couldn't load routes.")));
				return;
			}
			List<Summary> routes = new ArrayList<>();
			try {
				JsonArray array = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonArray("routes");
				for (JsonElement element : array) {
					JsonObject o = element.getAsJsonObject();
					routes.add(new Summary(text(o, "id"), text(o, "name"), text(o, "description"), text(o, "author"), text(o, "uuid"),
						number(o, "rooms"), number(o, "points"), o.has("at") ? o.get("at").getAsLong() : 0L));
				}
			} catch (Exception e) {
				deliver(done, new Result<>(null, "Couldn't read the route list."));
				return;
			}
			deliver(done, new Result<>(routes, null));
		});
	}

	public static void detail(String id, Consumer<Result<Detail>> done) {
		Detail known = DETAILS.get(id);
		if (known != null) {
			done.accept(new Result<>(known, null));
			return;
		}
		POOL.execute(() -> {
			HttpResponse<String> response = send("GET", "/api/routes/" + id, null, false);
			if (response == null || response.statusCode() != 200) {
				deliver(done, new Result<>(null, error(response, "Couldn't load that route.")));
				return;
			}
			try {
				JsonObject data = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("data");
				Detail detail = new Detail(id, data == null ? new JsonObject() : data);
				DETAILS.put(id, detail);
				deliver(done, new Result<>(detail, null));
			} catch (Exception e) {
				deliver(done, new Result<>(null, "Couldn't read that route."));
			}
		});
	}

	/** Shares your points under {@code name}; the same name again replaces your old one. */
	public static void upload(String name, JsonObject rooms, Consumer<Result<String>> done) {
		JsonObject body = new JsonObject();
		body.addProperty("name", name);
		body.add("rooms", rooms);
		byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
		POOL.execute(() -> {
			HttpResponse<String> response = send("POST", "/api/routes", bytes, true);
			if (response == null || response.statusCode() != 200) {
				deliver(done, new Result<>(null, error(response, "Couldn't share it.")));
				return;
			}
			try {
				deliver(done, new Result<>(JsonParser.parseString(response.body()).getAsJsonObject().get("id").getAsString(), null));
			} catch (Exception e) {
				deliver(done, new Result<>("", null));
			}
		});
	}

	public static void delete(String id, Consumer<Result<Boolean>> done) {
		POOL.execute(() -> {
			HttpResponse<String> response = send("DELETE", "/api/routes/" + id, null, true);
			if (response == null || response.statusCode() != 200) {
				deliver(done, new Result<>(null, error(response, "Couldn't delete it.")));
				return;
			}
			DETAILS.remove(id);
			deliver(done, new Result<>(true, null));
		});
	}

	private static <T> void deliver(Consumer<Result<T>> done, Result<T> result) {
		Minecraft.getInstance().execute(() -> done.accept(result));
	}

	private static String error(HttpResponse<String> response, String fallback) {
		if (response == null) {
			return CasinoApi.authToken() == null ? "Couldn't log in to stray.gay." : fallback;
		}
		try {
			JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
			if (body.has("retryIn")) {
				return "Too fast: try again in " + body.get("retryIn").getAsInt() + "s.";
			}
			if (body.has("error")) {
				return body.get("error").getAsString();
			}
		} catch (Exception ignored) {
		}
		return fallback;
	}

	private static HttpResponse<String> send(String method, String path, byte[] body, boolean auth) {
		for (int attempt = 0; attempt < 2; attempt++) {
			String token = auth ? CasinoApi.authToken() : null;
			if (auth && token == null) {
				return null;
			}
			try {
				HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BASE + path))
					.timeout(Duration.ofSeconds(15))
					.header("User-Agent", "Stray");
				if (token != null) {
					builder.header("Authorization", "Bearer " + token);
				}
				if (body != null) {
					builder.header("Content-Type", "application/json");
				}
				HttpRequest.BodyPublisher publisher = body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body);
				HttpResponse<String> response = HTTP.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
				if (auth && response.statusCode() == 401) {
					CasinoApi.dropToken();
					continue;
				}
				return response;
			} catch (Exception e) {
				Stray.LOGGER.debug("Routes {} {} failed", method, path, e);
				return null;
			}
		}
		return null;
	}

	private static String text(JsonObject o, String key) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
	}

	private static int number(JsonObject o, String key) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsInt() : 0;
	}
}
