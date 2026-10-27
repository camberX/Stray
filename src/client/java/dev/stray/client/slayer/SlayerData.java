package dev.stray.client.slayer;

import dev.stray.client.util.Text;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stray.Stray;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Static slayer data: mobs and XP per area, mini-bosses, wisdom items, spawn costs (from the
 * SkyHanni-REPO, MIT, bundled), the items each boss drops, and the RNG meter scores (from the
 * NEU repo, downloaded once and cached).
 */
public final class SlayerData {
	public record Mob(String name, int level, long maxHealth, double xp) {
	}

	private static boolean loaded;
	private static final Map<String, Map<String, List<Mob>>> NORMAL = new HashMap<>();
	private static final Map<String, Map<String, List<Mob>>> MINI = new HashMap<>();
	private static final Map<String, Map<String, Integer>> WEAPONS = new HashMap<>();
	private static final Map<String, Map<String, Integer>> EQUIPMENT = new HashMap<>();
	private static final Map<String, Map<String, Long>> SPAWN_COSTS = new HashMap<>();
	private static final Map<String, Set<String>> DROPS = new HashMap<>();
	private static final Map<String, List<Double>> PET_SCALING = new HashMap<>();
	private static final Map<String, String> PET_TYPE = new HashMap<>();
	private static final Map<String, Double> MAYOR = new HashMap<>();
	private static final List<Double> CHAMPION = new ArrayList<>();
	private static double habanero = 2.5;
	private static double arbitrary = 1.0;

	/** RNG meter: slayer name -> item id -> score needed. */
	private static volatile Map<String, Map<String, Long>> rngScores = Map.of();
	private static boolean rngRequested;

	private SlayerData() {
	}

	public static synchronized void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		try {
			JsonObject slayer = read("/assets/stray/slayer/Slayer.json");
			if (slayer != null) {
				mobs(slayer.getAsJsonObject("normal_mobs"), NORMAL);
				mobs(slayer.getAsJsonObject("mini_bosses"), MINI);
				ints(slayer.getAsJsonObject("weapons"), WEAPONS);
				ints(slayer.getAsJsonObject("equipments"), EQUIPMENT);
				JsonObject costs = slayer.getAsJsonObject("spawn_costs");
				for (String type : costs.keySet()) {
					Map<String, Long> tiers = new HashMap<>();
					JsonObject t = costs.getAsJsonObject(type);
					for (String tier : t.keySet()) {
						tiers.put(tier, t.get(tier).getAsLong());
					}
					SPAWN_COSTS.put(type, tiers);
				}
				JsonObject pets = slayer.getAsJsonObject("pets");
				for (String type : pets.keySet()) {
					JsonObject byPet = pets.getAsJsonObject(type);
					for (String pet : byPet.keySet()) {
						if (!byPet.get(pet).isJsonObject()) {
							continue;
						}
						JsonObject petData = byPet.getAsJsonObject(pet);
						if (!petData.has("scaling") || !petData.get("scaling").isJsonArray()) {
							// The repo nests some entries one level deeper; skip what does not fit.
							continue;
						}
						List<Double> scaling = new ArrayList<>();
						for (JsonElement e : petData.getAsJsonArray("scaling")) {
							scaling.add(e.getAsDouble());
						}
						if (petData.has("proper_pet_names")) {
							for (JsonElement name : petData.getAsJsonArray("proper_pet_names")) {
								PET_SCALING.put(type + "|" + name.getAsString(), scaling);
								PET_TYPE.put(name.getAsString(), type);
							}
						}
					}
				}
				for (JsonElement e : slayer.getAsJsonArray("champion")) {
					CHAMPION.add(e.getAsDouble());
				}
				JsonObject mayors = slayer.getAsJsonObject("multiplicative_mayor_perks");
				for (String perk : mayors.keySet()) {
					MAYOR.put(perk, mayors.get(perk).getAsDouble());
				}
				habanero = slayer.get("habanero_wisdom_per_level").getAsDouble();
				arbitrary = slayer.get("arbitrary_multiplier").getAsDouble();
			}
		} catch (RuntimeException e) {
			Stray.LOGGER.warn("Slayer data could not be read", e);
		}
		try {
			JsonObject items = read("/assets/stray/slayer/SlayerProfitTrackerItems.json");
			if (items != null) {
				JsonObject slayers = items.getAsJsonObject("slayers");
				for (String name : slayers.keySet()) {
					Set<String> ids = new HashSet<>();
					for (JsonElement e : slayers.getAsJsonArray(name)) {
						ids.add(e.getAsString().toUpperCase(Locale.ROOT));
					}
					DROPS.put(name, ids);
				}
			}
		} catch (RuntimeException e) {
			Stray.LOGGER.warn("Slayer data could not be read", e);
		}
	}

	private static JsonObject read(String path) {
		try (InputStream in = SlayerData.class.getResourceAsStream(path)) {
			if (in == null) {
				return null;
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			Stray.LOGGER.warn("Could not read {}", path, e);
			return null;
		}
	}

	private static void mobs(JsonObject root, Map<String, Map<String, List<Mob>>> out) {
		if (root == null) {
			return;
		}
		for (String type : root.keySet()) {
			Map<String, List<Mob>> byKey = new HashMap<>();
			JsonObject areas = root.getAsJsonObject(type);
			for (String key : areas.keySet()) {
				List<Mob> list = new ArrayList<>();
				for (JsonElement e : areas.getAsJsonArray(key)) {
					JsonObject m = e.getAsJsonObject();
					list.add(new Mob(
						Text.strip(m.get("name").getAsString()),
						m.get("level").getAsInt(),
						m.get("max_health").getAsLong(),
						m.get("xp").getAsDouble()
					));
				}
				byKey.put(key, list);
			}
			out.put(type, byKey);
		}
	}

	private static void ints(JsonObject root, Map<String, Map<String, Integer>> out) {
		if (root == null) {
			return;
		}
		for (String type : root.keySet()) {
			Map<String, Integer> map = new HashMap<>();
			JsonObject o = root.getAsJsonObject(type);
			for (String key : o.keySet()) {
				map.put(key.toUpperCase(Locale.ROOT), o.get(key).getAsInt());
			}
			out.put(type, map);
		}
	}

	public static List<Mob> normalMobs(SlayerType type, String area) {
		load();
		Map<String, List<Mob>> byArea = NORMAL.get(type.dataKey);
		if (byArea == null) {
			return List.of();
		}
		List<Mob> here = byArea.get(area);
		return here != null ? here : byArea.getOrDefault("no_area", List.of());
	}

	public static List<Mob> miniBosses(SlayerType type, int tier) {
		load();
		Map<String, List<Mob>> byTier = MINI.get(type.dataKey);
		return byTier == null ? List.of() : byTier.getOrDefault(String.valueOf(tier), List.of());
	}

	public static int weaponWisdom(SlayerType type, String itemId) {
		load();
		Map<String, Integer> map = WEAPONS.get(type.dataKey);
		return map == null || itemId == null ? -1 : map.getOrDefault(itemId, -1);
	}

	public static int equipmentWisdom(SlayerType type, String itemId) {
		load();
		Map<String, Integer> map = EQUIPMENT.get(type.dataKey);
		return map == null || itemId == null ? 0 : map.getOrDefault(itemId, 0);
	}

	public static long spawnCost(SlayerType type, int tier) {
		load();
		Map<String, Long> map = SPAWN_COSTS.get(type.dataKey);
		return map == null ? 0L : map.getOrDefault(String.valueOf(tier), 0L);
	}

	/** Whether an item counts as a drop of this boss for the profit tracker. */
	public static boolean isDrop(SlayerType type, String id) {
		load();
		Set<String> set = DROPS.get(type.displayName);
		if (set == null && type == SlayerType.VAMPIRE) {
			set = DROPS.get("Riftstalker Bloodfiend");
		}
		return set != null && id != null && set.contains(id.toUpperCase(Locale.ROOT));
	}

	public static double petScaling(SlayerType type, String petType, int rarityIndex) {
		load();
		List<Double> scaling = PET_SCALING.get(type.dataKey + "|" + petType);
		if (scaling == null || rarityIndex < 0 || rarityIndex >= scaling.size()) {
			return 0d;
		}
		return scaling.get(rarityIndex);
	}

	public static double champion(int level) {
		load();
		return level <= 0 || level > CHAMPION.size() ? 0d : CHAMPION.get(level - 1);
	}

	public static double mayorMultiplier(String perk) {
		load();
		return MAYOR.getOrDefault(perk, 1.0);
	}

	public static Set<String> mayorPerks() {
		load();
		return MAYOR.keySet();
	}

	public static double habaneroPerLevel() {
		load();
		return habanero;
	}

	public static double arbitraryMultiplier() {
		load();
		return arbitrary;
	}

	// ---------------------------------------------------------------- RNG meter scores (NEU repo)

	private static final String RNG_URL = "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/rngscore.json";

	/** Score needed for an RNG meter item, or -1 when unknown (yet). */
	public static long rngScore(String slayerName, String itemId) {
		requestRng();
		Map<String, Long> bySlayer = rngScores.get(slayerName);
		if (bySlayer == null || itemId == null) {
			return -1L;
		}
		return bySlayer.getOrDefault(itemId.toUpperCase(Locale.ROOT), -1L);
	}

	private static synchronized void requestRng() {
		if (rngRequested) {
			return;
		}
		rngRequested = true;
		Path cache = FabricLoader.getInstance().getConfigDir().resolve("stray/rngscore.json");
		CompletableFuture.runAsync(() -> {
			String body = null;
			try {
				HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10)).build();
				HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(RNG_URL)).timeout(Duration.ofSeconds(20)).build(), HttpResponse.BodyHandlers.ofString());
				if (res.statusCode() == 200) {
					body = res.body();
					Files.createDirectories(cache.getParent());
					Files.writeString(cache, body);
				}
			} catch (Exception e) {
				Stray.LOGGER.warn("Slayer: RNG meter scores could not be downloaded", e);
			}
			try {
				if (body == null && Files.isRegularFile(cache)) {
					body = Files.readString(cache);
				}
				if (body != null) {
					JsonObject root = JsonParser.parseString(body).getAsJsonObject();
					JsonObject slayer = root.getAsJsonObject("slayer");
					Map<String, Map<String, Long>> out = new HashMap<>();
					if (slayer != null) {
						for (String name : slayer.keySet()) {
							Map<String, Long> items = new HashMap<>();
							JsonObject o = slayer.getAsJsonObject(name);
							for (String id : o.keySet()) {
								items.put(id.toUpperCase(Locale.ROOT), o.get(id).getAsLong());
							}
							out.put(name, items);
						}
					}
					rngScores = out;
				}
			} catch (Exception e) {
				Stray.LOGGER.warn("Slayer: RNG meter scores could not be read", e);
			}
		});
	}
}
