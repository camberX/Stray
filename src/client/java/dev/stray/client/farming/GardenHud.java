package dev.stray.client.farming;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.item.ItemAppearance;
import dev.stray.client.item.ItemIds;
import dev.stray.client.item.ItemStorage;
import dev.stray.client.item.SkyblockItems;
import dev.stray.client.item.SkyblockRecipes;
import dev.stray.client.mixin.AbstractContainerScreenAccessor;
import dev.stray.client.visual.NickSteal;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Garden overlays: next Jacob contest, visitor timer, hoe level, crop
 * milestone, and visitor shopping list. Crop milestones come from the Crop
 * Milestones chest and the Garden tab widget, then the held tool counter
 * after the menu closes. Visitor NPC chests and hoe NBT still feed the
 * other overlays.
 */
public final class GardenHud {
	private static final int INFO_SLOT = 13;
	private static final int ACCEPT_SLOT = 29;
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
		"^\\s*(?:§.)+([^§]+).*"
	);
	private static final Pattern VISITOR_NEW = Pattern.compile(
		"(?i)\\s*\\(?NEW!?\\)?\\s*$"
	);
	private static final Pattern OFFERS_ACCEPTED = Pattern.compile(
		"^offers accepted:\\s*",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern ITEM_AMOUNT = Pattern.compile(
		"^(?:[-•]\\s*)?(?:(\\d[\\d,]*)x\\s+)?(.+?)(?:\\s+x(\\d[\\d,]*))?$",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern DURATION = Pattern.compile(
		"(?:(\\d+)d\\s*)?(?:(\\d+)h\\s*)?(?:(\\d+)m\\s*)?(?:(\\d+)s)?",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern OVERFLOW_CHAT = Pattern.compile(
		"OVERFLOW! Your (.+) has just dropped a Tool Exp Capsule!",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern CROP_MILESTONE_TITLE = Pattern.compile(
		"crop milestones?|milestones?",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern CROP_MILESTONE_PROGRESS = Pattern.compile(
		"progress(?: to)?(?:\\s+(?:tier|milestone))?\\s*(\\d+)?\\s*:\\s*([\\d,.]+[kmb]?)\\s*[/\\u2044\\u2215]\\s*([\\d,.]+[kmb]?)",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern CROP_MILESTONE_RATIO = Pattern.compile(
		"([\\d,.]+[kmb]?)\\s*[/\\u2044\\u2215]\\s*([\\d,.]+[kmb]?)",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern CROP_MILESTONE_TIER = Pattern.compile(
		"(?:tier|milestone)\\s+(\\d+)",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern CROP_MILESTONE_TOTAL = Pattern.compile(
		"(?:harvested|collected|grown)[:\\s]+([\\d,.]+[kmb]?)",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern CROP_MILESTONE_MAXED = Pattern.compile(
		"\\bmaxed(?:\\s+out)?\\b",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern TAB_MILESTONE_HEADER = Pattern.compile(
		"^crop milestones?:?\\s*(.*)$",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern TAB_MILESTONE_ROW = Pattern.compile(
		"^(?:(.+?)\\s+)?(\\d+)\\s*:\\s*([\\d,.]+[kmb]?)\\s*[/\\u2044\\u2215]\\s*([\\d,.]+[kmb]?)$",
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
	private static final Map<CropKind, CropProgress> CROP_PROGRESS = new EnumMap<>(CropKind.class);
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
	private static CropKind lastGuiCrop;
	private static long lastCounter = -1L;
	private static boolean milestoneMenuOpen;

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
		readCropMilestoneMenu(client);
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
		CROP_PROGRESS.clear();
		RATE.clear();
		parseTick = Integer.MIN_VALUE;
		contestUntilMs = 0L;
		contestActive = false;
		visitorUntilMs = 0L;
		visitorQueueFull = false;
		visitorLocked = false;
		lastCrop = "";
		lastGuiCrop = null;
		lastCounter = -1L;
		milestoneMenuOpen = false;
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

	/** Crop preview for a tab visitor: opened offer items first, else catalog. */
	public static List<String> cropsFor(String name) {
		List<Need> opened = matchShopping(name);
		if (opened != null && !opened.isEmpty()) {
			List<String> out = new ArrayList<>();
			for (Need need : opened) {
				if (need.name() != null && !need.name().isBlank() && !out.contains(need.name())) {
					out.add(need.name());
				}
			}
			if (!out.isEmpty()) {
				return List.copyOf(out);
			}
		}
		return GardenVisitors.preview(name);
	}

	private static void readTab(Minecraft client) {
		TabLines lines = tabLines(client);
		parseContest(lines.clean());
		parseVisitors(lines.clean(), lines.raw());
		parseMilestoneTab(lines.clean());
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
				remaining--;
				continue;
			}
			String name = visitorName(colored, line);
			if (!name.isEmpty()) {
				names.add(name);
			}
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
		CropKind heldCrop = cropOf(held);
		long counter = toolCounter(held);
		CropKind crop = milestoneMenuOpen && lastGuiCrop != null
			? lastGuiCrop
			: heldCrop != null ? heldCrop : lastGuiCrop;
		if (crop == null) {
			milestone = MilestoneSnap.empty();
			RATE.clear();
			lastCounter = -1L;
			return;
		}
		CropProgress stored = CROP_PROGRESS.get(crop);
		long harvested = -1L;
		if (stored != null) {
			if (milestoneMenuOpen) {
				harvested = stored.total;
			} else if (heldCrop == crop && counter >= 0L) {
				if (stored.toolAt >= 0L) {
					harvested = stored.total + Math.max(0L, counter - stored.toolAt);
				} else {
					harvested = stored.total;
					CROP_PROGRESS.put(crop, new CropProgress(stored.total, counter));
				}
			} else {
				harvested = stored.total;
			}
		} else if (heldCrop != null && counter >= 0L) {
			harvested = counter;
		}
		if (harvested < 0L) {
			milestone = MilestoneSnap.empty();
			RATE.clear();
			lastCounter = -1L;
			return;
		}
		if (!crop.label.equals(lastCrop) || harvested < lastCounter) {
			RATE.clear();
		}
		lastCrop = crop.label;
		lastCounter = harvested;
		long now = System.currentTimeMillis();
		RATE.addLast(new RateSample(now, harvested));
		while (RATE.size() > 1 && now - RATE.peekFirst().at > SAMPLE_MS) {
			RATE.removeFirst();
		}
		double perSecond = 0d;
		RateSample first = RATE.peekFirst();
		RateSample last = RATE.peekLast();
		if (first != null && last != null && last.at > first.at && last.value >= first.value) {
			perSecond = (last.value - first.value) * 1000d / (last.at - first.at);
		}
		Milestone math = milestoneOf(crop.table, harvested);
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
			harvested,
			math.maxed,
			eta,
			perSecond
		);
	}

	private static void readCropMilestoneMenu(Minecraft client) {
		milestoneMenuOpen = false;
		if (!(client.screen instanceof AbstractContainerScreen<?> screen) || client.player == null) {
			return;
		}
		String title = clean(screen.getTitle());
		boolean titled = CROP_MILESTONE_TITLE.matcher(title).find();
		CropKind pageCrop = exactCrop(title);
		if (pageCrop == null) {
			pageCrop = exactCrop(title.replaceAll("(?i)\\s*milestones?\\s*", " ").trim());
		}
		ItemStack held = client.player.getMainHandItem();
		CropKind heldCrop = cropOf(held);
		long counter = toolCounter(held);
		Slot hovered = ((AbstractContainerScreenAccessor) screen).stray$hoveredSlot();
		CropKind hoverCrop = null;
		CropKind firstCrop = null;
		List<Slot> slots = screen.getMenu().slots;
		int upper = Math.max(0, slots.size() - 36);
		for (int i = 0; i < upper; i++) {
			Slot slot = slots.get(i);
			if (slot.container == client.player.getInventory()) {
				continue;
			}
			ItemStack stack = slot.getItem();
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			CropKind crop = cropNamed(itemName(stack));
			if (crop == null && pageCrop != null && milestoneLore(stack)) {
				crop = pageCrop;
			}
			if (crop == null && milestoneLore(stack)) {
				crop = cropNamed(String.join(" ", itemLines(stack)));
			}
			if (crop == null) {
				continue;
			}
			long total = parseCropMilestone(crop, stack);
			if (total < 0L) {
				continue;
			}
			long at = heldCrop == crop && counter >= 0L ? counter : -1L;
			storeCropProgress(crop, total, at);
			if (firstCrop == null) {
				firstCrop = crop;
			}
			if (hovered == slot) {
				hoverCrop = crop;
			}
		}
		if (!titled && firstCrop == null) {
			return;
		}
		milestoneMenuOpen = true;
		if (hoverCrop != null) {
			lastGuiCrop = hoverCrop;
		} else if (heldCrop != null && CROP_PROGRESS.containsKey(heldCrop)) {
			lastGuiCrop = heldCrop;
		} else if (pageCrop != null && CROP_PROGRESS.containsKey(pageCrop)) {
			lastGuiCrop = pageCrop;
		} else if (firstCrop != null) {
			lastGuiCrop = firstCrop;
		}
	}

	private static void parseMilestoneTab(List<String> lines) {
		Minecraft client = Minecraft.getInstance();
		if (client.screen instanceof AbstractContainerScreen<?> screen) {
			String title = clean(screen.getTitle());
			if (CROP_MILESTONE_TITLE.matcher(title).find()) {
				return;
			}
		}
		for (int i = 0; i < lines.size(); i++) {
			Matcher header = TAB_MILESTONE_HEADER.matcher(lines.get(i));
			if (!header.matches()) {
				continue;
			}
			String rest = header.group(1) == null ? "" : header.group(1).trim();
			if (!rest.isEmpty()) {
				parseMilestoneTabRow(rest);
			}
			int limit = Math.min(lines.size(), i + 6);
			for (int j = i + 1; j < limit; j++) {
				String line = lines.get(j);
				if (line.isEmpty() || widget(line)) {
					break;
				}
				parseMilestoneTabRow(line);
			}
			break;
		}
	}

	private static void parseMilestoneTabRow(String raw) {
		String line = raw.replaceFirst("^[\\s\\u25CB\\u25CF\\u25E6\\u00B7\\u2022○●◯]+", "").trim();
		Matcher row = TAB_MILESTONE_ROW.matcher(line);
		if (!row.matches()) {
			return;
		}
		CropKind crop = cropNamed(row.group(1) == null ? "" : row.group(1).trim());
		if (crop == null) {
			crop = lastGuiCrop;
		}
		if (crop == null) {
			Minecraft client = Minecraft.getInstance();
			if (client.player != null) {
				crop = cropOf(client.player.getMainHandItem());
			}
		}
		if (crop == null) {
			return;
		}
		int tier = parseInt(row.group(2));
		long have = parseCount(row.group(3));
		long need = parseCount(row.group(4));
		long total = harvestedAt(crop, tier, have, need, false);
		if (total < 0L) {
			return;
		}
		CropProgress prior = CROP_PROGRESS.get(crop);
		if (prior != null && prior.toolAt >= 0L) {
			lastGuiCrop = crop;
			return;
		}
		storeCropProgress(crop, total, matchingToolCounter(crop));
		lastGuiCrop = crop;
	}

	private static boolean milestoneLore(ItemStack stack) {
		for (String line : itemLines(stack)) {
			String plain = clean(line);
			if (CROP_MILESTONE_PROGRESS.matcher(plain).find()
				|| CROP_MILESTONE_TOTAL.matcher(plain).find()
				|| CROP_MILESTONE_MAXED.matcher(plain).find()
				|| (fold(plain).contains("progress") && CROP_MILESTONE_RATIO.matcher(plain).find())) {
				return true;
			}
		}
		return false;
	}

	private static long parseCropMilestone(CropKind crop, ItemStack stack) {
		int nextTier = -1;
		int currentTier = -1;
		long have = -1L;
		long need = -1L;
		long harvested = -1L;
		boolean maxed = false;
		for (String line : itemLines(stack)) {
			String plain = clean(line);
			Matcher progress = CROP_MILESTONE_PROGRESS.matcher(plain);
			if (progress.find()) {
				if (progress.group(1) != null && !progress.group(1).isBlank()) {
					nextTier = parseInt(progress.group(1));
				}
				have = parseCount(progress.group(2));
				need = parseCount(progress.group(3));
				continue;
			}
			Matcher total = CROP_MILESTONE_TOTAL.matcher(plain);
			if (total.find()) {
				harvested = parseCount(total.group(1));
				continue;
			}
			if (CROP_MILESTONE_MAXED.matcher(plain).find()) {
				maxed = true;
				continue;
			}
			if (fold(plain).contains("progress")) {
				Matcher ratio = CROP_MILESTONE_RATIO.matcher(plain);
				if (ratio.find()) {
					have = parseCount(ratio.group(1));
					need = parseCount(ratio.group(2));
					continue;
				}
			}
			Matcher tier = CROP_MILESTONE_TIER.matcher(plain);
			if (tier.find()) {
				currentTier = parseInt(tier.group(1));
			}
		}
		if (harvested >= 0L) {
			return harvested;
		}
		int tier = nextTier > 0 ? nextTier : currentTier;
		if (have >= 0L && tier >= 0) {
			long fromProgress = harvestedAt(crop, tier, have, need, nextTier > 0);
			if (fromProgress >= 0L) {
				return fromProgress;
			}
		}
		if (have >= 0L && need > 0L && currentTier < 0 && nextTier < 0) {
			long fromProgress = harvestedAt(crop, guessTier(crop, need), have, need, false);
			if (fromProgress >= 0L) {
				return fromProgress;
			}
		}
		if (maxed) {
			return crop.table[crop.table.length - 1];
		}
		return -1L;
	}

	private static int guessTier(CropKind crop, long need) {
		long[] table = crop.table;
		for (int i = 1; i < table.length; i++) {
			if (Math.abs(table[i] - table[i - 1] - need) <= 1L) {
				return i - 1;
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
			Component named = stack.get(DataComponents.ITEM_NAME);
			if (named != null && !clean(named).isBlank()) {
				return clean(named);
			}
			return clean(stack.getHoverName());
		} finally {
			ItemAppearance.resume(prior);
		}
	}

	private static List<String> itemLines(ItemStack stack) {
		boolean prior = ItemAppearance.suppress();
		try {
			List<String> lines = new ArrayList<>();
			addItemLine(lines, stack.get(DataComponents.CUSTOM_NAME));
			addItemLine(lines, stack.get(DataComponents.ITEM_NAME));
			addItemLine(lines, stack.getHoverName());
			ItemLore lore = stack.get(DataComponents.LORE);
			if (lore != null) {
				for (Component line : lore.lines()) {
					addItemLine(lines, line);
				}
				for (Component line : lore.styledLines()) {
					addItemLine(lines, line);
				}
			}
			return lines;
		} finally {
			ItemAppearance.resume(prior);
		}
	}

	private static void addItemLine(List<String> lines, Component component) {
		if (component == null) {
			return;
		}
		String text = clean(component);
		if (!text.isEmpty() && !lines.contains(text)) {
			lines.add(text);
		}
	}

	private static long harvestedAt(CropKind crop, int tier, long have, long need, boolean progressTo) {
		long[] table = crop.table;
		long amount = Math.max(0L, have);
		if (need > 0L && tier >= 0) {
			if (tier + 1 < table.length && Math.abs(table[tier + 1] - table[tier] - need) <= 1L) {
				return table[tier] + amount;
			}
			if (tier > 0 && tier < table.length && Math.abs(table[tier] - table[tier - 1] - need) <= 1L) {
				return table[tier - 1] + amount;
			}
			long step = table.length > 1 ? Math.max(1L, table[table.length - 1] - table[table.length - 2]) : 0L;
			if (step > 0L && Math.abs(step - need) <= 1L && tier >= table.length - 1) {
				int extra = Math.max(0, progressTo ? tier - table.length : tier - (table.length - 1));
				return table[table.length - 1] + extra * step + amount;
			}
		}
		if (progressTo) {
			int from = Math.max(0, tier - 1);
			if (from < table.length) {
				return table[from] + amount;
			}
		} else if (tier >= 0 && tier < table.length) {
			return table[tier] + amount;
		}
		return -1L;
	}

	private static void storeCropProgress(CropKind crop, long total, long toolAt) {
		if (crop == null || total < 0L) {
			return;
		}
		CROP_PROGRESS.put(crop, new CropProgress(total, toolAt));
	}

	private static long matchingToolCounter(CropKind crop) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return -1L;
		}
		ItemStack held = client.player.getMainHandItem();
		if (cropOf(held) != crop) {
			return -1L;
		}
		return toolCounter(held);
	}

	private static long toolCounter(ItemStack stack) {
		CompoundTag extra = extra(stack);
		long counter = nbtLong(extra, "farmed_cultivating");
		if (counter < 0L) {
			counter = nbtLong(extra, "mined_crops");
		}
		return counter;
	}

	private static CropKind exactCrop(String raw) {
		String key = fold(raw);
		if (key.isEmpty()) {
			return null;
		}
		for (CropKind crop : CropKind.values()) {
			if (key.equals(fold(crop.label))) {
				return crop;
			}
			for (String alias : crop.aliases) {
				if (key.equals(alias)) {
					return crop;
				}
			}
		}
		return null;
	}

	private static void readVisitorChest(Minecraft client) {
		if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
			return;
		}
		ItemStack info = slotItem(screen, INFO_SLOT);
		if (info == null || info.isEmpty() || !visitorInfo(info)) {
			return;
		}
		ItemStack offer = slotItem(screen, ACCEPT_SLOT);
		if (offer == null || offer.isEmpty()) {
			return;
		}
		String offerName = clean(offer.getHoverName().getString());
		if (!offerName.equalsIgnoreCase("Accept Offer")) {
			return;
		}
		String name = visitorNpcName(info, screen.getTitle());
		if (name.isEmpty()) {
			return;
		}
		List<Need> needs = readRequired(offer);
		if (needs.isEmpty()) {
			needs = readRequired(info);
		}
		if (needs.isEmpty()) {
			return;
		}
		storeShopping(name, needs);
	}

	private static ItemStack slotItem(AbstractContainerScreen<?> screen, int index) {
		for (Slot slot : screen.getMenu().slots) {
			if (slot.index == index) {
				return slot.getItem();
			}
		}
		return ItemStack.EMPTY;
	}

	private static boolean visitorInfo(ItemStack stack) {
		List<String> lore = lore(stack);
		if (lore.size() != 4) {
			for (String line : lore) {
				if (OFFERS_ACCEPTED.matcher(clean(line)).find()) {
					return true;
				}
			}
			return false;
		}
		return OFFERS_ACCEPTED.matcher(clean(lore.get(3))).find();
	}

	private static String visitorNpcName(ItemStack info, Component title) {
		String named = visitorLabel(clean(info.getHoverName().getString()));
		if (!named.isEmpty() && !named.equalsIgnoreCase("Chest")) {
			return named;
		}
		return visitorLabel(clean(title));
	}

	private static List<Need> readRequired(ItemStack stack) {
		List<Need> needs = new ArrayList<>();
		boolean required = false;
		for (String line : lore(stack)) {
			String plain = clean(line);
			if (plain.equalsIgnoreCase("Items Required") || plain.equalsIgnoreCase("Items Required:")) {
				required = true;
				continue;
			}
			if (!required) {
				continue;
			}
			if (plain.isEmpty() || plain.toLowerCase(Locale.ROOT).startsWith("rewards")) {
				break;
			}
			Need need = parseNeed(plain);
			if (need != null) {
				needs.add(need);
			}
		}
		return needs;
	}

	private static void countShopping(LocalPlayer player) {
		VisitorSnap live = liveVisitors();
		if (!live.present() && SHOPPING.isEmpty()) {
			shopping = ShoppingSnap.empty();
			return;
		}
		HeldCounts having = inventoryCounts(player);
		Map<String, Integer> totals = new LinkedHashMap<>();
		Map<String, String> ids = new LinkedHashMap<>();
		List<String> known = new ArrayList<>();
		List<String> fresh = new ArrayList<>();
		if (live.present()) {
			for (String name : live.names()) {
				List<Need> needs = matchShopping(name);
				if (needs == null || needs.isEmpty()) {
					fresh.add(shortName(name));
					continue;
				}
				known.add(shortName(name));
				addNeeds(totals, ids, needs);
			}
		} else {
			for (Map.Entry<String, List<Need>> entry : SHOPPING.entrySet()) {
				known.add(shortName(entry.getKey()));
				addNeeds(totals, ids, entry.getValue());
			}
		}
		List<Need> items = new ArrayList<>();
		Map<String, Long> materials = craftMaterials(ItemStorage.counts(player));
		for (Map.Entry<String, Integer> entry : totals.entrySet()) {
			String name = entry.getKey();
			String id = ids.get(name);
			int have = having.of(name, id);
			int need = entry.getValue();
			boolean craftable = have < need && canCraft(id, need, materials);
			items.add(new Need(name, id == null ? "" : id, need, have, craftable));
		}
		boolean present = live.present() || !items.isEmpty();
		List<String> names = new ArrayList<>(known);
		names.addAll(fresh);
		shopping = new ShoppingSnap(present, List.copyOf(items), List.copyOf(names), List.copyOf(fresh));
	}

	private static void addNeeds(Map<String, Integer> totals, Map<String, String> ids, List<Need> needs) {
		for (Need need : needs) {
			totals.merge(need.name(), need.required(), Integer::sum);
			if (need.id() != null && !need.id().isBlank()) {
				ids.putIfAbsent(need.name(), need.id());
			}
		}
	}

	private static List<Need> matchShopping(String name) {
		List<Need> exact = SHOPPING.get(name);
		if (exact != null) {
			return exact;
		}
		for (Map.Entry<String, List<Need>> entry : SHOPPING.entrySet()) {
			if (sameVisitor(entry.getKey(), name)) {
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
			for (String name : names) {
				if (sameVisitor(stored, name)) {
					keep.add(stored);
					break;
				}
			}
		}
		SHOPPING.keySet().retainAll(keep);
	}

	private static void storeShopping(String name, List<Need> needs) {
		String label = visitorLabel(name);
		if (label.isEmpty()) {
			return;
		}
		String found = null;
		for (String stored : SHOPPING.keySet()) {
			if (sameVisitor(stored, label)) {
				found = stored;
				break;
			}
		}
		if (found != null) {
			SHOPPING.remove(found);
		}
		SHOPPING.put(label, List.copyOf(needs));
	}

	private static boolean sameVisitor(String left, String right) {
		String a = foldVisitor(left);
		String b = foldVisitor(right);
		if (a.isEmpty() || b.isEmpty()) {
			return false;
		}
		if (a.equals(b)) {
			return true;
		}
		return a.endsWith(" " + b) || b.endsWith(" " + a);
	}

	private static HeldCounts inventoryCounts(LocalPlayer player) {
		Map<String, Integer> byId = new LinkedHashMap<>();
		Map<String, Integer> byName = new LinkedHashMap<>();
		Inventory inventory = player.getInventory();
		boolean prior = ItemAppearance.suppress();
		try {
			for (ItemStack stack : inventory.getNonEquipmentItems()) {
				if (stack == null || stack.isEmpty()) {
					continue;
				}
				int count = stack.getCount();
				String id = ItemStorage.idOf(stack);
				if (id != null && !id.isBlank()) {
					byId.merge(id, count, Integer::sum);
				}
				String name = fold(clean(stack.getHoverName().getString()));
				if (!name.isEmpty()) {
					byName.merge(name, count, Integer::sum);
				}
			}
		} finally {
			ItemAppearance.resume(prior);
		}
		return new HeldCounts(byId, byName);
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
		if (amount <= 0) {
			amount = 1;
		}
		name = name.replaceAll("\\s+", " ").trim();
		if (name.isEmpty() || name.equalsIgnoreCase("Items Required") || name.length() < 2) {
			return null;
		}
		String id = SkyblockItems.idFromName(name);
		return new Need(name, id == null ? "" : id, amount, 0, false);
	}

	private static boolean canCraft(String id, long amount, Map<String, Long> materials) {
		if (materials == null || materials.isEmpty() || amount <= 0L) {
			return false;
		}
		String key = SkyblockRecipes.normalize(id);
		if (key.isEmpty() || !SkyblockRecipes.has(key)) {
			return false;
		}
		Map<String, Long> need = SkyblockRecipes.expand(key, amount, SkyblockRecipes.Expand.RAW);
		if (need.isEmpty()) {
			return false;
		}
		for (Map.Entry<String, Long> leaf : need.entrySet()) {
			if (materials.getOrDefault(leaf.getKey(), 0L) < leaf.getValue()) {
				return false;
			}
		}
		return true;
	}

	private static Map<String, Long> craftMaterials(Map<String, Long> owned) {
		if (owned == null || owned.isEmpty()) {
			return Map.of();
		}
		SkyblockRecipes.load();
		Map<String, Long> out = new HashMap<>();
		for (Map.Entry<String, Long> entry : owned.entrySet()) {
			long count = entry.getValue();
			if (count <= 0L) {
				continue;
			}
			String id = SkyblockRecipes.normalize(entry.getKey());
			if (id.isEmpty()) {
				continue;
			}
			Map<String, Long> leaves = SkyblockRecipes.has(id)
				? SkyblockRecipes.expand(id, count, SkyblockRecipes.Expand.RAW)
				: Map.of(id, count);
			for (Map.Entry<String, Long> leaf : leaves.entrySet()) {
				out.merge(leaf.getKey(), leaf.getValue(), Long::sum);
			}
		}
		return out;
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
			String colored = component == null ? "" : NickSteal.toLegacy(component);
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
		String named = "";
		if (colored != null) {
			Matcher matcher = VISITOR_NAME.matcher(colored);
			if (matcher.matches()) {
				named = clean(matcher.group(1));
			}
		}
		if (named.isEmpty()) {
			named = cleaned == null ? "" : cleaned;
		}
		return visitorLabel(named);
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

	private static long parseCount(String value) {
		if (value == null || value.isBlank()) {
			return -1L;
		}
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
			return -1L;
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

	private static String foldVisitor(String name) {
		return fold(visitorLabel(name));
	}

	private static String visitorLabel(String name) {
		String text = clean(name);
		if (text.isEmpty()) {
			return "";
		}
		text = text.replaceFirst("^[\\s\\u25CB\\u25CF\\u25E6\\u00B7\\u2022○●◯]+", "").trim();
		return VISITOR_NEW.matcher(text).replaceAll("").trim();
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

	public record Need(String name, String id, int required, int having, boolean craftable) {
	}

	private record TabLines(List<String> clean, List<String> raw) {
	}

	private record HeldCounts(Map<String, Integer> byId, Map<String, Integer> byName) {
		private int of(String name, String id) {
			int have = 0;
			if (id != null && !id.isBlank()) {
				have += byId.getOrDefault(id, 0);
			} else {
				have += byName.getOrDefault(fold(name), 0);
			}
			long sack = ItemStorage.openedSackCount(id);
			if (sack <= 0L) {
				sack = ItemStorage.openedSackNamed(name);
			}
			if (sack > Integer.MAX_VALUE) {
				have = Integer.MAX_VALUE;
			} else if (sack > 0L && have < Integer.MAX_VALUE - (int) sack) {
				have += (int) sack;
			}
			return have;
		}
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

	private record CropProgress(long total, long toolAt) {
	}

	private record CropMark(String name, boolean boosted) {
	}

	private record RateSample(long at, long value) {
	}

	private record Milestone(int tier, int next, long have, long need, boolean maxed) {
	}
}
