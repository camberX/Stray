package dev.stray.client.fairy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stray.Stray;
import dev.stray.client.location.SkyblockLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Wiki fairy-soul coordinates ({@code Fairy Souls/List}) plus the skull texture hash.
 */
public final class FairySouls {
	public static final String TEXTURE =
		"b96923ad247310007f6ae5d326d847ad53864cf16c3565a181dc8e6b20be2387";

	public record Soul(String island, String id, int x, int y, int z) {
		public BlockPos pos() {
			return new BlockPos(x, y, z);
		}

		public Vec3 center() {
			return new Vec3(x + 0.5, y + 0.5, z + 0.5);
		}

		public String key() {
			return island.toLowerCase(Locale.ROOT) + ":" + x + ":" + y + ":" + z;
		}
	}

	private static final Map<String, List<Soul>> BY_ISLAND = new HashMap<>();
	private static String texture = TEXTURE;
	private static boolean loaded;

	private FairySouls() {
	}

	public static String texture() {
		ensureLoaded();
		return texture;
	}

	public static List<Soul> forArea(String area) {
		ensureLoaded();
		String island = islandOf(area);
		if (island.isEmpty()) {
			return List.of();
		}
		return BY_ISLAND.getOrDefault(island.toLowerCase(Locale.ROOT), List.of());
	}

	public static List<Soul> current() {
		ensureLoaded();
		String island = islandOf(SkyblockLocation.area);
		if (island.isEmpty()) {
			island = islandOf(SkyblockLocation.poi);
		}
		if (island.isEmpty()) {
			return List.of();
		}
		return BY_ISLAND.getOrDefault(island.toLowerCase(Locale.ROOT), List.of());
	}

	public static String currentIsland() {
		String island = islandOf(SkyblockLocation.area);
		if (island.isEmpty()) {
			island = islandOf(SkyblockLocation.poi);
		}
		return island;
	}

	public static String islandOf(String area) {
		if (area == null || area.isBlank()) {
			return "";
		}
		String a = area.toLowerCase(Locale.ROOT).replace("'", "");
		if (a.contains("dungeon hub")) {
			return "dungeon hub";
		}
		if (a.contains("rift")) {
			return "rift dimension";
		}
		if (a.contains("jerry") || a.contains("winter")) {
			return "jerry's workshop";
		}
		if (a.contains("lotus")) {
			return "lotus atoll";
		}
		if (a.contains("bayou")) {
			return "backwater bayou";
		}
		if (a.contains("safari") || a.contains("critter")) {
			return "critter safari";
		}
		if (a.contains("torrhus")) {
			return "torrhus canyon";
		}
		if (a.contains("moonglade")) {
			return "moonglade marsh";
		}
		if (a.contains("park") || a.contains("galatea")) {
			return "the park";
		}
		if (a.equals("the end") || a.contains("end island") || a.contains("dragons nest") || a.contains("zealot")
			|| a.contains("void sepulture")) {
			return "the end";
		}
		if (a.contains("crimson")) {
			return "crimson isle";
		}
		if (a.contains("spider")) {
			return "spider's den";
		}
		if (a.contains("dwarven") || a.contains("glacite")) {
			return "dwarven mines";
		}
		if (a.contains("deep cavern")) {
			return "deep caverns";
		}
		if (a.contains("gold mine")) {
			return "gold mine";
		}
		if (a.contains("farming") || a.equals("the barn") || a.equals("barn") || a.contains("mushroom desert")) {
			return "the farming islands";
		}
		if (hubPlace(a)) {
			return "hub";
		}
		return "";
	}

	private static final String[] HUB_PLACES = {
		"hub", "village", "wilderness", "mountain", "graveyard", "ruins", "colosseum",
		"wizard tower", "forest", "farm", "coal mine", "mining district", "fishing outpost",
		"fishermans hut", "foraging camp", "thaumaturgist", "fashion shop", "rabbit house",
		"pet care", "abiphone", "shen", "catacombs entrance", "dark auction", "auction house",
		"community center", "bazaar", "library", "tavern", "museum", "hexatorum", "cannon",
		"builders house", "weaponsmith", "high level", "unincorporated", "carnival",
		"election", "city project", "bank", "flower house", "blacksmith"
	};

	private static boolean hubPlace(String a) {
		if (a.equals("hub") || a.startsWith("hub ") || a.endsWith(" hub") || a.contains(" hub ")) {
			return true;
		}
		for (String place : HUB_PLACES) {
			if (a.equals(place) || a.contains(place)) {
				return true;
			}
		}
		return false;
	}

	private static void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;
		try (InputStream raw = FairySouls.class.getResourceAsStream("/assets/stray/fairy_souls.json")) {
			if (raw == null) {
				Stray.LOGGER.warn("Missing fairy_souls.json");
				return;
			}
			try (Reader reader = new InputStreamReader(raw, StandardCharsets.UTF_8)) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				if (json.has("texture")) {
					String value = json.get("texture").getAsString().trim();
					if (!value.isEmpty()) {
						texture = value;
					}
				}
				JsonArray array = json.getAsJsonArray("souls");
				if (array == null) {
					return;
				}
				for (JsonElement element : array) {
					if (!element.isJsonObject()) {
						continue;
					}
					JsonObject row = element.getAsJsonObject();
					String island = row.get("island").getAsString();
					Soul soul = new Soul(
						island,
						row.get("id").getAsString(),
						row.get("x").getAsInt(),
						row.get("y").getAsInt(),
						row.get("z").getAsInt()
					);
					BY_ISLAND.computeIfAbsent(island.toLowerCase(Locale.ROOT), key -> new ArrayList<>()).add(soul);
				}
			}
		} catch (Exception exception) {
			Stray.LOGGER.warn("Failed to load fairy souls", exception);
		}
	}
}
