package dev.stray.client.mining;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stray.Stray;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;

/**
 * Receive-only Crystal Hollows room on ws.hysky.de. Subscribe / unsubscribe only;
 * never publishes our own finds.
 */
final class CrystalHollowsSocket implements WebSocket.Listener {
	private static final String WS_URL = "wss://ws.hysky.de";
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	private static volatile WebSocket socket;
	private static volatile String subscribedServer = "";
	private static final List<CharSequence> PARTS = new ArrayList<>();

	private CrystalHollowsSocket() {
	}

	static void tick(String serverId) {
		if (serverId == null || serverId.isBlank()) {
			return;
		}
		HyskyAuth.refreshIfNeeded();
		if (!HyskyAuth.ready()) {
			return;
		}
		if (!open()) {
			connect();
			return;
		}
		if (!serverId.equals(subscribedServer)) {
			if (!subscribedServer.isEmpty()) {
				send(unsubscribe(subscribedServer));
			}
			send(subscribe(serverId));
			subscribedServer = serverId;
		}
	}

	static void disconnect() {
		if (open() && !subscribedServer.isEmpty()) {
			send(unsubscribe(subscribedServer));
		}
		subscribedServer = "";
		WebSocket current = socket;
		socket = null;
		if (current != null) {
			try {
				current.sendClose(WebSocket.NORMAL_CLOSURE, "leave");
			} catch (Exception ignored) {
			}
		}
	}

	private static boolean open() {
		WebSocket current = socket;
		return current != null && !current.isInputClosed() && !current.isOutputClosed();
	}

	private static void connect() {
		if (socket != null || !HyskyAuth.ready()) {
			return;
		}
		try {
			HTTP.newWebSocketBuilder()
				.header("Authorization", "Bearer " + HyskyAuth.token())
				.header("User-Agent", HyskyAuth.userAgent())
				.buildAsync(URI.create(WS_URL), new CrystalHollowsSocket())
				.thenAccept(ws -> socket = ws)
				.exceptionally(error -> {
					Stray.LOGGER.warn("Crystal Hollows websocket failed", error);
					return null;
				});
		} catch (Exception e) {
			Stray.LOGGER.warn("Crystal Hollows websocket failed", e);
		}
	}

	private static JsonObject subscribe(String serverId) {
		JsonObject message = new JsonObject();
		message.addProperty("timestamp", System.currentTimeMillis() / 1000L + 26L * 20L * 60L);
		JsonObject payload = envelope("subscribe", serverId);
		payload.add("message", message);
		return payload;
	}

	private static JsonObject unsubscribe(String serverId) {
		return envelope("unsubscribe", serverId);
	}

	private static JsonObject envelope(String type, String serverId) {
		JsonObject payload = new JsonObject();
		payload.addProperty("type", type);
		payload.addProperty("service", "CRYSTAL_WAYPOINTS");
		payload.addProperty("serverId", serverId);
		return payload;
	}

	private static void send(JsonObject payload) {
		WebSocket current = socket;
		if (current == null) {
			return;
		}
		current.sendText(payload.toString(), true);
	}

	@Override
	public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
		synchronized (PARTS) {
			PARTS.add(data);
			webSocket.request(1);
			if (!last) {
				return new CompletableFuture<>();
			}
			String raw = String.join("", PARTS);
			PARTS.clear();
			handle(raw);
		}
		return WebSocket.Listener.super.onText(webSocket, data, last);
	}

	@Override
	public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
		return WebSocket.Listener.super.onPing(webSocket, message);
	}

	@Override
	public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
		socket = null;
		subscribedServer = "";
		return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
	}

	@Override
	public void onError(WebSocket webSocket, Throwable error) {
		Stray.LOGGER.warn("Crystal Hollows websocket error", error);
		socket = null;
	}

	private static void handle(String raw) {
		try {
			JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
			if (!json.has("type") || !json.has("service")) {
				return;
			}
			if (!"CRYSTAL_WAYPOINTS".equals(json.get("service").getAsString())) {
				return;
			}
			String type = json.get("type").getAsString();
			if (!json.has("message") || json.get("message").isJsonNull()) {
				return;
			}
			JsonElement message = json.get("message");
			Minecraft client = Minecraft.getInstance();
			if ("initialMessage".equals(type) && message.isJsonArray()) {
				List<Incoming> batch = new ArrayList<>();
				JsonArray array = message.getAsJsonArray();
				for (JsonElement element : array) {
					Incoming incoming = parse(element.getAsJsonObject());
					if (incoming != null) {
						batch.add(incoming);
					}
				}
				if (!batch.isEmpty()) {
					client.execute(() -> CrystalHollows.acceptSocket(batch));
				}
			} else if ("response".equals(type) && message.isJsonObject()) {
				Incoming incoming = parse(message.getAsJsonObject());
				if (incoming != null) {
					client.execute(() -> CrystalHollows.acceptSocket(List.of(incoming)));
				}
			}
		} catch (Exception e) {
			Stray.LOGGER.debug("Crystal Hollows websocket parse failed", e);
		}
	}

	private static Incoming parse(JsonObject object) {
		CrystalStructure structure = CrystalStructure.fromWire(object.has("name") ? object.get("name").getAsString() : "");
		if (structure == null || !object.has("coordinates") || !object.get("coordinates").isJsonObject()) {
			return null;
		}
		JsonObject pos = object.getAsJsonObject("coordinates");
		if (!pos.has("x") || !pos.has("y") || !pos.has("z")) {
			return null;
		}
		return new Incoming(structure, new BlockPos(pos.get("x").getAsInt(), pos.get("y").getAsInt(), pos.get("z").getAsInt()));
	}

	record Incoming(CrystalStructure structure, BlockPos pos) {
	}
}
