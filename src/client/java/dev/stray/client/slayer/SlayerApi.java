package dev.stray.client.slayer;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.location.SkyblockLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the player is doing with slayer: the quest from the scoreboard (type, tier, progress,
 * state), whether they are in that slayer's area, and their own boss (found by its
 * "Spawned by" tag) with its name tags, health and fight time. Feeds every slayer feature.
 */
public final class SlayerApi {
	public enum State {
		NONE, GRINDING, BOSS_FIGHT, SLAIN, FAILED, COCOONED
	}

	private static final Pattern QUEST_START = Pattern.compile("^\\s*SLAYER QUEST STARTED!");
	private static final Pattern QUEST_COMPLETE = Pattern.compile("^\\s*SLAYER QUEST COMPLETE!");
	private static final Pattern QUEST_FAILED = Pattern.compile("^\\s*SLAYER QUEST FAILED!");
	private static final Pattern COCOONED = Pattern.compile("^\\s+YOU COCOONED YOUR SLAYER BOSS");
	private static final Pattern BANK_AUTO = Pattern.compile("Took (?<coins>[\\d.,]+[kKmM]?) coins from your bank for auto-slayer");
	private static final Pattern PURSE = Pattern.compile("^(?:Purse|Piggy): ([\\d,]+(?:\\.\\d+)?)");
	private static final Pattern HEALTH = Pattern.compile("([\\d.,]+[kKmMbB]?)(?:/([\\d.,]+[kKmMbB]?))?\\s*❤");
	private static final Pattern SPAWNED_BY = Pattern.compile("Spawned by: (\\w+)");
	private static final Pattern ROMAN = Pattern.compile("\\b(I|II|III|IV|V)$");

	// quest
	private static String category = "";
	private static SlayerType type;
	private static int tier;
	private static String progress = "";
	private static State state = State.NONE;
	private static long questStartMs;
	private static long cocoonMs;
	private static SlayerType areaType;
	private static int tickCount;

	// purse
	private static long purse = -1;
	private static long purseAt;

	// own boss
	private static LivingEntity boss;
	private static ArmorStand bossNameStand;
	private static final List<String> BOSS_TAGS = new ArrayList<>();
	private static long bossSpawnMs;
	/** When this boss fight started; kept while the boss swaps entities (Inferno demons, Tarantula phases). */
	private static long fightStartMs;
	private static long bossHealth = -1;
	private static long bossMaxHealth = -1;
	private static int bossTier;

	private SlayerApi() {
	}

	// ---------------------------------------------------------------- getters

	public static SlayerType type() {
		return type;
	}

	public static int tier() {
		return tier;
	}

	/** "Voidgloom Seraph IV", or "" without a quest. */
	public static String category() {
		return category;
	}

	/** The quest category without its tier ("Voidgloom Seraph"). */
	public static String slayerName() {
		int space = category.lastIndexOf(' ');
		return space < 0 ? category : category.substring(0, space);
	}

	public static String progress() {
		return progress;
	}

	public static State state() {
		return state;
	}

	public static boolean hasQuest() {
		return type != null;
	}

	public static boolean inBossFight() {
		return state == State.BOSS_FIGHT;
	}

	public static SlayerType areaType() {
		return areaType;
	}

	/** In the area of any slayer (quest or not). */
	public static boolean inAnyArea() {
		return areaType != null;
	}

	/**
	 * In the active slayer's area, or somewhere the area is not known (the scoreboard location
	 * names do not cover every spot a boss can be fought).
	 */
	public static boolean plausibleArea() {
		return type != null && (areaType == null || areaType == type);
	}

	/** In the area of the slayer whose quest is active. */
	public static boolean inCorrectArea() {
		return areaType != null && areaType == type;
	}

	public static long questStartMs() {
		return questStartMs;
	}

	public static LivingEntity boss() {
		return boss != null && boss.isAlive() && !boss.isRemoved() ? boss : null;
	}

	/** Every name tag above the own boss this tick (colour codes removed). */
	public static List<String> bossTags() {
		return BOSS_TAGS;
	}

	public static long bossHealth() {
		return bossHealth;
	}

	public static long bossMaxHealth() {
		return bossMaxHealth;
	}

	public static long bossAliveMs() {
		return fightStartMs == 0 ? 0 : System.currentTimeMillis() - fightStartMs;
	}

	public static long fightStartMs() {
		return fightStartMs;
	}

	public static String bossTag(String containing) {
		for (String tag : BOSS_TAGS) {
			if (tag.contains(containing)) {
				return tag;
			}
		}
		return null;
	}

	public static String playerName() {
		Minecraft client = Minecraft.getInstance();
		return client.player == null ? "" : client.player.getGameProfile().name();
	}

	// ---------------------------------------------------------------- tick

	public static void tick(Minecraft client) {
		if (client.player == null || client.level == null || !SkyblockLocation.inSkyblock) {
			if (type != null || state != State.NONE) {
				reset();
			}
			return;
		}
		tickCount++;
		areaType = SlayerType.forArea(SkyblockLocation.area, SkyblockLocation.poi);
		SlayerTags.tick(client);
		if (tickCount % 4 == 0) {
			readSidebar(client);
		}
		trackBoss(client);
		if (state == State.COCOONED && System.currentTimeMillis() - cocoonMs > 6000) {
			// Cocoons burst after 5 s; fall back to the scoreboard afterwards.
			setState(detect(progress));
		}
	}

	private static void reset() {
		category = "";
		type = null;
		tier = 0;
		progress = "";
		state = State.NONE;
		boss = null;
		bossNameStand = null;
		BOSS_TAGS.clear();
		purse = -1;
	}

	private static void readSidebar(Minecraft client) {
		List<String> lines = sidebar(client);
		int at = -1;
		boolean afterQuestHeader = false;
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			if (line.equals("Slayer Quest")) {
				afterQuestHeader = true;
				continue;
			}
			Matcher purseMatch = PURSE.matcher(line);
			if (purseMatch.find()) {
				onPurse(parseLong(purseMatch.group(1)));
			}
			if (at < 0 && afterQuestHeader && SlayerType.byName(line) != null) {
				at = i;
			}
		}
		if (at < 0) {
			// The quest header can be missing on some scoreboard layouts; any quest line works.
			for (int i = 0; i < lines.size(); i++) {
				if (SlayerType.byName(lines.get(i)) != null && ROMAN.matcher(lines.get(i)).find()) {
					at = i;
					break;
				}
			}
		}
		if (at < 0) {
			// Not on the scoreboard (hidden by the scoreboard settings): read the tab list's "Slayer:" widget.
			List<String> tab = tabSlayer(client);
			for (int i = 0; i < tab.size(); i++) {
				if (SlayerType.byName(tab.get(i)) != null && ROMAN.matcher(tab.get(i)).find()) {
					lines = tab;
					at = i;
					break;
				}
			}
		}
		lastLines = lines;
		String newCategory = at < 0 ? "" : lines.get(at);
		String newProgress = at < 0 || at + 1 >= lines.size() ? "" : lines.get(at + 1);
		if (!newCategory.equals(category)) {
			String old = category;
			category = newCategory;
			type = SlayerType.byName(newCategory);
			tier = parseTier(newCategory);
			SlayerProfit.onCategoryChange(old, newCategory);
			SlayerRng.onCategoryChange();
		}
		if (!newProgress.equals(progress)) {
			progress = newProgress;
			SlayerAlerts.onProgress(newProgress);
			SlayerKills.onProgress(newProgress);
		}
		State next = detect(newProgress);
		if (state == State.COCOONED && System.currentTimeMillis() - cocoonMs <= 6000 && (next == State.NONE || next == State.SLAIN)) {
			next = State.COCOONED;
		}
		setState(next);
	}

	private static List<String> lastLines = List.of();
	private static String bossReason = "none";

	/** The tab list lines under the "Slayer:" header (empty when the widget is off). */
	static List<String> tabSlayer(Minecraft client) {
		List<String> tab = dev.stray.client.farming.PestCooldown.tabLines(client);
		List<String> out = new ArrayList<>();
		boolean in = false;
		for (String line : tab) {
			String t = line.replaceAll("[\\p{Cf}\\p{Co}]", "").trim();
			if (t.startsWith("Slayer:")) {
				in = true;
				String rest = t.substring(7).trim();
				if (!rest.isEmpty()) {
					out.add(rest);
				}
				continue;
			}
			if (in) {
				if (t.isEmpty() || t.endsWith(":") && !t.contains("/")) {
					break;
				}
				out.add(t);
			}
		}
		return out;
	}

	/** State dump for /stray slayer. */
	public static List<String> debug(Minecraft client) {
		List<String> out = new ArrayList<>();
		out.add("skyblock=" + SkyblockLocation.inSkyblock + " area='" + SkyblockLocation.area + "' poi='" + SkyblockLocation.poi + "'");
		out.add("quest='" + category + "' type=" + type + " tier=" + tier + " state=" + state);
		out.add("progress='" + progress + "' areaType=" + areaType + " correctArea=" + inCorrectArea());
		LivingEntity b = boss();
		out.add("boss=" + (b == null ? "none" : b.getType().toShortString() + " hp=" + bossHealth + "/" + bossMaxHealth + " tags=" + BOSS_TAGS));
		out.add("lines=" + lastLines);
		out.add("boss found by: " + bossReason + ", me='" + playerName() + "'");
		for (SlayerTags.Tag t : SlayerTags.all()) {
			if (t.has("Spawned by")) {
				out.add("  spawned-by tag " + t.lines() + " -> " + (t.owner() == null ? "no mob" : t.owner().getType().toShortString()));
			}
		}
		out.add(SlayerWorld.debug());
		out.addAll(SlayerProfit.debug());
		List<SlayerTags.Tag> tags = new ArrayList<>(SlayerTags.all());
		int owned = 0;
		for (SlayerTags.Tag t : tags) {
			if (t.owner() != null) {
				owned++;
			}
		}
		out.add("name tags=" + tags.size() + " matched to a mob=" + owned);
		if (client.player != null) {
			tags.sort(Comparator.comparingDouble(t -> t.entity().distanceToSqr(client.player)));
			for (int i = 0; i < Math.min(6, tags.size()); i++) {
				SlayerTags.Tag t = tags.get(i);
				out.add("  " + t.entity().getType().toShortString() + " " + t.lines() + " -> "
					+ (t.owner() == null ? "no mob" : t.owner().getType().toShortString()));
			}
		}
		if (client.player != null) {
			out.add("tab slayer=" + tabSlayer(client));
		}
		return out;
	}

	private static State detect(String line) {
		if (line == null || line.isEmpty()) {
			return type == null ? State.NONE : State.GRINDING;
		}
		if (line.contains("Combat") || line.contains("Kills")) {
			return State.GRINDING;
		}
		if (line.startsWith("Slay the boss")) {
			return State.BOSS_FIGHT;
		}
		if (line.startsWith("Boss slain")) {
			return State.SLAIN;
		}
		return type == null ? State.NONE : State.GRINDING;
	}

	private static void setState(State next) {
		if (next == state) {
			return;
		}
		State old = state;
		state = next;
		if (next == State.BOSS_FIGHT && fightStartMs == 0) {
			startFight();
		}
		if ((old == State.BOSS_FIGHT || old == State.COCOONED) && next == State.GRINDING) {
			SlayerAlerts.onState(State.SLAIN);
		}
		if (old == State.GRINDING && next == State.SLAIN) {
			SlayerAlerts.onState(State.BOSS_FIGHT);
		}
		SlayerAlerts.onState(next);
		if (next == State.SLAIN) {
			SlayerBosses.onSlain();
		}
		if (next != State.BOSS_FIGHT && next != State.COCOONED) {
			fightStartMs = 0;
		}
	}

	private static void startFight() {
		fightStartMs = System.currentTimeMillis();
		bossMaxHealth = -1;
		bossTier = tier;
		SlayerBosses.onFightStart();
	}

	private static int parseTier(String line) {
		Matcher m = ROMAN.matcher(line == null ? "" : line.trim());
		if (!m.find()) {
			return 0;
		}
		return switch (m.group(1)) {
			case "I" -> 1;
			case "II" -> 2;
			case "III" -> 3;
			case "IV" -> 4;
			case "V" -> 5;
			default -> 0;
		};
	}

	// ---------------------------------------------------------------- purse

	private static void onPurse(long value) {
		long now = System.currentTimeMillis();
		if (purse >= 0 && value != purse) {
			SlayerProfit.onPurseChange(value - purse, now - questStartMs < 4000);
		}
		purse = value;
		purseAt = now;
	}

	// ---------------------------------------------------------------- chat

	/** Called for every chat line (not the action bar); returns false to hide it. */
	public static boolean onChat(Component message) {
		if (!SkyblockLocation.inSkyblock || message == null) {
			return true;
		}
		String text = message.getString().replaceAll("§.", "");
		if (QUEST_START.matcher(text).find()) {
			questStartMs = System.currentTimeMillis();
			SlayerProfit.onQuestStart();
		} else if (QUEST_COMPLETE.matcher(text).find()) {
			SlayerProfit.onQuestComplete();
			SlayerAlerts.onQuestComplete();
		} else if (QUEST_FAILED.matcher(text).find()) {
			state = State.FAILED;
			SlayerAlerts.onState(State.FAILED);
		} else if (COCOONED.matcher(text).find()) {
			state = State.COCOONED;
			cocoonMs = System.currentTimeMillis();
			SlayerAlerts.onState(State.COCOONED);
		}
		Matcher bank = BANK_AUTO.matcher(text);
		if (bank.find()) {
			SlayerProfit.addSpawnCost(parseShort(bank.group("coins")));
		}
		boolean show = SlayerRng.onChat(message, text);
		SlayerKills.onChat(text);
		show &= SlayerBosses.onChat(text);
		return show;
	}

	// ---------------------------------------------------------------- own boss

	private static void trackBoss(Minecraft client) {
		String me = playerName();
		BOSS_TAGS.clear();
		LivingEntity found = null;
		// The own boss: the mob whose tags say "Spawned by: <me>".
		for (SlayerTags.Tag tag : SlayerTags.all()) {
			for (String line : tag.lines()) {
				Matcher m = SPAWNED_BY.matcher(line);
				if (m.find() && m.group(1).equalsIgnoreCase(me)) {
					found = tag.owner() != null ? tag.owner() : bossNear(client, tag.entity());
					bossReason = found == null ? "spawned-by tag without a mob" : "spawned-by tag";
					break;
				}
			}
			if (found != null) {
				break;
			}
		}
		if (found == null && type != null && (state == State.BOSS_FIGHT || state == State.COCOONED)) {
			// No "Spawned by" tag in sight: take the closest "[SLAYER BOSS]" of the quest's boss.
			String name = category;
			double best = Double.MAX_VALUE;
			for (SlayerTags.Tag tag : SlayerTags.all()) {
				LivingEntity mob = tag.owner();
				if (mob == null || !tag.has(type.displayName) && !otherName(tag)) {
					continue;
				}
				List<String> lines = SlayerTags.of(mob);
				boolean marked = false;
				for (String line : lines) {
					if (line.contains("SLAYER BOSS") || line.contains(name)) {
						marked = true;
						break;
					}
				}
				double d = mob.distanceToSqr(client.player);
				if (marked && d < best && d < 30 * 30) {
					best = d;
					found = mob;
					bossReason = "closest [SLAYER BOSS]";
				}
			}
		}
		if (found == null) {
			LivingEntity old = boss();
			if (old != null && old.distanceTo(client.player) < 48 && !SlayerTags.of(old).isEmpty()) {
				found = old;
			}
		}
		if (found == null) {
			bossReason = "none (state " + state + ")";
			if (boss != null) {
				onBossGone();
			}
			return;
		}
		if (boss == null || boss != found) {
			boss = found;
			bossSpawnMs = System.currentTimeMillis();
			if (fightStartMs == 0) {
				startFight();
			}
			SlayerBosses.onBossSpawn(found);
		}
		// Every tag of the boss (name + health, phase, hits, shield, timer...), plus loose tags right
		// above it that were not matched to any mob.
		BOSS_TAGS.addAll(SlayerTags.of(found));
		AABB above = found.getBoundingBox().inflate(1.5, 3.5, 1.5);
		for (SlayerTags.Tag tag : SlayerTags.all()) {
			if (tag.owner() == null && above.contains(tag.entity().position())) {
				BOSS_TAGS.addAll(tag.lines());
			}
		}
		for (String line : BOSS_TAGS) {
			if (!line.contains("❤") || type != null && SlayerType.byName(line) == null) {
				continue;
			}
			Matcher m = HEALTH.matcher(line);
			if (m.find()) {
				bossHealth = parseShort(m.group(1));
				long max = m.group(2) != null ? parseShort(m.group(2)) : bossHealth;
				if (bossMaxHealth < max) {
					bossMaxHealth = max;
				}
				break;
			}
		}
	}

	private static boolean otherName(SlayerTags.Tag tag) {
		for (String other : type.otherNames) {
			if (tag.has(other)) {
				return true;
			}
		}
		return false;
	}

	/** A boss next to a "Spawned by" tag that was not matched to a mob. */
	private static LivingEntity bossNear(Minecraft client, Entity tag) {
		LivingEntity best = null;
		double bestDist = Double.MAX_VALUE;
		AABB box = tag.getBoundingBox().inflate(1.5, 4.0, 1.5);
		for (Entity entity : client.level.getEntities(tag, box, e -> e instanceof LivingEntity && !SlayerTags.isTag(e) && e != client.player)) {
			if (type != null && !type.matches(entity)) {
				continue;
			}
			double d = entity.distanceToSqr(tag.getX(), tag.getY(), tag.getZ());
			if (d < bestDist) {
				bestDist = d;
				best = (LivingEntity) entity;
			}
		}
		return best;
	}

	private static void onBossGone() {
		long alive = bossSpawnMs == 0 ? 0 : System.currentTimeMillis() - bossSpawnMs;
		SlayerType t = type;
		int bt = bossTier;
		boss = null;
		bossNameStand = null;
		bossHealth = -1;
		bossSpawnMs = 0;
		if (t != null && alive > 0) {
			SlayerBosses.onBossGone(t, bt, alive, state);
		}
	}

	// ---------------------------------------------------------------- helpers

	public static String clean(Component component) {
		return SlayerTags.clean(component);
	}

	/** "1,234", "45.5M", "1.9k" -> number. */
	public static long parseShort(String raw) {
		if (raw == null || raw.isEmpty()) {
			return 0L;
		}
		String s = raw.trim().replace(",", "").toLowerCase(Locale.ROOT);
		double mult = 1;
		char last = s.charAt(s.length() - 1);
		if (last == 'k') {
			mult = 1e3;
		} else if (last == 'm') {
			mult = 1e6;
		} else if (last == 'b') {
			mult = 1e9;
		}
		if (mult != 1) {
			s = s.substring(0, s.length() - 1);
		}
		try {
			return Math.round(Double.parseDouble(s) * mult);
		} catch (NumberFormatException e) {
			return 0L;
		}
	}

	private static long parseLong(String raw) {
		try {
			return (long) Double.parseDouble(raw.replace(",", ""));
		} catch (NumberFormatException e) {
			return -1L;
		}
	}

	/** Sidebar lines, top to bottom, without colour codes. */
	public static List<String> sidebar(Minecraft client) {
		if (client.level == null || client.player == null) {
			return List.of();
		}
		Scoreboard scoreboard = client.level.getScoreboard();
		Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		PlayerTeam own = scoreboard.getPlayersTeam(client.player.getScoreboardName());
		if (own != null && own.getColor().isColor()) {
			DisplaySlot slot = DisplaySlot.teamColorToSlot(own.getColor());
			if (slot != null && scoreboard.getDisplayObjective(slot) != null) {
				objective = scoreboard.getDisplayObjective(slot);
			}
		}
		if (objective == null) {
			return List.of();
		}
		List<PlayerScoreEntry> entries = new ArrayList<>();
		for (PlayerScoreEntry entry : scoreboard.listPlayerScores(objective)) {
			if (!entry.isHidden()) {
				entries.add(entry);
			}
		}
		entries.sort(Comparator.comparingInt(PlayerScoreEntry::value).reversed());
		List<String> lines = new ArrayList<>();
		for (PlayerScoreEntry entry : entries) {
			Component raw = entry.display() != null ? entry.display() : Component.literal(entry.owner());
			String text = PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), raw).getString()
				.replaceAll("§.", "")
				.replace(' ', ' ')
				.replaceAll("[\\p{Cf}\\p{Co}]", "")
				.replaceAll("\\s+", " ")
				.trim();
			if (!text.isEmpty()) {
				lines.add(text);
			}
		}
		return lines;
	}

	/** True when the slayer features may run (on SkyBlock, module settings decide the rest). */
	public static boolean active() {
		return SkyblockLocation.inSkyblock && StrayConfig.get() != null;
	}
}
