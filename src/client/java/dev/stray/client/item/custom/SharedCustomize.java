package dev.stray.client.item.custom;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stray.Stray;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.item.ItemIds;
import dev.stray.client.visual.CosmeticsApi;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /st customize, shared with other Stray users: the dye and head skin of the armor you wear
 * go to stray.gay (Share with Stray users, off by default), and the armor other Stray users
 * wear is drawn with theirs.
 *
 * <p>Only the four worn pieces are shared, each with its slot, SkyBlock id and Minecraft item,
 * since other players' armor may come without the item's UUID. A piece someone wears is tagged
 * when the server sends it ({@link #tag}), and again when their shared look changes.
 */
public final class SharedCustomize {
	private static final String BASE = "https://stray.gay";
	private static final int MAX_BYTES = 16 * 1024;
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final Gson GSON = new Gson();
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

	private record Entry(String id, String item, CustomItems.Data data) {
	}

	private static final class Shared {
		volatile String hash = "";
		volatile Map<EquipmentSlot, Entry> slots = Map.of();
	}

	private static final Map<UUID, Shared> PLAYERS = new ConcurrentHashMap<>();

	/** What was last sent ("" = nothing shared), what is waiting to be sent, and since when. */
	private static String sent;
	private static String pending;
	private static long pendingSince;
	private static volatile boolean sending;
	private static long nextCheck;
	private static long retryAt;

	private SharedCustomize() {
	}

	// ------------------------------------------------------------------ others

	/** From /api/cape/<uuid>: the hash of their shared look, "" when they share none. */
	public static void set(UUID uuid, String hash) {
		if (uuid == null) {
			return;
		}
		if (hash == null || hash.isBlank()) {
			if (PLAYERS.remove(uuid) != null) {
				Minecraft.getInstance().execute(() -> retag(uuid));
			}
			return;
		}
		Shared shared = PLAYERS.computeIfAbsent(uuid, id -> new Shared());
		if (hash.equals(shared.hash)) {
			return;
		}
		shared.hash = hash;
		Util.nonCriticalIoPool().execute(() -> download(uuid, shared, hash));
	}

	private static void download(UUID uuid, Shared shared, String hash) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + "/customize/" + uuid + ".json"))
				.timeout(Duration.ofSeconds(10))
				.header("User-Agent", "Stray")
				.GET()
				.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200 || response.body().length() > MAX_BYTES || !hash.equals(shared.hash)) {
				return;
			}
			shared.slots = parse(response.body());
			Minecraft.getInstance().execute(() -> retag(uuid));
		} catch (Exception exception) {
			Stray.LOGGER.debug("Shared customize for {} failed", uuid, exception);
		}
	}

	private static Map<EquipmentSlot, Entry> parse(String body) {
		Map<EquipmentSlot, Entry> out = new EnumMap<>(EquipmentSlot.class);
		JsonElement root = JsonParser.parseString(body);
		if (!root.isJsonObject() || !root.getAsJsonObject().has("items")) {
			return out;
		}
		for (JsonElement element : root.getAsJsonObject().getAsJsonArray("items")) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject item = element.getAsJsonObject();
			EquipmentSlot slot = slot(string(item, "slot"));
			if (slot == null) {
				continue;
			}
			CustomItems.Data data = new CustomItems.Data();
			if (item.has("color") && item.get("color").isJsonObject()) {
				data.color = GSON.fromJson(item.get("color"), CustomItems.ColorSpec.class);
			}
			if (item.has("skin") && item.get("skin").isJsonObject()) {
				data.skin = GSON.fromJson(item.get("skin"), CustomItems.SkinSpec.class);
			}
			if (data.color != null || data.skin != null) {
				out.put(slot, new Entry(string(item, "id"), string(item, "item"), data));
			}
		}
		return out;
	}

	/** The server set a piece of another player's equipment: tag it when their look is for it. */
	public static void tag(Player player, EquipmentSlot slot, ItemStack stack) {
		Shared shared = PLAYERS.get(player.getUUID());
		if (shared == null || stack == null || stack.isEmpty()) {
			return;
		}
		Entry entry = shared.slots.get(slot);
		if (entry != null && matches(entry, stack)) {
			CustomItems.tagShared(stack, key(player.getUUID(), slot));
		}
	}

	/** Tag (or untag) what a player wears now, after their shared look arrived or went away. */
	private static void retag(UUID uuid) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return;
		}
		Player player = client.level.getPlayerByUUID(uuid);
		if (player == null || player == client.player) {
			return;
		}
		for (EquipmentSlot slot : SLOTS) {
			ItemStack stack = player.getItemBySlot(slot);
			CustomItems.untag(stack);
			tag(player, slot, stack);
		}
	}

	private static boolean matches(Entry entry, ItemStack stack) {
		String id = ItemIds.skyblockId(stack);
		if (entry.id() != null && id != null) {
			return entry.id().equals(id);
		}
		return entry.item() != null && entry.item().equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
	}

	/** The shared look under a key from {@link #key}. */
	static CustomItems.Data data(String key) {
		// "s:<uuid>:<slot>"
		int split = key.lastIndexOf(':');
		if (split < 3) {
			return null;
		}
		try {
			Shared shared = PLAYERS.get(UUID.fromString(key.substring(2, split)));
			EquipmentSlot slot = slot(key.substring(split + 1));
			Entry entry = shared == null || slot == null ? null : shared.slots.get(slot);
			return entry == null ? null : entry.data();
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private static String key(UUID uuid, EquipmentSlot slot) {
		return "s:" + uuid + ":" + slot.getName();
	}

	private static EquipmentSlot slot(String name) {
		for (EquipmentSlot slot : SLOTS) {
			if (slot.getName().equals(name)) {
				return slot;
			}
		}
		return null;
	}

	private static String string(JsonObject json, String key) {
		JsonElement value = json.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}

	// ------------------------------------------------------------------ yours

	/**
	 * Once a second: what you wear and how it is customized. When that changes (or sharing is
	 * turned on or off) and stays the same for 3 seconds, it is sent.
	 */
	public static void tick(Minecraft client) {
		long now = System.currentTimeMillis();
		if (now < nextCheck || client.player == null) {
			return;
		}
		nextCheck = now + 1_000L;
		String want = StrayConfig.get().customizeShare ? mine(client.player) : "";
		if (sent == null && want.isEmpty()) {
			// Never shared this session and nothing to share: leave the server alone.
			return;
		}
		if (want.equals(sent)) {
			pending = null;
			return;
		}
		if (!want.equals(pending)) {
			pending = want;
			pendingSince = now;
			return;
		}
		if (sending || now - pendingSince < 3_000L || now < retryAt) {
			return;
		}
		sending = true;
		String body = want;
		Util.nonCriticalIoPool().execute(() -> {
			int status = CosmeticsApi.sendCustomize(body.isEmpty() ? null : body);
			client.execute(() -> {
				sending = false;
				if (status >= 200 && status < 300) {
					sent = body;
				} else {
					// Slowed down, logged out or offline: try again a bit later.
					retryAt = System.currentTimeMillis() + (status == 429 ? 5_000L : 30_000L);
				}
			});
		});
	}

	/** Your worn pieces with a custom dye or head skin, as the JSON the server takes. */
	private static String mine(Player player) {
		JsonArray items = new JsonArray();
		for (EquipmentSlot slot : SLOTS) {
			ItemStack stack = player.getItemBySlot(slot);
			CustomItems.Data data = stack.isEmpty() ? null : CustomItems.get(stack);
			if (data == null || data.color == null && data.skin == null) {
				continue;
			}
			JsonObject item = new JsonObject();
			item.addProperty("slot", slot.getName());
			String id = ItemIds.skyblockId(stack);
			if (id != null) {
				item.addProperty("id", id);
			}
			item.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
			if (data.color != null) {
				item.add("color", GSON.toJsonTree(data.color));
			}
			if (data.skin != null) {
				item.add("skin", GSON.toJsonTree(data.skin));
			}
			items.add(item);
		}
		if (items.isEmpty()) {
			return "";
		}
		JsonObject root = new JsonObject();
		root.add("items", items);
		return root.toString();
	}
}
