package dev.stray.client.mining;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.item.ItemText;
import dev.stray.client.location.SkyblockLocation;
import dev.stray.client.ui.StrayScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MiningTracker {
	private static final Pattern INLINE = Pattern.compile(
		"^(.+?)(?:\\s*[:\\-–]\\s*|\\s+)(\\d+(?:\\.\\d+)?\\s*%|\\d+\\s*/\\s*\\d+|done|complete|✔)$",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern PERCENT = Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*%$");
	private static final Pattern RATIO = Pattern.compile("^(\\d+)\\s*/\\s*(\\d+)$");
	private static final Pattern WIDGET = Pattern.compile(
		"^(players?\\b|info|area\\b|server\\b|gems\\b|profile\\b|sb level|bank\\b|skills\\b|stats\\b|event\\b|pet\\b|powders?\\b|crystals?\\b|pickaxe ability|unclaimed|forges?\\b|timers\\b|party\\b|slayer\\b|active effects|bestiary|essence|collection|fire sales|election|north stars|guests\\b|coop\\b|island\\b|minions?\\b|account info|dungeon stats|player stats|puzzles\\b|opened rooms).*",
		Pattern.CASE_INSENSITIVE
	);
	private static final Comparator<PlayerInfo> TAB_ORDER = Comparator
		.comparingInt((PlayerInfo info) -> -info.getTabListOrder())
		.thenComparingInt(info -> info.getGameMode() == GameType.SPECTATOR ? 1 : 0)
		.thenComparing(info -> {
			PlayerTeam team = info.getTeam();
			return team == null ? "" : team.getName();
		})
		.thenComparing(info -> info.getProfile().name(), String.CASE_INSENSITIVE_ORDER);
	private static final long ALERT_MS = 3500L;
	/**
	 * Cooldown starts on cast and is shared by every pickaxe ability.
	 * Mining Speed Boost, Maniac Miner, Gemstone Infusion, and Sheer Force
	 * are 120s at every level. Pickobulus is 60/50/40s and Tunnel Vision is
	 * 120/110/110s by ability level. A fuel tank (2/4/6/10%), the Pickaxe
	 * Cooldown attribute (1% per level, up to 10%), Sky Mall (−20%), and the
	 * mineshaft roll (−25%) shorten it. Entering a Glacite Mineshaft clears
	 * it. The tab widget and the held tool's Cooldown line already include
	 * those, so they replace this base.
	 */
	private static final int BASE_SECONDS = 120;
	private static final int PICKOBULUS_SECONDS = 60;
	private static final Pattern CLOCK = Pattern.compile(
		"(?i)(?:(\\d{1,2})\\s*m(?:in(?:ute)?s?)?(?:\\s*(\\d{1,2})\\s*s(?:ec(?:ond)?s?)?)?|(\\d{1,3})\\s*s(?:ec(?:ond)?s?)?|(\\d{1,2}):(\\d{2}))"
	);
	private static final Pattern ABILITY_HEADER = Pattern.compile("(?i)^pickaxe ability:?$");

	private static final Object LOCK = new Object();
	private static List<Commission> commissions = List.of();
	private static String ability = "Pickaxe";
	private static boolean abilityReady = true;
	private static long cooldownUntil;
	private static long cooldownTotal = BASE_SECONDS * 1000L;
	private static long cooldownStarted;
	private static String trackedArea = "";
	private static boolean sawArea;
	private static String alertName = "";
	private static long alertUntil;
	private static int parseTick = Integer.MIN_VALUE;
	private static int snapTick = Integer.MIN_VALUE;
	private static Snapshot snapCache;

	private MiningTracker() {
	}

	public static void tick(Minecraft client) {
		if (client.player == null || client.level == null) {
			synchronized (LOCK) {
				commissions = List.of();
			}
			parseTick = Integer.MIN_VALUE;
			snapTick = Integer.MIN_VALUE;
			snapCache = null;
			return;
		}
		if (!needed(client)) {
			return;
		}
		int t = client.player.tickCount;
		boolean hot;
		synchronized (LOCK) {
			hot = !commissions.isEmpty() || !abilityReady;
		}
		hot = hot || inMiningIsland();
		int interval = hot ? 10 : 20;
		if (parseTick != Integer.MIN_VALUE && t - parseTick < interval && t >= parseTick) {
			return;
		}
		parseTick = t;
		List<Commission> parsed = readCommissions(client);
		syncAbility(client);
		synchronized (LOCK) {
			commissions = parsed;
			if (!abilityReady && System.currentTimeMillis() >= cooldownUntil) {
				abilityReady = true;
			}
		}
		snapTick = Integer.MIN_VALUE;
	}

	public static void onChat(Component message) {
		String text = plain(message);
		if (text.isEmpty()) {
			return;
		}
		String key = text.toLowerCase(Locale.ROOT);
		if (key.contains("has expired")) {
			return;
		}
		int mentioned = key.contains("cooldown") ? clockSeconds(text) : -1;
		if (mentioned >= 0) {
			String named = abilityIn(text);
			applyRemaining(named == null ? ability : named, mentioned);
			return;
		}
		String name = abilityIn(text);
		if (name == null) {
			return;
		}
		if (key.contains("is now available")) {
			markReady(name, true);
			return;
		}
		if (key.contains("you used") && key.contains("pickaxe ability")) {
			startCooldown(name);
		}
	}

	public static void reset() {
		synchronized (LOCK) {
			commissions = List.of();
			alertName = "";
			alertUntil = 0L;
		}
		parseTick = Integer.MIN_VALUE;
		snapTick = Integer.MIN_VALUE;
		snapCache = null;
		trackedArea = "";
		sawArea = false;
	}

	public static Snapshot snapshot() {
		Minecraft client = Minecraft.getInstance();
		int tick = client.player == null ? -1 : client.player.tickCount;
		if (snapCache != null && tick == snapTick) {
			return snapCache;
		}
		Snapshot snap;
		synchronized (LOCK) {
			long now = System.currentTimeMillis();
			boolean ready = abilityReady || now >= cooldownUntil;
			float remain = 0f;
			float progress = 1f;
			String label = "Ready";
			if (!ready) {
				remain = Math.max(0f, (cooldownUntil - now) / 1000f);
				progress = cooldownTotal <= 0L ? 0f : 1f - Math.min(1f, (cooldownUntil - now) / (float) cooldownTotal);
				label = formatTime(remain);
			}
			boolean alert = StrayConfig.get().miningAbilityAlert && now < alertUntil && !alertName.isEmpty();
			float alertT = alert ? Math.min(1f, (alertUntil - now) / (float) ALERT_MS) : 0f;
			boolean mining = inMiningIsland() || !commissions.isEmpty() || !ready || alert;
			snap = new Snapshot(
				mining,
				SkyblockLocation.area,
				List.copyOf(commissions),
				ability,
				label,
				ready,
				progress,
				alert ? alertName : "",
				alertT
			);
		}
		snapTick = tick;
		snapCache = snap;
		return snap;
	}

	public static boolean inMiningIsland() {
		String area = SkyblockLocation.area.toLowerCase(Locale.ROOT);
		if (area.isEmpty()) {
			return false;
		}
		return area.contains("dwarven")
			|| area.contains("crystal hollow")
			|| area.contains("glacite")
			|| area.contains("mines of divan")
			|| area.contains("magma field")
			|| area.contains("precursor")
			|| area.contains("goblin holdout")
			|| area.contains("mithril deposit")
			|| area.contains("khazad")
			|| area.contains("the forge")
			|| area.contains("forge")
			|| area.contains("base camp")
			|| area.contains("mineshaft")
			|| area.contains("quarry")
			|| area.contains("rampart")
			|| area.contains("upper mines")
			|| area.contains("royal mines")
			|| area.contains("cliffside veins")
			|| area.contains("lava springs")
			|| area.contains("divan")
			|| area.contains("deep cavern");
	}

	private static void syncAbility(Minecraft client) {
		watchArea();
		AbilityLine live = readAbility(client);
		if (live == null) {
			return;
		}
		long now = System.currentTimeMillis();
		if (live.ready) {
			boolean freshCast;
			synchronized (LOCK) {
				freshCast = cooldownStarted > 0L && now - cooldownStarted < 4000L;
			}
			if (!freshCast) {
				markReady(live.name == null ? ability : live.name, true);
			}
			return;
		}
		if (live.seconds >= 0) {
			boolean stale;
			synchronized (LOCK) {
				long remain = Math.max(0L, cooldownUntil - now);
				stale = cooldownStarted > 0L && now - cooldownStarted < 4000L && live.seconds * 1000L + 3000L < remain;
			}
			if (!stale) {
				applyRemaining(live.name == null ? ability : live.name, live.seconds);
			}
		} else if (live.name != null) {
			synchronized (LOCK) {
				ability = live.name;
			}
		}
	}

	private static void watchArea() {
		String area = SkyblockLocation.area == null ? "" : SkyblockLocation.area;
		if (area.isEmpty()) {
			return;
		}
		boolean shaft = area.toLowerCase(Locale.ROOT).contains("mineshaft");
		boolean wasShaft = trackedArea.toLowerCase(Locale.ROOT).contains("mineshaft");
		if (sawArea && shaft && !wasShaft) {
			boolean cooling;
			synchronized (LOCK) {
				cooling = !abilityReady && System.currentTimeMillis() < cooldownUntil;
			}
			markReady(ability, cooling);
		}
		trackedArea = area;
		sawArea = true;
	}

	private static AbilityLine readAbility(Minecraft client) {
		if (client.player == null || client.player.connection == null) {
			return null;
		}
		List<PlayerInfo> infos = new ArrayList<>(client.player.connection.getListedOnlinePlayers());
		infos.sort(TAB_ORDER);
		boolean section = false;
		String name = null;
		int seconds = -1;
		boolean ready = false;
		boolean saw = false;
		int left = 0;
		for (PlayerInfo info : infos) {
			String line = cleanName(plain(tabName(info)));
			if (line.isEmpty()) {
				continue;
			}
			if (ABILITY_HEADER.matcher(line).matches()) {
				section = true;
				saw = true;
				left = 4;
				continue;
			}
			if (!section) {
				continue;
			}
			if (isNewWidget(line) || left-- <= 0) {
				break;
			}
			String found = abilityIn(line);
			if (found != null) {
				name = found;
			}
			String key = line.toLowerCase(Locale.ROOT);
			int clock = clockSeconds(line);
			if (clock >= 0) {
				seconds = clock;
				ready = false;
				continue;
			}
			if (key.contains("not ready")) {
				ready = false;
				continue;
			}
			if (key.contains("available") || key.equals("ready") || key.equals("ready!")) {
				ready = seconds < 0;
			}
		}
		if (!saw) {
			return null;
		}
		return new AbilityLine(name, seconds, ready && seconds < 0);
	}

	private static void startCooldown(String name) {
		int seconds = loreSeconds();
		if (seconds <= 0) {
			seconds = baseSeconds(name);
		}
		long now = System.currentTimeMillis();
		synchronized (LOCK) {
			ability = name;
			abilityReady = false;
			cooldownStarted = now;
			cooldownTotal = seconds * 1000L;
			cooldownUntil = now + cooldownTotal;
		}
		snapTick = Integer.MIN_VALUE;
	}

	private static void applyRemaining(String name, int seconds) {
		if (seconds <= 0) {
			markReady(name, true);
			return;
		}
		long now = System.currentTimeMillis();
		synchronized (LOCK) {
			if (name != null && !name.isBlank() && !"Pickaxe".equals(name)) {
				ability = name;
			}
			long end = now + seconds * 1000L;
			boolean replace = abilityReady || cooldownUntil <= now || end + 2000L < cooldownUntil || end > cooldownUntil + 2500L;
			abilityReady = false;
			if (!replace) {
				return;
			}
			cooldownUntil = end;
			if (cooldownStarted > 0L && end > cooldownStarted) {
				cooldownTotal = end - cooldownStarted;
			} else {
				cooldownTotal = Math.max(seconds, baseSeconds(ability)) * 1000L;
			}
		}
		snapTick = Integer.MIN_VALUE;
	}

	private static void markReady(String name, boolean alert) {
		boolean wasReady;
		synchronized (LOCK) {
			wasReady = abilityReady && cooldownUntil <= System.currentTimeMillis();
			if (name != null && !name.isBlank() && !"Pickaxe".equals(name)) {
				ability = name;
			}
			abilityReady = true;
			cooldownUntil = 0L;
			cooldownStarted = 0L;
			if (alert && !wasReady && StrayConfig.get().miningAbilityAlert) {
				alertName = ability;
				alertUntil = System.currentTimeMillis() + ALERT_MS;
			}
		}
		snapTick = Integer.MIN_VALUE;
	}

	private static String abilityIn(String text) {
		String key = text.toLowerCase(Locale.ROOT);
		if (key.contains("pickobulus") || key.contains("pickobolus")) {
			return "Pickobulus";
		}
		if (key.contains("mining speed boost")) {
			return "Mining Speed Boost";
		}
		if (key.contains("maniac miner")) {
			return "Maniac Miner";
		}
		if (key.contains("gemstone infusion")) {
			return "Gemstone Infusion";
		}
		if (key.contains("sheer force")) {
			return "Sheer Force";
		}
		if (key.contains("tunnel vision")) {
			return "Tunnel Vision";
		}
		if (key.contains("anomalous desire")) {
			return "Anomalous Desire";
		}
		return null;
	}

	private static int baseSeconds(String name) {
		String key = name == null ? "" : name.toLowerCase(Locale.ROOT);
		if (key.contains("pickobulus") || key.contains("pickobolus")) {
			return PICKOBULUS_SECONDS;
		}
		return BASE_SECONDS;
	}

	private static int loreSeconds() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return 0;
		}
		ItemStack held = client.player.getMainHandItem();
		if (held == null || held.isEmpty()) {
			return 0;
		}
		ItemText text = ItemText.capture(held);
		if (text.lore() == null) {
			return 0;
		}
		int found = 0;
		boolean ability = false;
		for (Component line : text.lore().lines()) {
			String plain = plain(line);
			if (abilityIn(plain) != null || plain.toLowerCase(Locale.ROOT).contains("pickaxe ability")) {
				ability = true;
			}
			if (!plain.toLowerCase(Locale.ROOT).contains("cooldown")) {
				continue;
			}
			int seconds = clockSeconds(plain);
			if (seconds >= 10 && seconds <= 180 && (ability || found == 0)) {
				found = seconds;
				if (ability) {
					return seconds;
				}
			}
		}
		return found;
	}

	private static int clockSeconds(String text) {
		if (text == null || text.isEmpty()) {
			return -1;
		}
		Matcher matcher = CLOCK.matcher(text);
		if (!matcher.find()) {
			return -1;
		}
		if (matcher.group(1) != null) {
			int minutes = Integer.parseInt(matcher.group(1));
			int seconds = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
			int total = minutes * 60 + seconds;
			return total <= 180 ? total : -1;
		}
		if (matcher.group(3) != null) {
			int seconds = Integer.parseInt(matcher.group(3));
			return seconds <= 180 ? seconds : -1;
		}
		int minutes = Integer.parseInt(matcher.group(4));
		int seconds = Integer.parseInt(matcher.group(5));
		if (seconds >= 60) {
			return -1;
		}
		int total = minutes * 60 + seconds;
		return total <= 180 ? total : -1;
	}

	private record AbilityLine(String name, int seconds, boolean ready) {
	}

	public static boolean hasTitaniumCommission() {
		return titaniumFilter().active();
	}

	public static MiningAreas.TitaniumFilter titaniumFilter() {
		List<Commission> copy;
		synchronized (LOCK) {
			copy = commissions;
		}
		return MiningAreas.filter(copy);
	}

	private static boolean needed(Minecraft client) {
		StrayConfig config = StrayConfig.get();
		if (config.miningHudEnabled || config.titaniumEsp) {
			return true;
		}
		return client.gui.screen() instanceof StrayScreen;
	}

	private static List<Commission> readCommissions(Minecraft client) {
		if (client.player == null) {
			return List.of();
		}
		ClientPacketListener connection = client.player.connection;
		if (connection == null) {
			return List.of();
		}
		List<PlayerInfo> infos = new ArrayList<>(connection.getListedOnlinePlayers());
		infos.sort(TAB_ORDER);
		List<Commission> out = new ArrayList<>(4);
		boolean section = false;
		String pending = null;
		int limit = Math.min(80, infos.size());
		for (int i = 0; i < limit; i++) {
			String line = cleanName(plain(tabName(infos.get(i))));
			if (line.isEmpty()) {
				continue;
			}
			if (isCommissionsHeader(line)) {
				section = true;
				pending = null;
				continue;
			}
			if (!section) {
				continue;
			}
			if (isNewWidget(line)) {
				break;
			}
			Progress only = progressOnly(line);
			if (only != null && pending != null) {
				out.add(new Commission(pending, only.label, only.fraction, only.done));
				pending = null;
				continue;
			}
			Commission inline = inline(line);
			if (inline != null) {
				out.add(inline);
				pending = null;
				continue;
			}
			pending = line;
		}
		return List.copyOf(out);
	}

	private static Component tabName(PlayerInfo info) {
		Component display = info.getTabListDisplayName();
		if (display != null) {
			return display;
		}
		return PlayerTeam.formatNameForTeam(info.getTeam(), Component.literal(info.getProfile().name()));
	}

	private static boolean isCommissionsHeader(String line) {
		String key = line.toLowerCase(Locale.ROOT);
		return key.equals("commissions") || key.equals("commission");
	}

	private static boolean isNewWidget(String line) {
		return WIDGET.matcher(line).matches();
	}

	private static Commission inline(String line) {
		Matcher matcher = INLINE.matcher(line);
		if (!matcher.matches()) {
			return null;
		}
		Progress progress = progressOnly(matcher.group(2).trim());
		if (progress == null) {
			return null;
		}
		String name = cleanName(matcher.group(1));
		if (name.isEmpty()) {
			return null;
		}
		return new Commission(name, progress.label, progress.fraction, progress.done);
	}

	private static Progress progressOnly(String line) {
		String text = line.trim();
		if (text.equalsIgnoreCase("done") || text.equalsIgnoreCase("complete") || text.equals("✔")) {
			return new Progress("Done", 1f, true);
		}
		Matcher percent = PERCENT.matcher(text);
		if (percent.matches()) {
			float value = Float.parseFloat(percent.group(1));
			float fraction = Math.max(0f, Math.min(1f, value / 100f));
			return new Progress(Math.round(value) + "%", fraction, fraction >= 0.999f);
		}
		Matcher ratio = RATIO.matcher(text);
		if (ratio.matches()) {
			int have = Integer.parseInt(ratio.group(1));
			int need = Math.max(1, Integer.parseInt(ratio.group(2)));
			float fraction = Math.max(0f, Math.min(1f, have / (float) need));
			return new Progress(have + "/" + need, fraction, have >= need);
		}
		return null;
	}

	private static String cleanName(String value) {
		if (value == null) {
			return "";
		}
		String text = value.replace('\u00A0', ' ').replaceAll("§.", "").trim();
		text = text.replaceAll("^[\\s•·▪▸►\\-–—★☆✔✅⏣]+", "");
		text = text.replaceAll("[:\\-–—]+$", "");
		return text.replaceAll("\\s+", " ").trim();
	}

	private static String plain(Component component) {
		return component == null ? "" : component.getString().replaceAll("§.", "").replace('\u00A0', ' ').trim();
	}

	private static String formatTime(float seconds) {
		int total = Math.max(0, Math.round(seconds));
		int min = total / 60;
		int sec = total % 60;
		if (min > 0) {
			return min + ":" + String.format(Locale.ROOT, "%02d", sec);
		}
		return sec + "s";
	}

	public record Commission(String name, String progress, float fraction, boolean done) {
	}

	public record Snapshot(
		boolean present,
		String area,
		List<Commission> commissions,
		String ability,
		String abilityLabel,
		boolean abilityReady,
		float abilityProgress,
		String alertName,
		float alertT
	) {
	}

	private record Progress(String label, float fraction, boolean done) {
	}
}
