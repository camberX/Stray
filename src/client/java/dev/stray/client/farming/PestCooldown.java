package dev.stray.client.farming;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.debug.StrayDebug;
import dev.stray.client.location.SkyblockLocation;
import dev.stray.client.visual.NickSteal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Garden pest spawn cooldown from the pests tab widget.
 * The widget time is stored as an end timestamp. It is replaced when the
 * widget is more than a few seconds later, or when a shorter time shows up,
 * which is what happens in a pest spawn reduction set. A minutes-only value
 * is treated as having just rolled over. The swap title still fires once the
 * cooldown has been running for 204 seconds.
 *
 * <p>Loadout Swap B uses a 5:00 cooldown. A downward jump on the widget, such
 * as {@code 4m 45s -> 1m 57s}, is the reduction. Swap B is that many seconds
 * off 5:00, then the configured seconds before the result hits 0.
 */
public final class PestCooldown {
	public static final int BASE_SECONDS = 506;
	public static final int REDUCED_SECONDS = 209;
	/** Unreduced pest cooldown the loadout swap starts from. */
	public static final int COOLDOWN_SECONDS = 5 * 60;
	public static final int SWAP_LEAD_SECONDS = 5;
	public static final int SWAP_SECONDS = REDUCED_SECONDS - SWAP_LEAD_SECONDS;
	private static final long HOLD_MS = 2_000L;
	private static final Pattern PESTS_HEADER = Pattern.compile("(?i)^(?:.*\\s)?pests?:?$");
	private static final Pattern COOLDOWN = Pattern.compile(
		"(?i)^cooldown:\\s*(?:(ready)|(max\\s+pests)|(\\d+)\\s*m(?:\\s*(\\d+)\\s*s)?|(\\d+)\\s*s)\\s*$"
	);
	private static final Comparator<PlayerInfo> TAB_ORDER = Comparator
		.comparingInt((PlayerInfo info) -> -info.getTabListOrder())
		.thenComparingInt(info -> info.getGameMode() == GameType.SPECTATOR ? 1 : 0)
		.thenComparing(info -> {
			PlayerTeam team = info.getTeam();
			return team == null ? "" : team.getName();
		})
		.thenComparing(info -> info.getProfile().name(), String.CASE_INSENSITIVE_ORDER);

	private static Snap snap = Snap.missing();
	private static long seenAt;
	private static int previousSeconds = -1;
	private static Kind previousKind = Kind.MISSING;
	private static boolean cycle;
	private static long anchorMs;
	private static int elapsedAtAnchor;
	private static long cooldownEndMs;
	private static boolean titled;
	private static int parseTick = Integer.MIN_VALUE;
	private static int alive = -1;
	private static boolean infested;
	/** Seconds cut off the 5:00 cooldown, learned from a widget drop. Kept for the next cycle. */
	private static int reductionSeconds;
	/** This cycle's widget already shows the shortened time. */
	private static boolean reducedThisCycle;
	private static int widgetSeconds = -1;
	private static long widgetAtMs;
	private static boolean previousShowsSeconds;
	private static final Pattern ALIVE = Pattern.compile("(?i)alive:\\s*(\\d+)");
	/** Infested-plot line on the pests widget. Present only while pests are up. */
	private static final Pattern PLOTS = Pattern.compile("(?i)^plots:\\s*\\d");

	private PestCooldown() {
	}

	public enum Kind {
		MISSING,
		COUNTING,
		READY,
		MAX
	}

	public record Snap(boolean present, Kind kind, int seconds, String label) {
		public static Snap missing() {
			return new Snap(false, Kind.MISSING, -1, "No widget");
		}

		public static Snap sample() {
			return new Snap(true, Kind.COUNTING, REDUCED_SECONDS, format(REDUCED_SECONDS));
		}
	}

	public static void tick(Minecraft client) {
		StrayConfig config = StrayConfig.get();
		boolean hud = config.pestCooldownHudEnabled;
		boolean watch = hud || StrayDebug.enabled("pest") || config.pestLoadoutSwap;
		if (!watch) {
			if (snap.present() || cycle || titled) {
				reset();
			}
			PestLoadoutSwap.tick(client);
			return;
		}
		if (client == null || client.player == null || client.level == null || !wherePestsLive()) {
			reset();
			return;
		}
		long now = System.currentTimeMillis();
		int tick = client.player.tickCount;
		boolean due = parseTick == Integer.MIN_VALUE || tick < parseTick || tick - parseTick >= 5;
		if (due) {
			parseTick = tick;
			List<String> lines = tabLines(client);
			alive = findAlive(lines);
			infested = findInfested(lines);
			Snap read = read(lines);
			if (read.present()) {
				track(read, now);
				seenAt = now;
			} else if (seenAt == 0L || now - seenAt > HOLD_MS) {
				snap = Snap.missing();
				clearCycle();
				previousKind = Kind.MISSING;
				previousSeconds = -1;
				alive = -1;
				infested = false;
			}
		}
		if (hud && cycle) {
			refresh(client, config, now);
		}
		PestLoadoutSwap.tick(client);
	}

	public static int alive() {
		return alive;
	}

	/** True when the pests widget lists infested plot numbers. */
	public static boolean infested() {
		return infested;
	}

	/** Seconds left on the tracked cooldown, or {@code -1} when it is not counting. */
	public static int secondsLeft() {
		long end = endsAt();
		if (end <= 0L) {
			return -1;
		}
		long leftMs = end - System.currentTimeMillis();
		if (leftMs <= 0L) {
			return 0;
		}
		return (int) ((leftMs + 999L) / 1000L);
	}

	/** Absolute cooldown end, or {@code 0} when the widget is not counting. */
	public static long endsAt() {
		if (!cycle || cooldownEndMs <= 0L) {
			return 0L;
		}
		return cooldownEndMs;
	}

	/**
	 * When the reduced cooldown hits 0. The length is {@link #COOLDOWN_SECONDS}
	 * minus the widget's reduction. After {@code 4m 45s -> 1m 57s}, that
	 * reduction is 168s, so 5:00 becomes 2:12. Swap B fires the configured
	 * seconds before this moment.
	 */
	public static long reducedEndsAt() {
		if (!cycle || widgetSeconds < 0 || widgetAtMs <= 0L) {
			return 0L;
		}
		long now = System.currentTimeMillis();
		long shownMs = widgetSeconds * 1000L - Math.max(0L, now - widgetAtMs);
		long untilZeroMs = shownMs;
		int shortened = COOLDOWN_SECONDS - reductionSeconds;
		if (reductionSeconds > 0 && !reducedThisCycle && widgetSeconds > shortened + 15) {
			untilZeroMs = shownMs - reductionSeconds * 1000L;
		}
		return now + untilZeroMs;
	}

	public static Snap snap() {
		return snap;
	}

	public static void reset() {
		snap = Snap.missing();
		seenAt = 0L;
		previousSeconds = -1;
		previousKind = Kind.MISSING;
		previousShowsSeconds = false;
		clearCycle();
		parseTick = Integer.MIN_VALUE;
		alive = -1;
		infested = false;
		PestLoadoutSwap.reset();
	}

	private static void clearCycle() {
		cycle = false;
		anchorMs = 0L;
		elapsedAtAnchor = 0;
		cooldownEndMs = 0L;
		titled = false;
		reducedThisCycle = false;
		widgetSeconds = -1;
		widgetAtMs = 0L;
	}

	private static void track(Snap read, long now) {
		if (read.kind() == Kind.MAX) {
			clearCycle();
			previousKind = Kind.MAX;
			previousSeconds = -1;
			previousShowsSeconds = false;
			snap = read;
			return;
		}
		if (read.kind() != Kind.COUNTING) {
			clearCycle();
			previousKind = read.kind();
			previousSeconds = -1;
			previousShowsSeconds = false;
			snap = Snap.missing();
			return;
		}
		int seconds = read.seconds();
		boolean secondsShown = showsSeconds(read);
		if (StrayDebug.enabled("pest")
			&& previousKind == Kind.COUNTING
			&& previousSeconds >= 0
			&& Math.abs(seconds - previousSeconds) > 10) {
			int diff = seconds - previousSeconds;
			String sign = diff > 0 ? "+" : "";
			StrayDebug.tell(
				"Pest cooldown " + format(previousSeconds) + " -> " + format(seconds) + " (" + sign + diff + "s)"
			);
		}
		boolean drop = previousKind == Kind.COUNTING
			&& previousShowsSeconds
			&& secondsShown
			&& previousSeconds - seconds > 30;
		boolean jumped = cycle && previousSeconds >= 0 && seconds > previousSeconds + 15;
		boolean opened = previousKind == Kind.READY || previousKind == Kind.MAX;
		if (!cycle || jumped || opened) {
			cycle = true;
			anchorMs = now;
			elapsedAtAnchor = jumped || opened ? 0 : Math.max(0, BASE_SECONDS - seconds);
			titled = !jumped && !opened && elapsedAtAnchor >= SWAP_SECONDS;
			reducedThisCycle = false;
		}
		if (drop) {
			reductionSeconds = previousSeconds - seconds;
			reducedThisCycle = true;
			if (StrayDebug.enabled("pest")) {
				int lead = StrayConfig.clamp(StrayConfig.get().pestLoadoutLead, 0, 30);
				int untilSwap = Math.max(0, COOLDOWN_SECONDS - reductionSeconds - lead);
				StrayDebug.tell(
					"Pest reduction " + reductionSeconds + "s off 5:00, swap " + lead + "s before 0 (" + format(untilSwap) + ")"
				);
			}
		}
		widgetSeconds = seconds;
		widgetAtMs = now;
		long tabEnd = now + seconds * 1000L;
		if (takeTabEnd(tabEnd, secondsShown)) {
			cooldownEndMs = secondsShown ? tabEnd : tabEnd + 60_000L;
		}
		previousKind = Kind.COUNTING;
		previousSeconds = seconds;
		previousShowsSeconds = secondsShown;
	}

	/**
	 * Widget updates lag by a few seconds and never sit much higher than the
	 * real cooldown. A shorter reading is real: either the minute rolled, or
	 * a reduction set cut the timer.
	 */
	private static boolean takeTabEnd(long tabEnd, boolean secondsShown) {
		if (cooldownEndMs <= 0L) {
			return true;
		}
		if (tabEnd > cooldownEndMs + 6_000L) {
			return true;
		}
		if (!secondsShown && tabEnd + 60_000L < cooldownEndMs) {
			return true;
		}
		return secondsShown && tabEnd + 1_000L < cooldownEndMs;
	}

	private static boolean showsSeconds(Snap read) {
		String label = read.label();
		return label != null && !label.matches("\\d+m");
	}

	private static void refresh(Minecraft client, StrayConfig config, long now) {
		if (cycle && !titled) {
			int elapsed = elapsedAtAnchor + (int) Math.max(0L, (now - anchorMs) / 1000L);
			if (elapsed >= SWAP_SECONDS && config.pestCooldownTitle) {
				titled = true;
				title(client);
			}
		}
		if (cooldownEndMs <= 0L) {
			return;
		}
		long leftMs = cooldownEndMs - now;
		if (leftMs <= 0L) {
			snap = Snap.missing();
			return;
		}
		int left = (int) ((leftMs + 999L) / 1000L);
		snap = new Snap(true, Kind.COUNTING, left, format(left));
	}

	private static String format(int seconds) {
		if (seconds >= 60) {
			return (seconds / 60) + "m " + (seconds % 60) + "s";
		}
		return seconds + "s";
	}

	private static void title(Minecraft client) {
		if (client.gui != null) {
			StrayConfig config = StrayConfig.get();
			String title = config.pestCooldownAlert == null || config.pestCooldownAlert.isBlank() ? "Swap armor" : config.pestCooldownAlert;
			String subtitle = config.pestCooldownAlertSub == null || config.pestCooldownAlertSub.isBlank() ? "5s left" : config.pestCooldownAlertSub;
			client.gui.hud.setTimes(8, 50, 12);
			client.gui.hud.setTitle(Component.literal(title).withColor(0xFF5A4A));
			client.gui.hud.setSubtitle(Component.literal(subtitle));
		}
		client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 0.7f, 0.9f));
	}

	private static Snap read(List<String> lines) {
		Snap inSection = null;
		Snap anywhere = null;
		boolean inPests = false;
		for (String line : lines) {
			if (pestsHeader(line)) {
				inPests = true;
				continue;
			}
			if (inPests && otherHeader(line)) {
				inPests = false;
				continue;
			}
			Snap parsed = parse(line);
			if (parsed == null) {
				continue;
			}
			if (anywhere == null) {
				anywhere = parsed;
			}
			if (inPests) {
				inSection = parsed;
				break;
			}
		}
		return inSection != null ? inSection : anywhere != null ? anywhere : Snap.missing();
	}

	private static Snap parse(String line) {
		Matcher matcher = COOLDOWN.matcher(line);
		if (!matcher.matches()) {
			return null;
		}
		if (matcher.group(1) != null) {
			return new Snap(true, Kind.READY, 0, "Ready");
		}
		if (matcher.group(2) != null) {
			return new Snap(true, Kind.MAX, -1, "Max pests");
		}
		if (matcher.group(3) != null) {
			int minutes = Integer.parseInt(matcher.group(3));
			int seconds = matcher.group(4) == null ? 0 : Integer.parseInt(matcher.group(4));
			return new Snap(true, Kind.COUNTING, minutes * 60 + seconds, clock(minutes, seconds, matcher.group(4) != null));
		}
		int seconds = Integer.parseInt(matcher.group(5));
		return new Snap(true, Kind.COUNTING, seconds, seconds + "s");
	}

	private static String clock(int minutes, int seconds, boolean hadSeconds) {
		if (!hadSeconds) {
			return minutes + "m";
		}
		return minutes + "m " + seconds + "s";
	}

	private static int findAlive(List<String> lines) {
		for (String line : lines) {
			Matcher matcher = ALIVE.matcher(line);
			if (matcher.find()) {
				try {
					return Integer.parseInt(matcher.group(1));
				} catch (NumberFormatException ignored) {
					return -1;
				}
			}
		}
		return -1;
	}

	private static boolean findInfested(List<String> lines) {
		for (String line : lines) {
			if (PLOTS.matcher(line).find()) {
				return true;
			}
		}
		return false;
	}

	private static boolean pestsHeader(String line) {
		if (line.length() > 24 || line.toLowerCase(Locale.ROOT).contains("cooldown")) {
			return false;
		}
		return PESTS_HEADER.matcher(line).matches();
	}

	private static boolean otherHeader(String line) {
		String key = line.toLowerCase(Locale.ROOT);
		if (!key.endsWith(":")) {
			return false;
		}
		return !key.startsWith("alive")
			&& !key.startsWith("cooldown")
			&& !key.startsWith("infested")
			&& !key.startsWith("bonus")
			&& !key.startsWith("plots");
	}

	private static List<String> tabLines(Minecraft client) {
		ClientPacketListener connection = client.player.connection;
		if (connection == null) {
			return List.of();
		}
		List<PlayerInfo> infos = new ArrayList<>(connection.getListedOnlinePlayers());
		infos.sort(TAB_ORDER);
		List<String> lines = new ArrayList<>(infos.size());
		for (PlayerInfo info : infos) {
			Component display = info.getTabListDisplayName();
			Component component = display != null
				? display
				: PlayerTeam.formatNameForTeam(info.getTeam(), Component.literal(info.getProfile().name()));
			String colored = component == null ? "" : NickSteal.toLegacy(component);
			String line = clean(colored);
			if (!line.isEmpty()) {
				lines.add(line);
			}
		}
		return lines;
	}

	private static String clean(String text) {
		if (text == null) {
			return "";
		}
		return text.replaceAll("§.", "")
			.replace('\u00A0', ' ')
			.replaceAll("\\s+", " ")
			.trim();
	}

	private static boolean wherePestsLive() {
		if (SkyblockLocation.inGarden()) {
			return true;
		}
		if (!SkyblockLocation.inSkyblock) {
			return false;
		}
		String area = SkyblockLocation.area == null ? "" : SkyblockLocation.area.toLowerCase(Locale.ROOT);
		String poi = SkyblockLocation.poi == null ? "" : SkyblockLocation.poi.toLowerCase(Locale.ROOT);
		return area.contains("greenhouse") || poi.contains("greenhouse");
	}
}
