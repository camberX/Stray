package dev.stray.client.farming;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.item.ItemAppearance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads Hypixel's Garden composter widget and applies SkyHanni's composter formulas. */
public final class ComposterTracker {
	private static final Pattern VALUE = Pattern.compile(
		"^(Organic Matter|Fuel|Stored Compost):\\s*([\\d,.]+(?:\\.[\\d]+)?[kmb]?)(?:\\s*[/\\u2044\\u2215]\\s*([\\d,.]+(?:\\.[\\d]+)?[kmb]?))?$",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern TIME = Pattern.compile("^Time Left:\\s*(.+)$", Pattern.CASE_INSENSITIVE);
	/** Matches `67,464/100k` and Hypixel's `37,547.5§6/§e130k` lore. */
	private static final Pattern RATIO = Pattern.compile(
		"([\\d,.]+(?:\\.[\\d]+)?[kmb]?)\\s*(?:§[0-9a-fk-or])*\\s*[/\\u2044\\u2215]\\s*(?:§[0-9a-fk-or])*\\s*([\\d,.]+(?:\\.[\\d]+)?[kmb]?)",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern PROFILE = Pattern.compile("^Profile:\\s*(.+)$", Pattern.CASE_INSENSITIVE);
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
		Parsed parsed = readWidget(client);
		try {
			readCapacities(client);
		} catch (RuntimeException ignored) {
		}
		try {
			readUpgrades(client, parsed == null ? "" : parsed.profile);
		} catch (RuntimeException ignored) {
		}
		if (parsed != null) {
			persistCaps(parsed.maxOrganic, parsed.maxFuel);
		}
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
		String profile = "";
		for (String line : lines) {
			Matcher matcher = PROFILE.matcher(line);
			if (matcher.matches()) {
				profile = matcher.group(1).trim();
				break;
			}
		}
		for (int i = 0; i < lines.size(); i++) {
			if (!lines.get(i).equalsIgnoreCase("Composter:")) {
				continue;
			}
			long organic = -1;
			long fuel = -1;
			long stored = -1;
			long maxOrganic = -1;
			long maxFuel = -1;
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
					long cap = value.group(3) == null ? -1 : parseAmount(value.group(3));
					switch (value.group(1).toLowerCase(Locale.ROOT)) {
						case "organic matter" -> {
							organic = amount;
							maxOrganic = cap;
						}
						case "fuel" -> {
							fuel = amount;
							maxFuel = cap;
						}
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
				return new Parsed(
					Math.max(0, organic),
					Math.max(0, fuel),
					Math.max(0, stored),
					time,
					profile,
					maxOrganic,
					maxFuel
				);
			}
		}
		return null;
	}

	private static Snapshot calculate(Parsed parsed, StrayConfig config) {
		boolean active = !parsed.time.equalsIgnoreCase("INACTIVE");
		int nextSeconds = active ? parseDuration(parsed.time) : -1;
		boolean matchingProfile = parsed.profile.isBlank()
			|| config.composterProfile.isBlank()
			|| parsed.profile.equalsIgnoreCase(config.composterProfile);
		boolean upgradesKnown = config.composterUpgradesKnown && matchingProfile;
		long maxOrganic = config.composterMaxOrganic > 0
			? config.composterMaxOrganic
			: 40_000L + config.composterOrganicMatterCap * 30_000L;
		long maxFuel = config.composterMaxFuel > 0
			? config.composterMaxFuel
			: 100_000L + config.composterFuelCap * 30_000L;
		if (config.composterMaxOrganic <= 0 && !upgradesKnown) {
			maxOrganic = -1;
		}
		if (config.composterMaxFuel <= 0 && !upgradesKnown) {
			maxFuel = -1;
		}
		double speedFactor = 1d + config.composterSpeed * 0.2d;
		double secondsPer = 600d / speedFactor;
		double costFactor = 1d - config.composterCostReduction / 100d;
		double organicPer = 4_000d * costFactor;
		double fuelPer = 2_000d * costFactor;
		double multi = 1d + config.composterMultiDrop * 0.03d;
		double fraction = active && nextSeconds >= 0
			? Math.max(0d, Math.min(1d, nextSeconds / secondsPer))
			: 0d;
		long extra = Math.min(
			cyclesAfterNext(parsed.organic, fraction, organicPer),
			cyclesAfterNext(parsed.fuel, fraction, fuelPer)
		);
		if (!active || nextSeconds < 0) {
			extra = Math.max(0L, (long) Math.floor(Math.min(parsed.organic / organicPer, parsed.fuel / fuelPer)));
		}
		long remainingCycles = extra + (active && nextSeconds >= 0 ? 1 : 0);
		long predicted = Math.max(0L, Math.round(remainingCycles * multi));
		double perHour = 3_600d / secondsPer * multi;
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
			predicted,
			empty,
			perHour,
			upgradesKnown
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

	private static void readCapacities(Minecraft client) {
		if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
			return;
		}
		String title = clean(screen.getTitle()).toLowerCase(Locale.ROOT);
		boolean titled = title.contains("composter") && !title.contains("upgrade");
		long organicMax = -1;
		long fuelMax = -1;
		boolean sawOrganic = false;
		boolean sawFuel = false;
		for (Slot slot : screen.getMenu().slots) {
			ItemStack stack = slot.getItem();
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			try {
				String name = itemName(stack).toLowerCase(Locale.ROOT);
				String blob = itemBlob(stack);
				String lower = blob.toLowerCase(Locale.ROOT);
				long total = firstRatioMax(blob);
				if (name.contains("organic")) {
					sawOrganic = true;
				}
				if (name.contains("fuel") && !name.contains("organic")) {
					sawFuel = true;
				}
				if (total <= 0) {
					continue;
				}
				int index = slot.index;
				if (index == 46 || name.contains("organic") || lower.contains("organic")) {
					organicMax = total;
				} else if (index == 52 || name.contains("fuel") || lower.contains("fuel")) {
					fuelMax = total;
				}
			} catch (RuntimeException ignored) {
			}
		}
		if (!titled && !(sawOrganic && sawFuel)) {
			return;
		}
		persistCaps(organicMax, fuelMax);
	}

	private static void persistCaps(long organicMax, long fuelMax) {
		if (organicMax <= 0 && fuelMax <= 0) {
			return;
		}
		StrayConfig config = StrayConfig.get();
		boolean changed = false;
		if (organicMax > 0 && config.composterMaxOrganic != organicMax) {
			config.composterMaxOrganic = organicMax;
			config.composterOrganicMatterCap = capLevel(organicMax, 40_000L);
			changed = true;
		}
		if (fuelMax > 0 && config.composterMaxFuel != fuelMax) {
			config.composterMaxFuel = fuelMax;
			config.composterFuelCap = capLevel(fuelMax, 100_000L);
			changed = true;
		}
		if (changed) {
			config.save();
		}
	}

	static long firstRatioMax(String blob) {
		if (blob == null || blob.isBlank()) {
			return 0L;
		}
		String normalized = stripCodes(blob).replaceAll("[\\p{Cf}]", "").replace('\u00A0', ' ');
		Matcher ratio = RATIO.matcher(blob);
		if (ratio.find()) {
			long total = parseAmount(ratio.group(2));
			if (total > 0) {
				return total;
			}
		}
		Matcher cleaned = RATIO.matcher(normalized);
		return cleaned.find() ? parseAmount(cleaned.group(2)) : 0L;
	}

	private static int capLevel(long max, long base) {
		return StrayConfig.clamp((int) Math.round((max - base) / 30_000d), 0, 25);
	}

	private static String itemName(ItemStack stack) {
		boolean prior = ItemAppearance.suppress();
		try {
			if (stack == null || stack.isEmpty()) {
				return "";
			}
			Component custom = stack.get(DataComponents.CUSTOM_NAME);
			if (custom != null && !clean(custom).isBlank()) {
				return clean(custom);
			}
			return clean(stack.getHoverName());
		} finally {
			ItemAppearance.resume(prior);
		}
	}

	private static String itemBlob(ItemStack stack) {
		boolean prior = ItemAppearance.suppress();
		try {
			if (stack == null || stack.isEmpty()) {
				return "";
			}
			StringBuilder out = new StringBuilder();
			append(out, stack.get(DataComponents.CUSTOM_NAME));
			append(out, stack.get(DataComponents.ITEM_NAME));
			append(out, stack.getHoverName());
			ItemLore lore = stack.get(DataComponents.LORE);
			if (lore != null) {
				for (Component line : lore.lines()) {
					append(out, line);
				}
				for (Component line : lore.styledLines()) {
					append(out, line);
				}
			}
			CustomData data = stack.get(DataComponents.CUSTOM_DATA);
			if (data != null && !data.isEmpty()) {
				out.append('\n').append(data.copyTag());
			}
			return out.toString();
		} finally {
			ItemAppearance.resume(prior);
		}
	}

	private static void append(StringBuilder out, Component component) {
		if (component == null) {
			return;
		}
		out.append('\n');
		component.visit((style, text) -> {
			if (text != null) {
				out.append(text);
			}
			return Optional.empty();
		}, Style.EMPTY);
	}

	private static void readUpgrades(Minecraft client, String profile) {
		if (!(client.screen instanceof AbstractContainerScreen<?> screen)
			|| !"Composter Upgrades".equalsIgnoreCase(clean(screen.getTitle()))) {
			return;
		}
		String profileKey = profile == null ? "" : profile.trim();
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
			|| (!profileKey.isBlank() && !profileKey.equalsIgnoreCase(config.composterProfile))
			|| config.composterSpeed != speed
			|| config.composterMultiDrop != multi
			|| config.composterFuelCap != fuelCap
			|| config.composterOrganicMatterCap != organicCap
			|| config.composterCostReduction != cost;
		if (!changed) {
			return;
		}
		config.composterUpgradesKnown = true;
		if (!profileKey.isBlank()) {
			config.composterProfile = profileKey;
		}
		config.composterSpeed = speed;
		config.composterMultiDrop = multi;
		config.composterFuelCap = fuelCap;
		config.composterOrganicMatterCap = organicCap;
		config.composterCostReduction = cost;
		config.composterMaxOrganic = 40_000L + organicCap * 30_000L;
		config.composterMaxFuel = 100_000L + fuelCap * 30_000L;
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

	private static String stripCodes(String text) {
		return text == null ? "" : text.replaceAll("§.", "").replaceAll("\u00A7.", "");
	}

	private static String clean(Component component) {
		return component == null
			? ""
			: stripCodes(component.getString()).replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
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
		long predictedCompost,
		String emptyIn,
		double compostPerHour,
		boolean upgradesKnown
	) {
		private static Snapshot empty() {
			return new Snapshot(false, false, "?", 0, -1, 0, -1, 0, -1, "?", 0d, false);
		}
	}

	private record Parsed(
		long organic,
		long fuel,
		long stored,
		String time,
		String profile,
		long maxOrganic,
		long maxFuel
	) {
	}
}
