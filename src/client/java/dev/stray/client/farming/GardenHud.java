package dev.stray.client.farming;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.item.ItemAppearance;
import dev.stray.client.item.ItemIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Garden overlays: next Jacob contest, visitor timer, hoe level, crop
 * milestone, and visitor shopping list. Tab widgets, held-tool NBT, and the
 * visitor NPC chest — rewritten for Stray's HUD chrome.
 */
public final class GardenHud {
	private static final int INFO_SLOT = 13;
	private static final int HOE_OVERFLOW = 200_000;
	private static final int SAMPLE_MS = 10_000;
	/** XP to go from level n to n+1. Index 0 is level 1. */
	private static final int[] HOE_EXP = {
		1000, 2000, 3000, 5000, 8000, 12000, 16000, 20000, 25000, 40000,
		60000, 80000, 100000, 120000, 140000, 160000, 180000, 200000, 250000, 300000,
		350000, 400000, 450000, 500000, 550000, 600000, 650000, 700000, 750000, 800000,
		850000, 900000, 950000, 1000000, 1050000, 1100000, 1150000, 1200000, 1250000, 1300000,
		1500000, 2000000, 2500000, 3000000, 3500000, 4000000, 4500000, 5000000, 5500000
	};
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
	private static final Pattern CONTEST_HEADER = Pattern.compile(
		"^jacob'?s contest:?\\s*(.*)$",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern STARTS_IN = Pattern.compile(
		"^starts in:\\s*(.+)$",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern TIME_LEFT = Pattern.compile(
		"^(?:jacob'?s contest:\\s*)?(.+?)\\s+left$",
		Pattern.CASE_INSENSITIVE
	);
	/** SkyHanni TabWidget.VISITORS: `Visitors: (2)` — count is in parentheses. */
	private static final Pattern VISITORS_HEADER = Pattern.compile(
		"^(?:[^\\p{Alnum}]*)visitors?:\\s*(?:\\((\\d+)\\)|(.+))$",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern NEXT_VISITOR = Pattern.compile(
		"^next visitor:\\s*(.+)$",
		Pattern.CASE_INSENSITIVE
	);
	/** SkyHanni visitorNamePattern on colored tab text: ` §r§aEmissary Carlton`. */
	private static final Pattern VISITOR_NAME = Pattern.compile(
		"^\\s*(?:§.)+(§.[^§]+).*"
	);
	private static final Pattern DURATION = Pattern.compile(
		"(?:(\\d+)d\\s*)?(?:(\\d+)h\\s*)?(?:(\\d+)m\\s*)?(?:(\\d+)s)?",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern ITEM_AMOUNT = Pattern.compile(
		"^(?:[-•]\\s*)?(?:(\\d[\\d,]*)x\\s+)?(.+?)(?:\\s+x(\\d[\\d,]*))?$",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern OVERFLOW_CHAT = Pattern.compile(
		"OVERFLOW! Your (.+) has just dropped a Tool Exp Capsule!",
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

	private static final Map<String, List<Need>> SHOPPING = new LinkedHashMap<>();
	private static final ArrayDeque<RateSample> RATE = new ArrayDeque<>();
	private static ContestSnap contest = ContestSnap.empty();
	private static VisitorSnap visitors = VisitorSnap.empty();
	private static HoeSnap hoe = HoeSnap.empty();
	private static MilestoneSnap milestone = MilestoneSnap.empty();
	private static ShoppingSnap shopping = ShoppingSnap.empty();
	private static int parseTick = Integer.MIN_VALUE;
	private static long contestUntilMs;
	private static boolean contestActive;
	private static long visitorUntilMs;
	private static boolean visitorQueueFull;
	private static boolean visitorLocked;
	private static String lastCrop = "";
	private static long lastCounter = -1L;

	private GardenHud() {
	}

	public static void tick(Minecraft client) {
		if (client == null || client.player == null || client.level == null) {
			reset();
			return;
		}
		int tick = client.player.tickCount;
		if (parseTick == Integer.MIN_VALUE || tick < parseTick || tick - parseTick >= 5) {
			parseTick = tick;
			readTab(client);
		}
		readHoe(client.player);
		readMilestone(client.player);
		readVisitorChest(client);
		countShopping(client.player);
	}

	public static void reset() {
		contest = ContestSnap.empty();
		visitors = VisitorSnap.empty();
		hoe = HoeSnap.empty();
		milestone = MilestoneSnap.empty();
		shopping = ShoppingSnap.empty();
		SHOPPING.clear();
		RATE.clear();
		parseTick = Integer.MIN_VALUE;
		contestUntilMs = 0L;
		contestActive = false;
		visitorUntilMs = 0L;
		visitorQueueFull = false;
		visitorLocked = false;
		lastCrop = "";
		lastCounter = -1L;
	}

	public static void onChat(Component message) {
		if (message == null) {
			return;
		}
		String text = clean(message.getString());
		Matcher matcher = OVERFLOW_CHAT.matcher(text);
		if (!matcher.matches()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		ItemStack held = client.player.getMainHandItem();
		String tool = matcher.group(1).trim();
		String heldName = clean(held.getHoverName().getString());
		if (heldName.isEmpty() || !heldName.toLowerCase(Locale.ROOT).contains(tool.toLowerCase(Locale.ROOT))) {
			return;
		}
		String uuid = ItemIds.uuidOf(held);
		if (uuid == null || uuid.isBlank()) {
			return;
		}
		StrayConfig config = StrayConfig.get();
		if (config.overflowHoeLevels == null) {
			config.overflowHoeLevels = new LinkedHashMap<>();
		}
		int next = config.overflowHoeLevels.getOrDefault(uuid, 0) + 1;
		config.overflowHoeLevels.put(uuid, next);
		config.save();
	}

	public static ContestSnap contest() {
		return liveContest();
	}

	public static VisitorSnap visitors() {
		return liveVisitors();
	}

	public static HoeSnap hoe() {
		return hoe;
	}

	public static MilestoneSnap milestone() {
		return milestone;
	}

	public static ShoppingSnap shopping() {
		return shopping;
	}

	private static void readTab(Minecraft client) {
		TabLines tab = tabLines(client);
		parseContest(tab.clean);
		parseVisitors(tab.clean, tab.raw);
	}

	private static void parseContest(List<String> lines) {
		List<String> crops = new ArrayList<>();
		String boosted = "";
		String time = "";
		boolean active = false;
		for (int i = 0; i < lines.size(); i++) {
			Matcher header = CONTEST_HEADER.matcher(lines.get(i));
			if (!header.matches()) {
				continue;
			}
			String rest = header.group(1) == null ? "" : header.group(1).trim();
			if (!rest.isEmpty()) {
				Matcher left = TIME_LEFT.matcher("Jacob's Contest: " + rest);
				if (left.matches()) {
					time = left.group(1).trim();
					active = true;
				} else {
					int seconds = parseDuration(rest);
					if (seconds >= 0) {
						time = rest;
					}
				}
			}
			int limit = Math.min(lines.size(), i + 8);
			for (int j = i + 1; j < limit; j++) {
				String line = lines.get(j);
				if (line.isEmpty() || widget(line)) {
					break;
				}
				Matcher starts = STARTS_IN.matcher(line);
				if (starts.matches()) {
					time = starts.group(1).trim();
					active = false;
					continue;
				}
				Matcher left = TIME_LEFT.matcher(line);
				if (left.matches()) {
					time = left.group(1).trim();
					active = true;
					continue;
				}
				CropMark mark = cropLine(line);
				if (mark != null) {
					crops.add(mark.name);
					if (mark.boosted) {
						boosted = mark.name;
					}
				}
			}
			break;
		}
		if (crops.isEmpty() && time.isEmpty()) {
			contest = ContestSnap.empty();
			contestUntilMs = 0L;
			contestActive = false;
			return;
		}
		int seconds = parseDuration(time);
		if (seconds >= 0) {
			contestUntilMs = System.currentTimeMillis() + seconds * 1000L;
			contestActive = active;
		} else if (!time.isEmpty()) {
			contestUntilMs = 0L;
			contestActive = active;
		}
		contest = new ContestSnap(true, active, List.copyOf(crops), boosted, time.isEmpty() ? "?" : time);
	}

	private static void parseVisitors(List<String> cleaned, List<String> raw) {
		int count = -1;
		boolean locked = false;
		boolean queueFull = false;
		String next = "";
		List<String> names = new ArrayList<>();
		boolean inList = false;
		int remaining = 0;
		int size = Math.min(cleaned.size(), raw.size());
		for (int i = 0; i < size; i++) {
			String line = cleaned.get(i);
			String colored = raw.get(i);
			Matcher header = VISITORS_HEADER.matcher(line);
			if (header.matches()) {
				inList = true;
				String parens = header.group(1);
				String info = header.group(2) == null ? "" : header.group(2).trim();
				if (parens != null) {
					count = parseInt(parens);
					remaining = Math.max(0, count);
				} else if (info.equalsIgnoreCase("Not Unlocked!")) {
					locked = true;
					count = 0;
					remaining = 0;
				} else {
					count = parseInt(info);
					remaining = Math.max(0, count);
				}
				continue;
			}
			Matcher nextLine = NEXT_VISITOR.matcher(line);
			if (nextLine.matches()) {
				inList = false;
				String info = nextLine.group(1).trim();
				if (info.equalsIgnoreCase("Not Unlocked!")) {
					locked = true;
				} else if (info.equalsIgnoreCase("Queue Full!")) {
					queueFull = true;
					next = "Queue Full!";
				} else {
					next = info;
				}
				continue;
			}
			if (!inList) {
				continue;
			}
			if (remaining <= 0 || widget(line)) {
				inList = false;
				continue;
			}
			if (line.isEmpty()) {
				continue;
			}
			String name = visitorName(colored, line);
			if (name.isEmpty()) {
				continue;
			}
			names.add(name);
			remaining--;
		}
		if (count < 0 && names.isEmpty() && next.isEmpty() && !locked) {
			visitors = VisitorSnap.empty();
			visitorUntilMs = 0L;
			visitorQueueFull = false;
			visitorLocked = false;
			return;
		}
		if (count < 0) {
			count = names.size();
		}
		visitorLocked = locked;
		visitorQueueFull = queueFull;
		int seconds = queueFull || locked ? -1 : parseDuration(next);
		if (seconds >= 0) {
			visitorUntilMs = System.currentTimeMillis() + seconds * 1000L;
		} else if (queueFull || locked) {
			visitorUntilMs = 0L;
		}
		pruneShopping(names);
		visitors = new VisitorSnap(true, count, List.copyOf(names), next.isEmpty() ? "?" : next, queueFull, locked);
	}

	private static ContestSnap liveContest() {
		if (!contest.present()) {
			return contest;
		}
		if (contestUntilMs <= 0L) {
			return contest;
		}
		long left = Math.max(0L, (contestUntilMs - System.currentTimeMillis()) / 1000L);
		return new ContestSnap(true, contestActive, contest.crops(), contest.boosted(), formatDuration(left));
	}

	private static VisitorSnap liveVisitors() {
		if (!visitors.present()) {
			return visitors;
		}
		if (visitorLocked) {
			return new VisitorSnap(true, visitors.count(), visitors.names(), "Not unlocked", false, true);
		}
		if (visitorQueueFull) {
			return new VisitorSnap(true, visitors.count(), visitors.names(), "Queue Full!", true, false);
		}
		if (visitorUntilMs <= 0L) {
			return visitors;
		}
		long left = Math.max(0L, (visitorUntilMs - System.currentTimeMillis()) / 1000L);
		return new VisitorSnap(true, visitors.count(), visitors.names(), formatDuration(left), false, false);
	}

	private static void readHoe(LocalPlayer player) {
		ItemStack held = player.getMainHandItem();
		CompoundTag extra = extra(held);
		int level = (int) nbtLong(extra, "levelable_lvl");
		long exp = nbtLong(extra, "levelable_exp");
		if (level <= 0 && exp < 0) {
			hoe = HoeSnap.empty();
			return;
		}
		if (level <= 0) {
			level = 1;
		}
		long need = level <= HOE_EXP.length ? HOE_EXP[level - 1] : HOE_OVERFLOW;
		int overflow = 0;
		if (level >= HOE_EXP.length) {
			String uuid = ItemIds.uuidOf(held);
			Map<String, Integer> stored = StrayConfig.get().overflowHoeLevels;
			if (uuid != null && stored != null) {
				overflow = Math.max(0, stored.getOrDefault(uuid, 0));
			}
		}
		int shown = level + overflow;
		boolean upgrade = exp > need && need > 0;
		hoe = new HoeSnap(true, shown, shown + 1, Math.max(0L, exp), need, upgrade, upgrade && shown >= 40, overflow > 0);
	}

	private static void readMilestone(LocalPlayer player) {
		ItemStack held = player.getMainHandItem();
		if (!FarmingHud.isFarmingTool(held) && extra(held).isEmpty()) {
			milestone = MilestoneSnap.empty();
			RATE.clear();
			lastCounter = -1L;
			return;
		}
		CompoundTag extra = extra(held);
		long counter = nbtLong(extra, "farmed_cultivating");
		if (counter < 0) {
			counter = nbtLong(extra, "mined_crops");
		}
		CropKind crop = cropOf(held);
		if (counter < 0 || crop == null) {
			milestone = MilestoneSnap.empty();
			RATE.clear();
			lastCounter = -1L;
			return;
		}
		if (!crop.label.equals(lastCrop) || counter < lastCounter) {
			RATE.clear();
		}
		lastCrop = crop.label;
		lastCounter = counter;
		long now = System.currentTimeMillis();
		RATE.addLast(new RateSample(now, counter));
		while (RATE.size() > 1 && now - RATE.peekFirst().at > SAMPLE_MS) {
			RATE.removeFirst();
		}
		double perSecond = 0d;
		RateSample first = RATE.peekFirst();
		RateSample last = RATE.peekLast();
		if (first != null && last != null && last.at > first.at && last.value >= first.value) {
			perSecond = (last.value - first.value) * 1000d / (last.at - first.at);
		}
		Milestone math = milestoneOf(crop.table, counter);
		String eta = "";
		if (perSecond > 0.05d && !math.maxed) {
			long missing = Math.max(0L, math.need - math.have);
			eta = formatDuration(Math.round(missing / perSecond));
		}
		milestone = new MilestoneSnap(
			true,
			crop.label,
			math.tier,
			math.next,
			math.have,
			math.need,
			counter,
			math.maxed,
			eta,
			perSecond
		);
	}

	private static void readVisitorChest(Minecraft client) {
		if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
			return;
		}
		String title = clean(screen.getTitle());
		if (title.isEmpty() || title.equalsIgnoreCase("Chest") || title.toLowerCase(Locale.ROOT).contains("skyblock menu")) {
			return;
		}
		ItemStack info = ItemStack.EMPTY;
		for (Slot slot : screen.getMenu().slots) {
			if (slot.index == INFO_SLOT) {
				info = slot.getItem();
				break;
			}
		}
		if (info == null || info.isEmpty()) {
			return;
		}
		List<String> lore = lore(info);
		boolean visitor = false;
		for (String line : lore) {
			if (line.toLowerCase(Locale.ROOT).startsWith("offers accepted")) {
				visitor = true;
				break;
			}
		}
		if (!visitor) {
			return;
		}
		List<Need> needs = new ArrayList<>();
		boolean required = false;
		for (String line : lore) {
			String plain = clean(line);
			if (plain.equalsIgnoreCase("Items Required:")) {
				required = true;
				continue;
			}
			if (!required) {
				continue;
			}
			if (plain.isEmpty() || plain.toLowerCase(Locale.ROOT).startsWith("rewards")
				|| plain.toLowerCase(Locale.ROOT).startsWith("copper")
				|| plain.toLowerCase(Locale.ROOT).startsWith("garden experience")) {
				break;
			}
			Need need = parseNeed(plain);
			if (need != null) {
				needs.add(need);
			}
		}
		SHOPPING.put(title, List.copyOf(needs));
	}

	private static void countShopping(LocalPlayer player) {
		VisitorSnap live = liveVisitors();
		if (!live.present() && SHOPPING.isEmpty()) {
			shopping = ShoppingSnap.empty();
			return;
		}
		Map<String, Integer> having = inventoryCounts(player);
		Map<String, Integer> totals = new LinkedHashMap<>();
		List<String> known = new ArrayList<>();
		List<String> fresh = new ArrayList<>();
		if (live.present()) {
			for (String name : live.names()) {
				List<Need> needs = SHOPPING.get(name);
				if (needs == null) {
					needs = matchShopping(name);
				}
				if (needs == null || needs.isEmpty()) {
					fresh.add(shortName(name));
					continue;
				}
				known.add(shortName(name));
				for (Need need : needs) {
					totals.merge(need.name(), need.required(), Integer::sum);
				}
			}
		} else {
			for (Map.Entry<String, List<Need>> entry : SHOPPING.entrySet()) {
				known.add(shortName(entry.getKey()));
				for (Need need : entry.getValue()) {
					totals.merge(need.name(), need.required(), Integer::sum);
				}
			}
		}
		List<Need> items = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : totals.entrySet()) {
			items.add(new Need(entry.getKey(), entry.getValue(), having.getOrDefault(fold(entry.getKey()), 0)));
		}
		boolean present = live.present() || !items.isEmpty();
		List<String> names = new ArrayList<>(known);
		names.addAll(fresh);
		shopping = new ShoppingSnap(present, List.copyOf(items), List.copyOf(names), List.copyOf(fresh));
	}

	private static List<Need> matchShopping(String name) {
		List<Need> exact = SHOPPING.get(name);
		if (exact != null) {
			return exact;
		}
		String folded = fold(name);
		for (Map.Entry<String, List<Need>> entry : SHOPPING.entrySet()) {
			if (fold(entry.getKey()).equals(folded) || fold(entry.getKey()).endsWith(folded) || folded.endsWith(fold(entry.getKey()))) {
				return entry.getValue();
			}
		}
		return null;
	}

	private static void pruneShopping(List<String> names) {
		if (names.isEmpty() && visitors.present()) {
			return;
		}
		List<String> keep = new ArrayList<>();
		for (String stored : SHOPPING.keySet()) {
			boolean found = false;
			for (String name : names) {
				String a = fold(stored);
				String b = fold(name);
				if (a.equals(b) || a.endsWith(b) || b.endsWith(a)) {
					found = true;
					break;
				}
			}
			if (found) {
				keep.add(stored);
			}
		}
		SHOPPING.keySet().retainAll(keep);
	}

	private static Map<String, Integer> inventoryCounts(LocalPlayer player) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		Inventory inventory = player.getInventory();
		boolean prior = ItemAppearance.suppress();
		try {
			for (ItemStack stack : inventory.getNonEquipmentItems()) {
				if (stack == null || stack.isEmpty()) {
					continue;
				}
				String name = fold(clean(stack.getHoverName().getString()));
				if (name.isEmpty()) {
					continue;
				}
				counts.merge(name, stack.getCount(), Integer::sum);
			}
		} finally {
			ItemAppearance.resume(prior);
		}
		return counts;
	}

	private static Need parseNeed(String line) {
		Matcher matcher = ITEM_AMOUNT.matcher(line);
		if (!matcher.matches()) {
			return null;
		}
		String left = matcher.group(1);
		String name = matcher.group(2) == null ? "" : matcher.group(2).trim();
		String right = matcher.group(3);
		int amount = parseInt(left != null ? left : right);
		if (name.isEmpty() || amount <= 0) {
			return null;
		}
		name = name.replaceAll("\\s+", " ").trim();
		if (name.equalsIgnoreCase("Items Required") || name.length() < 2) {
			return null;
		}
		return new Need(name, amount, 0);
	}

	private static CropMark cropLine(String line) {
		boolean boosted = line.indexOf('\u2618') >= 0 || line.indexOf('☘') >= 0;
		String name = line.replaceFirst("^[\\s\\u25CB\\u25CF\\u25E6\\u00B7\\u2022○●◯☘☀☆★\\u2618]+", "").trim();
		if (name.isEmpty()) {
			return null;
		}
		CropKind crop = cropNamed(name);
		if (crop == null) {
			return null;
		}
		return new CropMark(crop.label, boosted);
	}

	private static CropKind cropOf(ItemStack stack) {
		String id = ItemIds.skyblockId(stack);
		if (id != null) {
			String key = id.toUpperCase(Locale.ROOT);
			if (key.contains("THEORETICAL_HOE_WHEAT") || key.contains("HOE_OF_GREAT_TILLING") && key.contains("WHEAT")) {
				return CropKind.WHEAT;
			}
			if (key.contains("THEORETICAL_HOE_CARROT")) {
				return CropKind.CARROT;
			}
			if (key.contains("THEORETICAL_HOE_POTATO")) {
				return CropKind.POTATO;
			}
			if (key.contains("THEORETICAL_HOE_WARTS") || key.contains("THEORETICAL_HOE_WART")) {
				return CropKind.WART;
			}
			if (key.contains("THEORETICAL_HOE_CANE")) {
				return CropKind.CANE;
			}
			if (key.contains("PUMPKIN_DICER")) {
				return CropKind.PUMPKIN;
			}
			if (key.contains("MELON_DICER")) {
				return CropKind.MELON;
			}
			if (key.contains("FUNGI_CUTTER")) {
				return CropKind.MUSHROOM;
			}
			if (key.contains("COCO_CHOPPER") || key.contains("COCOA")) {
				return CropKind.COCOA;
			}
			if (key.contains("CACTUS_KNIFE")) {
				return CropKind.CACTUS;
			}
		}
		return cropNamed(clean(stack.getHoverName().getString()));
	}

	private static CropKind cropNamed(String raw) {
		String key = fold(raw);
		if (key.isEmpty()) {
			return null;
		}
		for (CropKind crop : CropKind.values()) {
			if (key.equals(fold(crop.label)) || key.contains(fold(crop.label))) {
				return crop;
			}
			for (String alias : crop.aliases) {
				if (key.equals(alias) || key.contains(alias)) {
					return crop;
				}
			}
		}
		return null;
	}

	private static Milestone milestoneOf(long[] table, long amount) {
		int cap = table.length - 1;
		int level = 0;
		for (int i = 1; i < table.length; i++) {
			if (amount >= table[i]) {
				level = i;
			} else {
				break;
			}
		}
		if (level < cap) {
			long from = table[level];
			long to = table[level + 1];
			return new Milestone(level, level + 1, amount - from, to - from, false);
		}
		long last = table[cap];
		long step = cap > 0 ? Math.max(1L, table[cap] - table[cap - 1]) : 1L;
		long extra = Math.max(0L, amount - last);
		int overflow = (int) (extra / step);
		long have = extra % step;
		return new Milestone(cap + overflow, cap + overflow + 1, have, step, false);
	}

	private static TabLines tabLines(Minecraft client) {
		ClientPacketListener connection = client.player.connection;
		if (connection == null) {
			return new TabLines(List.of(), List.of());
		}
		List<PlayerInfo> infos = new ArrayList<>(connection.getListedOnlinePlayers());
		infos.sort(TAB_ORDER);
		List<String> raw = new ArrayList<>(infos.size());
		List<String> clean = new ArrayList<>(infos.size());
		for (PlayerInfo info : infos) {
			Component component = tabName(info);
			String colored = component == null ? "" : component.getString();
			String line = clean(colored);
			if (line.isEmpty()) {
				continue;
			}
			raw.add(colored);
			clean.add(line);
		}
		return new TabLines(clean, raw);
	}

	private static String visitorName(String colored, String cleaned) {
		if (colored != null) {
			Matcher matcher = VISITOR_NAME.matcher(colored);
			if (matcher.matches()) {
				String named = clean(matcher.group(1));
				if (!named.isEmpty()) {
					return named;
				}
			}
		}
		return cleaned == null ? "" : cleaned;
	}

	private static Component tabName(PlayerInfo info) {
		Component display = info.getTabListDisplayName();
		return display != null
			? display
			: PlayerTeam.formatNameForTeam(info.getTeam(), Component.literal(info.getProfile().name()));
	}

	private static CompoundTag extra(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return new CompoundTag();
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || data.isEmpty()) {
			return new CompoundTag();
		}
		CompoundTag tag = data.copyTag();
		CompoundTag extra = tag.getCompoundOrEmpty("ExtraAttributes");
		if (!extra.isEmpty()) {
			return extra;
		}
		CompoundTag bukkit = tag.getCompoundOrEmpty("PublicBukkitValues");
		return bukkit.isEmpty() ? tag : bukkit;
	}

	private static long nbtLong(CompoundTag tag, String key) {
		if (tag == null || key == null || !tag.contains(key)) {
			return -1L;
		}
		try {
			long value = tag.getLongOr(key, Long.MIN_VALUE);
			if (value != Long.MIN_VALUE) {
				return Math.max(0L, value);
			}
		} catch (RuntimeException ignored) {
		}
		try {
			return Math.max(0L, tag.getIntOr(key, -1));
		} catch (RuntimeException ignored) {
			return -1L;
		}
	}

	private static List<String> lore(ItemStack stack) {
		boolean prior = ItemAppearance.suppress();
		try {
			ItemLore lore = stack.get(DataComponents.LORE);
			if (lore == null) {
				return List.of();
			}
			List<String> lines = new ArrayList<>();
			for (Component line : lore.lines()) {
				lines.add(line == null ? "" : line.getString());
			}
			return lines;
		} finally {
			ItemAppearance.resume(prior);
		}
	}

	private static boolean widget(String line) {
		String key = line.toLowerCase(Locale.ROOT);
		return key.startsWith("area:")
			|| key.startsWith("profile:")
			|| key.startsWith("skills:")
			|| key.startsWith("collections")
			|| key.startsWith("crop milestone")
			|| key.startsWith("garden level")
			|| key.startsWith("copper:")
			|| key.startsWith("pets:")
			|| key.startsWith("composter")
			|| key.startsWith("pests:")
			|| key.startsWith("plots:")
			|| key.startsWith("event:")
			|| key.startsWith("jacob")
			|| key.equals("farming")
			|| key.equals("garden")
			|| key.equals("info");
	}

	private static int parseDuration(String value) {
		if (value == null) {
			return -1;
		}
		String text = value.trim();
		if (text.isEmpty() || text.contains("Full") || text.contains("Unlock")) {
			return -1;
		}
		Matcher matcher = DURATION.matcher(text);
		if (!matcher.matches() || matcher.group(0).isEmpty()) {
			return -1;
		}
		int days = number(matcher.group(1));
		int hours = number(matcher.group(2));
		int minutes = number(matcher.group(3));
		int seconds = number(matcher.group(4));
		int total = days * 86_400 + hours * 3_600 + minutes * 60 + seconds;
		return total <= 0 ? -1 : total;
	}

	private static String formatDuration(long totalSeconds) {
		long seconds = Math.max(0L, totalSeconds);
		long days = seconds / 86_400;
		long hours = seconds % 86_400 / 3_600;
		long minutes = seconds % 3_600 / 60;
		long rest = seconds % 60;
		if (days > 0) {
			return days + "d " + hours + "h";
		}
		if (hours > 0) {
			return hours + "h " + minutes + "m";
		}
		if (minutes > 0) {
			return rest > 0 ? minutes + "m " + rest + "s" : minutes + "m";
		}
		return rest + "s";
	}

	private static int number(String value) {
		return value == null || value.isEmpty() ? 0 : Integer.parseInt(value);
	}

	private static int parseInt(String value) {
		if (value == null || value.isBlank()) {
			return -1;
		}
		try {
			return Integer.parseInt(value.replace(",", "").trim());
		} catch (NumberFormatException ignored) {
			return -1;
		}
	}

	private static String clean(Component component) {
		return component == null ? "" : clean(component.getString());
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

	private static String fold(String value) {
		return clean(value).toLowerCase(Locale.ROOT);
	}

	private static String shortName(String name) {
		String text = clean(name);
		int space = text.lastIndexOf(' ');
		return space > 0 && text.length() > 18 ? text.substring(space + 1) : text;
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

	private record TabLines(List<String> clean, List<String> raw) {
	}

	public record ContestSnap(boolean present, boolean active, List<String> crops, String boosted, String time) {
		private static ContestSnap empty() {
			return new ContestSnap(false, false, List.of(), "", "");
		}
	}

	public record VisitorSnap(
		boolean present,
		int count,
		List<String> names,
		String next,
		boolean queueFull,
		boolean locked
	) {
		private static VisitorSnap empty() {
			return new VisitorSnap(false, 0, List.of(), "", false, false);
		}
	}

	public record HoeSnap(
		boolean present,
		int level,
		int next,
		long exp,
		long need,
		boolean upgrade,
		boolean overclock,
		boolean overflow
	) {
		private static HoeSnap empty() {
			return new HoeSnap(false, 0, 1, 0, 0, false, false, false);
		}
	}

	public record MilestoneSnap(
		boolean present,
		String crop,
		int tier,
		int next,
		long have,
		long need,
		long counter,
		boolean maxed,
		String eta,
		double perSecond
	) {
		private static MilestoneSnap empty() {
			return new MilestoneSnap(false, "", 0, 1, 0, 0, 0, false, "", 0d);
		}
	}

	public record ShoppingSnap(boolean present, List<Need> items, List<String> visitors, List<String> unread) {
		private static ShoppingSnap empty() {
			return new ShoppingSnap(false, List.of(), List.of(), List.of());
		}
	}

	public record Need(String name, int required, int having) {
	}

	private enum CropKind {
		WHEAT("Wheat", CROP_WHEAT, "wheat"),
		CARROT("Carrot", CROP_CARROT, "carrot"),
		POTATO("Potato", CROP_CARROT, "potato"),
		PUMPKIN("Pumpkin", CROP_WHEAT, "pumpkin"),
		MELON("Melon", CROP_MELON, "melon"),
		MUSHROOM("Mushroom", CROP_WHEAT, "mushroom"),
		COCOA("Cocoa Beans", CROP_WART, "cocoa", "cocoa beans"),
		CACTUS("Cactus", CROP_CANE, "cactus"),
		CANE("Sugar Cane", CROP_CANE, "cane", "sugar cane", "sugarcane"),
		WART("Nether Wart", CROP_WART, "wart", "nether wart", "netherwart");

		private final String label;
		private final long[] table;
		private final String[] aliases;

		CropKind(String label, long[] table, String... aliases) {
			this.label = label;
			this.table = table;
			this.aliases = aliases;
		}
	}

	private record CropMark(String name, boolean boosted) {
	}

	private record RateSample(long at, long value) {
	}

	private record Milestone(int tier, int next, long have, long need, boolean maxed) {
	}
}
