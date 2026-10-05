package dev.stray.client.combat;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.item.ItemText;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Combines matching SkyBlock enchanted books in the anvil.
 * Enter starts it and Enter stops it. Clicks go through {@link OdinClicks#guiClick}
 * with {@link ContainerInput#CLONE}, the same call Auto Experiments uses.
 */
public final class AutoBookCombine {
	private static final int LEFT = 29;
	private static final int RIGHT = 33;
	private static final int RESULT = 13;
	private static final int COMBINE = 22;
	private static final int PREVIEW_WAITS = 12;

	private static boolean running;
	private static boolean combinedAny;
	private static boolean active;
	private static boolean undoing;
	private static String enchant = "";
	private static int level;
	private static int waits;
	private static int sameClicks;
	private static int lastSlot = -1;
	private static String lastPrint = "";
	private static long lastClick;
	private static long pauseUntil;
	private static final Set<String> refused = new HashSet<>();

	private AutoBookCombine() {
	}

	public static void init() {
		ScreenEvents.BEFORE_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			ScreenMouseEvents.allowMouseClick(screen).register((opened, event) -> !blockMouse(opened));
			ScreenMouseEvents.allowMouseRelease(screen).register((opened, event) -> !blockMouse(opened));
		});
	}

	public static boolean keyPressed(AbstractContainerScreen<?> screen, KeyEvent event) {
		if (!StrayConfig.get().autoBookCombineEnabled || !isAnvil(screen) || !isEnter(event)) {
			return false;
		}
		if (running) {
			reset();
			tell("Book combine stopped");
		} else {
			reset();
			running = true;
			tell("Book combine started");
		}
		return true;
	}

	public static void reset() {
		running = false;
		combinedAny = false;
		active = false;
		undoing = false;
		enchant = "";
		level = 0;
		waits = 0;
		sameClicks = 0;
		lastSlot = -1;
		lastPrint = "";
		pauseUntil = 0L;
		refused.clear();
	}

	public static boolean blockMouse(Screen screen) {
		if (!running || !StrayConfig.get().autoBookCombineEnabled) {
			return false;
		}
		return screen instanceof AbstractContainerScreen<?> container && isAnvil(container);
	}

	public static void tick(Minecraft client) {
		if (!StrayConfig.get().autoBookCombineEnabled) {
			if (running) {
				reset();
				tell("Book combine stopped");
			}
			return;
		}
		if (!(client.screen instanceof AbstractContainerScreen<?> screen) || !isAnvil(screen)) {
			if (running) {
				reset();
				tell("Book combine stopped");
			}
			return;
		}
		if (!running) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now < pauseUntil || now - lastClick < delay()) {
			return;
		}
		AbstractContainerMenu menu = screen.getMenu();
		Integer slot = nextClick(menu);
		if (slot == null) {
			sameClicks = 0;
			lastSlot = -1;
			if (!active && !undoing && menu.slots.size() >= 90) {
				boolean clear = anvilClear(menu);
				boolean done = combinedAny;
				reset();
				if (clear) {
					tell(done ? "Book combine finished" : "No matching books");
				} else {
					tell(done ? "Book combine stopped" : "Empty the anvil first");
				}
			}
			return;
		}
		String print = fingerprint(menu);
		if (print.equals(lastPrint) && slot == lastSlot) {
			sameClicks++;
			if (sameClicks >= 4) {
				sameClicks = 0;
				if (undoing) {
					reset();
					tell("Book combine stopped");
					return;
				}
				refuseCurrent();
				undoing = true;
				pauseUntil = now + 1500L;
				return;
			}
		} else {
			sameClicks = 0;
			lastPrint = print;
			lastSlot = slot;
		}
		if (slot == RESULT || slot == COMBINE) {
			combinedAny = true;
		}
		OdinClicks.guiClick(menu.containerId, slot, 0, ContainerInput.CLONE);
		lastClick = now;
	}

	private static long delay() {
		StrayConfig config = StrayConfig.get();
		int clickDelay = config.autoBookCombineClickDelay;
		int delayVariety = config.autoBookCombineDelayVariety;
		int extra = delayVariety <= 0 ? 0 : (int) (Math.random() * (delayVariety + 1));
		return clickDelay + extra;
	}

	private static boolean isEnter(KeyEvent event) {
		int key = event.key();
		return key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
	}

	private static boolean isAnvil(AbstractContainerScreen<?> screen) {
		String title = OdinClicks.noControlCodes(screen.getTitle().getString()).trim();
		return "Anvil".equalsIgnoreCase(title);
	}

	private static boolean anvilClear(AbstractContainerMenu menu) {
		List<Slot> slots = menu.slots;
		if (slots.size() <= RIGHT) {
			return false;
		}
		ItemStack carried = menu.getCarried();
		if (carried != null && !carried.isEmpty()) {
			return false;
		}
		return vacant(stack(slots, LEFT)) && vacant(stack(slots, RIGHT)) && vacant(stack(slots, RESULT));
	}

	private static void tell(String text) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		client.player.sendSystemMessage(
			Component.literal("STRAY").withStyle(Style.EMPTY.withColor(0x2FB5FF).withBold(true))
				.append(Component.literal(" · ").withStyle(Style.EMPTY.withColor(0x6B7280).withBold(false)))
				.append(Component.literal(text).withStyle(Style.EMPTY.withColor(0xE5E7EB).withBold(false)))
		);
	}

	private static Integer nextClick(AbstractContainerMenu menu) {
		List<Slot> slots = menu.slots;
		if (slots.size() < 90) {
			return null;
		}
		int playerStart = slots.size() - 36;
		if (playerStart <= RIGHT) {
			return null;
		}
		ItemStack carried = menu.getCarried();
		boolean leftVacant = vacant(stack(slots, LEFT));
		boolean rightVacant = vacant(stack(slots, RIGHT));
		boolean resultVacant = vacant(stack(slots, RESULT));
		Book left = leftVacant ? null : book(stack(slots, LEFT));
		Book right = rightVacant ? null : book(stack(slots, RIGHT));
		Book result = resultVacant ? null : book(stack(slots, RESULT));
		Book cursor = carried == null || carried.isEmpty() ? null : book(carried);

		if (undoing) {
			return undoClick(slots, playerStart, cursor, left, right, leftVacant, rightVacant);
		}

		if (!active) {
			if (cursor != null || !leftVacant || !rightVacant || !resultVacant || (carried != null && !carried.isEmpty())) {
				return null;
			}
			Pair pair = findPair(slots, playerStart);
			if (pair == null) {
				return null;
			}
			active = true;
			enchant = pair.key;
			level = pair.level;
			waits = 0;
			return pair.first;
		}

		if (cursor != null) {
			if (cursor.matches(enchant, level + 1) && leftVacant && rightVacant) {
				return emptyPlayerSlot(slots, playerStart);
			}
			if (cursor.matches(enchant, level) && leftVacant && rightVacant) {
				return LEFT;
			}
			if (cursor.matches(enchant, level) && left != null && left.matches(enchant, level) && rightVacant) {
				return RIGHT;
			}
			Integer empty = emptyPlayerSlot(slots, playerStart);
			if (empty == null) {
				active = false;
			}
			return empty;
		}

		if (occupiedBySomethingElse(leftVacant, rightVacant, left, right)) {
			active = false;
			return null;
		}

		if (left != null && left.matches(enchant, level) && right != null && right.matches(enchant, level)) {
			if (result != null && result.matches(enchant, level + 1)) {
				Integer combine = combineSlot(slots, playerStart);
				if (combine != null) {
					waits = 0;
					return combine;
				}
			} else if (result != null) {
				undoing = true;
				refuseCurrent();
				waits = 0;
				return RIGHT;
			}
			waits++;
			if (waits >= PREVIEW_WAITS) {
				undoing = true;
				refuseCurrent();
				waits = 0;
				return RIGHT;
			}
			return null;
		}

		if (left != null && left.matches(enchant, level) && rightVacant) {
			Integer match = findMatch(slots, playerStart, enchant, level, -1);
			if (match == null) {
				return LEFT;
			}
			return match;
		}

		if (leftVacant && right != null && right.matches(enchant, level)) {
			return RIGHT;
		}

		if (leftVacant && rightVacant && result != null && result.matches(enchant, level + 1)) {
			return slots.get(RESULT).index;
		}

		if (leftVacant && rightVacant && resultVacant) {
			active = false;
			Pair pair = findPair(slots, playerStart);
			if (pair == null) {
				return null;
			}
			active = true;
			enchant = pair.key;
			level = pair.level;
			waits = 0;
			return pair.first;
		}

		active = false;
		return null;
	}

	private static Integer undoClick(
		List<Slot> slots,
		int playerStart,
		Book cursor,
		Book left,
		Book right,
		boolean leftVacant,
		boolean rightVacant
	) {
		if (cursor != null) {
			Integer empty = emptyPlayerSlot(slots, playerStart);
			if (empty == null) {
				undoing = false;
				active = false;
			}
			return empty;
		}
		if (!rightVacant && right != null && right.matches(enchant, level)) {
			return RIGHT;
		}
		if (!leftVacant && left != null && left.matches(enchant, level) && rightVacant) {
			return LEFT;
		}
		if (!leftVacant || !rightVacant) {
			undoing = false;
			active = false;
			return null;
		}
		undoing = false;
		active = false;
		return null;
	}

	private static boolean occupiedBySomethingElse(boolean leftVacant, boolean rightVacant, Book left, Book right) {
		if (!leftVacant && (left == null || !left.matches(enchant, level))) {
			return true;
		}
		if (!rightVacant && (right == null || !right.matches(enchant, level))) {
			return true;
		}
		return false;
	}

	private static Integer combineSlot(List<Slot> slots, int playerStart) {
		if (combineReady(stack(slots, COMBINE))) {
			return slots.get(COMBINE).index;
		}
		for (int i = 0; i < playerStart; i++) {
			if (i == LEFT || i == RIGHT || i == RESULT) {
				continue;
			}
			if (combineReady(slots.get(i).getItem())) {
				return slots.get(i).index;
			}
		}
		return null;
	}

	private static boolean combineReady(ItemStack stack) {
		if (stack == null || stack.isEmpty() || book(stack) != null) {
			return false;
		}
		String text = plain(stack);
		if (text.isBlank()) {
			return false;
		}
		if (text.contains("error") || text.contains("cannot") || text.contains("invalid")) {
			return false;
		}
		return text.contains("combine");
	}

	private static Pair findPair(List<Slot> slots, int playerStart) {
		String bestKey = "";
		int bestLevel = Integer.MAX_VALUE;
		int first = -1;
		int second = -1;
		for (int i = playerStart; i < slots.size(); i++) {
			Book book = book(slots.get(i).getItem());
			if (book == null || refused.contains(book.pairId())) {
				continue;
			}
			Integer match = findMatch(slots, playerStart, book.key, book.level, slots.get(i).index);
			if (match == null) {
				continue;
			}
			if (book.level < bestLevel) {
				bestLevel = book.level;
				bestKey = book.key;
				first = slots.get(i).index;
				second = match;
			}
		}
		if (first < 0) {
			return null;
		}
		return new Pair(bestKey, bestLevel, first, second);
	}

	private static Integer findMatch(List<Slot> slots, int playerStart, String key, int bookLevel, int except) {
		for (int i = playerStart; i < slots.size(); i++) {
			if (slots.get(i).index == except) {
				continue;
			}
			Book book = book(slots.get(i).getItem());
			if (book != null && book.matches(key, bookLevel)) {
				return slots.get(i).index;
			}
		}
		return null;
	}

	private static Integer emptyPlayerSlot(List<Slot> slots, int playerStart) {
		for (int i = playerStart; i < slots.size(); i++) {
			ItemStack stack = slots.get(i).getItem();
			if (stack == null || stack.isEmpty()) {
				return slots.get(i).index;
			}
		}
		return null;
	}

	private static void refuseCurrent() {
		if (!enchant.isEmpty() && level > 0) {
			refused.add(enchant + ":" + level);
		}
	}

	private static String fingerprint(AbstractContainerMenu menu) {
		List<Slot> slots = menu.slots;
		return enchant + ":" + level + ":" + undoing
			+ ":" + itemKey(menu.getCarried())
			+ ":" + itemKey(stack(slots, LEFT))
			+ ":" + itemKey(stack(slots, RIGHT))
			+ ":" + itemKey(stack(slots, RESULT));
	}

	private static String itemKey(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "-";
		}
		Book book = book(stack);
		if (book != null) {
			return book.pairId();
		}
		return stack.getItem().toString() + "x" + stack.getCount();
	}

	private static ItemStack stack(List<Slot> slots, int index) {
		if (index < 0 || index >= slots.size()) {
			return ItemStack.EMPTY;
		}
		return slots.get(index).getItem();
	}

	private static boolean vacant(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return true;
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (id == null) {
			return false;
		}
		String path = id.getPath();
		// Empty anvil slots are fillers: black panes on the inputs, a barrier on the result.
		return path.equals("barrier")
			|| path.equals("glass")
			|| path.endsWith("glass_pane")
			|| path.endsWith("stained_glass");
	}

	private static Book book(ItemStack stack) {
		if (stack == null || stack.isEmpty() || stack.getCount() != 1 || vacant(stack)) {
			return null;
		}
		CompoundTag custom = OdinClicks.customData(stack);
		CompoundTag extra = custom.getCompoundOrEmpty("ExtraAttributes");
		if (extra.isEmpty()) {
			extra = custom;
		}
		String id = extra.getStringOr("id", "");
		if (!"ENCHANTED_BOOK".equals(id)) {
			return null;
		}
		CompoundTag enchants = extra.getCompoundOrEmpty("enchantments");
		if (enchants.isEmpty() || enchants.keySet().size() != 1) {
			return null;
		}
		String raw = enchants.keySet().iterator().next();
		int enchantLevel = enchants.getIntOr(raw, -1);
		if (enchantLevel <= 0 || !BookCombineRules.canUpgrade(raw, enchantLevel)) {
			return null;
		}
		String text = plain(stack);
		if (text.contains("cannot be combined") || text.contains("can't be combined")) {
			return null;
		}
		return new Book(BookCombineRules.normalize(raw), enchantLevel);
	}

	private static String plain(ItemStack stack) {
		ItemText text = ItemText.capture(stack);
		StringBuilder out = new StringBuilder();
		if (text.name() != null) {
			out.append(text.name().getString()).append('\n');
		}
		ItemLore lore = text.lore();
		if (lore != null) {
			for (Component line : lore.lines()) {
				out.append(line.getString()).append('\n');
			}
		}
		return OdinClicks.noControlCodes(out.toString()).toLowerCase(Locale.ROOT);
	}

	private record Book(String key, int level) {
		boolean matches(String otherKey, int otherLevel) {
			return level == otherLevel && key.equals(otherKey);
		}

		String pairId() {
			return key + ":" + level;
		}
	}

	private record Pair(String key, int level, int first, int second) {
	}
}
