package dev.stray.client.farming;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.debug.StrayDebug;
import dev.stray.client.ui.LoadoutSwap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pest-farming loadout cycle. Swap B equips when the cooldown reaches the
 * configured seconds left. After pests spawn, TriSwap waits 1–2 seconds and
 * equips Swap C. Swap A comes back once every pest is dead.
 *
 * <p>A spawn is the garden chat line ({@code GROSS! A Pest has appeared},
 * {@code YUCK! 4 Pests have spawned}), the scoreboard {@code The Garden} /
 * {@code Plot -} row with {@code xN}, or the pests tab line {@code Plots:}.
 * A pest armor stand within range counts too, with Pest ESP off.
 */
public final class PestLoadoutSwap {
	private enum Phase {
		FARM,
		WAIT_SPAWN,
		DELAY,
		WAIT_CLEAR
	}

	/** {@code GROSS! A  Pest has appeared} and {@code YUCK! 4  Pests have spawned}. */
	private static final Pattern SPAWN = Pattern.compile(
		"(?i)^\\w+!\\s+(?:a\\s+)?(?:[\\uE018\\uE07F]\\s*)?(?:(\\d+)\\s+)?(?:[\\uE018\\uE07F]\\s*)?pests?\\s+(?:has appeared|have spawned|has spawned)\\b"
	);
	private static final Pattern OFFLINE = Pattern.compile(
		"(?i)^\\w+!\\s+while you were offline,\\s*(?:[\\uE018\\uE07F]\\s*)?pests?\\s+spawned\\b"
	);
	private static final Pattern KILL = Pattern.compile("(?i)^you received\\b.+\\bfor killing an?\\b");
	/** Scoreboard pest row keeps the pest icon and ends in {@code xN}. */
	private static final Pattern BOARD_COUNT = Pattern.compile(
		"(?i)(?:the garden|plot\\s*-\\s*.+?)\\s*[\\uE018\\uE07F]?\\s*x\\s*(\\d+)\\s*$"
	);
	/** Same row with the icon gone means the garden is clear. The {@code ⏣} area line is not this row. */
	private static final Pattern BOARD_CLEAR = Pattern.compile(
		"(?i)^[^\\p{L}⏣]*+(?:the garden|plot\\s*-\\s*\\S.*)$"
	);
	private static final long CHAT_GRACE_MS = 2_000L;

	private static Phase phase = Phase.FARM;
	private static boolean hold;
	private static long waitSince;
	private static long delayUntil;
	/** Pests still alive. Chat sets this before the scoreboard catches up. */
	private static int wave;
	private static long chatAt;
	private static int board = -1;
	private static boolean near;
	private static boolean up;

	private PestLoadoutSwap() {
	}

	public static void reset() {
		phase = Phase.FARM;
		hold = false;
		waitSince = 0L;
		delayUntil = 0L;
		wave = 0;
		chatAt = 0L;
		board = -1;
		near = false;
		up = false;
		LoadoutSwap.cancelQueue();
	}

	public static void onMessage(Component message, boolean overlay) {
		if (overlay || message == null || !StrayConfig.get().pestLoadoutSwap) {
			return;
		}
		String plain = ChatFormatting.stripFormatting(message.getString());
		if (plain == null || plain.isEmpty()) {
			return;
		}
		plain = plain.replace('\n', ' ').trim();
		Matcher spawn = SPAWN.matcher(plain);
		if (spawn.find()) {
			int count = 1;
			if (spawn.group(1) != null) {
				try {
					count = Math.max(1, Integer.parseInt(spawn.group(1)));
				} catch (NumberFormatException ignored) {
					count = 1;
				}
			}
			noteChat(count);
			return;
		}
		if (OFFLINE.matcher(plain).find()) {
			noteChat(Math.max(wave, 1));
			return;
		}
		if (wave > 0 && KILL.matcher(plain).find()) {
			wave--;
		}
	}

	public static void tick(Minecraft client) {
		StrayConfig config = StrayConfig.get();
		if (!config.pestLoadoutSwap || client == null || client.player == null) {
			if (phase != Phase.FARM || hold || wave > 0) {
				reset();
			}
			return;
		}
		long now = System.currentTimeMillis();
		sense(client, phase != Phase.FARM);
		int lead = StrayConfig.clamp(config.pestLoadoutLead, 0, 30);
		int left = PestCooldown.secondsLeft();
		switch (phase) {
			case FARM -> {
				if (left < 0) {
					return;
				}
				if (hold) {
					if (left > lead) {
						hold = false;
					}
					return;
				}
				if (left <= lead && LoadoutSwap.queue(slot(config.loadoutSwapSlotB))) {
					phase = Phase.WAIT_SPAWN;
					waitSince = now;
				}
			}
			case WAIT_SPAWN -> {
				if (pestsPresent()) {
					if (config.loadoutSwapTri) {
						delayUntil = now + 1000L + ThreadLocalRandom.current().nextInt(1001);
						phase = Phase.DELAY;
					} else {
						phase = Phase.WAIT_CLEAR;
					}
					return;
				}
				if (now - waitSince > (lead + 20L) * 1000L && LoadoutSwap.queue(slot(config.loadoutSwapSlotA))) {
					hold = true;
					phase = Phase.FARM;
				}
			}
			case DELAY -> {
				if (!config.loadoutSwapTri) {
					phase = Phase.WAIT_CLEAR;
					return;
				}
				if (pestsClear()) {
					if (LoadoutSwap.queue(slot(config.loadoutSwapSlotA))) {
						hold = true;
						phase = Phase.FARM;
					}
					return;
				}
				if (now >= delayUntil && LoadoutSwap.queue(slot(config.loadoutSwapSlotC))) {
					phase = Phase.WAIT_CLEAR;
				}
			}
			case WAIT_CLEAR -> {
				if (pestsClear() && LoadoutSwap.queue(slot(config.loadoutSwapSlotA))) {
					hold = true;
					phase = Phase.FARM;
				}
			}
		}
	}

	private static void noteChat(int count) {
		wave = Math.max(wave, count);
		chatAt = System.currentTimeMillis();
	}

	private static void sense(Minecraft client, boolean scanWorld) {
		board = scoreboardPests(client);
		near = scanWorld && PestEsp.nearby(client);
		int alive = PestCooldown.alive();
		boolean plots = PestCooldown.infested();
		long now = System.currentTimeMillis();
		if (board > 0) {
			wave = board;
		} else if (alive > 0) {
			wave = Math.max(wave, alive);
		} else if (plots || near) {
			wave = Math.max(wave, 1);
		} else if (board == 0 && (chatAt == 0L || now - chatAt > CHAT_GRACE_MS)) {
			wave = 0;
		}
		boolean present = pestsPresent();
		if (StrayDebug.enabled("pest") && present != up) {
			StrayDebug.tell(present ? "Pest spawn (" + via(alive, plots) + ")" : "Pests dead");
		}
		up = present;
	}

	private static String via(int alive, boolean plots) {
		long now = System.currentTimeMillis();
		if (chatAt > 0L && now - chatAt < 3_000L && board <= 0) {
			return "chat " + wave;
		}
		if (board > 0) {
			return "scoreboard " + board;
		}
		if (alive > 0) {
			return "tab alive " + alive;
		}
		if (plots) {
			return "tab plots";
		}
		if (near) {
			return "nearby";
		}
		return "chat " + wave;
	}

	private static boolean pestsPresent() {
		return wave > 0 || board > 0 || PestCooldown.alive() > 0 || PestCooldown.infested() || near;
	}

	private static boolean pestsClear() {
		if (pestsPresent()) {
			return false;
		}
		long sinceChat = chatAt == 0L ? Long.MAX_VALUE : System.currentTimeMillis() - chatAt;
		if (sinceChat <= CHAT_GRACE_MS) {
			return false;
		}
		return board == 0 || PestCooldown.alive() == 0;
	}

	private static int scoreboardPests(Minecraft client) {
		int count = -1;
		for (String line : sidebar(client)) {
			Matcher counted = BOARD_COUNT.matcher(line);
			if (counted.find()) {
				try {
					count = Math.max(count, Integer.parseInt(counted.group(1)));
				} catch (NumberFormatException ignored) {
					count = Math.max(count, 1);
				}
				continue;
			}
			if (count <= 0 && BOARD_CLEAR.matcher(line).matches()) {
				count = 0;
			}
		}
		return count;
	}

	private static List<String> sidebar(Minecraft client) {
		if (client.level == null || client.player == null) {
			return List.of();
		}
		Scoreboard scoreboard = client.level.getScoreboard();
		Objective objective = sidebarObjective(scoreboard, client.player.getScoreboardName());
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
		List<String> lines = new ArrayList<>(entries.size());
		for (PlayerScoreEntry entry : entries) {
			Component raw = entry.display() != null ? entry.display() : Component.literal(entry.owner());
			String text = clean(PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), raw));
			if (!text.isEmpty()) {
				lines.add(text);
			}
		}
		return lines;
	}

	private static Objective sidebarObjective(Scoreboard scoreboard, String playerName) {
		Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		PlayerTeam team = scoreboard.getPlayersTeam(playerName);
		if (team != null && team.getColor().isColor()) {
			DisplaySlot colored = DisplaySlot.teamColorToSlot(team.getColor());
			if (colored != null) {
				Objective teamObjective = scoreboard.getDisplayObjective(colored);
				if (teamObjective != null) {
					return teamObjective;
				}
			}
		}
		return objective;
	}

	private static String clean(Component component) {
		if (component == null) {
			return "";
		}
		String text = component.getString();
		if (text == null) {
			return "";
		}
		return text.replaceAll("§.", "").replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
	}

	private static int slot(int configured) {
		return StrayConfig.clampLoadoutSwapSlot(configured) - 1;
	}
}
