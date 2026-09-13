package dev.stray.client.profile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.stray.Stray;
import dev.stray.client.item.SkyblockItems;
import dev.stray.client.item.SkyblockLore;
import dev.stray.client.item.SkyblockPetLore;
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

	private static final long[] SKILL_XP = {
		0L, 50L, 175L, 375L, 675L, 1175L, 1925L, 2925L, 4425L, 6425L,
		9925L, 14925L, 22425L, 32425L, 47425L, 67425L, 97425L, 147425L, 222425L, 322425L,
		522425L, 822425L, 1222425L, 1722425L, 2322425L, 3022425L, 3822425L, 4722425L, 5722425L, 6822425L,
		8022425L, 9322425L, 10722425L, 12222425L, 13822425L, 15522425L, 17322425L, 19222425L, 21222425L, 23322425L,
		25522425L, 27822425L, 30222425L, 32722425L, 35322425L, 38072425L, 40972425L, 44072425L, 47472425L, 51172425L,
		55172425L, 59472425L, 64072425L, 68972425L, 74172425L, 79672425L, 85472425L, 91572425L, 97972425L, 104672425L,
		111672425L
	};
	private static final long[] RUNE_XP = {
		0L, 50L, 150L, 275L, 435L, 635L, 885L, 1200L, 1600L, 2100L,
		2725L, 3510L, 4510L, 5760L, 7360L, 9360L, 11825L, 14950L, 18950L, 23950L,
		30150L, 37950L, 47750L, 59950L, 75250L, 94300L
	};
	private static final long[] SOCIAL_XP = {
		0L, 50L, 150L, 300L, 550L, 1050L, 1800L, 2800L, 4050L, 5550L,
		7550L, 10050L, 13050L, 16800L, 21300L, 27300L, 35300L, 45300L, 57800L, 72800L,
		92800L, 117800L, 147800L, 182800L, 222800L, 272800L
	};
	private static final long[] CATA_XP = {
		0L, 50L, 125L, 235L, 395L, 625L, 955L, 1425L, 2095L, 3045L,
		4385L, 6275L, 8940L, 12700L, 17960L, 25340L, 35640L, 50040L, 70040L, 97640L,
		135640L, 188140L, 259640L, 356640L, 488640L, 668640L, 911640L, 1239640L, 1684640L, 2284640L,
		3084640L, 4149640L, 5559640L, 7459640L, 9959640L, 13259640L, 17559640L, 23159640L, 30359640L, 39559640L,
		51559640L, 66559640L, 85559640L, 109559640L, 139559640L, 177559640L, 225559640L, 285559640L, 360559640L, 453559640L,
		569809640L
	};
	private static final long CATA_OVERFLOW = 200_000_000L;
	private static final long[] HOTM_XP = {
		0L, 0L, 3000L, 12000L, 37000L, 97000L, 197000L, 347000L, 557000L, 847000L, 1247000L
	};
	private static final Map<String, Integer> HOTM_TIER = hotmTiers();
	private static final int[] SLAYER_ZOMBIE = {5, 15, 200, 1000, 5000, 20000, 100000, 400000, 1000000};
	private static final int[] SLAYER_SPIDER = {5, 25, 200, 1000, 5000, 20000, 100000, 400000, 1000000};
	private static final int[] SLAYER_WOLF = {10, 30, 250, 1500, 5000, 20000, 100000, 400000, 1000000};
	private static final int[] SLAYER_ENDER = {10, 30, 250, 1500, 5000, 20000, 100000, 400000, 1000000};
	private static final int[] SLAYER_BLAZE = {10, 30, 250, 1500, 5000, 20000, 100000, 400000, 1000000};
	private static final int[] SLAYER_VAMP = {20, 75, 240, 840, 2400};
	private static final long[] GARDEN_XP = cropCum(
		70, 70, 140, 240, 600, 1500, 2000, 2500, 3000, 10000, 10000, 10000, 10000, 10000
	);
	/** Cumulative harvests to reach each Garden crop milestone. Wiki tables, 46 tiers. */
	private static final long[] CROP_WHEAT = cropCum(
		30, 50, 80, 200, 350, 700, 1500, 2500, 3500, 5000,
		6500, 8000, 10000, 20000, 35000, 50000, 75000, 100000, 175000, 250000,
		325000, 400000, 500000, 650000, 800000, 800000, 800000, 800000, 800000, 800000,
		800000, 800000, 800000, 800000, 800000, 800000, 800000, 800000, 800000, 800000,
		800000, 800000, 800000, 800000, 800000, 800000
	);
	private static final long[] CROP_CARROT = cropCum(
		100, 150, 250, 500, 1000, 2000, 4500, 9000, 12000, 15000,
		20000, 25000, 35000, 70000, 120000, 180000, 250000, 350000, 600000, 850000,
		1100000, 1400000, 1800000, 2200000, 2600000, 2600000, 2600000, 2600000, 2600000, 2600000,
		2600000, 2600000, 2600000, 2600000, 2600000, 2600000, 2600000, 2600000, 2600000, 2600000,
		2600000, 2600000, 2600000, 2600000, 2600000, 2600000
	);
	private static final long[] CROP_MELON = cropCum(
		150, 250, 400, 1000, 1800, 3500, 7500, 12500, 17500, 25000,
		32500, 40000, 50000, 100000, 175000, 250000, 375000, 500000, 875000, 1200000,
		1600000, 2000000, 2500000, 3200000, 4000000, 4000000, 4000000, 4000000, 4000000, 4000000,
		4000000, 4000000, 4000000, 4000000, 4000000, 4000000, 4000000, 4000000, 4000000, 4000000,
		4000000, 4000000, 4000000, 4000000, 4000000, 4000000
	);
	private static final long[] CROP_CANE = cropCum(
		60, 100, 160, 400, 700, 1400, 3000, 5000, 7000, 10000,
		13000, 16000, 20000, 40000, 70000, 100000, 150000, 200000, 350000, 500000,
		650000, 800000, 1000000, 1300000, 1600000, 1600000, 1600000, 1600000, 1600000, 1600000,
		1600000, 1600000, 1600000, 1600000, 1600000, 1600000, 1600000, 1600000, 1600000, 1600000,
		1600000, 1600000, 1600000, 1600000, 1600000, 1600000
	);
	private static final long[] CROP_WART = cropCum(
		90, 150, 250, 500, 1000, 2000, 4000, 7500, 10000, 15000,
		20000, 25000, 30000, 50000, 100000, 150000, 200000, 300000, 500000, 750000,
		1000000, 1300000, 1600000, 2000000, 2400000, 2400000, 2400000, 2400000, 2400000, 2400000,
		2400000, 2400000, 2400000, 2400000, 2400000, 2400000, 2400000, 2400000, 2400000, 2400000,
		2400000, 2400000, 2400000, 2400000, 2400000, 2400000
	);
	private static final String ELITE_GARDEN = "https://api.elitebot.dev/garden/";

	public enum Status {
		IDLE, LOADING, READY, ERROR
	}

	private enum Overflow {
		NONE, SKILL, DUNGEON
	}

	public static int perkTier(String id) {
		Integer tier = HOTM_TIER.get(normPerk(id));
		return tier == null ? 1 : tier;
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
				String have = normPerk(perk.id());
				for (String id : ids) {
					if (samePerk(have, id)) {
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

	public record Pet(String name, String type, String tier, int level, boolean active, String held, int candy) {
		public Pet(String name, String type, String tier, int level, boolean active) {
			this(name, type, tier, level, active, "", 0);
		}
	}

	public record SlotItem(int slot, String id, String name, int count, List<String> lore, String valueId) {
		public SlotItem(int slot, String id, String name, int count) {
			this(slot, id, name, count, List.of(), id == null ? "" : id);
		}

		public static SlotItem empty(int slot) {
			return new SlotItem(slot, "", "", 0, List.of(), "");
		}

		public boolean empty() {
			return (id == null || id.isBlank()) && (name == null || name.isBlank());
		}

		public String priceId() {
			return valueId == null || valueId.isBlank() ? (id == null ? "" : id) : valueId;
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

	public record Snapshot(
		String name,
		UUID uuid,
		List<Profile> profiles,
		int selected,
		String error,
		String taggedName,
		String skinValue,
		String skinSignature
	) {
		public static Snapshot empty() {
			return new Snapshot("", null, List.of(), 0, "", "", "", "");
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
			return new Snapshot(
				name,
				uuid,
				profiles,
				Math.max(0, Math.min(index, profiles.size() - 1)),
				error,
				taggedName,
				skinValue,
				skinSignature
			);
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
			SkyblockPetLore.ensure();
			JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
			if (root.has("success") && root.get("success").isJsonPrimitive() && !root.get("success").getAsBoolean()) {
				fail(gen, "profile lookup failed");
				return;
			}
			String compact = compact(resolved.uuid());
			JsonObject soopy = getJson("https://soopy.dev/api/v2/player_skyblock/" + compact);
			JsonObject soopyPlayer = getJson("https://soopy.dev/api/v2/player/" + compact);
			List<Profile> profiles = parseProfiles(root, resolved.uuid(), soopy);
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
			snapshot = new Snapshot(
				resolved.name(),
				resolved.uuid(),
				List.copyOf(profiles),
				selected,
				"",
				taggedName(resolved.name(), soopyPlayer, profiles.get(selected)),
				resolved.skinValue(),
				resolved.skinSignature()
			);
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
			return withSkin(new Resolved(localName.isBlank() ? name : localName, local, "", ""));
		}
		JsonObject mojang = getJson(MOJANG + encode(name));
		if (mojang != null) {
			UUID uuid = uuidOf(string(mojang, "id"));
			String named = string(mojang, "name");
			if (uuid != null) {
				return withSkin(new Resolved(named.isBlank() ? name : named, uuid, "", ""));
			}
		}
		JsonObject ashcon = getJson(ASHCON + encode(name));
		if (ashcon != null) {
			UUID uuid = uuidOf(string(ashcon, "uuid"));
			String named = string(ashcon, "username");
			if (uuid != null) {
				return withSkin(new Resolved(named.isBlank() ? name : named, uuid, "", ""), ashcon);
			}
		}
		return null;
	}

	private static Resolved withSkin(Resolved resolved) {
		return withSkin(resolved, null);
	}

	private static Resolved withSkin(Resolved resolved, JsonObject ashcon) {
		if (resolved == null) {
			return null;
		}
		JsonObject textures = ashcon != null ? object(object(ashcon, "textures"), "raw") : null;
		if (textures == null) {
			textures = object(object(getJson(ASHCON + encode(resolved.name())), "textures"), "raw");
		}
		String value = string(textures, "value");
		String signature = string(textures, "signature");
		if (value.isBlank()) {
			JsonObject session = getJson("https://sessionserver.mojang.com/session/minecraft/profile/" + compact(resolved.uuid()) + "?unsigned=false");
			JsonArray properties = array(session, "properties");
			if (properties != null) {
				for (JsonElement element : properties) {
					if (element == null || !element.isJsonObject()) {
						continue;
					}
					JsonObject property = element.getAsJsonObject();
					if ("textures".equalsIgnoreCase(string(property, "name"))) {
						value = string(property, "value");
						signature = string(property, "signature");
						break;
					}
				}
			}
		}
		return new Resolved(resolved.name(), resolved.uuid(), value, signature);
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

	private static List<Profile> parseProfiles(JsonObject root, UUID uuid, JsonObject soopy) {
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
			out.add(parseMember(object, member, compact, soopy));
		}
		return out;
	}

	private static Profile parseMember(JsonObject profile, JsonObject member, String compact, JsonObject soopy) {
		JsonObject soopyMember = soopyMember(soopy, string(profile, "profile_id"), string(profile, "cute_name"), compact);
		JsonObject soopySkills = object(object(soopyMember, "skills"), "levels");
		JsonObject playerData = object(member, "player_data");
		JsonObject experience = object(playerData, "experience");
		List<Skill> skills = new ArrayList<>();
		skills.add(skill("Farming", skillXp(experience, member, soopySkills, "farming", "SKILL_FARMING", "experience_skill_farming"), 60, soopySkills));
		skills.add(skill("Mining", skillXp(experience, member, soopySkills, "mining", "SKILL_MINING", "experience_skill_mining"), 60, soopySkills));
		skills.add(skill("Combat", skillXp(experience, member, soopySkills, "combat", "SKILL_COMBAT", "experience_skill_combat"), 60, soopySkills));
		skills.add(skill("Foraging", skillXp(experience, member, soopySkills, "foraging", "SKILL_FORAGING", "experience_skill_foraging"), 50, soopySkills));
		skills.add(skill("Fishing", skillXp(experience, member, soopySkills, "fishing", "SKILL_FISHING", "experience_skill_fishing"), 50, soopySkills));
		skills.add(skill("Enchanting", skillXp(experience, member, soopySkills, "enchanting", "SKILL_ENCHANTING", "experience_skill_enchanting"), 60, soopySkills));
		skills.add(skill("Alchemy", skillXp(experience, member, soopySkills, "alchemy", "SKILL_ALCHEMY", "experience_skill_alchemy"), 50, soopySkills));
		skills.add(skill("Taming", skillXp(experience, member, soopySkills, "taming", "SKILL_TAMING", "experience_skill_taming"), 50, soopySkills));
		skills.add(skill("Carpentry", skillXp(experience, member, soopySkills, "carpentry", "SKILL_CARPENTRY", "experience_skill_carpentry"), 50, soopySkills));
		skills.add(skill("Runecrafting", skillXp(experience, member, soopySkills, "runecrafting", "SKILL_RUNECRAFTING", "experience_skill_runecrafting"), 25, RUNE_XP, false, soopySkills));
		skills.add(skill("Social", skillXp(experience, member, soopySkills, "social", "SKILL_SOCIAL", "experience_skill_social2"), 25, SOCIAL_XP, false, soopySkills));
		float average = 0f;
		int counted = 0;
		for (Skill skill : skills) {
			if (skill.cap() >= 50 && !"Runecrafting".equals(skill.name()) && !"Social".equals(skill.name())) {
				average += Math.min(skill.level(), skill.cap());
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
		double personal = num(object(member, "profile"), "bank_account");
		if (personal == 0d) {
			personal = num(currencies, "personal_bank");
		}
		bank += personal;

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

		Dungeon dungeons = parseDungeons(member, soopyMember);
		List<Slayer> slayers = parseSlayers(member, soopyMember);
		Mining mining = parseMining(member, soopyMember);
		Farming farming = parseFarming(member, gardenData(string(profile, "profile_id")));
		List<Pet> pets = parsePets(member);
		JsonObject inventory = object(member, "inventory");
		Bag inv = parseBag("Inventory", first(inventory, member, "inv_contents"), 9, 36);
		Bag armor = parseBag("Armor", first(inventory, member, "inv_armor"), 1, 4);
		Bag equipment = parseBag("Equipment", first(inventory, member, "equipment_contents"), 1, 4);
		Bag wardrobe = parseBag("Wardrobe", first(inventory, member, "wardrobe_contents"), 9, 36);
		Bag accessories = parseBag("Accessories", bagOf(inventory, member, "talisman_bag"), 9, 45);
		if (accessories.vacant()) {
			accessories = parseBag("Accessories", bagOf(inventory, member, "accessory_bag"), 9, 45);
		}
		if (accessories.vacant()) {
			accessories = parseBag("Accessories", first(inventory, member, "talisman_bag"), 9, 45);
		}
		if (accessories.vacant()) {
			accessories = parseBag("Accessories", first(inventory, member, "accessory_bag"), 9, 45);
		}
		Bag vault = parseBag("Vault", first(inventory, member, "personal_vault_contents"), 9, 36);
		if (vault.vacant()) {
			vault = parseBag("Vault", first(inventory, member, "personal_vault"), 9, 36);
		}
		Bag quiver = parseBag("Quiver", bagOf(inventory, member, "quiver"), 9, 36);
		Bag potions = parseBag("Potions", bagOf(inventory, member, "potion_bag"), 9, 36);
		Bag fishing = parseBag("Fishing", bagOf(inventory, member, "fishing_bag"), 9, 36);
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
		if (sbLevel <= 0 && soopyMember != null) {
			double soopyLevel = num(soopyMember, "sbLvl");
			if (soopyLevel > 0d) {
				sbLevel = (int) Math.floor(soopyLevel);
				sbProgress = (float) (soopyLevel - sbLevel);
			}
		}
		double items = bagWorth(inv) + bagWorth(armor) + bagWorth(equipment) + bagWorth(wardrobe)
			+ bagWorth(accessories) + bagWorth(vault) + bagWorth(quiver) + bagWorth(potions) + bagWorth(fishing);
		for (Bag bag : ender) {
			items += bagWorth(bag);
		}
		for (Bag bag : backpacks) {
			items += bagWorth(bag);
		}
		items += sackWorth(member, inventory);
		items += essenceWorth(currencies);
		for (Pet pet : pets) {
			items += priceOf(pet.type() + ";" + petTier(pet.tier()));
		}
		double net = purse + bank + items;
		double helper = skyhelperNetworth(soopyMember);
		if (helper > 0d) {
			double helperNet = helper + (skyhelperHasCoins(soopyMember) ? 0d : purse + bank);
			if (helperNet > net) {
				items = Math.max(0d, helperNet - purse - bank);
				net = helperNet;
			}
		}
		if (bool(profile, "selected")) {
			double remote = coflNetworth(profile, compact);
			if (remote > net) {
				items = Math.max(0d, remote - purse - bank);
				net = remote;
			}
		}
		return new Profile(
			string(profile, "profile_id"),
			cute,
			mode,
			bool(profile, "selected"),
			purse,
			bank,
			items,
			net,
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

	private static Dungeon parseDungeons(JsonObject member, JsonObject soopy) {
		JsonObject dungeons = object(member, "dungeons");
		JsonObject types = object(dungeons, "dungeon_types");
		JsonObject cata = object(types, "catacombs");
		JsonObject master = object(types, "master_catacombs");
		JsonObject soopyDungeons = object(soopy, "dungeons");
		double xp = num(cata, "experience");
		if (xp <= 0d) {
			xp = num(soopyDungeons, "catacombs_xp");
		}
		Skill level = dungeonFrom("Catacombs", xp, soopyDungeons);
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
		return dungeonFrom(name, num(object(classes, key), "experience"), null);
	}

	private static List<Slayer> parseSlayers(JsonObject member, JsonObject soopy) {
		JsonObject slayer = object(member, "slayer");
		JsonObject bosses = object(slayer, "slayer_bosses");
		if (bosses == null) {
			bosses = object(member, "slayer_bosses");
		}
		if (bosses == null) {
			bosses = slayer;
		}
		JsonObject soopySlayer = object(soopy, "slayer");
		List<Slayer> out = new ArrayList<>();
		out.add(slayer("Zombie", bosses, soopySlayer, "zombie", SLAYER_ZOMBIE));
		out.add(slayer("Spider", bosses, soopySlayer, "spider", SLAYER_SPIDER));
		out.add(slayer("Wolf", bosses, soopySlayer, "wolf", SLAYER_WOLF));
		out.add(slayer("Enderman", bosses, soopySlayer, "enderman", SLAYER_ENDER));
		out.add(slayer("Blaze", bosses, soopySlayer, "blaze", SLAYER_BLAZE));
		out.add(slayer("Vampire", bosses, soopySlayer, "vampire", SLAYER_VAMP));
		return out;
	}

	private static Mining parseMining(JsonObject member, JsonObject extra) {
		JsonObject tree = firstSkillTree(member, extra);
		JsonObject core = bestCore(miningCore(member), extra == null ? null : miningCore(extra));
		if (core == null && extra != null) {
			core = object(extra, "mining_core");
		}
		JsonObject nodes = miningNodes(tree);
		if (!hasPerkLevels(nodes)) {
			nodes = nodesOf(core);
		}
		double xp = miningXp(tree, core, extra);
		List<Perk> perks = parsePerks(nodes);
		if (core == null && perks.isEmpty() && xp <= 0d) {
			return Mining.empty();
		}
		int fromXp = xp > 0d ? skillFrom("HOTM", xp, HOTM_XP, 10).level() : 0;
		int listed = (int) num(object(extra, "hotm_level"), "level");
		int hotm = fromXp > 0 ? fromXp : Math.max(listed, Math.max(hotmFromPerks(perks), inferHotm(core)));
		if (hotm == 0 && (bool(core, "received_free_tier") || !perks.isEmpty())) {
			hotm = 1;
		}
		return new Mining(hotm, powder(core, "mithril"), powder(core, "gemstone"), powder(core, "glacite"), perks);
	}

	private static JsonObject firstSkillTree(JsonObject member, JsonObject extra) {
		JsonObject tree = skillTree(member);
		return hasPerkLevels(miningNodes(tree)) || num(object(tree, "experience"), "mining") > 0d ? tree : skillTree(extra);
	}

	private static JsonObject skillTree(JsonObject member) {
		JsonObject tree = object(member, "skill_tree");
		if (tree == null) {
			tree = object(object(member, "player_data"), "skill_tree");
		}
		return tree;
	}

	private static JsonObject miningNodes(JsonObject tree) {
		JsonObject nodes = object(tree, "nodes");
		if (nodes == null) {
			return null;
		}
		int slot = (int) num(object(tree, "selected_skill_tree_slot"), "mining");
		String key = slot <= 1 ? "mining" : "mining_" + slot;
		JsonObject selected = object(nodes, key);
		if (hasPerkLevels(selected)) {
			return selected;
		}
		JsonObject best = object(nodes, "mining");
		int bestCount = perkCount(best);
		for (String name : nodes.keySet()) {
			if (name == null || !name.startsWith("mining")) {
				continue;
			}
			JsonObject map = object(nodes, name);
			int count = perkCount(map);
			if (count > bestCount) {
				best = map;
				bestCount = count;
			}
		}
		return best;
	}

	private static double miningXp(JsonObject tree, JsonObject core, JsonObject extra) {
		double xp = num(object(tree, "experience"), "mining");
		if (xp > 0d) {
			return xp;
		}
		xp = num(core, "experience");
		if (xp == 0d) {
			xp = num(core, "hotm_experience");
		}
		if (xp == 0d) {
			xp = num(object(core, "hotm"), "experience");
		}
		if (xp == 0d) {
			xp = num(object(extra, "hotm_level"), "totalExp");
		}
		return xp;
	}

	private static boolean hasPerkLevels(JsonObject nodes) {
		return perkCount(nodes) > 0;
	}

	private static int perkCount(JsonObject nodes) {
		if (nodes == null) {
			return 0;
		}
		int count = 0;
		for (String key : nodes.keySet()) {
			if (key != null && !key.startsWith("toggle") && nodeLevel(nodes, key) > 0) {
				count++;
			}
		}
		return count;
	}

	private static JsonObject nodesOf(JsonObject core) {
		JsonObject nodes = object(core, "nodes");
		if (nodes == null) {
			nodes = object(core, "perks");
		}
		if (nodes == null) {
			nodes = object(object(core, "hotm"), "nodes");
		}
		return nodes;
	}

	private static JsonObject bestCore(JsonObject primary, JsonObject extra) {
		if (!validMining(primary)) {
			return validMining(extra) ? extra : primary;
		}
		if (!validMining(extra)) {
			return primary;
		}
		long primaryScore = powderScore(primary) + (nodesOf(primary) != null ? 1_000_000_000L : 0L);
		long extraScore = powderScore(extra) + (nodesOf(extra) != null ? 1_000_000_000L : 0L);
		return extraScore > primaryScore ? extra : primary;
	}

	private static long powderScore(JsonObject core) {
		return powder(core, "mithril") + powder(core, "gemstone") + powder(core, "glacite");
	}

	private static int inferHotm(JsonObject core) {
		if (core == null) {
			return 0;
		}
		long mithril = powder(core, "mithril");
		long gemstone = powder(core, "gemstone");
		long glacite = powder(core, "glacite");
		long mithrilSpent = (long) num(core, "powder_spent_mithril");
		long gemSpent = (long) num(core, "powder_spent_gemstone");
		long glaciteSpent = (long) num(core, "powder_spent_glacite");
		int hotm = 0;
		if (bool(core, "received_free_tier") || mithril > 0L || mithrilSpent > 0L) {
			hotm = 1;
		}
		if (mithrilSpent >= 3_000L || mithril >= 12_000L) {
			hotm = Math.max(hotm, 3);
		}
		if (mithrilSpent >= 50_000L) {
			hotm = Math.max(hotm, 5);
		}
		if (glacite > 0L || glaciteSpent > 0L) {
			hotm = Math.max(hotm, 7);
		}
		if (glaciteSpent >= 50_000L || glacite >= 1_000_000L) {
			hotm = Math.max(hotm, 10);
		}
		return hotm;
	}

	private static JsonObject miningCore(JsonObject member) {
		JsonObject core = object(member, "mining_core");
		if (validMining(core)) {
			return core;
		}
		core = object(object(member, "player_data"), "mining_core");
		if (validMining(core)) {
			return core;
		}
		core = object(member, "hotm");
		if (validMining(core)) {
			return core;
		}
		core = object(object(member, "player_data"), "hotm");
		if (validMining(core)) {
			return core;
		}
		return findMining(member, 0);
	}

	private static boolean validMining(JsonObject object) {
		return object != null && (object.has("experience") || object.has("nodes") || object.has("perks")
			|| object.has("powder_mithril") || object.has("powder") || object.has("hotm_experience"));
	}

	private static JsonObject findMining(JsonObject object, int depth) {
		if (object == null || depth > 2) {
			return null;
		}
		for (String key : object.keySet()) {
			JsonObject child = object(object, key);
			if (validMining(child) && ("mining_core".equals(key) || "hotm".equals(key) || child.has("nodes") || child.has("powder_mithril"))) {
				return child;
			}
		}
		for (String key : object.keySet()) {
			JsonObject found = findMining(object(object, key), depth + 1);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	private static long powder(JsonObject core, String kind) {
		long available = (long) num(core, "powder_" + kind);
		long spent = (long) num(core, "powder_spent_" + kind);
		if (available + spent > 0L) {
			return available + spent;
		}
		long total = (long) num(core, "powder_" + kind + "_total");
		if (total > 0L) {
			return total;
		}
		JsonObject nested = object(object(core, "powder"), kind);
		total = (long) (num(nested, "available") + num(nested, "spent"));
		if (total > 0L) {
			return total;
		}
		return (long) num(nested, "total");
	}

	private static int hotmFromPerks(List<Perk> perks) {
		int best = 0;
		if (perks == null) {
			return 0;
		}
		for (Perk perk : perks) {
			if (perk == null || perk.level() <= 0) {
				continue;
			}
			Integer tier = HOTM_TIER.get(normPerk(perk.id()));
			if (tier != null) {
				best = Math.max(best, tier);
			}
		}
		return best;
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
			if (level <= 0) {
				continue;
			}
			out.add(new Perk(normPerk(key), pretty(key), level));
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

	private static JsonObject gardenData(String profileId) {
		String id = compact(profileId);
		if (id.isBlank()) {
			return null;
		}
		JsonObject elite = getJson(ELITE_GARDEN + id);
		if (elite != null && (elite.has("crops") || elite.has("gardenLevel") || elite.has("experience"))) {
			return elite;
		}
		return null;
	}

	private static Farming parseFarming(JsonObject member, JsonObject gardenApi) {
		JsonObject nested = object(member, "garden_player_data");
		if (nested == null) {
			nested = object(member, "garden");
		}
		JsonObject garden = gardenApi != null ? gardenApi : nested;
		int visitors = (int) num(garden, "uniqueVisitors");
		if (visitors == 0) {
			visitors = (int) num(object(garden, "commission_data"), "unique_npcs_served");
		}
		if (visitors == 0) {
			visitors = (int) num(object(nested, "commission_data"), "unique_npcs_served");
		}
		if (visitors == 0) {
			visitors = (int) num(garden, "unique_visitors");
		}
		int level = (int) num(garden, "gardenLevel");
		if (level <= 0) {
			double gardenXp = num(garden, "experience");
			if (gardenXp == 0d) {
				gardenXp = num(garden, "garden_experience");
			}
			if (gardenXp == 0d) {
				gardenXp = num(nested, "garden_experience");
			}
			level = skillFrom("Garden", gardenXp, GARDEN_XP, 15).level();
		}
		JsonObject resources = object(garden, "crops");
		if (resources == null) {
			resources = object(garden, "resources_collected");
		}
		if (resources == null) {
			resources = object(nested, "resources_collected");
		}
		List<Crop> crops = new ArrayList<>();
		crops.add(crop("Wheat", CROP_WHEAT, cropAmount(resources, "wheat", "WHEAT")));
		crops.add(crop("Carrot", CROP_CARROT, cropAmount(resources, "carrot", "CARROT_ITEM", "CARROT")));
		crops.add(crop("Potato", CROP_CARROT, cropAmount(resources, "potato", "POTATO_ITEM", "POTATO")));
		crops.add(crop("Pumpkin", CROP_WHEAT, cropAmount(resources, "pumpkin", "PUMPKIN")));
		crops.add(crop("Melon", CROP_MELON, cropAmount(resources, "melon", "MELON", "MELON_SLICE")));
		crops.add(crop("Mushroom", CROP_WHEAT, cropAmount(resources, "mushroom", "MUSHROOM_COLLECTION", "MUSHROOM")));
		crops.add(crop("Cocoa", CROP_WART, cropAmount(resources, "cocoaBeans", "cocoa_beans", "INK_SACK:3", "COCOA")));
		crops.add(crop("Cactus", CROP_CANE, cropAmount(resources, "cactus", "CACTUS")));
		crops.add(crop("Cane", CROP_CANE, cropAmount(resources, "sugarCane", "sugar_cane", "SUGAR_CANE")));
		crops.add(crop("Wart", CROP_WART, cropAmount(resources, "netherWart", "nether_wart", "NETHER_STALK", "NETHER_WART")));
		return new Farming(visitors, level, List.copyOf(crops));
	}

	private static long cropAmount(JsonObject resources, String... keys) {
		if (resources == null) {
			return 0L;
		}
		for (String key : keys) {
			if (key == null || key.isBlank()) {
				continue;
			}
			double amount = num(resources, key);
			if (amount == 0d) {
				amount = num(resources, key.toUpperCase(Locale.ROOT));
			}
			if (amount == 0d) {
				amount = num(resources, key.toLowerCase(Locale.ROOT));
			}
			if (amount > 0d) {
				return (long) amount;
			}
		}
		return 0L;
	}

	private static Crop crop(String name, long[] table, long amount) {
		int cap = table.length - 1;
		int level = 0;
		for (int i = 1; i < table.length; i++) {
			if (amount >= table[i]) {
				level = i;
			} else {
				break;
			}
		}
		float progress = 1f;
		if (level < cap) {
			double from = table[level];
			double to = table[level + 1];
			progress = to <= from ? 1f : (float) Math.max(0d, Math.min(1d, (amount - from) / (to - from)));
		}
		return new Crop(name, amount, level, cap, progress);
	}

	private static long[] cropCum(long... steps) {
		long[] out = new long[steps.length + 1];
		long total = 0L;
		for (int i = 0; i < steps.length; i++) {
			total += steps[i];
			out[i + 1] = total;
		}
		return out;
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
			int level = SkyblockPetLore.level(type, string(pet, "tier"), num(pet, "exp"));
			String held = string(pet, "heldItem");
			int candy = (int) num(pet, "candyUsed");
			out.add(new Pet(pretty(type), type, tier, level, bool(pet, "active"), held, candy));
			int rarity = switch (string(pet, "tier").toUpperCase(Locale.ROOT)) {
				case "UNCOMMON" -> 1;
				case "RARE" -> 2;
				case "EPIC" -> 3;
				case "LEGENDARY" -> 4;
				case "MYTHIC" -> 5;
				default -> 0;
			};
			SkyblockLore.request(type.toUpperCase(Locale.ROOT).replace(' ', '_') + ";" + rarity);
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

	private static JsonElement bagOf(JsonObject inventory, JsonObject member, String name) {
		JsonObject bags = object(inventory, "bag_contents");
		if (bags == null) {
			bags = object(member, "bag_contents");
		}
		if (bags != null && bags.has(name)) {
			return bags.get(name);
		}
		return first(inventory, member, name);
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
				slice.add(new SlotItem(i, item.id(), item.name(), item.count(), item.lore(), item.valueId()));
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
		List<String> lore = itemLore(compound);
		String name = itemDisplayName(compound);
		if (name.isBlank()) {
			name = itemLabel(compound);
		}
		if ((id == null || id.isBlank()) && (name == null || name.isBlank())) {
			return SlotItem.empty(slot);
		}
		int count = Math.max(1, (int) num(compound, "Count"));
		if (count == 1) {
			count = Math.max(1, (int) num(compound, "count"));
		}
		String valueId = valueId(id == null ? "" : id, extraAttributes(compound));
		return new SlotItem(slot, id == null ? "" : id, name, count, lore, valueId);
	}

	private static boolean isAir(CompoundTag tag) {
		String id = tag.getStringOr("id", "");
		return id.equalsIgnoreCase("minecraft:air") || id.equalsIgnoreCase("air");
	}

	private static CompoundTag extraAttributes(CompoundTag tag) {
		CompoundTag extra = tag.getCompoundOrEmpty("tag").getCompoundOrEmpty("ExtraAttributes");
		if (extra.isEmpty()) {
			extra = tag.getCompoundOrEmpty("ExtraAttributes");
		}
		if (extra.isEmpty()) {
			extra = tag.getCompoundOrEmpty("components").getCompoundOrEmpty("minecraft:custom_data");
		}
		if (extra.isEmpty()) {
			extra = tag.getCompoundOrEmpty("tag").getCompoundOrEmpty("components").getCompoundOrEmpty("minecraft:custom_data");
		}
		return extra;
	}

	private static String itemId(CompoundTag tag) {
		CompoundTag extra = extraAttributes(tag);
		String id = extra.getStringOr("id", "");
		if (!id.isBlank()) {
			return id;
		}
		return tag.getStringOr("id", "");
	}

	private static String itemDisplayName(CompoundTag tag) {
		CompoundTag display = tag.getCompoundOrEmpty("tag").getCompoundOrEmpty("display");
		if (display.isEmpty()) {
			display = tag.getCompoundOrEmpty("display");
		}
		String name = display.getStringOr("Name", "");
		if (name.isBlank()) {
			CompoundTag components = tag.getCompoundOrEmpty("components");
			if (components.isEmpty()) {
				components = tag.getCompoundOrEmpty("tag").getCompoundOrEmpty("components");
			}
			name = components.getStringOr("minecraft:custom_name", "");
		}
		return unwrapText(name);
	}

	private static List<String> itemLore(CompoundTag tag) {
		List<String> out = new ArrayList<>();
		CompoundTag display = tag.getCompoundOrEmpty("tag").getCompoundOrEmpty("display");
		if (display.isEmpty()) {
			display = tag.getCompoundOrEmpty("display");
		}
		addLore(out, display.get("Lore"));
		if (out.isEmpty()) {
			CompoundTag components = tag.getCompoundOrEmpty("components");
			if (components.isEmpty()) {
				components = tag.getCompoundOrEmpty("tag").getCompoundOrEmpty("components");
			}
			addLore(out, components.get("minecraft:lore"));
		}
		return List.copyOf(out);
	}

	private static void addLore(List<String> out, Tag lore) {
		if (!(lore instanceof ListTag list)) {
			return;
		}
		for (int i = 0; i < list.size() && out.size() < 64; i++) {
			Tag line = list.get(i);
			if (line == null) {
				out.add("");
				continue;
			}
			out.add(unwrapText(line.asString().orElse("")));
		}
	}

	private static String unwrapText(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		String text = raw.trim();
		if (text.startsWith("{") && text.contains("\"text\"")) {
			try {
				JsonElement element = JsonParser.parseString(text);
				return unwrapJson(element);
			} catch (Exception ignored) {
			}
		}
		if (text.startsWith("\"") && text.endsWith("\"") && text.length() >= 2) {
			text = text.substring(1, text.length() - 1);
		}
		return text.replace("\\u00a7", "§").replace("\\u00A7", "§");
	}

	private static String unwrapJson(JsonElement element) {
		if (element == null || element.isJsonNull()) {
			return "";
		}
		if (element.isJsonPrimitive()) {
			return element.getAsString();
		}
		if (element.isJsonArray()) {
			StringBuilder out = new StringBuilder();
			for (JsonElement child : element.getAsJsonArray()) {
				out.append(unwrapJson(child));
			}
			return out.toString();
		}
		if (!element.isJsonObject()) {
			return "";
		}
		JsonObject object = element.getAsJsonObject();
		StringBuilder out = new StringBuilder();
		if (object.has("text")) {
			out.append(string(object, "text"));
		}
		if (object.has("extra") && object.get("extra").isJsonArray()) {
			for (JsonElement child : object.getAsJsonArray("extra")) {
				out.append(unwrapJson(child));
			}
		}
		return out.toString();
	}

	private static String valueId(String id, CompoundTag extra) {
		String raw = id == null ? "" : id.trim();
		if (extra != null && !extra.isEmpty()) {
			if ("ENCHANTED_BOOK".equalsIgnoreCase(raw) || "minecraft:enchanted_book".equalsIgnoreCase(raw)) {
				CompoundTag enchants = extra.getCompoundOrEmpty("enchantments");
				String best = "";
				int bestLevel = -1;
				for (String key : enchants.keySet()) {
					int level = (int) num(enchants, key);
					if (level >= bestLevel && key != null && !key.isBlank()) {
						bestLevel = level;
						best = "ENCHANTMENT_" + key.toUpperCase(Locale.ROOT) + ";" + level;
					}
				}
				if (!best.isBlank()) {
					return best;
				}
			}
			String petInfo = extra.getStringOr("petInfo", "");
			if (!petInfo.isBlank()) {
				try {
					JsonObject pet = JsonParser.parseString(petInfo).getAsJsonObject();
					String type = string(pet, "type");
					if (!type.isBlank()) {
						return type.toUpperCase(Locale.ROOT) + ";" + petTier(string(pet, "tier"));
					}
				} catch (Exception ignored) {
				}
			}
		}
		return raw;
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

	private static Skill skill(String name, double xp, int cap, JsonObject soopySkills) {
		return skill(name, xp, cap, SKILL_XP, true, soopySkills);
	}

	private static Skill skill(String name, double xp, int cap, long[] table, boolean overflow, JsonObject soopySkills) {
		Skill computed = skillFrom(name, xp, table, cap, overflow ? Overflow.SKILL : Overflow.NONE);
		JsonObject soopy = object(soopySkills, name.toLowerCase(Locale.ROOT));
		int soopyLevel = (int) num(object(soopy, "over60"), "level");
		if (soopyLevel <= 0) {
			soopyLevel = (int) num(soopy, "level");
		}
		if (soopyLevel > computed.level()) {
			double soopyXp = num(soopy, "xp");
			float progress = (float) num(object(soopy, "over60"), "progress");
			if (progress <= 0f) {
				progress = (float) num(soopy, "progress");
			}
			return new Skill(name, soopyLevel, cap, xp > 0d ? xp : soopyXp, Math.max(0f, Math.min(1f, progress)));
		}
		return computed;
	}

	private static Skill dungeonFrom(String name, double xp, JsonObject soopyDungeons) {
		Skill computed = skillFrom(name, xp, CATA_XP, 50, Overflow.DUNGEON);
		if ("Catacombs".equals(name)) {
			double listed = num(soopyDungeons, "catacombs_level");
			int soopyLevel = (int) Math.floor(listed);
			if (soopyLevel > computed.level()) {
				return new Skill(name, soopyLevel, 50, xp, (float) Math.max(0d, Math.min(1d, listed - soopyLevel)));
			}
		}
		return computed;
	}

	private static Skill skillFrom(String name, double xp, long[] table, int cap) {
		return skillFrom(name, xp, table, cap, Overflow.NONE);
	}

	private static Skill skillFrom(String name, double xp, long[] table, int cap, Overflow overflow) {
		int highest = table == null ? 0 : table.length - 1;
		int level = 0;
		for (int i = 1; i <= highest; i++) {
			if (xp >= table[i]) {
				level = i;
			} else {
				break;
			}
		}
		float progress = 1f;
		if (level < highest) {
			double from = table[level];
			double to = table[level + 1];
			progress = to <= from ? 1f : (float) Math.max(0d, Math.min(1d, (xp - from) / (to - from)));
		} else if (overflow == Overflow.DUNGEON) {
			double extra = Math.max(0d, xp - table[highest]);
			int more = (int) Math.floor(extra / CATA_OVERFLOW);
			level += more;
			progress = (float) ((extra - more * (double) CATA_OVERFLOW) / CATA_OVERFLOW);
		} else if (overflow == Overflow.SKILL && highest >= 2) {
			double extra = Math.max(0d, xp - table[highest]);
			double step = table[highest] - table[highest - 1];
			int safety = 0;
			while (extra >= step && safety++ < 400) {
				extra -= step;
				level++;
				step += 300_000d;
			}
			progress = step <= 0d ? 1f : (float) Math.max(0d, Math.min(1d, extra / step));
		}
		if (overflow == Overflow.NONE) {
			level = Math.min(level, cap);
		}
		return new Skill(name, level, cap, xp, progress);
	}

	private static Slayer slayer(String name, JsonObject bosses, JsonObject soopySlayer, String key, int[] table) {
		JsonObject boss = object(bosses, key);
		double xp = num(boss, "xp");
		if (xp <= 0d) {
			xp = num(object(soopySlayer, key), "xp");
		}
		int level = slayerLevel(xp, table);
		level = Math.max(level, claimedSlayer(boss));
		JsonObject extra = object(soopySlayer, key);
		level = Math.max(level, (int) num(extra, "level"));
		level = Math.max(level, (int) num(extra, "claimed_level"));
		return new Slayer(name, level, xp);
	}

	private static int slayerLevel(double xp, int[] table) {
		int level = 0;
		if (table == null) {
			return 0;
		}
		for (int i = 0; i < table.length; i++) {
			if (xp >= table[i]) {
				level = i + 1;
			} else {
				break;
			}
		}
		return level;
	}

	private static int claimedSlayer(JsonObject boss) {
		int best = (int) num(boss, "claimed_level");
		JsonObject claimed = object(boss, "claimed_levels");
		if (claimed == null) {
			return best;
		}
		for (String key : claimed.keySet()) {
			if (key == null || !claimedOn(claimed, key)) {
				continue;
			}
			String digits = key.replaceAll("\\D+", "");
			if (!digits.isEmpty()) {
				try {
					best = Math.max(best, Integer.parseInt(digits));
				} catch (NumberFormatException ignored) {
				}
			}
		}
		return best;
	}

	private static boolean claimedOn(JsonObject claimed, String key) {
		if (claimed == null || key == null || !claimed.has(key)) {
			return false;
		}
		JsonElement value = claimed.get(key);
		if (value == null || !value.isJsonPrimitive()) {
			return false;
		}
		JsonPrimitive primitive = value.getAsJsonPrimitive();
		if (primitive.isBoolean()) {
			return primitive.getAsBoolean();
		}
		if (primitive.isNumber()) {
			return primitive.getAsDouble() > 0d;
		}
		return "true".equalsIgnoreCase(primitive.getAsString());
	}

	private static double skillXp(JsonObject experience, JsonObject member, JsonObject soopySkills, String soopyKey, String modern, String legacy) {
		double xp = num(experience, modern);
		if (xp == 0d) {
			xp = num(member, legacy);
		}
		if (xp == 0d) {
			xp = num(object(soopySkills, soopyKey), "xp");
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
						double value = buy > 0 ? buy : sell;
						if (value > 0d) {
							next.put(id.toUpperCase(Locale.ROOT), value);
						}
					}
				}
			}
		} catch (Exception ignored) {
		}
		mergePrices(next, "https://sky.coflnet.com/api/prices/neu");
		mergePrices(next, "https://lb.tricked.pro/lowestbins");
		mergePrices(next, "https://moulberry.codes/lowestbin.json");
		mergePrices(next, "https://sky.coflnet.com/api/auctions/lowestbins");
		if (!next.isEmpty()) {
			PRICES = Map.copyOf(next);
			pricesAt = System.currentTimeMillis();
		} else {
			Stray.LOGGER.warn("Profile viewer price lookup returned no items");
		}
	}

	private static void mergePrices(Map<String, Double> next, String url) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(12))
				.header("User-Agent", "Stray/" + Stray.MOD_ID)
				.GET()
				.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				Stray.LOGGER.warn("Price source {} returned HTTP {}", url, response.statusCode());
				return;
			}
			JsonElement root = JsonParser.parseString(response.body());
			if (root == null || !root.isJsonObject()) {
				return;
			}
			JsonObject bins = root.getAsJsonObject();
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
		} catch (Exception exception) {
			Stray.LOGGER.warn("Price source {} failed", url, exception);
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
			total += priceOf(item.priceId()) * Math.max(1, item.count());
		}
		return total;
	}

	private static double sackWorth(JsonObject member, JsonObject inventory) {
		JsonObject sacks = object(member, "sacks_counts");
		if (sacks == null) {
			sacks = object(inventory, "sacks_counts");
		}
		if (sacks == null) {
			sacks = object(object(member, "sacks"), "counts");
		}
		if (sacks == null) {
			return 0d;
		}
		double total = 0d;
		for (String id : sacks.keySet()) {
			total += priceOf(id) * Math.max(0d, num(sacks, id));
		}
		return total;
	}

	private static double essenceWorth(JsonObject currencies) {
		JsonObject essence = object(currencies, "essence");
		if (essence == null) {
			return 0d;
		}
		double total = 0d;
		for (String key : essence.keySet()) {
			JsonObject entry = object(essence, key);
			double amount = entry != null ? num(entry, "current") : num(essence, key);
			if (amount <= 0d) {
				continue;
			}
			String id = key.toUpperCase(Locale.ROOT);
			if (!id.startsWith("ESSENCE")) {
				id = "ESSENCE_" + id;
			}
			total += priceOf(id) * amount;
		}
		return total;
	}

	private static double priceOf(String id) {
		if (id == null || id.isBlank() || PRICES.isEmpty()) {
			return 0d;
		}
		String key = id.trim().toUpperCase(Locale.ROOT);
		if (key.startsWith("MINECRAFT:")) {
			key = key.substring(10);
		}
		if (key.startsWith("SB:")) {
			key = key.substring(3);
		}
		Double value = lookupPrice(key);
		if (value != null) {
			return value;
		}
		if (key.startsWith("STARRED_")) {
			value = lookupPrice(key.substring(8));
			if (value != null) {
				return value;
			}
		}
		int plus = key.indexOf('+');
		if (plus > 0) {
			value = lookupPrice(key.substring(0, plus));
			if (value != null) {
				return value;
			}
		}
		int dash = key.indexOf('-');
		if (dash > 0) {
			value = lookupPrice(key.substring(0, dash));
			if (value != null) {
				return value;
			}
		}
		return 0d;
	}

	private static Double lookupPrice(String key) {
		if (key == null || key.isBlank()) {
			return null;
		}
		Double value = PRICES.get(key);
		if (value != null) {
			return value;
		}
		if (key.contains(":")) {
			value = PRICES.get(key.replace(':', ';'));
			if (value != null) {
				return value;
			}
		}
		if (key.startsWith("PET_")) {
			return PRICES.get(key.substring(4));
		}
		return PRICES.get("PET_" + key);
	}

	private static double coflNetworth(JsonObject profile, String compact) {
		if (profile == null) {
			return 0d;
		}
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create("https://sky.coflnet.com/api/networth"))
				.timeout(Duration.ofSeconds(10))
				.header("User-Agent", "Stray/" + Stray.MOD_ID)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(profile.toString()))
				.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				Stray.LOGGER.warn("Coflnet networth returned HTTP {}", response.statusCode());
				return 0d;
			}
			JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
			JsonObject members = object(root, "member");
			if (members != null && compact != null) {
				JsonObject own = object(members, compact);
				if (own == null) {
					for (String key : members.keySet()) {
						if (compact(key).equals(compact)) {
							own = object(members, key);
							break;
						}
					}
				}
				double value = own == null ? 0d : num(own, "fullValue");
				if (value > 0d) {
					return value;
				}
			}
			return num(root, "fullValue");
		} catch (Exception exception) {
			Stray.LOGGER.warn("Coflnet networth lookup failed", exception);
			return 0d;
		}
	}

	private static String normPerk(String id) {
		return id == null ? "" : id.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
	}

	private static boolean samePerk(String have, String want) {
		String left = normPerk(have);
		String right = normPerk(want);
		if (left.isEmpty() || right.isEmpty()) {
			return false;
		}
		return left.equals(right) || perkAlias(left).equals(right) || perkAlias(right).equals(left);
	}

	public static String perkAlias(String id) {
		return switch (normPerk(id)) {
			case "speedy_mineman" -> "mining_speed_2";
			case "mining_speed_2" -> "speedy_mineman";
			case "fortunate_mineman" -> "mining_fortune_2";
			case "mining_fortune_2" -> "fortunate_mineman";
			case "gifts_from_above" -> "gifts_from_the_departed";
			case "gifts_from_the_departed" -> "gifts_from_above";
			case "pickobulus" -> "pickaxe_toss";
			case "pickaxe_toss" -> "pickobulus";
			case "sky_mall" -> "daily_effect";
			case "daily_effect" -> "sky_mall";
			case "luck_of_the_cave" -> "random_event";
			case "random_event" -> "luck_of_the_cave";
			case "seasoned_mineman" -> "mining_experience";
			case "mining_experience" -> "seasoned_mineman";
			case "gem_lover" -> "fortunate";
			case "fortunate" -> "gem_lover";
			case "special_0" -> "core_of_the_mountain";
			case "core_of_the_mountain" -> "special_0";
			case "quick_forge" -> "forge_time";
			case "forge_time" -> "quick_forge";
			case "warm_heart" -> "warm_hearted";
			case "warm_hearted" -> "warm_heart";
			case "dead_mans_chest" -> "hungry_for_more";
			case "hungry_for_more" -> "dead_mans_chest";
			default -> normPerk(id);
		};
	}

	private static Map<String, Integer> hotmTiers() {
		Map<String, Integer> out = new HashMap<>();
		out.put("mining_speed", 1);
		out.put("mining_speed_boost", 2);
		out.put("precision_mining", 2);
		out.put("mining_fortune", 2);
		out.put("titanium_insanium", 2);
		out.put("pickaxe_toss", 2);
		out.put("pickobulus", 2);
		out.put("random_event", 3);
		out.put("luck_of_the_cave", 3);
		out.put("efficient_miner", 3);
		out.put("forge_time", 3);
		out.put("quick_forge", 3);
		out.put("daily_effect", 4);
		out.put("sky_mall", 4);
		out.put("old_school", 4);
		out.put("professional", 4);
		out.put("mole", 4);
		out.put("fortunate", 4);
		out.put("gem_lover", 4);
		out.put("mining_experience", 4);
		out.put("seasoned_mineman", 4);
		out.put("front_loaded", 4);
		out.put("daily_grind", 5);
		out.put("special_0", 5);
		out.put("core_of_the_mountain", 5);
		out.put("daily_powder", 5);
		out.put("anomalous_desire", 6);
		out.put("blockhead", 6);
		out.put("subterranean_fisher", 6);
		out.put("keep_it_cool", 6);
		out.put("lonesome_miner", 6);
		out.put("great_explorer", 6);
		out.put("maniac_miner", 6);
		out.put("mining_speed_2", 7);
		out.put("speedy_mineman", 7);
		out.put("powder_buff", 7);
		out.put("mining_fortune_2", 7);
		out.put("fortunate_mineman", 7);
		out.put("miners_blessing", 8);
		out.put("no_stone_unturned", 8);
		out.put("strong_arm", 8);
		out.put("steady_hand", 8);
		out.put("warm_hearted", 8);
		out.put("warm_heart", 8);
		out.put("surveyor", 8);
		out.put("mineshaft_mayhem", 8);
		out.put("metal_head", 9);
		out.put("rags_to_riches", 9);
		out.put("eager_adventurer", 9);
		out.put("gemstone_infusion", 10);
		out.put("crystalline", 10);
		out.put("gifts_from_the_departed", 10);
		out.put("gifts_from_above", 10);
		out.put("mining_master", 10);
		out.put("hungry_for_more", 10);
		out.put("dead_mans_chest", 10);
		out.put("vanguard_seeker", 10);
		out.put("sheer_force", 10);
		return Map.copyOf(out);
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

	private static JsonObject soopyMember(JsonObject soopy, String profileId, String cute, String compact) {
		JsonObject profiles = object(object(soopy, "data"), "profiles");
		if (profiles == null) {
			return null;
		}
		JsonObject match = object(profiles, compact(profileId));
		if (match == null && cute != null && !cute.isBlank()) {
			for (String key : profiles.keySet()) {
				JsonObject profile = object(profiles, key);
				if (cute.equalsIgnoreCase(string(profile, "cute_name"))) {
					match = profile;
					break;
				}
			}
		}
		if (match == null) {
			JsonObject stats = object(object(soopy, "data"), "stats");
			match = object(profiles, string(stats, "currentProfileId"));
		}
		return object(object(match, "members"), compact);
	}

	private static double skyhelperNetworth(JsonObject member) {
		JsonObject nw = object(member, "skyhelperNetworth");
		if (nw == null) {
			nw = object(member, "networth");
		}
		double total = num(nw, "total");
		if (total <= 0d) {
			total = num(nw, "networth");
		}
		return total;
	}

	private static boolean skyhelperHasCoins(JsonObject member) {
		JsonObject cats = object(object(member, "skyhelperNetworth"), "categories");
		return num(object(cats, "coins"), "total") > 0d;
	}

	private static String taggedName(String name, JsonObject soopyPlayer, Profile profile) {
		String raw = name == null || name.isBlank() ? "?" : name;
		JsonObject stats = object(object(soopyPlayer, "data"), "stats");
		String prefix = string(stats, "nameWithPrefix");
		if (prefix.isBlank()) {
			prefix = string(stats, "prefixCalculated") + raw;
		}
		if (prefix.isBlank()) {
			prefix = raw;
		}
		int level = profile == null ? 0 : profile.skyblockLevel();
		if (level <= 0) {
			return prefix;
		}
		return "§8[" + levelColor(level) + level + "§8] " + prefix;
	}

	public static String levelColor(int level) {
		if (level >= 480) {
			return "§4";
		}
		if (level >= 440) {
			return "§c";
		}
		if (level >= 400) {
			return "§6";
		}
		if (level >= 360) {
			return "§5";
		}
		if (level >= 320) {
			return "§d";
		}
		if (level >= 280) {
			return "§9";
		}
		if (level >= 240) {
			return "§3";
		}
		if (level >= 200) {
			return "§b";
		}
		if (level >= 160) {
			return "§2";
		}
		if (level >= 120) {
			return "§a";
		}
		if (level >= 80) {
			return "§e";
		}
		if (level >= 40) {
			return "§f";
		}
		return "§7";
	}

	private record Resolved(String name, UUID uuid, String skinValue, String skinSignature) {
	}
}
