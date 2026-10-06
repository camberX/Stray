package dev.stray.client.combat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.item.ItemAppearance;
import dev.stray.client.item.ItemText;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
	private static int seenBooks;
	private static String missDetail = "";
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
		seenBooks = 0;
		missDetail = "";
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
				int noticed = seenBooks;
				String detail = missDetail;
				reset();
				if (clear) {
					String none = noticed == 0 ? "No enchanted books found" : "No matching books";
					if (!detail.isEmpty() && noticed > 0) {
						none = none + " (" + detail + ")";
					}
					tell(done ? "Book combine finished" : none);
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
			Pair pair = findPair(slots);
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
			Integer match = findMatch(slots, enchant, level, -1);
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
			Pair pair = findPair(slots);
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

	private static Pair findPair(List<Slot> slots) {
		seenBooks = 0;
		String bestKey = "";
		int bestLevel = Integer.MAX_VALUE;
		int first = -1;
		int second = -1;
		for (int i = 0; i < slots.size(); i++) {
			int slot = slots.get(i).index;
			if (anvilSlot(slot)) {
				continue;
			}
			ItemStack stack = slots.get(i).getItem();
			if (enchantedBookItem(stack)) {
				seenBooks++;
			}
			Book book = book(stack);
			if (book == null) {
				if (missDetail.isEmpty() && enchantedBookItem(stack)) {
					missDetail = describe(stack);
				}
				continue;
			}
			if (refused.contains(book.pairId())) {
				continue;
			}
			Integer match = findMatch(slots, book.key, book.level, slot);
			if (match == null) {
				continue;
			}
			if (book.level < bestLevel) {
				bestLevel = book.level;
				bestKey = book.key;
				first = slot;
				second = match;
			}
		}
		if (first < 0) {
			return null;
		}
		return new Pair(bestKey, bestLevel, first, second);
	}

	private static Integer findMatch(List<Slot> slots, String key, int bookLevel, int except) {
		for (int i = 0; i < slots.size(); i++) {
			int slot = slots.get(i).index;
			if (slot == except || anvilSlot(slot)) {
				continue;
			}
			Book book = book(slots.get(i).getItem());
			if (book != null && book.matches(key, bookLevel)) {
				return slot;
			}
		}
		return null;
	}

	private static boolean anvilSlot(int slot) {
		return slot == LEFT || slot == RIGHT || slot == RESULT || slot == COMBINE;
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
		if (stack == null || stack.isEmpty() || stack.getCount() < 1 || vacant(stack)) {
			return null;
		}
		// Item data is the enchantment. Lore is only a fallback when the data has none.
		NbtRead data = readNbt(stack);
		if (data.refused) {
			return null;
		}
		if (data.book != null) {
			return data.book;
		}
		if (!enchantedBookItem(stack)) {
			return null;
		}
		List<String> lore = loreLines(stack);
		if (blocked(lore)) {
			return null;
		}
		return enchantFromLore(lore);
	}

	/** SkyBlock enchanted books are named Enchanted Book. The enchantment is item data. */
	private static boolean enchantedBookItem(ItemStack stack) {
		if (stack == null || stack.isEmpty() || vacant(stack)) {
			return false;
		}
		if (enchantedBookName(textOf(stack.get(DataComponents.CUSTOM_NAME)))) {
			return true;
		}
		if (enchantedBookName(textOf(stack.get(DataComponents.ITEM_NAME)))) {
			return true;
		}
		if (enchantedBookName(textOf(stack.getHoverName()))) {
			return true;
		}
		return enchantedBookName(legacyName(OdinClicks.customData(stack)));
	}

	private static boolean enchantedBookName(String name) {
		String clean = BookCombineRules.clean(name);
		return clean.equals("enchanted book")
			|| clean.startsWith("enchanted book ")
			|| clean.endsWith(" enchanted book")
			|| clean.contains(" enchanted book ");
	}

	private static Book enchantFromLore(List<String> lore) {
		Book found = null;
		boolean blockedLevel = false;
		List<String> lines = joinedLevels(lore);
		for (String line : lines) {
			BookCombineRules.EnchantLevel parsed = BookCombineRules.findIn(line);
			if (parsed == null) {
				continue;
			}
			if (found != null && !found.matches(parsed.key(), parsed.level())) {
				return null;
			}
			found = new Book(parsed.key(), parsed.level());
			if (!BookCombineRules.canUpgrade(parsed.key(), parsed.level())) {
				blockedLevel = true;
			}
		}
		if (found == null || blockedLevel || !BookCombineRules.canUpgrade(found.key, found.level)) {
			return null;
		}
		return found;
	}

	/** "Feather Falling" on one line and "VI" on the next is one enchant. */
	private static List<String> joinedLevels(List<String> lore) {
		List<String> joined = new ArrayList<>();
		for (int i = 0; i < lore.size(); i++) {
			String line = lore.get(i);
			if (i + 1 < lore.size()
				&& BookCombineRules.findIn(line) == null
				&& !BookCombineRules.fromDisplay(line).isEmpty()
				&& BookCombineRules.levelNumber(lore.get(i + 1)) > 0) {
				joined.add(line + " " + lore.get(i + 1));
				i++;
				continue;
			}
			joined.add(line);
		}
		return joined;
	}

	/**
	 * Item data is the book. SkyBlock stores {@code enchantments:{feather_falling:6}}
	 * and the id {@code ENCHANTED_BOOK}. The same text is also scanned, because the
	 * tag prints as {@code feather_falling:6}. Lore is only used when that data has
	 * no enchant. A level the anvil cannot raise, or more than one enchant, is refused.
	 */
	private static NbtRead readNbt(ItemStack stack) {
		CompoundTag custom = OdinClicks.customData(stack);
		String id = custom.isEmpty() ? null : skyblockId(custom);
		Book product = productBook(id);
		boolean bookItem = bookId(id) || product != null || vanillaBook(stack) || enchantedBookItem(stack);
		CompoundTag map = enchantCompound(custom);
		int known = knownEnchantCount(map);
		if (known > 1) {
			return bookItem ? NbtRead.refuse() : NbtRead.absent();
		}
		if (known == 1 && bookItem) {
			Book parsed = singleEnchant(map);
			if (parsed == null || !BookCombineRules.canUpgrade(parsed.key, parsed.level)) {
				return NbtRead.refuse();
			}
			if (product != null && !product.matches(parsed.key, parsed.level)) {
				return NbtRead.refuse();
			}
			return NbtRead.ok(parsed);
		}
		if (bookItem && !custom.isEmpty()) {
			BookCombineRules.EnchantLevel inTag = BookCombineRules.findOnly(custom.toString());
			if (inTag != null) {
				if (!BookCombineRules.canUpgrade(inTag.key(), inTag.level())) {
					return NbtRead.refuse();
				}
				if (product != null && !product.matches(inTag.key(), inTag.level())) {
					return NbtRead.refuse();
				}
				return NbtRead.ok(new Book(inTag.key(), inTag.level()));
			}
		}
		Map<String, Integer> enchants = (custom.isEmpty() || known > 0) ? null : enchantments(custom);
		if ((enchants == null || enchants.isEmpty()) && bookItem) {
			enchants = storedEnchantments(stack);
		}
		if (enchants != null && !enchants.isEmpty()) {
			if (!bookItem) {
				return NbtRead.absent();
			}
			if (enchants.size() != 1) {
				return NbtRead.refuse();
			}
			Map.Entry<String, Integer> entry = enchants.entrySet().iterator().next();
			if (entry.getValue() <= 0 || !BookCombineRules.canUpgrade(entry.getKey(), entry.getValue())) {
				return NbtRead.refuse();
			}
			Book parsed = new Book(entry.getKey(), entry.getValue());
			if (product != null && !product.matches(parsed.key, parsed.level)) {
				return NbtRead.refuse();
			}
			return NbtRead.ok(parsed);
		}
		if (product != null && bookItem) {
			if (!BookCombineRules.canUpgrade(product.key, product.level)) {
				return NbtRead.refuse();
			}
			return NbtRead.ok(product);
		}
		return NbtRead.absent();
	}

	private static CompoundTag enchantCompound(CompoundTag tag) {
		if (tag == null || tag.isEmpty()) {
			return new CompoundTag();
		}
		CompoundTag direct = namedCompound(tag, "enchantments");
		if (!direct.isEmpty()) {
			return direct;
		}
		CompoundTag extra = namedCompound(tag, "extraattributes");
		if (!extra.isEmpty()) {
			return namedCompound(extra, "enchantments");
		}
		return new CompoundTag();
	}

	private static CompoundTag namedCompound(CompoundTag tag, String wanted) {
		for (String key : tag.keySet()) {
			String lower = key.toLowerCase(Locale.ROOT);
			if (!lower.equals(wanted) && !lower.endsWith(":" + wanted)) {
				continue;
			}
			CompoundTag child = tag.getCompoundOrEmpty(key);
			if (!child.isEmpty()) {
				return child;
			}
		}
		return new CompoundTag();
	}

	private static int knownEnchantCount(CompoundTag map) {
		int count = 0;
		if (map == null || map.isEmpty()) {
			return 0;
		}
		for (String key : map.keySet()) {
			if (enchantKey(key) != null && levelOf(map, key) > 0) {
				count++;
			}
		}
		return count;
	}

	private static Book singleEnchant(CompoundTag map) {
		Book found = null;
		for (String key : map.keySet()) {
			String enchant = enchantKey(key);
			int level = levelOf(map, key);
			if (enchant == null || level <= 0) {
				continue;
			}
			Book parsed = new Book(enchant, level);
			if (found != null && !found.matches(parsed.key, parsed.level)) {
				return null;
			}
			found = parsed;
		}
		return found;
	}

	private static int levelOf(CompoundTag map, String key) {
		int level = map.getIntOr(key, -1);
		if (level > 0) {
			return level;
		}
		return readLevel(map.get(key));
	}

	private static String describe(ItemStack stack) {
		CompoundTag custom = OdinClicks.customData(stack);
		String id = custom.isEmpty() ? "-" : String.valueOf(skyblockId(custom));
		String keys = custom.isEmpty() ? "-" : custom.keySet().toString();
		CompoundTag map = enchantCompound(custom);
		String ench = map.isEmpty() ? "-" : clip(map.toString(), 80);
		String line = "";
		List<String> lines = loreLines(stack);
		for (String candidate : lines) {
			String lower = candidate.toLowerCase(Locale.ROOT);
			if (BookCombineRules.findIn(candidate) != null || lower.contains("fall") || lower.contains("enchant")) {
				line = candidate;
				break;
			}
		}
		if (line.isEmpty() && !lines.isEmpty()) {
			line = lines.get(Math.min(2, lines.size() - 1));
		}
		return clip("id=" + id + " keys=" + keys + " ench=" + ench + " line=" + clip(line, 50), 180);
	}

	private static String clip(String text, int max) {
		if (text == null) {
			return "";
		}
		String flat = text.replace('\n', ' ');
		return flat.length() <= max ? flat : flat.substring(0, max);
	}

	private static List<String> loreLines(ItemStack stack) {
		List<String> lines = new ArrayList<>();
		boolean prior = ItemAppearance.suppress();
		try {
			ItemLore lore = stack.get(DataComponents.LORE);
			if (lore != null) {
				for (Component line : lore.lines()) {
					addLine(lines, line);
				}
				for (Component line : lore.styledLines()) {
					addLine(lines, line);
				}
			}
			addLegacyLore(lines, OdinClicks.customData(stack), 0);
			Minecraft client = Minecraft.getInstance();
			try {
				for (Component line : Screen.getTooltipFromItem(client, stack)) {
					addLine(lines, line);
				}
			} catch (RuntimeException ignored) {
				// The lore lines above are the same text the tooltip draws.
			}
			if (client.player != null) {
				try {
					Item.TooltipContext context = client.level == null
						? Item.TooltipContext.EMPTY
						: Item.TooltipContext.of(client.level);
					for (Component line : stack.getTooltipLines(context, client.player, TooltipFlag.NORMAL)) {
						addLine(lines, line);
					}
					for (Component line : stack.getTooltipLines(context, client.player, TooltipFlag.ADVANCED)) {
						addLine(lines, line);
					}
				} catch (RuntimeException ignored) {
					// The screen tooltip above is the list the anvil draws.
				}
			}
		} finally {
			ItemAppearance.resume(prior);
		}
		return lines;
	}

	private static void addLegacyLore(List<String> lines, CompoundTag tag, int depth) {
		if (tag == null || tag.isEmpty() || depth > 4) {
			return;
		}
		addLoreTag(lines, tag.get("Lore"));
		addLoreTag(lines, tag.get("lore"));
		CompoundTag display = tag.getCompoundOrEmpty("display");
		if (!display.isEmpty()) {
			addLoreTag(lines, display.get("Lore"));
			addLoreTag(lines, display.get("lore"));
		}
		for (String key : new String[]{"ExtraAttributes", "tag", "components", "minecraft:custom_data"}) {
			CompoundTag child = tag.getCompoundOrEmpty(key);
			if (!child.isEmpty()) {
				addLegacyLore(lines, child, depth + 1);
			}
		}
	}

	private static void addLoreTag(List<String> lines, Tag lore) {
		if (lore == null) {
			return;
		}
		if (lore.asList().isPresent()) {
			ListTag list = lore.asList().get();
			for (int i = 0; i < list.size(); i++) {
				String text = readTagString(list.get(i));
				addPlain(lines, text == null ? "" : unwrapText(text));
			}
			return;
		}
		String text = readTagString(lore);
		if (text != null) {
			addPlain(lines, unwrapText(text));
		}
	}

	private static String legacyName(CompoundTag tag) {
		if (tag == null || tag.isEmpty()) {
			return "";
		}
		CompoundTag display = tag.getCompoundOrEmpty("display");
		if (display.isEmpty()) {
			display = tag.getCompoundOrEmpty("tag").getCompoundOrEmpty("display");
		}
		String name = readTagString(display.get("Name"));
		if (name == null || name.isBlank()) {
			name = readTagString(display.get("name"));
		}
		if (name == null || name.isBlank()) {
			CompoundTag components = tag.getCompoundOrEmpty("components");
			name = readTagString(components.get("minecraft:custom_name"));
		}
		return name == null ? "" : unwrapText(name);
	}

	private static String textOf(Component component) {
		if (component == null) {
			return "";
		}
		String plain = component.getString();
		String json = unwrapText(plain);
		if (!json.isBlank() && (plain.isBlank() || json.length() > plain.length())) {
			return json;
		}
		return plain == null ? "" : plain;
	}

	private static String unwrapText(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		String text = raw.trim();
		if (text.startsWith("{") && text.contains("\"text\"")) {
			try {
				return flattenJson(JsonParser.parseString(text));
			} catch (Exception ignored) {
				return text;
			}
		}
		if (text.startsWith("\"") && text.endsWith("\"") && text.length() >= 2) {
			text = text.substring(1, text.length() - 1);
		}
		return text.replace("\\u00a7", "§").replace("\\u00A7", "§");
	}

	private static String flattenJson(JsonElement element) {
		if (element == null || element.isJsonNull()) {
			return "";
		}
		if (element.isJsonPrimitive()) {
			return element.getAsString();
		}
		if (element.isJsonArray()) {
			StringBuilder out = new StringBuilder();
			for (JsonElement child : element.getAsJsonArray()) {
				out.append(flattenJson(child));
			}
			return out.toString();
		}
		if (!element.isJsonObject()) {
			return "";
		}
		JsonObject object = element.getAsJsonObject();
		StringBuilder out = new StringBuilder();
		if (object.has("text") && object.get("text").isJsonPrimitive()) {
			out.append(object.get("text").getAsString());
		}
		if (object.has("extra") && object.get("extra").isJsonArray()) {
			for (JsonElement child : object.getAsJsonArray("extra")) {
				out.append(flattenJson(child));
			}
		}
		return out.toString();
	}

	private static boolean blocked(List<String> lines) {
		for (String line : lines) {
			String clean = BookCombineRules.clean(line);
			if (clean.contains("cannot be combined") || clean.contains("cant be combined")) {
				return true;
			}
		}
		return false;
	}

	private static void addLine(List<String> lines, Component component) {
		addPlain(lines, textOf(component));
	}

	private static void addPlain(List<String> lines, String raw) {
		if (raw == null || raw.isBlank()) {
			return;
		}
		String plain = OdinClicks.noControlCodes(raw).replace('\u00A0', ' ').replaceAll("[\\u200B-\\u200D\\uFEFF]", "");
		for (String part : plain.split("\\n")) {
			String line = part.trim();
			if (!line.isEmpty() && !lines.contains(line)) {
				lines.add(line);
			}
		}
	}

	private static boolean vanillaBook(ItemStack stack) {
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return id != null && "enchanted_book".equals(id.getPath());
	}

	private static boolean bookId(String id) {
		return "ENCHANTED_BOOK".equals(id) || "MINECRAFT:ENCHANTED_BOOK".equals(id);
	}

	private static Book productBook(String id) {
		BookCombineRules.EnchantLevel parsed = BookCombineRules.fromSkyblockId(id);
		if (parsed == null) {
			return null;
		}
		return new Book(parsed.key(), parsed.level());
	}

	private static String skyblockId(CompoundTag root) {
		String fallback = null;
		String genericBook = null;
		for (CompoundTag node : attributeNodes(root)) {
			String id = readId(node);
			if (id == null) {
				continue;
			}
			if (BookCombineRules.fromSkyblockId(id) != null) {
				return id;
			}
			if (genericBook == null && bookId(id)) {
				genericBook = id;
			}
			if (fallback == null && !id.contains(":")) {
				fallback = id;
			}
		}
		return genericBook != null ? genericBook : fallback;
	}

	private static String readId(CompoundTag tag) {
		if (tag == null || tag.isEmpty()) {
			return null;
		}
		for (String key : tag.keySet()) {
			String lower = key.toLowerCase(Locale.ROOT);
			if (!lower.equals("id") && !lower.endsWith(":id")) {
				continue;
			}
			String value = readTagString(tag.get(key));
			if (value != null && !value.isBlank()) {
				return value.trim().toUpperCase(Locale.ROOT);
			}
		}
		return null;
	}

	private static Map<String, Integer> enchantments(CompoundTag root) {
		List<Map<String, Integer>> found = new ArrayList<>();
		collectEnchantMaps(root, 0, found);
		Map<String, Integer> single = null;
		for (Map<String, Integer> map : found) {
			if (map.size() > 1) {
				return map;
			}
			if (map.size() != 1) {
				continue;
			}
			if (single == null) {
				single = map;
				continue;
			}
			String key = map.keySet().iterator().next();
			if (!single.containsKey(key) || !single.get(key).equals(map.get(key))) {
				Map<String, Integer> conflict = new LinkedHashMap<>(single);
				conflict.putAll(map);
				return conflict;
			}
		}
		return single;
	}

	private static void collectEnchantMaps(Tag tag, int depth, List<Map<String, Integer>> found) {
		if (tag == null || depth > 8) {
			return;
		}
		if (tag.asCompound().isPresent()) {
			CompoundTag compound = tag.asCompound().get();
			// Only the enchantments compound. Other numeric fields are not enchants.
			Map<String, Integer> named = enchantmentsOf(compound);
			if (named != null && !named.isEmpty()) {
				found.add(named);
			}
			for (String key : compound.keySet()) {
				collectEnchantMaps(compound.get(key), depth + 1, found);
			}
			return;
		}
		if (tag.asList().isPresent()) {
			for (Tag child : tag.asList().get()) {
				collectEnchantMaps(child, depth + 1, found);
			}
		}
	}

	private static Map<String, Integer> enchantmentsOf(CompoundTag node) {
		if (node == null || node.isEmpty()) {
			return null;
		}
		for (String key : node.keySet()) {
			String lower = key.toLowerCase(Locale.ROOT);
			if (!lower.equals("enchantments") && !lower.endsWith(":enchantments") && !lower.equals("enchants")) {
				continue;
			}
			Map<String, Integer> parsed = readEnchantTag(node.get(key));
			if (parsed != null && !parsed.isEmpty()) {
				return parsed;
			}
		}
		return null;
	}

	private static Map<String, Integer> readEnchantTag(Tag raw) {
		if (raw == null) {
			return null;
		}
		if (raw.asCompound().isPresent()) {
			return readEnchantCompound(raw.asCompound().get());
		}
		if (raw.asList().isPresent()) {
			Map<String, Integer> map = new LinkedHashMap<>();
			ListTag list = raw.asList().get();
			for (int i = 0; i < list.size(); i++) {
				CompoundTag entry = list.getCompoundOrEmpty(i);
				if (!entry.isEmpty()) {
					String name = firstString(entry, "id", "key", "enchant", "type", "name");
					int level = firstLevel(entry, "level", "lvl", "value");
					String enchant = name == null ? null : enchantKey(name);
					if (enchant != null && level > 0) {
						map.put(enchant, level);
					}
					continue;
				}
				addEnchantText(map, readTagString(list.get(i)));
			}
			return map;
		}
		String text = readTagString(raw);
		if (text != null && text.startsWith("{")) {
			try {
				return readEnchantCompound(TagParser.parseCompoundFully(text));
			} catch (Exception ignored) {
				Map<String, Integer> one = new LinkedHashMap<>();
				addEnchantText(one, text);
				return one.isEmpty() ? null : one;
			}
		}
		if (text != null) {
			Map<String, Integer> one = new LinkedHashMap<>();
			addEnchantText(one, text);
			return one.isEmpty() ? null : one;
		}
		return null;
	}

	private static void addEnchantText(Map<String, Integer> map, String text) {
		if (text == null || text.isBlank()) {
			return;
		}
		String raw = text.trim();
		BookCombineRules.EnchantLevel parsed = BookCombineRules.fromSkyblockId(raw);
		if (parsed == null && raw.indexOf(':') > 0 && raw.indexOf(';') < 0) {
			parsed = BookCombineRules.fromSkyblockId(raw.replace(':', ';'));
		}
		if (parsed != null) {
			map.put(parsed.key(), parsed.level());
		}
	}

	private static Map<String, Integer> readEnchantCompound(CompoundTag compound) {
		Map<String, Integer> map = new LinkedHashMap<>();
		for (String key : compound.keySet()) {
			String enchant = enchantKey(key);
			int level = readLevel(compound.get(key));
			if (enchant != null && level > 0) {
				map.put(enchant, level);
			}
		}
		return map;
	}

	private static String enchantKey(String raw) {
		String stripped = stripEnchantKey(raw);
		String normalized = BookCombineRules.normalize(stripped);
		if (BookCombineRules.known(normalized)) {
			return normalized;
		}
		String display = BookCombineRules.fromDisplay(stripped);
		return display.isEmpty() ? null : display;
	}

	/** Vanilla stored/applied enchantments, used when SkyBlock data has no enchant map. */
	private static Map<String, Integer> storedEnchantments(ItemStack stack) {
		Map<String, Integer> stored = enchantmentComponent(stack.get(DataComponents.STORED_ENCHANTMENTS));
		if (stored != null && !stored.isEmpty()) {
			return stored;
		}
		return enchantmentComponent(stack.get(DataComponents.ENCHANTMENTS));
	}

	private static Map<String, Integer> enchantmentComponent(ItemEnchantments enchants) {
		if (enchants == null || enchants.isEmpty()) {
			return null;
		}
		Map<String, Integer> map = new LinkedHashMap<>();
		for (Holder<Enchantment> holder : enchants.keySet()) {
			Identifier id = holder.unwrapKey().map(key -> key.identifier()).orElse(null);
			if (id == null) {
				continue;
			}
			String enchant = enchantKey(id.getPath());
			int level = enchants.getLevel(holder);
			if (enchant != null && level > 0) {
				map.put(enchant, level);
			}
		}
		return map;
	}

	private static String stripEnchantKey(String key) {
		int colon = key.lastIndexOf(':');
		return colon >= 0 ? key.substring(colon + 1) : key;
	}

	private static String firstString(CompoundTag tag, String... keys) {
		for (String key : keys) {
			String value = readTagString(tag.get(key));
			if (value != null && !value.isBlank()) {
				return value.trim();
			}
		}
		return null;
	}

	private static int firstLevel(CompoundTag tag, String... keys) {
		for (String key : keys) {
			if (tag.contains(key)) {
				int level = readLevel(tag.get(key));
				if (level > 0) {
					return level;
				}
			}
		}
		return -1;
	}

	private static int readLevel(Tag value) {
		if (value == null) {
			return -1;
		}
		if (value.asNumber().isPresent()) {
			int level = value.asNumber().get().intValue();
			return level > 0 ? level : -1;
		}
		String text = readTagString(value);
		if (text != null) {
			String raw = text.trim();
			if (raw.startsWith("{")) {
				try {
					return firstLevel(TagParser.parseCompoundFully(raw), "level", "lvl", "value");
				} catch (Exception ignored) {
					return BookCombineRules.levelNumber(raw);
				}
			}
			int level = BookCombineRules.levelNumber(raw);
			if (level > 0) {
				return level;
			}
			try {
				level = Integer.parseInt(raw);
				return level > 0 ? level : -1;
			} catch (NumberFormatException ignored) {
				return -1;
			}
		}
		if (value.asCompound().isPresent()) {
			return firstLevel(value.asCompound().get(), "level", "lvl", "value");
		}
		return -1;
	}

	private static String readTagString(Tag value) {
		if (value == null) {
			return null;
		}
		return value.asString().orElse(null);
	}

	private static List<CompoundTag> attributeNodes(CompoundTag root) {
		List<CompoundTag> nodes = new ArrayList<>();
		if (root != null && !root.isEmpty()) {
			nodes.add(root);
			addCompound(nodes, root, "ExtraAttributes");
			addCompound(nodes, root, "PublicBukkitValues");
			CompoundTag tag = root.getCompoundOrEmpty("tag");
			if (!tag.isEmpty()) {
				nodes.add(tag);
				addCompound(nodes, tag, "ExtraAttributes");
			}
		}
		return nodes;
	}

	private static void addCompound(List<CompoundTag> nodes, CompoundTag parent, String key) {
		CompoundTag child = parent.getCompoundOrEmpty(key);
		if (!child.isEmpty()) {
			nodes.add(child);
		}
	}

	private static final class NbtRead {
		private final Book book;
		private final boolean refused;

		private NbtRead(Book book, boolean refused) {
			this.book = book;
			this.refused = refused;
		}

		private static NbtRead absent() {
			return new NbtRead(null, false);
		}

		private static NbtRead refuse() {
			return new NbtRead(null, true);
		}

		private static NbtRead ok(Book book) {
			return new NbtRead(book, false);
		}
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
