package dev.stray.client.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stray.Stray;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.location.SkyblockLocation;
import dev.stray.update.UpdateMeta;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Global IRC and lobby pings on the existing stray.gay websocket.
 */
public final class StrayLive implements WebSocket.Listener {
	private static final String PATH = "/ws";
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();
	private static final long HELLO_MS = 8_000L;
	private static final long BACKOFF_START = 3_000L;
	private static final long BACKOFF_MAX = 30_000L;
	private static final Style BRACKET = Style.EMPTY.withColor(ChatFormatting.YELLOW);
	private static final Style NAME = Style.EMPTY.withColor(ChatFormatting.AQUA);
	private static final Style TEXT = Style.EMPTY.withColor(ChatFormatting.WHITE);
	private static final Style MUTED = Style.EMPTY.withColor(ChatFormatting.GRAY);

	private static volatile WebSocket socket;
	private static volatile boolean connecting;
	private static volatile long nextConnectAt;
	private static volatile long backoff = BACKOFF_START;
	private static volatile String lastServer = "";
	private static volatile boolean greeted;
	private static volatile long helloAt;
	private static final List<CharSequence> PARTS = new ArrayList<>();

	private StrayLive() {
	}

	public static void init() {
		LobbyPings.init();
	}

	public static void tick(Minecraft client) {
		LobbyPings.tick();
		if (!wanted()) {
			disconnect();
			return;
		}
		if (client == null || client.player == null) {
			return;
		}
		if (!open()) {
			connect();
			return;
		}
		if (!greeted) {
			if (helloAt != 0L && System.currentTimeMillis() - helloAt > HELLO_MS) {
				disconnect();
			}
			return;
		}
		String server = SkyblockLocation.server == null ? "" : SkyblockLocation.server.trim();
		if (!server.equals(lastServer)) {
			lastServer = server;
			send(serverUpdate(server));
			LobbyPings.clear();
		}
	}

	public static void disconnect() {
		greeted = false;
		helloAt = 0L;
		lastServer = "";
		connecting = false;
		LobbyPings.clear();
		WebSocket current = socket;
		socket = null;
		if (current != null) {
			try {
				current.sendClose(WebSocket.NORMAL_CLOSURE, "leave");
			} catch (Exception ignored) {
			}
		}
	}

	public static boolean connected() {
		return open() && greeted;
	}

	public static boolean sendIrc(String text) {
		String clean = sanitizeIrc(text);
		if (clean.isEmpty()) {
			tell("Type a message.", ChatFormatting.GRAY);
			return false;
		}
		if (!StrayConfig.get().strayIrcEnabled) {
			tell("Turn on Global IRC in Menus.", ChatFormatting.GRAY);
			return false;
		}
		if (!connected()) {
			tell("IRC is not connected yet.", ChatFormatting.GRAY);
			return false;
		}
		JsonObject payload = new JsonObject();
		payload.addProperty("type", "irc");
		payload.addProperty("text", clean);
		send(payload);
		return true;
	}

	public static boolean sendPing(int x, int y, int z, String label) {
		if (!StrayConfig.get().strayPingEnabled) {
			tell("Turn on Lobby pings in Menus.", ChatFormatting.GRAY);
			return false;
		}
		if (!connected()) {
			tell("Live socket is not connected yet.", ChatFormatting.GRAY);
			return false;
		}
		String server = SkyblockLocation.server == null ? "" : SkyblockLocation.server.trim();
		if (server.isEmpty()) {
			tell("Join a Hypixel lobby to ping.", ChatFormatting.GRAY);
			return false;
		}
		JsonObject payload = new JsonObject();
		payload.addProperty("type", "ping");
		payload.addProperty("x", x);
		payload.addProperty("y", y);
		payload.addProperty("z", z);
		if (label != null && !label.isBlank()) {
			payload.addProperty("label", sanitizeIrc(label));
		}
		send(payload);
		return true;
	}

	private static boolean wanted() {
		StrayConfig config = StrayConfig.get();
		return config.strayIrcEnabled || config.strayPingEnabled;
	}

	private static boolean open() {
		WebSocket current = socket;
		return current != null && !current.isInputClosed() && !current.isOutputClosed();
	}

	private static void connect() {
		if (connecting || open()) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now < nextConnectAt) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		connecting = true;
		nextConnectAt = now + backoff;
		try {
			HTTP.newWebSocketBuilder()
				.header("User-Agent", userAgent())
				.buildAsync(URI.create(wsUrl()), new StrayLive())
				.thenAccept(ws -> {
					socket = ws;
					connecting = false;
					backoff = BACKOFF_START;
					hello(client);
				})
				.exceptionally(error -> {
					connecting = false;
					socket = null;
					backoff = Math.min(BACKOFF_MAX, backoff * 2L);
					Stray.LOGGER.debug("Stray live websocket failed", error);
					return null;
				});
		} catch (Exception e) {
			connecting = false;
			socket = null;
			backoff = Math.min(BACKOFF_MAX, backoff * 2L);
			Stray.LOGGER.debug("Stray live websocket failed", e);
		}
	}

	private static void hello(Minecraft client) {
		if (client.player == null) {
			return;
		}
		String name = client.player.getGameProfile().name();
		UUID uuid = client.player.getUUID();
		JsonObject payload = new JsonObject();
		payload.addProperty("type", "hello");
		payload.addProperty("name", name == null ? "" : name);
		payload.addProperty("uuid", uuid == null ? "" : uuid.toString());
		payload.addProperty("server", SkyblockLocation.server == null ? "" : SkyblockLocation.server);
		payload.addProperty("version", modVersion());
		helloAt = System.currentTimeMillis();
		send(payload);
	}

	private static JsonObject serverUpdate(String server) {
		JsonObject payload = new JsonObject();
		payload.addProperty("type", "server");
		payload.addProperty("server", server == null ? "" : server);
		return payload;
	}

	private static void send(JsonObject payload) {
		WebSocket current = socket;
		if (current == null || payload == null) {
			return;
		}
		try {
			current.sendText(payload.toString(), true);
		} catch (Exception ignored) {
		}
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
		greeted = false;
		helloAt = 0L;
		connecting = false;
		lastServer = "";
		return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
	}

	@Override
	public void onError(WebSocket webSocket, Throwable error) {
		Stray.LOGGER.debug("Stray live websocket error", error);
		socket = null;
		greeted = false;
		helloAt = 0L;
		connecting = false;
	}

	private static void handle(String raw) {
		try {
			JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
			if (!json.has("type") || !json.get("type").isJsonPrimitive()) {
				return;
			}
			String type = json.get("type").getAsString();
			Minecraft client = Minecraft.getInstance();
			switch (type) {
				case "hello" -> client.execute(() -> onHello(json));
				case "irc" -> client.execute(() -> onIrc(json));
				case "history" -> client.execute(() -> onHistory(json));
				case "ping" -> client.execute(() -> onPing(json));
				case "error" -> client.execute(() -> onErrorMessage(json));
				default -> {
				}
			}
		} catch (Exception e) {
			Stray.LOGGER.debug("Stray live parse failed", e);
		}
	}

	private static void onHello(JsonObject json) {
		greeted = json.has("ok") && json.get("ok").getAsBoolean();
		lastServer = SkyblockLocation.server == null ? "" : SkyblockLocation.server;
	}

	private static void onHistory(JsonObject json) {
		if (!StrayConfig.get().strayIrcEnabled || !json.has("messages") || !json.get("messages").isJsonArray()) {
			return;
		}
		JsonArray array = json.get("messages").getAsJsonArray();
		for (JsonElement element : array) {
			if (element.isJsonObject()) {
				onIrc(element.getAsJsonObject());
			}
		}
	}

	private static void onIrc(JsonObject json) {
		if (!StrayConfig.get().strayIrcEnabled) {
			return;
		}
		String name = text(json, "name");
		String body = text(json, "text");
		if (name.isEmpty() || body.isEmpty()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.gui == null) {
			return;
		}
		MutableComponent line = Component.empty();
		line.append(Component.literal("[").setStyle(BRACKET));
		line.append(Component.literal("IRC").setStyle(BRACKET));
		line.append(Component.literal("] ").setStyle(BRACKET));
		line.append(Component.literal(name).setStyle(NAME));
		line.append(Component.literal(": ").setStyle(TEXT));
		line.append(Component.literal(body).setStyle(TEXT));
		client.gui.getChat().addClientSystemMessage(line);
	}

	private static void onPing(JsonObject json) {
		if (!StrayConfig.get().strayPingEnabled) {
			return;
		}
		String name = text(json, "name");
		int x = json.has("x") ? json.get("x").getAsInt() : Integer.MIN_VALUE;
		int y = json.has("y") ? json.get("y").getAsInt() : Integer.MIN_VALUE;
		int z = json.has("z") ? json.get("z").getAsInt() : Integer.MIN_VALUE;
		if (name.isEmpty() || y == Integer.MIN_VALUE) {
			return;
		}
		String label = text(json, "label");
		LobbyPings.accept(name, x, y, z, label);
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.gui == null) {
			return;
		}
		MutableComponent line = Component.empty();
		line.append(Component.literal("[").setStyle(BRACKET));
		line.append(Component.literal("Ping").setStyle(BRACKET));
		line.append(Component.literal("] ").setStyle(BRACKET));
		line.append(Component.literal(name).setStyle(NAME));
		line.append(Component.literal(" marked ").setStyle(MUTED));
		line.append(Component.literal(x + ", " + y + ", " + z).setStyle(TEXT));
		if (!label.isEmpty()) {
			line.append(Component.literal(" · " + label).setStyle(MUTED));
		}
		client.gui.getChat().addClientSystemMessage(line);
	}

	private static void onErrorMessage(JsonObject json) {
		String message = text(json, "message");
		if (!message.isEmpty()) {
			tell(message, ChatFormatting.RED);
		}
	}

	private static String text(JsonObject json, String key) {
		if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
			return "";
		}
		String value = json.get(key).getAsString();
		return value == null ? "" : value.trim();
	}

	static String sanitizeIrc(String text) {
		if (text == null) {
			return "";
		}
		String clean = text.replace('\n', ' ').replace('\r', ' ').replaceAll("§.", "").trim();
		if (clean.length() > 180) {
			clean = clean.substring(0, 180);
		}
		return clean;
	}

	private static String wsUrl() {
		String base = UpdateMeta.SHOP;
		if (base.startsWith("https://")) {
			return "wss://" + base.substring("https://".length()) + PATH;
		}
		if (base.startsWith("http://")) {
			return "ws://" + base.substring("http://".length()) + PATH;
		}
		return "wss://stray.gay" + PATH;
	}

	private static String userAgent() {
		return "Stray/" + modVersion() + " (" + SharedConstants.getCurrentVersion().name() + ")";
	}

	private static String modVersion() {
		return FabricLoader.getInstance()
			.getModContainer("stray")
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("dev");
	}

	static void tell(String text, ChatFormatting color) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		client.player.sendSystemMessage(
			Component.literal("[Stray] ").withStyle(ChatFormatting.YELLOW)
				.append(Component.literal(text).withStyle(color))
		);
	}
}
