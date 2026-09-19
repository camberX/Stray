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
	private static final long ORGANIC_BASE = 40_000L;
	private static final long ORGANIC_PER_LEVEL = 20_000L;
	private static final long FUEL_BASE = 100_000L;
	private static final long FUEL_PER_LEVEL = 30_000L;
	private static final int ORGANIC_SLOT = 2;
	private static final int FUEL_SLOT = 7;
	private static long liveOrganicMax = -1;
	private static long liveFuelMax = -1;
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
		liveOrganicMax = -1;
		liveFuelMax = -1;
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
			persistCaps(parsed.maxOrganic, parsed.maxFuel, false);
		}
		shrinkInflatedCaps();
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
		liveOrganicMax = -1;
		liveFuelMax = -1;
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
		long formulaOrganic = upgradesKnown ? organicCapacity(config.composterOrganicMatterCap) : -1;
		long formulaFuel = upgradesKnown ? fuelCapacity(config.composterFuelCap) : -1;
		long maxOrganic = firstCap(liveOrganicMax, config.composterMaxOrganic, formulaOrganic, parsed.maxOrganic);
		long maxFuel = firstCap(liveFuelMax, config.composterMaxFuel, formulaFuel, parsed.maxFuel);
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
		List<Slot> slots = screen.getMenu().slots;
		int upper = Math.max(0, slots.size() - 36);
		for (int i = 0; i < upper; i++) {
			Slot slot = slots.get(i);
			if (client.player != null && slot.container == client.player.getInventory()) {
				continue;
			}
			ItemStack stack = slot.getItem();
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			try {
				String name = itemName(stack).toLowerCase(Locale.ROOT);
				boolean organicPane = i == ORGANIC_SLOT || name.contains("organic");
				boolean fuelPane = !organicPane && (i == FUEL_SLOT || name.contains("fuel"));
				if (organicPane) {
					sawOrganic = true;
				}
				if (fuelPane) {
					sawFuel = true;
				}
				if (!organicPane && !fuelPane) {
					continue;
				}
				long total = firstRatioMax(itemBlob(stack), organicPane ? ORGANIC_BASE : FUEL_BASE);
				if (total <= 0) {
					continue;
				}
				if (organicPane) {
					organicMax = total;
				} else {
					fuelMax = total;
				}
			} catch (RuntimeException ignored) {
			}
		}
		if (!titled && !(sawOrganic && sawFuel)) {
			return;
		}
		liveOrganicMax = organicMax;
		liveFuelMax = fuelMax;
		persistCaps(organicMax, fuelMax, true);
	}

	private static void persistCaps(long organicMax, long fuelMax, boolean authoritative) {
		if (organicMax <= 0 && fuelMax <= 0) {
			return;
		}
		StrayConfig config = StrayConfig.get();
		boolean changed = false;
		if (organicMax > 0 && acceptCap(organicMax, config.composterMaxOrganic, authoritative)) {
			config.composterMaxOrganic = organicMax;
			int implied = capLevel(organicMax, ORGANIC_BASE, ORGANIC_PER_LEVEL);
			if (!config.composterUpgradesKnown || implied > config.composterOrganicMatterCap) {
				config.composterOrganicMatterCap = implied;
			}
			changed = true;
		}
		if (fuelMax > 0 && acceptCap(fuelMax, config.composterMaxFuel, authoritative)) {
			config.composterMaxFuel = fuelMax;
			int implied = capLevel(fuelMax, FUEL_BASE, FUEL_PER_LEVEL);
			if (!config.composterUpgradesKnown || implied > config.composterFuelCap) {
				config.composterFuelCap = implied;
			}
			changed = true;
		}
		if (changed) {
			config.save();
		}
	}

	private static boolean acceptCap(long incoming, long stored, boolean authoritative) {
		if (incoming <= 0) {
			return false;
		}
		if (stored <= 0) {
			return true;
		}
		if (authoritative) {
			return incoming != stored;
		}
		return incoming > stored;
	}

	private static void shrinkInflatedCaps() {
		StrayConfig config = StrayConfig.get();
		if (!legacyOrganic(config.composterMaxOrganic)) {
			return;
		}
		long organic = config.composterUpgradesKnown
			? organicCapacity(config.composterOrganicMatterCap)
			: organicCapacity(capLevel(config.composterMaxOrganic, ORGANIC_BASE, 30_000L));
		if (organic <= 0 || config.composterMaxOrganic == organic) {
			return;
		}
		config.composterMaxOrganic = organic;
		config.save();
	}

	/** Old HUD used 30k/level. 220k is also a valid 20k/level cap, so leave those alone. */
	private static boolean legacyOrganic(long max) {
		if (max <= ORGANIC_BASE) {
			return false;
		}
		long extra = max - ORGANIC_BASE;
		return extra % 30_000L == 0 && extra % ORGANIC_PER_LEVEL != 0;
	}

	static long firstRatioMax(String blob) {
		return firstRatioMax(blob, ORGANIC_BASE);
	}

	static long firstRatioMax(String blob, long minCap) {
		if (blob == null || blob.isBlank()) {
			return 0L;
		}
		long best = 0L;
		best = Math.max(best, bestRatio(blob, minCap));
		String normalized = stripCodes(blob).replaceAll("[\\p{Cf}]", "").replace('\u00A0', ' ');
		return Math.max(best, bestRatio(normalized, minCap));
	}

	private static long bestRatio(String text, long minCap) {
		long best = 0L;
		Matcher ratio = RATIO.matcher(text);
		while (ratio.find()) {
			long total = parseAmount(ratio.group(2));
			if (total >= minCap && total > best) {
				best = total;
			}
		}
		return best;
	}

	private static int capLevel(long max, long base, long perLevel) {
		return StrayConfig.clamp((int) Math.round((max - base) / (double) perLevel), 0, 25);
	}

	private static long organicCapacity(int level) {
		return ORGANIC_BASE + Math.max(0, level) * ORGANIC_PER_LEVEL;
	}

	private static long fuelCapacity(int level) {
		return FUEL_BASE + Math.max(0, level) * FUEL_PER_LEVEL;
	}

	private static long firstCap(long... values) {
		for (long value : values) {
			if (value > 0) {
				return value;
			}
		}
		return -1;
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
		config.composterMaxOrganic = organicCapacity(organicCap);
		config.composterMaxFuel = fuelCapacity(fuelCap);
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
