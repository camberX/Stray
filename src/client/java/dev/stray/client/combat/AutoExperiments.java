package dev.stray.client.combat;

import dev.stray.client.config.StrayConfig;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * OdinClient AutoExperiments.kt, including Ultrasequencer's inverted nextClick.
 */
public final class AutoExperiments {
	private static final Pattern DIGITS = Pattern.compile("\\d+");
	private static ExperimentHandler handler;
	private static long lastClick;

	private AutoExperiments() {
	}

	public static void init() {
		ScreenEvents.BEFORE_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			ScreenMouseEvents.allowMouseClick(screen).register((opened, event) -> !blockMouse(opened));
			ScreenMouseEvents.allowMouseRelease(screen).register((opened, event) -> !blockMouse(opened));
		});
	}

	public static void reset() {
		handler = null;
		lastClick = 0;
	}

	public static void onOpen(Screen screen) {
		if (!StrayConfig.get().autoExperimentsEnabled) {
			return;
		}
		if (screen == null) {
			return;
		}
		String title = screen.getTitle().getString();
		if (title.startsWith("Chronomatron (")) {
			handler = new ChronomatronHandler();
		} else if (title.startsWith("Ultrasequencer (")) {
			handler = new UltrasequencerHandler();
		} else if (title.startsWith("Superpairs (") && StrayConfig.get().autoExperimentsSuperpairs) {
			handler = new SuperpairsHandler();
		} else {
			handler = null;
		}
	}

	public static boolean blockMouse(Screen screen) {
		if (handler == null) {
			return false;
		}
		if (!(screen instanceof AbstractContainerScreen<?>)) {
			return false;
		}
		return StrayConfig.get().autoExperimentsEnabled;
	}

	public static void onPacket(Packet<?> packet) {
		if (!StrayConfig.get().autoExperimentsEnabled) {
			return;
		}
		if (handler == null) {
			return;
		}
		if (packet instanceof ClientboundContainerSetSlotPacket) {
			handler.onSlotUpdate();
		}
	}

	public static void tick(Minecraft client) {
		if (!StrayConfig.get().autoExperimentsEnabled) {
			return;
		}
		ExperimentHandler current = handler;
		if (current == null) {
			return;
		}
		if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
			return;
		}

		long now = System.currentTimeMillis();
		if (now - lastClick < delay()) {
			return;
		}

		Integer slotId = current.nextClick();
		if (slotId != null) {
			OdinClicks.guiClick(screen.getMenu().containerId, slotId, 0, ContainerInput.CLONE);
			lastClick = now;
		}

		if (!current.shouldClose(StrayConfig.get().autoExperimentsAutoClose)) {
			return;
		}

		if (client.player != null) {
			client.player.closeContainer();
		}
		handler = null;
	}

	private static long delay() {
		StrayConfig config = StrayConfig.get();
		int clickDelay = config.autoExperimentsClickDelay;
		int delayVariety = config.autoExperimentsDelayVariety;
		int extra = delayVariety <= 0 ? 0 : (int) (Math.random() * (delayVariety + 1));
		return (long) (clickDelay + extra);
	}

	private static abstract class ExperimentHandler {
		protected int clicks;
		protected boolean hasData;

		abstract void onSlotUpdate();

		abstract Integer nextClick();

		abstract boolean shouldClose(boolean autoClose);
	}

	private static final class ChronomatronHandler extends ExperimentHandler {
		private final List<Integer> order = new ArrayList<>();
		private int lastAddedSlot = -1;
		private boolean close;

		@Override
		void onSlotUpdate() {
			Minecraft client = Minecraft.getInstance();
			if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
				return;
			}
			List<Slot> slots = screen.getMenu().slots;
			if (slots.size() <= 49) {
				return;
			}
			ItemStack center = slots.get(49).getItem();

			if (
				lastAddedSlot != -1 &&
				center.getItem() == Items.GLOWSTONE &&
				!OdinClicks.hasGlint(slots.get(lastAddedSlot).getItem())
			) {
				int serum = StrayConfig.get().autoExperimentsSerumCount;
				close = order.size() > (StrayConfig.get().autoExperimentsGetMaxXp ? 15 : 11 - serum);
				hasData = false;
				return;
			}

			if (hasData || center.getItem() != Items.CLOCK) {
				return;
			}

			Slot slot = null;
			for (Slot candidate : slots) {
				if (candidate.index >= 10 && candidate.index <= 43 && OdinClicks.hasGlint(candidate.getItem())) {
					slot = candidate;
					break;
				}
			}
			if (slot == null) {
				return;
			}

			order.add(slot.index);
			lastAddedSlot = slot.index;
			hasData = true;
			clicks = 0;
		}

		@Override
		Integer nextClick() {
			return hasData && clicks < order.size() ? order.get(clicks++) : null;
		}

		@Override
		boolean shouldClose(boolean autoClose) {
			if (!autoClose || !close) {
				return false;
			}
			if (clicks < order.size()) {
				return false;
			}
			close = false;
			return true;
		}
	}

	private static final class UltrasequencerHandler extends ExperimentHandler {
		private final ConcurrentHashMap<Integer, Integer> order = new ConcurrentHashMap<>();

		@Override
		void onSlotUpdate() {
			Minecraft client = Minecraft.getInstance();
			if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
				return;
			}
			List<Slot> slots = screen.getMenu().slots;
			if (slots.size() <= 49) {
				return;
			}
			ItemStack center = slots.get(49).getItem();

			if (center.getItem() == Items.CLOCK) {
				hasData = false;
				return;
			}

			if (hasData || center.getItem() != Items.GLOWSTONE) {
				return;
			}

			order.clear();

			for (Slot slot : slots) {
				if (slot.index >= 9 && slot.index <= 44
					&& DIGITS.matcher(OdinClicks.noControlCodes(slot.getItem().getHoverName().getString())).matches()) {
					order.put(slot.getItem().getCount() - 1, slot.index);
				}
			}

			hasData = true;
			clicks = 0;
		}

		@Override
		Integer nextClick() {
			return !hasData ? order.get(clicks++) : null;
		}

		@Override
		boolean shouldClose(boolean autoClose) {
			int serum = StrayConfig.get().autoExperimentsSerumCount;
			return autoClose && order.size() > (StrayConfig.get().autoExperimentsGetMaxXp ? 20 : 9 - serum);
		}
	}

	/**
	 * Superpairs memory game. Hidden cards are cyan stained glass; after the first
	 * pick the other hidden cards are renamed "Click a second button!". Every
	 * reveal is remembered, so a card seen once is paired the moment its twin
	 * turns up. Books beat Titanic bottles beat Enchanting XP dyes, and within XP
	 * the biggest number wins.
	 */
	private static final class SuperpairsHandler extends ExperimentHandler {
		private static final long SETTLE_MS = 260;
		private static final long PERMANENT_MS = 1400;
		private static final Pattern XP = Pattern.compile("([\\d,.]+)\\s*([kKmMbB])?\\s+Enchanting Exp", Pattern.CASE_INSENSITIVE);
		private static final Pattern ROMAN = Pattern.compile("\\b(X|IX|VIII|VII|VI|V|IV|III|II|I)$");

		private final java.util.Map<Integer, String> memory = new java.util.HashMap<>();
		private final java.util.Map<Integer, Long> revealedSince = new java.util.HashMap<>();
		private final java.util.Set<Integer> settled = new java.util.HashSet<>();
		private final java.util.Random random = new java.util.Random();
		private long lastUpdate = System.currentTimeMillis();
		private long lastOwnClick;

		@Override
		void onSlotUpdate() {
			lastUpdate = System.currentTimeMillis();
		}

		@Override
		Integer nextClick() {
			Minecraft client = Minecraft.getInstance();
			if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
				return null;
			}
			long now = System.currentTimeMillis();
			if (now - lastUpdate < SETTLE_MS || now - lastOwnClick < SETTLE_MS) {
				return null;
			}
			List<Slot> slots = screen.getMenu().slots;
			if (slots.size() < 45) {
				return null;
			}

			List<Integer> hidden = new ArrayList<>();
			java.util.Map<Integer, String> revealed = new java.util.HashMap<>();
			boolean secondPending = false;
			for (int index = 9; index <= 44; index++) {
				ItemStack stack = slots.get(index).getItem();
				if (stack.isEmpty() || stack.is(Items.BLACK_STAINED_GLASS_PANE)) {
					continue;
				}
				String name = OdinClicks.noControlCodes(stack.getHoverName().getString()).trim();
				if (stack.is(Items.CYAN_STAINED_GLASS)) {
					hidden.add(index);
					if (name.toLowerCase(java.util.Locale.ROOT).contains("second")) {
						secondPending = true;
					}
					revealedSince.remove(index);
					continue;
				}
				String key = identity(stack, name);
				if (key == null) {
					continue;
				}
				revealed.put(index, key);
				memory.put(index, key);
				revealedSince.putIfAbsent(index, now);
			}
			if (hidden.isEmpty()) {
				return null;
			}

			// Cards that stayed face-up long enough are claimed pairs or spent power-ups.
			boolean transientReveal = false;
			for (java.util.Map.Entry<Integer, Long> entry : revealedSince.entrySet()) {
				if (settled.contains(entry.getKey())) {
					continue;
				}
				if (now - entry.getValue() >= PERMANENT_MS) {
					settled.add(entry.getKey());
				} else if (!secondPending) {
					transientReveal = true;
				}
			}
			if (transientReveal) {
				return null;
			}

			Integer choice = secondPending ? secondPick(revealed, hidden) : firstPick(hidden);
			if (choice != null) {
				lastOwnClick = now;
			}
			return choice;
		}

		private Integer secondPick(java.util.Map<Integer, String> revealed, List<Integer> hidden) {
			String want = null;
			for (java.util.Map.Entry<Integer, String> entry : revealed.entrySet()) {
				if (!settled.contains(entry.getKey())) {
					want = entry.getValue();
					break;
				}
			}
			if (want != null && !skipped(want)) {
				for (int slot : hidden) {
					if (want.equals(memory.get(slot))) {
						return slot;
					}
				}
			}
			return explore(hidden);
		}

		private Integer firstPick(List<Integer> hidden) {
			java.util.Map<String, List<Integer>> known = new java.util.HashMap<>();
			for (int slot : hidden) {
				String key = memory.get(slot);
				if (key != null && !isPowerup(key) && !skipped(key)) {
					known.computeIfAbsent(key, ignored -> new ArrayList<>()).add(slot);
				}
			}
			String best = null;
			long bestScore = Long.MIN_VALUE;
			for (java.util.Map.Entry<String, List<Integer>> entry : known.entrySet()) {
				if (entry.getValue().size() < 2) {
					continue;
				}
				long score = score(entry.getKey());
				if (score > bestScore) {
					bestScore = score;
					best = entry.getKey();
				}
			}
			if (best != null) {
				return known.get(best).getFirst();
			}
			return explore(hidden);
		}

		/** Prefer cards never seen, then known cards we still care about, then anything. */
		private Integer explore(List<Integer> hidden) {
			List<Integer> unknown = new ArrayList<>();
			List<Integer> wanted = new ArrayList<>();
			for (int slot : hidden) {
				String key = memory.get(slot);
				if (key == null) {
					unknown.add(slot);
				} else if (!skipped(key)) {
					wanted.add(slot);
				}
			}
			List<Integer> pool = !unknown.isEmpty() ? unknown : !wanted.isEmpty() ? wanted : hidden;
			return pool.get(random.nextInt(pool.size()));
		}

		private static boolean skipped(String key) {
			StrayConfig config = StrayConfig.get();
			return switch (category(key)) {
				case BOOK -> config.superpairsSkipBooks;
				case TITANIC -> config.superpairsSkipTitanic;
				case XP -> config.superpairsSkipXp;
				case BOTTLE -> config.superpairsSkipBottles;
				default -> false;
			};
		}

		private enum Category {
			BOOK, TITANIC, XP, BOTTLE, POWERUP, OTHER
		}

		private static Category category(String key) {
			int split = key.indexOf('|');
			String id = key.substring(0, split);
			String lower = key.substring(split + 1).toLowerCase(java.util.Locale.ROOT);
			if (isPowerup(key)) {
				return Category.POWERUP;
			}
			if (id.endsWith("enchanted_book")) {
				return Category.BOOK;
			}
			if (lower.contains("titanic")) {
				return Category.TITANIC;
			}
			if (XP.matcher(lower).find()) {
				return Category.XP;
			}
			if (lower.contains("experience bottle") || lower.contains("bottle o' enchanting") || lower.contains("colossal")) {
				return Category.BOTTLE;
			}
			return Category.OTHER;
		}

		private static String identity(ItemStack stack, String name) {
			if (name.isEmpty()) {
				return null;
			}
			String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			return id + "|" + name;
		}

		private static boolean isPowerup(String key) {
			String name = key.substring(key.indexOf('|') + 1).toLowerCase(java.util.Locale.ROOT);
			return name.contains("instant find") || name.contains("click") || name.contains("powerup") || name.contains("power-up");
		}

		private static long score(String key) {
			int split = key.indexOf('|');
			String id = key.substring(0, split);
			String name = key.substring(split + 1);
			String lower = name.toLowerCase(java.util.Locale.ROOT);
			if (id.endsWith("enchanted_book")) {
				return 4_000_000_000L + roman(name) * 1_000_000L;
			}
			if (lower.contains("titanic")) {
				return 3_000_000_000L;
			}
			java.util.regex.Matcher xp = XP.matcher(name);
			if (xp.find()) {
				return 1_000_000_000L + parseAmount(xp.group(1), xp.group(2));
			}
			if (lower.contains("colossal")) {
				return 900_000_000L;
			}
			if (lower.contains("grand experience")) {
				return 500_000_000L;
			}
			if (lower.contains("experience bottle") || lower.contains("bottle o' enchanting")) {
				return 200_000_000L;
			}
			return 100_000_000L;
		}

		private static long parseAmount(String number, String suffix) {
			try {
				double value = Double.parseDouble(number.replace(",", ""));
				if (suffix != null) {
					switch (Character.toLowerCase(suffix.charAt(0))) {
						case 'k' -> value *= 1_000;
						case 'm' -> value *= 1_000_000;
						case 'b' -> value *= 1_000_000_000;
						default -> {
						}
					}
				}
				return (long) value;
			} catch (NumberFormatException ignored) {
				return 0;
			}
		}

		private static int roman(String name) {
			java.util.regex.Matcher matcher = ROMAN.matcher(name.trim());
			if (!matcher.find()) {
				return 0;
			}
			return switch (matcher.group(1)) {
				case "I" -> 1;
				case "II" -> 2;
				case "III" -> 3;
				case "IV" -> 4;
				case "V" -> 5;
				case "VI" -> 6;
				case "VII" -> 7;
				case "VIII" -> 8;
				case "IX" -> 9;
				case "X" -> 10;
				default -> 0;
			};
		}

		@Override
		boolean shouldClose(boolean autoClose) {
			return false;
		}
	}
}
