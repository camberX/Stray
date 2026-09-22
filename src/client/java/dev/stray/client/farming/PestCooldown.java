package dev.stray.client.farming;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.location.SkyblockLocation;
import dev.stray.client.visual.NickSteal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
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
 * The two-minute title fires once each time the remaining time crosses 120 seconds.
 */
public final class PestCooldown {
	public static final int TITLE_SECONDS = 120;
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
	private static boolean titled;
	private static int parseTick = Integer.MIN_VALUE;

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
			return new Snap(true, Kind.COUNTING, 118, "1m 58s");
		}
	}

	public static void tick(Minecraft client) {
		StrayConfig config = StrayConfig.get();
		if (!config.pestCooldownHudEnabled) {
			if (snap.present() || titled) {
				reset();
			}
			return;
		}
		if (client == null || client.player == null || client.level == null || !wherePestsLive()) {
			reset();
			return;
		}
		int tick = client.player.tickCount;
		if (parseTick != Integer.MIN_VALUE && tick >= parseTick && tick - parseTick < 5) {
			return;
		}
		parseTick = tick;
		Snap read = read(client);
		long now = System.currentTimeMillis();
		if (read.present()) {
			note(client, config, read);
			snap = read;
			seenAt = now;
			return;
		}
		if (seenAt == 0L || now - seenAt > HOLD_MS) {
			snap = Snap.missing();
			previousSeconds = -1;
		}
	}

	public static Snap snap() {
		return snap;
	}

	public static void reset() {
		snap = Snap.missing();
		seenAt = 0L;
		previousSeconds = -1;
		titled = false;
		parseTick = Integer.MIN_VALUE;
	}

	private static void note(Minecraft client, StrayConfig config, Snap read) {
		int seconds = read.seconds();
		if (read.kind() != Kind.COUNTING) {
			previousSeconds = -1;
			titled = false;
			return;
		}
		if (seconds > TITLE_SECONDS) {
			titled = false;
		} else if (!titled && previousSeconds > TITLE_SECONDS && config.pestCooldownTitle) {
			titled = true;
			title(client);
		}
		previousSeconds = seconds;
	}

	private static void title(Minecraft client) {
		Gui gui = client.gui;
		if (gui != null) {
			gui.setTimes(8, 50, 12);
			gui.setTitle(Component.literal("Pest cooldown").withColor(0xFF5A4A));
			gui.setSubtitle(Component.literal("2:00 left"));
		}
		client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 0.7f, 0.9f));
	}

	private static Snap read(Minecraft client) {
		List<String> lines = tabLines(client);
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
