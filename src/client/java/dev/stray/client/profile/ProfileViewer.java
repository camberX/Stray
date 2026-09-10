package dev.stray.client.profile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.stray.Stray;
import dev.stray.client.item.SkyblockItems;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

/**
 * Loads Hypixel Skyblock profiles for {@code /pv}. Data comes from the same
 * public profile host Stray already uses for storage counts.
 */
public final class ProfileViewer {
	private static final String PROFILE = "https://hypixel.odtheking.com/get/";
	private static final String MOJANG = "https://api.mojang.com/users/profiles/minecraft/";
	private static final String ASHCON = "https://api.ashcon.app/mojang/v2/user/";
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL)
		.connectTimeout(Duration.ofSeconds(10))
		.build();

	private static final int[] SKILL_XP = {
		0, 50, 175, 375, 675, 1175, 1925, 2925, 4425, 6425,
		9925, 14925, 22425, 32425, 47425, 67425, 97425, 147425, 222425, 322425,
		472425, 672425, 972425, 1472425, 2222425, 3222425, 5222425, 8222425, 12222425, 17222425,
		23222425, 30222425, 38222425, 47222425, 57222425, 68222425, 80222425, 93222425, 107222425, 122222425,
		138222425, 155222425, 173222425, 192222425, 212222425, 233222425, 255222425, 278222425, 302222425, 327222425,
		353222425, 380722425, 409222425, 438722425, 469222425, 500722425, 533222425, 566722425, 601222425, 636722425,
		673222425
	};
	private static final int[] CATA_XP = {
		0, 50, 125, 235, 395, 625, 955, 1425, 2095, 3045,
		4385, 6275, 8940, 12700, 17960, 25340, 35640, 50040, 70040, 97640,
		135640, 188140, 259640, 356640, 488640, 668640, 911640, 1239640, 1684640, 2284640,
		3084640, 4149640, 5559640, 7459640, 9959640, 13259640, 17559640, 23159640, 30359640, 39559640,
		51559640, 66559640, 85559640, 109559640, 139559640, 177559640, 225559640, 285559640, 360559640, 453559640,
		569809640
	};
	private static final int[] HOTM_XP = {
		0, 3000, 9000, 25000, 60000, 100000, 150000, 210000, 290000, 400000
	};
	private static final int[] SLAYER_ZOMBIE = {5, 15, 200, 1000, 5000, 20000, 100000, 400000, 1000000};
	private static final int[] SLAYER_SPIDER = {5, 25, 200, 1000, 5000, 20000, 100000, 400000, 1000000};
	private static final int[] SLAYER_WOLF = {10, 30, 250, 1500, 5000, 20000, 100000, 400000, 1000000};
	private static final int[] SLAYER_ENDER = {10, 30, 250, 1500, 5000, 20000, 100000, 400000, 1000000};
	private static final int[] SLAYER_BLAZE = {10, 30, 250, 1500, 5000, 20000, 100000, 400000, 1000000};
	private static final int[] SLAYER_VAMP = {20, 75, 240, 840, 2400};
	private static final long[] CROP_MILESTONE = {
		0L,
		30L, 50L, 100L, 250L, 500L, 1000L, 2500L, 5000L, 10000L, 25000L,
		50000L, 100000L, 250000L, 500000L, 1000000L, 2500000L, 5000000L, 10000000L, 15000000L, 20000000L,
		25000000L, 30000000L, 35000000L, 40000000L, 50000000L, 100000000L, 125000000L, 160000000L, 200000000L, 250000000L,
		325000000L, 400000000L, 490000000L, 590000000L, 700000000L, 850000000L, 1000000000L, 1150000000L, 1300000000L, 1500000000L,
		1700000000L, 1950000000L, 2200000000L, 2500000000L
	};

	public enum Status {
		IDLE, LOADING, READY, ERROR
	}

	public record Skill(String name, int level, int cap, double xp, float progress) {
	}

	public record Slayer(String name, int level, double xp) {
	}

	public record Dungeon(
		int cata,
		float progress,
		int secrets,
		List<Skill> classes,
		String selectedClass,
		int runs,
		List<Floor> normal,
		List<Floor> master
	) {
		public static Dungeon empty() {
			return new Dungeon(0, 0f, 0, List.of(), "", 0, List.of(), List.of());
		}
	}

	public record Floor(String name, int completions, int bestS, int bestSPlus) {
	}

	public record Perk(String id, String name, int level) {
	}

	public record Mining(int hotm, long mithril, long gemstone, long glacite, List<Perk> perks) {
		public static Mining empty() {
			return new Mining(0, 0L, 0L, 0L, List.of());
		}

		public int perk(String... ids) {
			if (perks == null || ids == null) {
				return 0;
			}
			for (Perk perk : perks) {
				for (String id : ids) {
					if (id != null && perk.id().equalsIgnoreCase(id)) {
						return perk.level();
					}
				}
			}
			return 0;
		}
	}

	public record Crop(String name, long amount, int level, int cap, float progress) {
	}

	public record Farming(int visitors, int garden, List<Crop> crops) {
		public static Farming empty() {
			return new Farming(0, 0, List.of());
		}
	}

	public record Pet(String name, String type, String tier, int level, boolean active) {
	}

	public record SlotItem(int slot, String id, String name, int count) {
		public static SlotItem empty(int slot) {
			return new SlotItem(slot, "", "", 0);
		}

		public boolean empty() {
			return (id == null || id.isBlank()) && (name == null || name.isBlank());
		}
	}

	public record Bag(String name, int columns, List<SlotItem> slots) {
		public int size() {
			return slots == null ? 0 : slots.size();
		}

		public int rows() {
			int cols = Math.max(1, columns);
			return Math.max(1, (int) Math.ceil(size() / (double) cols));
		}

		public SlotItem at(int index) {
			if (slots == null || index < 0 || index >= slots.size()) {
				return SlotItem.empty(index);
			}
			SlotItem item = slots.get(index);
			return item == null ? SlotItem.empty(index) : item;
		}

		public static Bag empty(String name, int columns, int size) {
			List<SlotItem> slots = new ArrayList<>();
			for (int i = 0; i < Math.max(0, size); i++) {
				slots.add(SlotItem.empty(i));
			}
			return new Bag(name, columns, List.copyOf(slots));
		}

		public boolean vacant() {
			if (slots == null) {
				return true;
			}
			for (SlotItem item : slots) {
				if (item != null && !item.empty()) {
					return false;
				}
			}
			return true;
		}
	}

	public record Collection(String name, long amount) {
	}

	public record Profile(
		String id,
		String cuteName,
		String gameMode,
		boolean selected,
		double purse,
		double bank,
		double itemWorth,
		double networth,
		int skyblockLevel,
		float skyblockProgress,
		float skillAverage,
		int fairySouls,
		int secrets,
		long firstJoin,
		boolean cookie,
		long kills,
		long deaths,
		List<Skill> skills,
		List<Slayer> slayers,
		Dungeon dungeons,
		Mining mining,
		Farming farming,
		List<Pet> pets,
		Bag inventory,
		Bag armor,
		List<Bag> ender,
		List<Bag> backpacks,
		List<Collection> collections
	) {
		public static Profile empty(String cuteName) {
			return new Profile(
				"",
				cuteName,
				"normal",
				false,
				0,
				0,
				0,
				0,
				0,
				0f,
				0f,
				0,
				0,
				0L,
				false,
				0L,
				0L,
				List.of(),
				List.of(),
				Dungeon.empty(),
				Mining.empty(),
				Farming.empty(),
				List.of(),
				Bag.empty("Inventory", 9, 36),
				Bag.empty("Armor", 1, 4),
				List.of(),
				List.of(),
				List.of()
			);
		}
	}

	public record Snapshot(String name, UUID uuid, List<Profile> profiles, int selected, String error) {
		public static Snapshot empty() {
			return new Snapshot("", null, List.of(), 0, "");
		}

		public Profile current() {
			if (profiles.isEmpty()) {
				return Profile.empty("");
			}
			int index = Math.max(0, Math.min(selected, profiles.size() - 1));
			return profiles.get(index);
		}

		public Snapshot withSelected(int index) {
			if (profiles.isEmpty()) {
				return this;
			}
			return new Snapshot(name, uuid, profiles, Math.max(0, Math.min(index, profiles.size() - 1)), error);
		}
	}

	private static final AtomicInteger GEN = new AtomicInteger();
	private static volatile Status status = Status.IDLE;
	private static volatile String error = "";
	private static volatile String query = "";
	private static volatile Snapshot snapshot = Snapshot.empty();

	private ProfileViewer() {
	}

	public static Status status() {
		return status;
	}

	public static String error() {
		return error == null ? "" : error;
	}

	public static String query() {
		return query == null ? "" : query;
	}

	public static Snapshot snapshot() {
		return snapshot == null ? Snapshot.empty() : snapshot;
	}

	public static void select(int index) {
		Snapshot current = snapshot;
		if (current == null || current.profiles.isEmpty()) {
			return;
		}
		snapshot = current.withSelected(index);
	}

	public static void openSelf() {
		Minecraft client = Minecraft.getInstance();
		String name = "";
		if (client.player != null) {
			name = client.player.getGameProfile().name();
		} else if (client.getUser() != null) {
			name = client.getUser().getName();
		}
		load(name);
	}

	public static void load(String raw) {
		String name = raw == null ? "" : raw.trim();
		Minecraft client = Minecraft.getInstance();
		if (name.isBlank()) {
			openSelf();
			return;
		}
		query = name;
		status = Status.LOADING;
		error = "";
		int gen = GEN.incrementAndGet();
		Util.nonCriticalIoPool().execute(() -> fetch(name, client, gen));
	}

	private static void fetch(String name, Minecraft client, int gen) {
		try {
			Resolved resolved = resolve(name, client);
			if (stale(gen)) {
				return;
			}
			if (resolved == null) {
				fail(gen, "unknown player");
				return;
			}
			HttpRequest request = HttpRequest.newBuilder(URI.create(PROFILE + compact(resolved.uuid())))
				.timeout(Duration.ofSeconds(20))
				.header("User-Agent", "Stray/" + Stray.MOD_ID)
				.GET()
				.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (stale(gen)) {
				return;
			}
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				fail(gen, "HTTP " + response.statusCode());
				return;
			}
			ensurePrices();
			JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
			if (root.has("success") && root.get("success").isJsonPrimitive() && !root.get("success").getAsBoolean()) {
				fail(gen, "profile lookup failed");
				return;
			}
			List<Profile> profiles = parseProfiles(root, resolved.uuid());
			if (profiles.isEmpty()) {
				fail(gen, "no Skyblock profile");
				return;
			}
			int selected = 0;
			for (int i = 0; i < profiles.size(); i++) {
				if (profiles.get(i).selected()) {
					selected = i;
					break;
				}
			}
			snapshot = new Snapshot(resolved.name(), resolved.uuid(), List.copyOf(profiles), selected, "");
			error = "";
			status = Status.READY;
			query = resolved.name();
		} catch (Exception exception) {
			if (!stale(gen)) {
				fail(gen, exception.getMessage() == null ? "lookup failed" : exception.getMessage());
				Stray.LOGGER.warn("Profile viewer lookup failed", exception);
			}
		}
	}

	private static Resolved resolve(String name, Minecraft client) throws Exception {
		UUID local = uuidOf(client);
		String localName = localName(client);
		if (local != null && (name.isBlank() || name.equalsIgnoreCase(localName))) {
			return new Resolved(localName.isBlank() ? name : localName, local);
		}
		JsonObject mojang = getJson(MOJANG + encode(name));
		if (mojang != null) {
			UUID uuid = uuidOf(string(mojang, "id"));
			String named = string(mojang, "name");
			if (uuid != null) {
				return new Resolved(named.isBlank() ? name : named, uuid);
			}
		}
		JsonObject ashcon = getJson(ASHCON + encode(name));
		if (ashcon != null) {
			UUID uuid = uuidOf(string(ashcon, "uuid"));
			String named = string(ashcon, "username");
			if (uuid != null) {
				return new Resolved(named.isBlank() ? name : named, uuid);
			}
		}
		return null;
	}

	private static JsonObject getJson(String url) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(10))
				.header("User-Agent", "Stray/" + Stray.MOD_ID)
				.GET()
				.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				return null;
			}
			JsonElement element = JsonParser.parseString(response.body());
			return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
		} catch (Exception ignored) {
			return null;
		}
	}

	private static List<Profile> parseProfiles(JsonObject root, UUID uuid) {
		List<Profile> out = new ArrayList<>();
		if (root == null || !root.has("profiles") || !root.get("profiles").isJsonArray()) {
			return out;
		}
		String compact = compact(uuid);
		for (JsonElement element : root.getAsJsonArray("profiles")) {
			if (element == null || !element.isJsonObject()) {
				continue;
			}
			JsonObject object = element.getAsJsonObject();
			JsonObject member = memberIn(object, compact);
			if (member == null) {
				continue;
			}
			out.add(parseMember(object, member));
		}
		return out;
	}

	private static Profile parseMember(JsonObject profile, JsonObject member) {
		JsonObject playerData = object(member, "player_data");
		JsonObject experience = object(playerData, "experience");
		List<Skill> skills = new ArrayList<>();
		skills.add(skill("Farming", skillXp(experience, member, "SKILL_FARMING", "experience_skill_farming"), 60));
		skills.add(skill("Mining", skillXp(experience, member, "SKILL_MINING", "experience_skill_mining"), 60));
		skills.add(skill("Combat", skillXp(experience, member, "SKILL_COMBAT", "experience_skill_combat"), 60));
		skills.add(skill("Foraging", skillXp(experience, member, "SKILL_FORAGING", "experience_skill_foraging"), 60));
		skills.add(skill("Fishing", skillXp(experience, member, "SKILL_FISHING", "experience_skill_fishing"), 50));
		skills.add(skill("Enchanting", skillXp(experience, member, "SKILL_ENCHANTING", "experience_skill_enchanting"), 60));
		skills.add(skill("Alchemy", skillXp(experience, member, "SKILL_ALCHEMY", "experience_skill_alchemy"), 50));
		skills.add(skill("Taming", skillXp(experience, member, "SKILL_TAMING", "experience_skill_taming"), 60));
		skills.add(skill("Carpentry", skillXp(experience, member, "SKILL_CARPENTRY", "experience_skill_carpentry"), 50));
		skills.add(skill("Runecrafting", skillXp(experience, member, "SKILL_RUNECRAFTING", "experience_skill_runecrafting"), 25));
		skills.add(skill("Social", skillXp(experience, member, "SKILL_SOCIAL", "experience_skill_social2"), 25));
		float average = 0f;
		int counted = 0;
		for (Skill skill : skills) {
			if (skill.cap() >= 50 && !"Runecrafting".equals(skill.name()) && !"Social".equals(skill.name())) {
				average += skill.level();
				counted++;
			}
		}
		if (counted > 0) {
			average /= counted;
		}

		JsonObject currencies = object(member, "currencies");
		double purse = num(currencies, "coin_purse");
		if (purse == 0d) {
			purse = num(member, "coin_purse");
		}
		double bank = num(object(profile, "banking"), "balance");

		JsonObject leveling = object(member, "leveling");
		double sbXp = num(leveling, "experience");
		int sbLevel = (int) Math.floor(sbXp / 100d);
		float sbProgress = (float) ((sbXp % 100d) / 100d);

		int souls = (int) num(object(member, "fairy_soul"), "total_collected");
		if (souls == 0) {
			souls = (int) num(member, "fairy_souls_collected");
		}

		JsonObject profileMember = object(member, "profile");
		long firstJoin = (long) num(profileMember, "first_join");
		if (firstJoin == 0L) {
			firstJoin = (long) num(member, "first_join");
		}
		if (firstJoin == 0L) {
			firstJoin = (long) num(profile, "created_at");
		}
		boolean cookie = bool(currencies, "cookie_buff_active") || bool(profileMember, "cookie_buff_active");
		if (!cookie) {
			long expiry = (long) num(profileMember, "cookie_buff_expiry");
			if (expiry == 0L) {
				expiry = (long) num(currencies, "cookie_buff_expiry");
			}
			if (expiry > 0L && expiry < 10_000_000_000L) {
				expiry *= 1000L;
			}
			cookie = expiry > System.currentTimeMillis();
		}
		JsonObject stats = object(member, "player_stats");
		if (stats == null) {
			stats = object(member, "stats");
		}
		long kills = statCount(stats, "kills");
		long deaths = statCount(stats, "deaths");

		Dungeon dungeons = parseDungeons(member);
		List<Slayer> slayers = parseSlayers(member);
		Mining mining = parseMining(member);
		Farming farming = parseFarming(member);
		List<Pet> pets = parsePets(member);
		JsonObject inventory = object(member, "inventory");
		Bag inv = parseBag("Inventory", first(inventory, member, "inv_contents"), 9, 36);
		Bag armor = parseBag("Armor", first(inventory, member, "inv_armor"), 1, 4);
		List<Bag> ender = parseEnder(inventory, member);
		List<Bag> backpacks = parseBackpacks(inventory, member);
		List<Collection> collections = parseCollections(member);

		String cute = string(profile, "cute_name");
		if (cute.isBlank()) {
			cute = string(profile, "profile_id");
		}
		String mode = string(profile, "game_mode");
		if (mode.isBlank()) {
			mode = "normal";
		}
		double items = bagWorth(inv) + bagWorth(armor);
		for (Bag bag : ender) {
			items += bagWorth(bag);
		}
		for (Bag bag : backpacks) {
			items += bagWorth(bag);
		}
		for (Pet pet : pets) {
			items += priceOf(pet.type() + ";" + petTier(pet.tier()));
		}
		return new Profile(
			string(profile, "profile_id"),
			cute,
			mode,
			bool(profile, "selected"),
			purse,
			bank,
			items,
			purse + bank + items,
			sbLevel,
			sbProgress,
			average,
			souls,
			dungeons.secrets(),
			firstJoin,
			cookie,
			kills,
			deaths,
			List.copyOf(skills),
			List.copyOf(slayers),
			dungeons,
			mining,
			farming,
			List.copyOf(pets),
			inv,
			armor,
			List.copyOf(ender),
			List.copyOf(backpacks),
			List.copyOf(collections)
		);
	}

	private static Dungeon parseDungeons(JsonObject member) {
		JsonObject dungeons = object(member, "dungeons");
		JsonObject types = object(dungeons, "dungeon_types");
		JsonObject cata = object(types, "catacombs");
		JsonObject master = object(types, "master_catacombs");
		double xp = num(cata, "experience");
		Skill level = skillFrom("Catacombs", xp, CATA_XP, 50);
		int secrets = (int) num(dungeons, "secrets");
		if (secrets == 0) {
			secrets = (int) num(object(object(member, "player_stats"), "dungeons"), "secrets_found");
		}
		if (secrets == 0) {
			secrets = (int) num(object(member, "player_stats"), "secrets_found");
		}
		JsonObject classes = object(dungeons, "player_classes");
		List<Skill> out = new ArrayList<>();
		out.add(classSkill(classes, "healer", "Healer"));
		out.add(classSkill(classes, "mage", "Mage"));
		out.add(classSkill(classes, "berserk", "Berserk"));
		out.add(classSkill(classes, "archer", "Archer"));
		out.add(classSkill(classes, "tank", "Tank"));
		String selected = pretty(string(dungeons, "selected_dungeon_class"));
		List<Floor> normal = parseFloors(cata, false);
		List<Floor> masterFloors = parseFloors(master, true);
		int runs = sumFloors(normal) + sumFloors(masterFloors);
		if (runs == 0) {
			runs = sumKeyed(object(cata, "times_played")) + sumKeyed(object(master, "times_played"));
		}
		return new Dungeon(level.level(), level.progress(), secrets, List.copyOf(out), selected, runs, List.copyOf(normal), List.copyOf(masterFloors));
	}

	private static List<Floor> parseFloors(JsonObject type, boolean master) {
		List<Floor> out = new ArrayList<>();
		JsonObject completions = object(type, "tier_completions");
		if (completions == null) {
			completions = object(type, "milestone_completions");
		}
		JsonObject bestS = object(type, "fastest_time_s");
		JsonObject bestSPlus = object(type, "fastest_time_s_plus");
		int start = master ? 1 : 0;
		for (int i = start; i <= 7; i++) {
			String name = master ? "M" + i : (i == 0 ? "E" : "F" + i);
			out.add(new Floor(name, keyedInt(completions, i), keyedInt(bestS, i), keyedInt(bestSPlus, i)));
		}
		return out;
	}

	private static int sumFloors(List<Floor> floors) {
		int total = 0;
		for (Floor floor : floors) {
			total += floor.completions();
		}
		return total;
	}

	private static int sumKeyed(JsonObject object) {
		if (object == null) {
			return 0;
		}
		int total = 0;
		for (String key : object.keySet()) {
			total += (int) num(object, key);
		}
		return total;
	}

	private static int keyedInt(JsonObject object, int key) {
		if (object == null) {
			return 0;
		}
		String raw = String.valueOf(key);
		if (object.has(raw)) {
			return (int) num(object, raw);
		}
		return (int) num(object, raw + ".0");
	}

	private static Skill classSkill(JsonObject classes, String key, String name) {
		return skillFrom(name, num(object(classes, key), "experience"), CATA_XP, 50);
	}

	private static List<Slayer> parseSlayers(JsonObject member) {
		JsonObject slayer = object(member, "slayer");
		JsonObject bosses = object(slayer, "slayer_bosses");
		if (bosses == null) {
			bosses = object(member, "slayer_bosses");
		}
		List<Slayer> out = new ArrayList<>();
		out.add(slayer("Zombie", num(object(bosses, "zombie"), "xp"), SLAYER_ZOMBIE));
		out.add(slayer("Spider", num(object(bosses, "spider"), "xp"), SLAYER_SPIDER));
		out.add(slayer("Wolf", num(object(bosses, "wolf"), "xp"), SLAYER_WOLF));
		out.add(slayer("Enderman", num(object(bosses, "enderman"), "xp"), SLAYER_ENDER));
		out.add(slayer("Blaze", num(object(bosses, "blaze"), "xp"), SLAYER_BLAZE));
		out.add(slayer("Vampire", num(object(bosses, "vampire"), "xp"), SLAYER_VAMP));
		return out;
	}

	private static Mining parseMining(JsonObject member) {
		JsonObject core = object(member, "mining_core");
		int hotm = skillFrom("HOTM", num(core, "experience"), HOTM_XP, 10).level();
		long mithril = (long) (num(core, "powder_mithril") + num(core, "powder_spent_mithril"));
		if (mithril == 0L) {
			mithril = (long) num(core, "powder_mithril_total");
		}
		long gemstone = (long) (num(core, "powder_gemstone") + num(core, "powder_spent_gemstone"));
		if (gemstone == 0L) {
			gemstone = (long) num(core, "powder_gemstone_total");
		}
		long glacite = (long) (num(core, "powder_glacite") + num(core, "powder_spent_glacite"));
		if (glacite == 0L) {
			glacite = (long) num(core, "powder_glacite_total");
		}
		return new Mining(hotm, mithril, gemstone, glacite, parsePerks(object(core, "nodes")));
	}

	private static List<Perk> parsePerks(JsonObject nodes) {
		List<Perk> out = new ArrayList<>();
		if (nodes == null) {
			return out;
		}
		for (String key : nodes.keySet()) {
			if (key == null || key.startsWith("toggle") || key.endsWith("_toggle")) {
				continue;
			}
			int level = nodeLevel(nodes, key);
			if (level < 0) {
				continue;
			}
			out.add(new Perk(key, pretty(key), level));
		}
		return List.copyOf(out);
	}

	private static int nodeLevel(JsonObject nodes, String key) {
		if (nodes == null || key == null || !nodes.has(key)) {
			return 0;
		}
		JsonElement value = nodes.get(key);
		if (value == null || value.isJsonNull()) {
			return 0;
		}
		if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
			return value.getAsBoolean() ? 1 : 0;
		}
		return (int) num(nodes, key);
	}

	private static Farming parseFarming(JsonObject member) {
		JsonObject garden = object(member, "garden_player_data");
		if (garden == null) {
			garden = object(member, "garden");
		}
		int visitors = (int) num(object(garden, "commission_data"), "unique_npcs_served");
		if (visitors == 0) {
			visitors = (int) num(garden, "unique_visitors");
		}
		int level = skillFrom("Garden", num(garden, "garden_experience"), SKILL_XP, 15).level();
		JsonObject resources = object(garden, "resources_collected");
		if (resources == null) {
			resources = object(object(member, "garden"), "resources_collected");
		}
		JsonObject collection = object(member, "collection");
		List<Crop> crops = new ArrayList<>();
		crops.add(crop("Wheat", cropAmount(resources, collection, "wheat", "WHEAT")));
		crops.add(crop("Carrot", cropAmount(resources, collection, "carrot", "CARROT_ITEM", "CARROT")));
		crops.add(crop("Potato", cropAmount(resources, collection, "potato", "POTATO_ITEM", "POTATO")));
		crops.add(crop("Pumpkin", cropAmount(resources, collection, "pumpkin", "PUMPKIN")));
		crops.add(crop("Melon", cropAmount(resources, collection, "melon_slice", "melon", "MELON")));
		crops.add(crop("Mushroom", cropAmount(resources, collection, "mushroom", "MUSHROOM_COLLECTION", "RED_MUSHROOM")));
		crops.add(crop("Cocoa", cropAmount(resources, collection, "cocoa_beans", "INK_SACK:3", "COCOA")));
		crops.add(crop("Cactus", cropAmount(resources, collection, "cactus", "CACTUS")));
		crops.add(crop("Cane", cropAmount(resources, collection, "sugar_cane", "SUGAR_CANE")));
		crops.add(crop("Wart", cropAmount(resources, collection, "nether_wart", "NETHER_STALK", "NETHER_WART")));
		return new Farming(visitors, level, List.copyOf(crops));
	}

	private static long cropAmount(JsonObject resources, JsonObject collection, String gardenKey, String... collectionKeys) {
		double amount = num(resources, gardenKey);
		if (amount == 0d && gardenKey != null) {
			amount = num(resources, gardenKey.toUpperCase(Locale.ROOT));
		}
		if (amount == 0d && collection != null) {
			for (String key : collectionKeys) {
				amount = num(collection, key);
				if (amount > 0d) {
					break;
				}
			}
		}
		return (long) amount;
	}

	private static Crop crop(String name, long amount) {
		int level = 0;
		for (int i = 1; i < CROP_MILESTONE.length; i++) {
			if (amount >= CROP_MILESTONE[i]) {
				level = i;
			} else {
				break;
			}
		}
		int cap = CROP_MILESTONE.length - 1;
		float progress = 1f;
		if (level < cap) {
			double from = CROP_MILESTONE[level];
			double to = CROP_MILESTONE[level + 1];
			progress = to <= from ? 1f : (float) Math.max(0d, Math.min(1d, (amount - from) / (to - from)));
		}
		return new Crop(name, amount, level, cap, progress);
	}

	private static List<Pet> parsePets(JsonObject member) {
		List<Pet> out = new ArrayList<>();
		JsonArray pets = array(object(member, "pets_data"), "pets");
		if (pets == null) {
			pets = array(member, "pets");
		}
		if (pets == null) {
			return out;
		}
		for (JsonElement element : pets) {
			if (element == null || !element.isJsonObject()) {
				continue;
			}
			JsonObject pet = element.getAsJsonObject();
			String type = string(pet, "type");
			if (type.isBlank()) {
				continue;
			}
			String tier = pretty(string(pet, "tier"));
			int level = petLevel(num(pet, "exp"), tier);
			out.add(new Pet(pretty(type), type, tier, level, bool(pet, "active")));
		}
		out.sort(Comparator.comparing((Pet pet) -> !pet.active()).thenComparing(Pet::name));
		return out;
	}

	private static List<Collection> parseCollections(JsonObject member) {
		List<Collection> out = new ArrayList<>();
		JsonObject collection = object(member, "collection");
		if (collection == null) {
			return out;
		}
		for (Map.Entry<String, JsonElement> entry : collection.entrySet()) {
			if (entry.getValue() == null || !entry.getValue().isJsonPrimitive()) {
				continue;
			}
			long amount = Math.max(0L, (long) num(collection, entry.getKey()));
			if (amount <= 0L) {
				continue;
			}
			out.add(new Collection(itemName(entry.getKey()), amount));
		}
		out.sort(Comparator.comparingLong(Collection::amount).reversed());
		if (out.size() > 48) {
			return new ArrayList<>(out.subList(0, 48));
		}
		return out;
	}

	private static JsonElement first(JsonObject primary, JsonObject fallback, String key) {
		if (primary != null && primary.has(key)) {
			return primary.get(key);
		}
		if (fallback != null && fallback.has(key)) {
			return fallback.get(key);
		}
		if (primary != null && primary.has(key + "_data")) {
			return primary.get(key + "_data");
		}
		if (fallback != null && fallback.has(key + "_data")) {
			return fallback.get(key + "_data");
		}
		return null;
	}

	private static List<Bag> parseEnder(JsonObject inventory, JsonObject member) {
		JsonElement element = first(inventory, member, "ender_chest_contents");
		if (element == null) {
			element = first(inventory, member, "enderchest_contents");
		}
		if (element == null) {
			element = named(inventory, member, "ender_chest", "enderchest");
		}
		Bag all = parseBag("Ender Chest", element, 9, 45);
		if (all.vacant()) {
			return List.of();
		}
		return paginate(all, 45, "Ender");
	}

	private static List<Bag> parseBackpacks(JsonObject inventory, JsonObject member) {
		List<Bag> out = new ArrayList<>();
		JsonObject bags = object(inventory, "backpack_contents");
		if (bags == null) {
			bags = object(member, "backpack_contents");
		}
		if (bags == null) {
			bags = object(inventory, "backpacks");
		}
		if (bags == null) {
			bags = object(member, "backpacks");
		}
		if (bags != null) {
			List<String> keys = new ArrayList<>(bags.keySet());
			keys.sort((a, b) -> Integer.compare(indexKey(a), indexKey(b)));
			int n = 1;
			for (String key : keys) {
				if (key != null && key.toLowerCase(Locale.ROOT).contains("icon")) {
					continue;
				}
				Bag bag = parseBag("Backpack " + n, bags.get(key), 9, 45);
				if (!bag.vacant()) {
					out.add(bag);
					n++;
				}
			}
		}
		if (out.isEmpty()) {
			for (JsonObject parent : new JsonObject[]{inventory, member}) {
				if (parent == null) {
					continue;
				}
				for (String key : parent.keySet()) {
					if (!backpackKey(key)) {
						continue;
					}
					Bag bag = parseBag("Backpack " + (out.size() + 1), parent.get(key), 9, 45);
					if (!bag.vacant()) {
						out.add(bag);
					}
				}
			}
		}
		return out;
	}

	private static JsonElement named(JsonObject primary, JsonObject fallback, String... needles) {
		for (JsonObject parent : new JsonObject[]{primary, fallback}) {
			if (parent == null) {
				continue;
			}
			for (String key : parent.keySet()) {
				String lower = key.toLowerCase(Locale.ROOT);
				for (String needle : needles) {
					if (lower.contains(needle) && !lower.contains("icon")) {
						return parent.get(key);
					}
				}
			}
		}
		return null;
	}

	private static boolean backpackKey(String key) {
		if (key == null) {
			return false;
		}
		String lower = key.toLowerCase(Locale.ROOT);
		if (lower.contains("icon")) {
			return false;
		}
		return lower.contains("backpack_contents")
			|| lower.contains("backpack_content")
			|| (lower.contains("backpack") && lower.contains("content"));
	}

	private static int indexKey(String key) {
		if (key == null) {
			return Integer.MAX_VALUE;
		}
		try {
			return Integer.parseInt(key.replaceAll("[^0-9]", "").isBlank() ? "999" : key.replaceAll("[^0-9]", ""));
		} catch (NumberFormatException ignored) {
			return Integer.MAX_VALUE;
		}
	}

	private static List<Bag> paginate(Bag bag, int pageSize, String label) {
		int size = bag.size();
		if (size <= pageSize) {
			return List.of(new Bag(label, bag.columns(), bag.slots()));
		}
		List<Bag> pages = new ArrayList<>();
		int pagesN = (int) Math.ceil(size / (double) pageSize);
		for (int p = 0; p < pagesN; p++) {
			List<SlotItem> slice = new ArrayList<>();
			for (int i = 0; i < pageSize; i++) {
				SlotItem item = bag.at(p * pageSize + i);
				slice.add(new SlotItem(i, item.id(), item.name(), item.count()));
			}
			pages.add(new Bag(label + " " + (p + 1), bag.columns(), List.copyOf(slice)));
		}
		return pages;
	}

	private static Bag parseBag(String name, JsonElement element, int columns, int fallbackSize) {
		if (element == null || element.isJsonNull()) {
			return Bag.empty(name, columns, fallbackSize);
		}
		CompoundTag tag = readNbt(data(element));
		if (tag == null && element.isJsonObject()) {
			tag = readNbt(data(element.getAsJsonObject().get("data")));
		}
		List<SlotItem> raw = parseSlotList(tag);
		int size = paddedSize(raw, fallbackSize, columns);
		return new Bag(name, columns, padSlots(raw, size));
	}

	private static List<SlotItem> parseSlotList(CompoundTag root) {
		List<SlotItem> out = new ArrayList<>();
		if (root == null) {
			return out;
		}
		Tag items = root.get("i");
		if (items == null) {
			items = root.get("Items");
		}
		if (items == null) {
			items = root.get("items");
		}
		if (items == null && looksLikeItem(root)) {
			SlotItem item = slotItem(root, 0);
			if (!item.empty()) {
				out.add(item);
			}
			return out;
		}
		if (!(items instanceof ListTag list)) {
			for (String key : root.keySet()) {
				if ("i".equals(key) || "Items".equals(key) || "items".equals(key) || "inventory".equals(key)) {
					return parseSlotList(root.get(key) instanceof CompoundTag child ? child : root);
				}
			}
			return out;
		}
		for (int i = 0; i < list.size() && i < 512; i++) {
			Tag child = list.get(i);
			if (!(child instanceof CompoundTag compound) || compound.isEmpty()) {
				continue;
			}
			SlotItem item = slotItem(compound, i);
			if (!item.empty()) {
				out.add(item);
			}
		}
		return out;
	}

	private static SlotItem slotItem(CompoundTag compound, int index) {
		if (isAir(compound)) {
			return SlotItem.empty(index);
		}
		int slot = index;
		if (compound.contains("Slot")) {
			slot = (int) num(compound, "Slot");
		} else if (compound.contains("slot")) {
			slot = (int) num(compound, "slot");
		}
		String id = itemId(compound);
		String name = itemLabel(compound);
		if ((id == null || id.isBlank()) && (name == null || name.isBlank())) {
			return SlotItem.empty(slot);
		}
		int count = Math.max(1, (int) num(compound, "Count"));
		if (count == 1) {
			count = Math.max(1, (int) num(compound, "count"));
		}
		return new SlotItem(slot, id == null ? "" : id, name, count);
	}

	private static boolean isAir(CompoundTag tag) {
		String id = tag.getStringOr("id", "");
		return id.equalsIgnoreCase("minecraft:air") || id.equalsIgnoreCase("air");
	}

	private static String itemId(CompoundTag tag) {
		CompoundTag extra = tag.getCompoundOrEmpty("tag").getCompoundOrEmpty("ExtraAttributes");
		if (extra.isEmpty()) {
			extra = tag.getCompoundOrEmpty("ExtraAttributes");
		}
		if (extra.isEmpty()) {
			extra = tag.getCompoundOrEmpty("components").getCompoundOrEmpty("minecraft:custom_data");
		}
		String id = extra.getStringOr("id", "");
		if (!id.isBlank()) {
			return id;
		}
		return tag.getStringOr("id", "");
	}

	private static int paddedSize(List<SlotItem> raw, int fallback, int columns) {
		int max = -1;
		for (SlotItem item : raw) {
			if (item != null && item.slot() > max) {
				max = item.slot();
			}
		}
		int size = max >= 0 ? max + 1 : 0;
		if (size < fallback) {
			size = fallback;
		}
		int cols = Math.max(1, columns);
		if (size % cols != 0) {
			size += cols - (size % cols);
		}
		return Math.min(512, size);
	}

	private static List<SlotItem> padSlots(List<SlotItem> raw, int size) {
		SlotItem[] slots = new SlotItem[size];
		for (int i = 0; i < size; i++) {
			slots[i] = SlotItem.empty(i);
		}
		for (SlotItem item : raw) {
			if (item == null || item.slot() < 0 || item.slot() >= size) {
				continue;
			}
			slots[item.slot()] = item;
		}
		return List.of(slots);
	}

	private static boolean looksLikeItem(CompoundTag tag) {
		return tag.contains("id")
			|| tag.contains("Count")
			|| tag.contains("count")
			|| tag.contains("tag")
			|| tag.contains("components");
	}

	private static String itemLabel(CompoundTag tag) {
		CompoundTag extra = tag.getCompoundOrEmpty("tag").getCompoundOrEmpty("ExtraAttributes");
		if (extra.isEmpty()) {
			extra = tag.getCompoundOrEmpty("ExtraAttributes");
		}
		if (extra.isEmpty()) {
			CompoundTag components = tag.getCompoundOrEmpty("components");
			extra = components.getCompoundOrEmpty("minecraft:custom_data");
		}
		String id = extra.getStringOr("id", "");
		if (!id.isBlank()) {
			return itemName(id);
		}
		String vanilla = tag.getStringOr("id", "");
		return vanilla.isBlank() ? "" : pretty(vanilla.replace("minecraft:", ""));
	}

	private static String itemName(String id) {
		SkyblockItems.Entry entry = SkyblockItems.get(id);
		if (entry != null && entry.name() != null && !entry.name().isBlank()) {
			return entry.name();
		}
		return pretty(id);
	}

	private static Skill skill(String name, double xp, int cap) {
		return skillFrom(name, xp, SKILL_XP, cap);
	}

	private static Skill skillFrom(String name, double xp, int[] table, int cap) {
		int level = 0;
		for (int i = 1; i < table.length && i <= cap; i++) {
			if (xp >= table[i]) {
				level = i;
			} else {
				break;
			}
		}
		level = Math.min(level, cap);
		float progress = 1f;
		if (level < cap && level + 1 < table.length) {
			double from = table[level];
			double to = table[level + 1];
			progress = to <= from ? 1f : (float) Math.max(0d, Math.min(1d, (xp - from) / (to - from)));
		}
		return new Skill(name, level, cap, xp, progress);
	}

	private static Slayer slayer(String name, double xp, int[] table) {
		int level = 0;
		double need = 0d;
		for (int step : table) {
			need += step;
			if (xp >= need) {
				level++;
			} else {
				break;
			}
		}
		return new Slayer(name, level, xp);
	}

	private static int petLevel(double xp, String tier) {
		int[] table = switch (tier == null ? "" : tier.toLowerCase(Locale.ROOT)) {
			case "common" -> new int[]{0, 100, 210, 330, 460, 605, 765, 940, 1130, 1340, 1570, 1820, 2095, 2395, 2725, 3085, 3485, 3925, 4415, 4955, 5555, 6215, 6945, 7745, 8625, 9595, 10655, 11815, 13085, 14475, 15995, 17655, 19465, 21435, 23575, 25895, 28405, 31115, 34035, 37175, 40545, 44155, 48015, 52135, 56525, 61195, 66155, 71415, 76985, 82875, 89095, 95655, 102565, 109835, 117475, 125495, 133905, 142715, 151935, 161575, 171645, 182155, 193115, 204535, 216425, 228795, 241655, 255015, 268885, 283275, 298195, 313655, 329665, 346235, 363375, 381095, 399405, 418315, 437835, 457975, 478745, 500155, 522215, 544935, 568325, 592395, 617155, 642615, 668785, 695675, 723295, 751655, 780765, 810635, 841275, 872695, 904905, 937915, 971735, 1006375};
			case "uncommon" -> new int[]{0, 175, 365, 575, 805, 1055, 1330, 1630, 1960, 2320, 2715, 3145, 3615, 4130, 4690, 5300, 5965, 6685, 7465, 8310, 9225, 10215, 11285, 12440, 13685, 15025, 16465, 18010, 19665, 21435, 23325, 25340, 27485, 29765, 32185, 34750, 37465, 40335, 43365, 46560, 49925, 53465, 57185, 61090, 65185, 69475, 73965, 78660, 83565, 88685, 94025, 99590, 105385, 111415, 117685, 124200, 130965, 137985, 145265, 152810, 160625, 168715, 177085, 185740, 194685, 203925, 213465, 223310, 233465, 243935, 254725, 265840, 277285, 289065, 301185, 313650, 326465, 339635, 353165, 367060, 381325, 395965, 410985, 426390, 442185, 458375, 474965, 491960, 509365, 527185, 545425, 564090, 583185, 602715, 622685, 643100, 663965, 685285, 707065, 729310};
			default -> new int[]{0, 660, 1390, 2190, 3070, 4040, 5110, 6290, 7590, 9020, 10590, 12310, 14190, 16240, 18470, 20890, 23510, 26340, 29390, 32670, 36190, 39960, 43990, 48290, 52870, 57740, 62910, 68390, 74190, 80320, 86790, 93610, 100790, 108340, 116270, 124590, 133310, 142440, 151990, 161970, 172390, 183260, 194590, 206390, 218670, 231440, 244710, 258490, 272790, 287620, 302990, 318910, 335390, 352440, 370070, 388290, 407110, 426540, 446590, 467270, 488590, 510560, 533190, 556490, 580470, 605140, 630510, 656590, 683390, 710920, 739190, 768210, 797990, 828540, 859870, 891990, 924910, 958640, 993190, 1028570, 1064690, 1101550, 1139160, 1177530, 1216670, 1256590, 1297300, 1338810, 1381130, 1424270, 1468240, 1513050, 1558710, 1605230, 1652620, 1700890, 1750050, 1800110, 1851080, 1902970};
		};
		int level = 1;
		for (int i = 1; i < table.length && i < 100; i++) {
			if (xp >= table[i]) {
				level = i + 1;
			} else {
				break;
			}
		}
		return Math.min(100, level);
	}

	private static double skillXp(JsonObject experience, JsonObject member, String modern, String legacy) {
		double xp = num(experience, modern);
		if (xp == 0d) {
			xp = num(member, legacy);
		}
		return xp;
	}

	private static JsonObject memberIn(JsonObject profile, String compact) {
		JsonObject members = object(profile, "members");
		if (members == null) {
			return null;
		}
		JsonObject direct = object(members, compact);
		if (direct != null) {
			return direct;
		}
		for (Map.Entry<String, JsonElement> entry : members.entrySet()) {
			if (compact(entry.getKey()).equals(compact) && entry.getValue() != null && entry.getValue().isJsonObject()) {
				return entry.getValue().getAsJsonObject();
			}
		}
		return null;
	}

	private static CompoundTag readNbt(String data) {
		if (data == null || data.isBlank()) {
			return null;
		}
		String trimmed = data.trim();
		if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
			try {
				return TagParser.parseCompoundFully(trimmed);
			} catch (Exception ignored) {
			}
		}
		byte[] raw = decodeBase64(trimmed);
		if (raw == null || raw.length == 0) {
			return null;
		}
		try (ByteArrayInputStream in = new ByteArrayInputStream(raw)) {
			return NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
		} catch (Exception ignored) {
			try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(raw));
				 DataInputStream input = new DataInputStream(gzip)) {
				return NbtIo.read(input, NbtAccounter.unlimitedHeap());
			} catch (Exception ignoredGzip) {
				try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(raw))) {
					return NbtIo.read(input, NbtAccounter.unlimitedHeap());
				} catch (Exception ignoredRaw) {
					return null;
				}
			}
		}
	}

	private static byte[] decodeBase64(String data) {
		String cleaned = data.replaceAll("\\s+", "");
		try {
			return Base64.getDecoder().decode(cleaned);
		} catch (IllegalArgumentException ignored) {
			try {
				return Base64.getUrlDecoder().decode(cleaned);
			} catch (IllegalArgumentException ignoredUrl) {
				return null;
			}
		}
	}

	private static String data(JsonElement element) {
		if (element == null || element.isJsonNull()) {
			return "";
		}
		if (element.isJsonPrimitive()) {
			JsonPrimitive primitive = element.getAsJsonPrimitive();
			return primitive.isString() ? primitive.getAsString() : "";
		}
		if (!element.isJsonObject()) {
			return "";
		}
		JsonObject object = element.getAsJsonObject();
		for (String key : new String[]{"data", "value", "contents", "nbt", "bytes", "item_bytes"}) {
			JsonElement value = object.get(key);
			if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
				String text = value.getAsString();
				if (!text.isBlank()) {
					return text;
				}
			}
		}
		return "";
	}

	private static JsonObject object(JsonObject parent, String key) {
		if (parent == null || key == null || !parent.has(key)) {
			return null;
		}
		JsonElement value = parent.get(key);
		return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
	}

	private static JsonArray array(JsonObject parent, String key) {
		if (parent == null || key == null || !parent.has(key)) {
			return null;
		}
		JsonElement value = parent.get(key);
		return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
	}

	private static String string(JsonObject object, String key) {
		if (object == null || key == null || !object.has(key)) {
			return "";
		}
		JsonElement value = object.get(key);
		if (value == null || !value.isJsonPrimitive()) {
			return "";
		}
		return value.getAsString();
	}

	private static double num(JsonObject object, String key) {
		if (object == null || key == null || !object.has(key)) {
			return 0d;
		}
		JsonElement value = object.get(key);
		if (value == null || !value.isJsonPrimitive()) {
			return 0d;
		}
		JsonPrimitive primitive = value.getAsJsonPrimitive();
		if (primitive.isNumber()) {
			return primitive.getAsDouble();
		}
		if (primitive.isString()) {
			try {
				return Double.parseDouble(primitive.getAsString());
			} catch (NumberFormatException ignored) {
				return 0d;
			}
		}
		return 0d;
	}

	private static double num(CompoundTag tag, String key) {
		if (tag == null || key == null || !tag.contains(key)) {
			return 0d;
		}
		try {
			return tag.getDoubleOr(key, tag.getIntOr(key, 0));
		} catch (Exception ignored) {
			return 0d;
		}
	}

	private static volatile Map<String, Double> PRICES = Map.of();
	private static volatile long pricesAt;

	private static void ensurePrices() {
		if (!PRICES.isEmpty() && System.currentTimeMillis() - pricesAt < 30 * 60 * 1000L) {
			return;
		}
		Map<String, Double> next = new HashMap<>(PRICES);
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.hypixel.net/v2/skyblock/bazaar"))
				.timeout(Duration.ofSeconds(12))
				.header("User-Agent", "Stray/" + Stray.MOD_ID)
				.GET()
				.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() >= 200 && response.statusCode() < 300) {
				JsonObject products = object(JsonParser.parseString(response.body()).getAsJsonObject(), "products");
				if (products != null) {
					for (String id : products.keySet()) {
						JsonObject quick = object(object(products, id), "quick_status");
						double sell = num(quick, "sellPrice");
						double buy = num(quick, "buyPrice");
						double value = sell > 0 ? sell : buy;
						if (value > 0d) {
							next.put(id.toUpperCase(Locale.ROOT), value);
						}
					}
				}
			}
		} catch (Exception ignored) {
		}
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create("https://moulberry.codes/lowestbin.json"))
				.timeout(Duration.ofSeconds(12))
				.header("User-Agent", "Stray/" + Stray.MOD_ID)
				.GET()
				.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() >= 200 && response.statusCode() < 300) {
				JsonObject bins = JsonParser.parseString(response.body()).getAsJsonObject();
				for (String id : bins.keySet()) {
					double value = num(bins, id);
					if (value <= 0d) {
						continue;
					}
					String key = id.toUpperCase(Locale.ROOT);
					Double current = next.get(key);
					if (current == null || value > current) {
						next.put(key, value);
					}
				}
			}
		} catch (Exception ignored) {
		}
		if (!next.isEmpty()) {
			PRICES = Map.copyOf(next);
			pricesAt = System.currentTimeMillis();
		}
	}

	private static double bagWorth(Bag bag) {
		if (bag == null || bag.slots() == null) {
			return 0d;
		}
		double total = 0d;
		for (SlotItem item : bag.slots()) {
			if (item == null || item.empty()) {
				continue;
			}
			total += priceOf(item.id()) * Math.max(1, item.count());
		}
		return total;
	}

	private static double priceOf(String id) {
		if (id == null || id.isBlank() || PRICES.isEmpty()) {
			return 0d;
		}
		Double value = PRICES.get(id.trim().toUpperCase(Locale.ROOT));
		return value == null ? 0d : value;
	}

	private static int petTier(String tier) {
		return switch (tier == null ? "" : tier.toLowerCase(Locale.ROOT)) {
			case "uncommon" -> 1;
			case "rare" -> 2;
			case "epic" -> 3;
			case "legendary" -> 4;
			case "mythic" -> 5;
			default -> 0;
		};
	}

	private static long statCount(JsonObject stats, String key) {
		if (stats == null || key == null || !stats.has(key)) {
			return 0L;
		}
		JsonElement value = stats.get(key);
		if (value == null || value.isJsonNull()) {
			return 0L;
		}
		if (value.isJsonPrimitive()) {
			return (long) num(stats, key);
		}
		if (!value.isJsonObject()) {
			return 0L;
		}
		JsonObject object = value.getAsJsonObject();
		double total = num(object, "total");
		if (total > 0d) {
			return (long) total;
		}
		double sum = 0d;
		for (String name : object.keySet()) {
			JsonElement child = object.get(name);
			if (child != null && child.isJsonPrimitive()) {
				sum += num(object, name);
			}
		}
		return (long) sum;
	}

	private static boolean bool(JsonObject object, String key) {
		if (object == null || !object.has(key)) {
			return false;
		}
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() && value.getAsBoolean();
	}

	private static String pretty(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		String cleaned = raw.replace("minecraft:", "").replace('_', ' ').trim();
		StringBuilder out = new StringBuilder(cleaned.length());
		boolean cap = true;
		for (int i = 0; i < cleaned.length(); i++) {
			char ch = cleaned.charAt(i);
			if (ch == ' ') {
				out.append(ch);
				cap = true;
				continue;
			}
			out.append(cap ? Character.toUpperCase(ch) : Character.toLowerCase(ch));
			cap = false;
		}
		return out.toString();
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static String compact(UUID uuid) {
		return uuid == null ? "" : uuid.toString().replace("-", "").toLowerCase(Locale.ROOT);
	}

	private static String compact(String value) {
		return value == null ? "" : value.replace("-", "").toLowerCase(Locale.ROOT);
	}

	private static UUID uuidOf(String raw) {
		String compact = compact(raw);
		if (compact.length() != 32) {
			return null;
		}
		try {
			return UUID.fromString(
				compact.substring(0, 8) + "-"
					+ compact.substring(8, 12) + "-"
					+ compact.substring(12, 16) + "-"
					+ compact.substring(16, 20) + "-"
					+ compact.substring(20)
			);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private static UUID uuidOf(Minecraft client) {
		if (client == null) {
			return null;
		}
		Player player = client.player;
		if (player != null) {
			return player.getUUID();
		}
		if (client.getUser() != null) {
			return client.getUser().getProfileId();
		}
		return null;
	}

	private static String localName(Minecraft client) {
		if (client == null) {
			return "";
		}
		if (client.player != null) {
			return client.player.getGameProfile().name();
		}
		if (client.getUser() != null) {
			return client.getUser().getName();
		}
		return "";
	}

	private static boolean stale(int gen) {
		return gen != GEN.get();
	}

	private static void fail(int gen, String message) {
		if (stale(gen)) {
			return;
		}
		error = message == null || message.isBlank() ? "lookup failed" : message;
		status = Status.ERROR;
	}

	private record Resolved(String name, UUID uuid) {
	}
}
