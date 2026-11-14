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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /st customize, shared with other Stray users: the dyes and head skins you set go to
 * stray.gay (Share, off by default), and the armor other Stray users wear is drawn with theirs.
 *
 * <p>Every customized piece is shared, not only what is worn, so another player's look is
 * already here when they change armor: the new piece is matched the moment the server sends it
 * ({@link #tag}), with nothing to upload or download. A piece is matched by its item UUID when
 * the server sends one, else by SkyBlock id, else by Minecraft item.
 */
public final class SharedCustomize {
	private static final String BASE = "https://stray.gay";
	private static final int MAX_BYTES = 64 * 1024;
	/** How many customized pieces are shared at most (the newest ones). */
	private static final int MAX_ITEMS = 48;
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final Gson GSON = new Gson();
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

	/** One shared piece: its customize key ("u:<uuid>", "t:...", "i:<id>"), what it is, and its look. */
	private record Entry(String key, String id, String item, CustomItems.Data data) {
	}

	private static final class Shared {
		volatile String hash = "";
		volatile List<Entry> entries = List.of();
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
			shared.entries = parse(response.body());
			Minecraft.getInstance().execute(() -> retag(uuid));
		} catch (Exception exception) {
			Stray.LOGGER.debug("Shared customize for {} failed", uuid, exception);
		}
	}

	private static List<Entry> parse(String body) {
		List<Entry> out = new ArrayList<>();
		JsonElement root = JsonParser.parseString(body);
		if (!root.isJsonObject() || !root.getAsJsonObject().has("items")) {
			return out;
		}
		for (JsonElement element : root.getAsJsonObject().getAsJsonArray("items")) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject item = element.getAsJsonObject();
			CustomItems.Data data = new CustomItems.Data();
			if (item.has("color") && item.get("color").isJsonObject()) {
				data.color = GSON.fromJson(item.get("color"), CustomItems.ColorSpec.class);
			}
			if (item.has("skin") && item.get("skin").isJsonObject()) {
				data.skin = GSON.fromJson(item.get("skin"), CustomItems.SkinSpec.class);
			}
			if (data.color != null || data.skin != null) {
				out.add(new Entry(string(item, "key"), string(item, "id"), string(item, "item"), data));
			}
		}
		return List.copyOf(out);
	}

	/** The server set a piece of another player's equipment: tag it when they shared a look for it. */
	public static void tag(Player player, EquipmentSlot slot, ItemStack stack) {
		Shared shared = PLAYERS.get(player.getUUID());
		if (shared == null || stack == null || stack.isEmpty()) {
			return;
		}
		int index = match(shared.entries, stack);
		if (index >= 0) {
			CustomItems.tagShared(stack, "s:" + player.getUUID() + ":" + index);
		}
	}

	/** Which shared entry is this item: the same item UUID, else the same SkyBlock id, else the same Minecraft item. */
	private static int match(List<Entry> entries, ItemStack stack) {
		if (entries.isEmpty()) {
			return -1;
		}
		String key = CustomItems.keyOf(stack);
		if (key != null && !key.startsWith("s:")) {
			for (int i = 0; i < entries.size(); i++) {
				if (key.equals(entries.get(i).key())) {
					return i;
				}
			}
		}
		String id = ItemIds.skyblockId(stack);
		String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		int byItem = -1;
		for (int i = entries.size() - 1; i >= 0; i--) {
			Entry entry = entries.get(i);
			if (id != null && id.equals(entry.id())) {
				return i;
			}
			// The server sent no SkyBlock id (other players' armor may come without one): the newest
			// piece of the same Minecraft item.
			if (byItem < 0 && id == null && item.equals(entry.item())) {
				byItem = i;
			}
		}
		return byItem;
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

	/** The shared look under a tag key: "s:<player uuid>:<entry index>". */
	static CustomItems.Data data(String key) {
		int split = key.lastIndexOf(':');
		if (split < 3) {
			return null;
		}
		try {
			Shared shared = PLAYERS.get(UUID.fromString(key.substring(2, split)));
			int index = Integer.parseInt(key.substring(split + 1));
			List<Entry> entries = shared == null ? List.of() : shared.entries;
			return index >= 0 && index < entries.size() ? entries.get(index).data() : null;
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private static String string(JsonObject json, String key) {
		JsonElement value = json.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}

	// ------------------------------------------------------------------ yours

	/**
	 * Once a second: your customized pieces. When they change (a new dye or skin, or sharing
	 * turned on or off) and stay the same for 3 seconds, they are sent. Changing armor sends
	 * nothing: every piece is already shared.
	 */
	public static void tick(Minecraft client) {
		long now = System.currentTimeMillis();
		if (now < nextCheck || client.player == null) {
			return;
		}
		nextCheck = now + 1_000L;
		String want = StrayConfig.get().customizeShare ? mine() : "";
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

	/** Your customized pieces with a dye or head skin, as the JSON the server takes ("" for none). */
	private static String mine() {
		JsonArray items = new JsonArray();
		for (Map.Entry<String, CustomItems.Data> entry : CustomItems.shareable(MAX_ITEMS)) {
			CustomItems.Data data = entry.getValue();
			JsonObject item = new JsonObject();
			if (!CustomItems.sharedKey(entry.getKey())) {
				// "i:" keys apply to every copy of an id; the id alone says that.
				item.addProperty("key", entry.getKey());
			}
			if (data.sbId != null) {
				item.addProperty("id", data.sbId);
			}
			item.addProperty("item", data.item);
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
