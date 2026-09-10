package dev.stray.client.farming;

import dev.stray.client.config.StrayConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads Hypixel's Garden composter widget and applies SkyHanni's composter formulas. */
public final class ComposterTracker {
	private static final Pattern VALUE = Pattern.compile(
		"^(Organic Matter|Fuel|Stored Compost):\\s*([\\d,.]+[kmb]?)$",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern TIME = Pattern.compile("^Time Left:\\s*(.+)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern UPGRADE = Pattern.compile(
		"^(Composter Speed|Multi Drop|Fuel Cap|Organic Matter Cap|Cost Reduction)(?:\\s+([IVXLCDM]+|\\d+))?$",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern DURATION = Pattern.compile(
		"(?:(\\d+)d\\s*)?(?:(\\d+)h\\s*)?(?:(\\d+)m\\s*)?(?:(\\d+)s)?",
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

	private static Snapshot snapshot = Snapshot.empty();
	private static int parseTick = Integer.MIN_VALUE;
	private static int missingTicks;

	private ComposterTracker() {
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
		readUpgrades(client);
		Parsed parsed = readWidget(client);
		if (parsed == null) {
			if (++missingTicks >= 8) {
				snapshot = Snapshot.empty();
			}
			return;
		}
		missingTicks = 0;
		snapshot = calculate(parsed, StrayConfig.get());
	}

	public static Snapshot snapshot() {
		return snapshot;
	}

	public static void reset() {
		snapshot = Snapshot.empty();
		parseTick = Integer.MIN_VALUE;
		missingTicks = 0;
	}

	private static Parsed readWidget(Minecraft client) {
		ClientPacketListener connection = client.player.connection;
		if (connection == null) {
			return null;
		}
		List<PlayerInfo> infos = new ArrayList<>(connection.getListedOnlinePlayers());
		infos.sort(TAB_ORDER);
		List<String> lines = new ArrayList<>(infos.size());
		for (PlayerInfo info : infos) {
			lines.add(clean(tabName(info)));
		}
		for (int i = 0; i < lines.size(); i++) {
			if (!lines.get(i).equalsIgnoreCase("Composter:")) {
				continue;
			}
			long organic = -1;
			long fuel = -1;
			long stored = -1;
			String time = "";
			boolean started = false;
			for (int j = i + 1; j < Math.min(lines.size(), i + 8); j++) {
				String line = lines.get(j);
				if (line.isEmpty()) {
					if (started) {
						break;
					}
					continue;
				}
				Matcher value = VALUE.matcher(line);
				if (value.matches()) {
					started = true;
					long amount = parseAmount(value.group(2));
					switch (value.group(1).toLowerCase(Locale.ROOT)) {
						case "organic matter" -> organic = amount;
						case "fuel" -> fuel = amount;
						case "stored compost" -> stored = amount;
						default -> {
						}
					}
					continue;
				}
				Matcher timeLine = TIME.matcher(line);
				if (timeLine.matches()) {
					started = true;
					time = timeLine.group(1).trim();
				}
			}
			if (organic >= 0 || fuel >= 0 || stored >= 0 || !time.isEmpty()) {
				return new Parsed(Math.max(0, organic), Math.max(0, fuel), Math.max(0, stored), time);
			}
		}
		return null;
	}

	private static Snapshot calculate(Parsed parsed, StrayConfig config) {
		boolean active = !parsed.time.equalsIgnoreCase("INACTIVE");
		int nextSeconds = active ? parseDuration(parsed.time) : -1;
		if (!config.composterUpgradesKnown) {
			return new Snapshot(
				true,
				active,
				parsed.time.isEmpty() ? "?" : parsed.time,
				parsed.organic,
				-1,
				parsed.fuel,
				-1,
				parsed.stored,
				-1,
				"Open Composter Upgrades",
				0d,
				false
			);
		}

		long maxOrganic = 40_000L + config.composterOrganicMatterCap * 30_000L;
		long maxFuel = 100_000L + config.composterFuelCap * 30_000L;
		double speedFactor = 1d + config.composterSpeed * 0.2d;
		double secondsPer = 600d / speedFactor;
		double costFactor = 1d - config.composterCostReduction / 100d;
		double organicPer = 4_000d * costFactor;
		double fuelPer = 2_000d * costFactor;
		long cycles = Math.max(0L, (long) Math.floor(Math.min(parsed.organic / organicPer, parsed.fuel / fuelPer)));
		double perHour = 3_600d / secondsPer * (1d + config.composterMultiDrop * 0.03d);
		String empty = !active
			? "Inactive"
			: nextSeconds < 0
				? "?"
				: formatDuration(emptySeconds(parsed.organic, parsed.fuel, nextSeconds, secondsPer, organicPer, fuelPer));
		return new Snapshot(
			true,
			active,
			parsed.time.isEmpty() ? "?" : parsed.time,
			parsed.organic,
			maxOrganic,
			parsed.fuel,
			maxFuel,
			parsed.stored,
			cycles,
			empty,
			perHour,
			true
		);
	}

	private static long emptySeconds(
		long organic,
		long fuel,
		int nextSeconds,
		double secondsPer,
		double organicPer,
		double fuelPer
	) {
		double fraction = Math.max(0d, Math.min(1d, nextSeconds / secondsPer));
		long organicCycles = cyclesAfterNext(organic, fraction, organicPer);
		long fuelCycles = cyclesAfterNext(fuel, fraction, fuelPer);
		double result = nextSeconds + Math.min(organicCycles, fuelCycles) * secondsPer;
		return Math.max(0L, Math.round(result));
	}

	private static long cyclesAfterNext(long amount, double fraction, double required) {
		double remaining = amount - fraction * required;
		return Math.max(0L, (long) Math.floor(remaining / required));
	}

	private static void readUpgrades(Minecraft client) {
		if (!(client.screen instanceof AbstractContainerScreen<?> screen)
			|| !"Composter Upgrades".equals(screen.getTitle().getString())) {
			return;
		}
		int speed = -1;
		int multi = -1;
		int fuelCap = -1;
		int organicCap = -1;
		int cost = -1;
		List<Slot> slots = screen.getMenu().slots;
		int upper = Math.max(0, slots.size() - 36);
		for (int i = 0; i < upper; i++) {
			String name = clean(slots.get(i).getItem().getHoverName());
			Matcher matcher = UPGRADE.matcher(name);
			if (!matcher.matches()) {
				continue;
			}
			int level = parseLevel(matcher.group(2));
			switch (matcher.group(1).toLowerCase(Locale.ROOT)) {
				case "composter speed" -> speed = level;
				case "multi drop" -> multi = level;
				case "fuel cap" -> fuelCap = level;
				case "organic matter cap" -> organicCap = level;
				case "cost reduction" -> cost = level;
				default -> {
				}
			}
		}
		if (speed < 0 || multi < 0 || fuelCap < 0 || organicCap < 0 || cost < 0) {
			return;
		}
		StrayConfig config = StrayConfig.get();
		boolean changed = !config.composterUpgradesKnown
			|| config.composterSpeed != speed
			|| config.composterMultiDrop != multi
			|| config.composterFuelCap != fuelCap
			|| config.composterOrganicMatterCap != organicCap
			|| config.composterCostReduction != cost;
		if (!changed) {
			return;
		}
		config.composterUpgradesKnown = true;
		config.composterSpeed = speed;
		config.composterMultiDrop = multi;
		config.composterFuelCap = fuelCap;
		config.composterOrganicMatterCap = organicCap;
		config.composterCostReduction = cost;
		config.save();
	}

	private static int parseDuration(String value) {
		if (value == null) {
			return -1;
		}
		Matcher matcher = DURATION.matcher(value.trim());
		if (!matcher.matches() || matcher.group(0).isEmpty()) {
			return -1;
		}
		int days = number(matcher.group(1));
		int hours = number(matcher.group(2));
		int minutes = number(matcher.group(3));
		int seconds = number(matcher.group(4));
		return days * 86_400 + hours * 3_600 + minutes * 60 + seconds;
	}

	private static int number(String value) {
		return value == null || value.isEmpty() ? 0 : Integer.parseInt(value);
	}

	private static int parseLevel(String value) {
		if (value == null || value.isBlank()) {
			return 0;
		}
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException ignored) {
			return roman(value.toUpperCase(Locale.ROOT));
		}
	}

	private static int roman(String value) {
		int total = 0;
		int previous = 0;
		for (int i = value.length() - 1; i >= 0; i--) {
			int current = switch (value.charAt(i)) {
				case 'I' -> 1;
				case 'V' -> 5;
				case 'X' -> 10;
				case 'L' -> 50;
				case 'C' -> 100;
				case 'D' -> 500;
				case 'M' -> 1_000;
				default -> 0;
			};
			total += current < previous ? -current : current;
			previous = Math.max(previous, current);
		}
		return total;
	}

	private static long parseAmount(String value) {
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
			return Math.max(0L, Math.round(Double.parseDouble(text) * multiplier));
		} catch (NumberFormatException ignored) {
			return 0L;
		}
	}

	private static String formatDuration(long totalSeconds) {
		long days = totalSeconds / 86_400;
		long hours = totalSeconds % 86_400 / 3_600;
		long minutes = totalSeconds % 3_600 / 60;
		long seconds = totalSeconds % 60;
		if (days > 0) {
			return days + "d " + hours + "h";
		}
		if (hours > 0) {
			return hours + "h " + minutes + "m";
		}
		return minutes + "m " + seconds + "s";
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

	public record Snapshot(
		boolean present,
		boolean active,
		String nextCompost,
		long organicMatter,
		long maxOrganicMatter,
		long fuel,
		long maxFuel,
		long storedCompost,
		long cyclesLeft,
		String emptyIn,
		double compostPerHour,
		boolean upgradesKnown
	) {
		private static Snapshot empty() {
			return new Snapshot(false, false, "?", 0, -1, 0, -1, 0, -1, "?", 0d, false);
		}
	}

	private record Parsed(long organic, long fuel, long stored, String time) {
	}
}
