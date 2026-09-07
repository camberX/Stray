package dev.voidmark.client.farming;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads Hypixel's Jacob widget from the player list and projects the contest result. */
public final class JacobContestTracker {
	private static final int CONTEST_SECONDS = 20 * 60;
	private static final int SAMPLE_WINDOW_SECONDS = 60;
	private static final Pattern HEADER = Pattern.compile("^jacob'?s contest:?\\s*(.*)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern TIME = Pattern.compile("^(?:(\\d+)m\\s*)?(\\d+)s(?:\\s+left)?$", Pattern.CASE_INSENSITIVE);
	private static final Pattern RANK = Pattern.compile(
		"^(BRONZE|SILVER|GOLD|PLATINUM|DIAMOND)\\s+(?:with\\s+)?([\\d,.]+[kmb]?)$",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern GAP = Pattern.compile(
		"^([\\d,.]+[kmb]?)\\s+(below|over)\\s+(BRONZE|SILVER|GOLD|PLATINUM|DIAMOND)$",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern CROP = Pattern.compile("^(.+?)\\s+([\\d,.]+[kmb]?)$", Pattern.CASE_INSENSITIVE);
	private static final Comparator<PlayerInfo> TAB_ORDER = Comparator
		.comparingInt((PlayerInfo info) -> -info.getTabListOrder())
		.thenComparingInt(info -> info.getGameMode() == GameType.SPECTATOR ? 1 : 0)
		.thenComparing(info -> {
			PlayerTeam team = info.getTeam();
			return team == null ? "" : team.getName();
		})
		.thenComparing(info -> info.getProfile().name(), String.CASE_INSENSITIVE_ORDER);

	private static final Deque<ScoreSample> SCORE_SAMPLES = new ArrayDeque<>();
	private static final Map<Medal, Deque<CutoffSample>> CUTOFF_SAMPLES = new EnumMap<>(Medal.class);
	private static Parsed latest;
	private static Snapshot snapshot = Snapshot.empty();
	private static int parseTick = Integer.MIN_VALUE;
	private static int missingTicks;
	private static int lastScore = -1;
	private static long updateTotal;
	private static int updateCount;

	private JacobContestTracker() {
	}

	public static void tick(Minecraft client) {
		if (client == null || client.player == null || client.level == null) {
			reset();
			return;
		}
		int tick = client.player.tickCount;
		if (parseTick != Integer.MIN_VALUE && tick >= parseTick && tick - parseTick < 5) {
			return;
		}
		parseTick = tick;
		Parsed parsed = read(client);
		if (parsed == null) {
			if (++missingTicks >= 8) {
				clearContest();
			}
			return;
		}
		missingTicks = 0;
		if (newContest(parsed)) {
			clearSamples();
		}
		record(parsed);
		latest = parsed;
		snapshot = project(parsed);
	}

	public static Snapshot snapshot() {
		return snapshot;
	}

	public static void reset() {
		clearContest();
		parseTick = Integer.MIN_VALUE;
	}

	private static Parsed read(Minecraft client) {
		ClientPacketListener connection = client.player.connection;
		if (connection == null) {
			return null;
		}
		List<PlayerInfo> infos = new ArrayList<>(connection.getListedOnlinePlayers());
		infos.sort(TAB_ORDER);
		List<String> lines = new ArrayList<>(infos.size());
		for (PlayerInfo info : infos) {
			String line = clean(tabName(info));
			if (!line.isEmpty()) {
				lines.add(line);
			}
		}
		for (int i = 0; i < lines.size(); i++) {
			Matcher header = HEADER.matcher(lines.get(i));
			if (!header.matches()) {
				continue;
			}
			int remaining = parseTime(header.group(1));
			String crop = "";
			int score = -1;
			Medal rank = Medal.NONE;
			Map<Medal, Integer> cutoffs = new EnumMap<>(Medal.class);
			int limit = Math.min(lines.size(), i + 8);
			for (int j = i + 1; j < limit; j++) {
				String line = lines.get(j);
				if (remaining < 0) {
					remaining = parseTime(line);
					if (remaining >= 0) {
						continue;
					}
				}
				Matcher rankLine = RANK.matcher(line);
				if (rankLine.matches()) {
					rank = Medal.valueOf(rankLine.group(1).toUpperCase(Locale.ROOT));
					score = parseAmount(rankLine.group(2));
					continue;
				}
				Matcher gap = GAP.matcher(line);
				if (gap.matches() && score >= 0) {
					int amount = parseAmount(gap.group(1));
					Medal medal = Medal.valueOf(gap.group(3).toUpperCase(Locale.ROOT));
					int cutoff = gap.group(2).equalsIgnoreCase("below") ? score + amount : score - amount;
					cutoffs.put(medal, Math.max(0, cutoff));
					continue;
				}
				Matcher cropLine = CROP.matcher(line);
				if (crop.isEmpty() && cropLine.matches() && !isWidget(cropLine.group(1))) {
					crop = cropLine.group(1).trim();
					if (score < 0) {
						score = parseAmount(cropLine.group(2));
					}
				}
			}
			if (remaining >= 0 && score >= 0 && rank != Medal.NONE) {
				return new Parsed(crop.isEmpty() ? "Farming" : crop, remaining, score, rank, cutoffs);
			}
		}
		return null;
	}

	private static void record(Parsed parsed) {
		if (lastScore >= 0 && parsed.score > lastScore) {
			updateTotal += parsed.score - lastScore;
			updateCount++;
		}
		lastScore = parsed.score;
		SCORE_SAMPLES.addLast(new ScoreSample(parsed.remaining, parsed.score));
		trimScores(parsed.remaining);
		CUTOFF_SAMPLES.keySet().retainAll(parsed.cutoffs.keySet());
		for (Map.Entry<Medal, Integer> entry : parsed.cutoffs.entrySet()) {
			Deque<CutoffSample> samples = CUTOFF_SAMPLES.computeIfAbsent(entry.getKey(), ignored -> new ArrayDeque<>());
			samples.addLast(new CutoffSample(parsed.remaining, entry.getValue()));
			while (samples.size() > 1 && samples.peekFirst().remaining - parsed.remaining > SAMPLE_WINDOW_SECONDS) {
				samples.removeFirst();
			}
		}
	}

	private static Snapshot project(Parsed parsed) {
		double perSecond = rate(SCORE_SAMPLES, parsed.score, parsed.remaining);
		int projectedScore = safeRound(parsed.score + perSecond * parsed.remaining);
		Medal projectedRank = parsed.rank;
		int demotionCap = Medal.DIAMOND.ordinal();
		for (Medal medal : parsed.cutoffs.keySet()) {
			Deque<CutoffSample> samples = CUTOFF_SAMPLES.get(medal);
			if (samples == null || samples.isEmpty()) {
				continue;
			}
			CutoffSample current = samples.peekLast();
			double cutoffRate = cutoffRate(samples, current.value, parsed.remaining);
			int projectedCutoff = safeRound(current.value + cutoffRate * parsed.remaining);
			if (projectedScore >= projectedCutoff) {
				if (medal.ordinal() > projectedRank.ordinal()) {
					projectedRank = medal;
				}
			} else if (medal.ordinal() <= parsed.rank.ordinal()) {
				demotionCap = Math.min(demotionCap, medal.ordinal() - 1);
			}
		}
		if (projectedRank.ordinal() > demotionCap) {
			projectedRank = Medal.values()[Math.max(Medal.NONE.ordinal(), demotionCap)];
		}
		double perUpdate = updateCount == 0 ? 0d : updateTotal / (double) updateCount;
		return new Snapshot(
			true,
			parsed.crop,
			formatTime(parsed.remaining),
			parsed.score,
			parsed.rank,
			projectedRank,
			Math.max(parsed.score, projectedScore),
			Math.max(0d, perSecond),
			Math.max(0d, perUpdate),
			updateCount
		);
	}

	private static double rate(Deque<ScoreSample> samples, int current, int remaining) {
		ScoreSample first = samples.peekFirst();
		if (first != null) {
			int seconds = first.remaining - remaining;
			if (seconds >= 2 && current >= first.value) {
				return (current - first.value) / (double) seconds;
			}
		}
		int elapsed = CONTEST_SECONDS - remaining;
		return elapsed > 0 ? current / (double) elapsed : 0d;
	}

	private static double cutoffRate(Deque<CutoffSample> samples, int current, int remaining) {
		CutoffSample first = samples.peekFirst();
		if (first != null) {
			int seconds = first.remaining - remaining;
			if (seconds >= 2) {
				return (current - first.value) / (double) seconds;
			}
		}
		int elapsed = CONTEST_SECONDS - remaining;
		return elapsed > 0 ? current / (double) elapsed : 0d;
	}

	private static boolean newContest(Parsed parsed) {
		if (latest == null) {
			return true;
		}
		return !latest.crop.equalsIgnoreCase(parsed.crop)
			|| parsed.remaining > latest.remaining + 10
			|| parsed.score < latest.score;
	}

	private static void trimScores(int remaining) {
		while (SCORE_SAMPLES.size() > 1 && SCORE_SAMPLES.peekFirst().remaining - remaining > SAMPLE_WINDOW_SECONDS) {
			SCORE_SAMPLES.removeFirst();
		}
	}

	private static void clearContest() {
		latest = null;
		snapshot = Snapshot.empty();
		missingTicks = 0;
		clearSamples();
	}

	private static void clearSamples() {
		SCORE_SAMPLES.clear();
		CUTOFF_SAMPLES.clear();
		lastScore = -1;
		updateTotal = 0L;
		updateCount = 0;
	}

	private static int parseTime(String value) {
		if (value == null) {
			return -1;
		}
		Matcher matcher = TIME.matcher(value.trim());
		if (!matcher.matches()) {
			return -1;
		}
		int minutes = matcher.group(1) == null ? 0 : Integer.parseInt(matcher.group(1));
		int seconds = Integer.parseInt(matcher.group(2));
		return Math.min(CONTEST_SECONDS, minutes * 60 + seconds);
	}

	private static int parseAmount(String value) {
		String text = value.toLowerCase(Locale.ROOT).replace(",", "").trim();
		double multiplier = 1d;
		if (text.endsWith("k")) {
			multiplier = 1_000d;
			text = text.substring(0, text.length() - 1);
		} else if (text.endsWith("m")) {
			multiplier = 1_000_000d;
			text = text.substring(0, text.length() - 1);
		} else if (text.endsWith("b")) {
			multiplier = 1_000_000_000d;
			text = text.substring(0, text.length() - 1);
		}
		try {
			return safeRound(Double.parseDouble(text) * multiplier);
		} catch (NumberFormatException ignored) {
			return -1;
		}
	}

	private static int safeRound(double value) {
		return (int) Math.max(0d, Math.min(Integer.MAX_VALUE, Math.round(value)));
	}

	private static Component tabName(PlayerInfo info) {
		Component display = info.getTabListDisplayName();
		return display != null
			? display
			: PlayerTeam.formatNameForTeam(info.getTeam(), Component.literal(info.getProfile().name()));
	}

	private static String clean(Component component) {
		return component == null
			? ""
			: component.getString().replaceAll("§.", "").replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
	}

	private static boolean isWidget(String value) {
		String key = value.trim().toLowerCase(Locale.ROOT);
		return key.equals("jacob's contest")
			|| key.equals("jacobs contest")
			|| key.equals("event")
			|| key.equals("farming");
	}

	private static String formatTime(int seconds) {
		return (seconds / 60) + ":" + String.format(Locale.ROOT, "%02d", seconds % 60);
	}

	public enum Medal {
		NONE,
		BRONZE,
		SILVER,
		GOLD,
		PLATINUM,
		DIAMOND
	}

	public record Snapshot(
		boolean present,
		String crop,
		String remaining,
		int score,
		Medal currentRank,
		Medal projectedRank,
		int projectedScore,
		double perSecond,
		double perUpdate,
		int updates
	) {
		private static Snapshot empty() {
			return new Snapshot(false, "", "0:00", 0, Medal.NONE, Medal.NONE, 0, 0d, 0d, 0);
		}
	}

	private record Parsed(String crop, int remaining, int score, Medal rank, Map<Medal, Integer> cutoffs) {
	}

	private record ScoreSample(int remaining, int value) {
	}

	private record CutoffSample(int remaining, int value) {
	}
}
