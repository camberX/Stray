package dev.stray.client.farming;

import dev.stray.client.combat.OdinClicks;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.item.ItemAppearance;
import dev.stray.client.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Auto-clicks the next SkyHanni DNA analyzer swap in Crop Analyzer menus
 * whose window title is the mutation name plus {@code DNA}, e.g.
 * {@code Ashwreath DNA} or {@code All-in Aloe DNA}.
 */
public final class AutoDna {
	private static final int HIGHLIGHT = 0x8000C853;
	private static final int CLOSE_SLOT = 49;
	private static final int FIRST_SLOT = 9;
	private static final int LAST_SLOT = 44;
	private static final int INFO_SLOT = 4;
	private static final Pattern MUTATION_DNA = Pattern.compile(
		".+\\s+DNA\\s*$",
		Pattern.CASE_INSENSITIVE
	);

	private static boolean inInventory;
	private static String packetTitle = "";
	private static boolean fakeInventory;
	private static int errorCount;
	private static DnaAnalyzerSolver.Solution board = DnaAnalyzerSolver.Solution.none();
	private static int pendingA = -1;
	private static int pendingB = -1;
	private static boolean clickedFirst;
	private static boolean waitingForUpdate;
	private static long lastClick;

	private AutoDna() {
	}

	public static void init() {
		ScreenEvents.BEFORE_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof AbstractContainerScreen<?>)) {
				return;
			}
			ScreenEvents.afterExtract(screen).register((opened, graphics, mouseX, mouseY, tickDelta) ->
				afterRender(opened, graphics)
			);
		});
	}

	public static void reset() {
		inInventory = false;
		packetTitle = "";
		fakeInventory = false;
		errorCount = 0;
		board = DnaAnalyzerSolver.Solution.none();
		clearPending();
	}

	public static void onOpen(Screen screen) {
		if (screen == null) {
			reset();
			return;
		}
		inInventory = isDnaInventory(screen);
		fakeInventory = false;
		errorCount = 0;
		board = DnaAnalyzerSolver.Solution.none();
		clearPending();
		if (inInventory) {
			Minecraft.getInstance().execute(AutoDna::readBoard);
		}
	}

	public static void onPacket(Packet<?> packet) {
		if (packet instanceof ClientboundOpenScreenPacket open) {
			packetTitle = plain(open.getTitle());
			inInventory = isDnaTitle(packetTitle);
			fakeInventory = false;
			errorCount = 0;
			board = DnaAnalyzerSolver.Solution.none();
			clearPending();
			Minecraft.getInstance().execute(AutoDna::readBoard);
			return;
		}
		if (!(packet instanceof ClientboundContainerSetSlotPacket
			|| packet instanceof ClientboundContainerSetContentPacket)) {
			return;
		}
		Minecraft.getInstance().execute(AutoDna::readBoard);
	}

	public static boolean shouldBlock(int slotId) {
		return enabled() && StrayConfig.get().autoDnaBlockClose && slotId == CLOSE_SLOT;
	}

	public static void tick(Minecraft client) {
		if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
			return;
		}
		if (!inInventory || pendingA < 0 || waitingForUpdate) {
			readBoard();
		}
		if (!enabled()) {
			return;
		}
		if (!screen.getMenu().getCarried().isEmpty() && clickedFirst && pendingB >= 0) {
			clickIfReady(screen, pendingB, true);
			return;
		}
		if (waitingForUpdate || pendingA < 0 || pendingB < 0) {
			return;
		}
		if (!clickedFirst) {
			clickIfReady(screen, pendingA, false);
			return;
		}
		clickIfReady(screen, pendingB, true);
	}

	private static void clickIfReady(AbstractContainerScreen<?> screen, int slot, boolean lastOfPair) {
		long now = System.currentTimeMillis();
		if (now - lastClick < delay()) {
			return;
		}
		ContainerInput type = StrayConfig.get().autoDnaMiddleClick
			? ContainerInput.CLONE
			: ContainerInput.PICKUP;
		OdinClicks.guiClick(screen.getMenu().containerId, slot, 0, type);
		lastClick = now;
		if (lastOfPair) {
			clickedFirst = false;
			pendingA = -1;
			pendingB = -1;
			waitingForUpdate = true;
		} else {
			clickedFirst = true;
		}
	}

	private static void readBoard() {
		Minecraft client = Minecraft.getInstance();
		if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
			return;
		}
		if (!isDnaInventory(screen)) {
			if (inInventory) {
				reset();
			}
			return;
		}
		inInventory = true;
		List<Slot> slots = screen.getMenu().slots;
		if (slots.size() <= LAST_SLOT) {
			return;
		}
		if (!screen.getMenu().getCarried().isEmpty()) {
			return;
		}

		DnaAnalyzerSolver.Color[][] columns = new DnaAnalyzerSolver.Color[DnaAnalyzerSolver.COLUMNS][DnaAnalyzerSolver.ROWS];
		boolean empty = false;
		boolean unknown = false;
		for (int slotId = FIRST_SLOT; slotId <= LAST_SLOT; slotId++) {
			int row = (slotId / 9) - 1;
			int column = slotId % 9;
			ItemStack stack = slots.get(slotId).getItem();
			if (stack == null || stack.isEmpty()) {
				empty = true;
				continue;
			}
			DnaAnalyzerSolver.Color color = colorOf(stack);
			if (color == null) {
				unknown = true;
				continue;
			}
			columns[column][row] = color;
		}
		if (empty) {
			return;
		}
		if (unknown) {
			fakeInventory = true;
			board = DnaAnalyzerSolver.Solution.none();
			clearPending();
			return;
		}
		fakeInventory = false;

		for (DnaAnalyzerSolver.Color[] column : columns) {
			Set<DnaAnalyzerSolver.Color> unique = new HashSet<>(List.of(column));
			if (unique.size() != column.length) {
				errorCount++;
				return;
			}
		}
		errorCount = 0;
		waitingForUpdate = false;
		board = DnaAnalyzerSolver.solve(columns, StrayConfig.get().autoDnaAllowEnds);
		armNextSwap();
	}

	private static void armNextSwap() {
		pendingA = -1;
		pendingB = -1;
		clickedFirst = false;
		if (!board.reachable() || board.swaps().isEmpty()) {
			return;
		}
		List<DnaAnalyzerSolver.Swap> swaps = board.swaps();
		DnaAnalyzerSolver.Swap next = swaps.get(swaps.size() - 1);
		pendingA = next.a().slot();
		pendingB = next.b().slot();
	}

	private static void afterRender(Screen screen, GuiGraphicsExtractor graphics) {
		if (!enabled()) {
			return;
		}
		if (!(screen instanceof AbstractContainerScreen<?> container)) {
			return;
		}
		if (pendingA < 0 || pendingB < 0) {
			return;
		}
		int left = ((AbstractContainerScreenAccessor) container).stray$leftPos();
		int top = ((AbstractContainerScreenAccessor) container).stray$topPos();
		highlight(graphics, container, left, top, pendingA);
		highlight(graphics, container, left, top, pendingB);
	}

	private static void highlight(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen, int left, int top, int slotId) {
		if (slotId < 0 || slotId >= screen.getMenu().slots.size()) {
			return;
		}
		Slot slot = screen.getMenu().getSlot(slotId);
		int x = left + slot.x;
		int y = top + slot.y;
		graphics.fill(x, y, x + 16, y + 16, HIGHLIGHT);
	}

	private static void clearPending() {
		pendingA = -1;
		pendingB = -1;
		clickedFirst = false;
		waitingForUpdate = false;
		lastClick = 0;
	}

	private static boolean enabled() {
		return StrayConfig.get().autoDnaEnabled && inInventory && !fakeInventory;
	}

	private static long delay() {
		StrayConfig config = StrayConfig.get();
		int extra = config.autoDnaDelayVariety <= 0
			? 0
			: (int) (Math.random() * (config.autoDnaDelayVariety + 1));
		return (long) config.autoDnaClickDelay + extra;
	}

	static boolean isDnaInventory(Screen screen) {
		if (screen == null) {
			return false;
		}
		if (isDnaTitle(screen.getTitle()) || isDnaTitle(packetTitle)) {
			return true;
		}
		if (!(screen instanceof AbstractContainerScreen<?> container)) {
			return false;
		}
		List<Slot> slots = container.getMenu().slots;
		if (slots.size() <= INFO_SLOT) {
			return false;
		}
		ItemStack info = slots.get(INFO_SLOT).getItem();
		if (info == null || info.isEmpty()) {
			return false;
		}
		boolean prior = ItemAppearance.suppress();
		try {
			return isDnaTitle(info.getHoverName());
		} finally {
			ItemAppearance.resume(prior);
		}
	}

	static boolean isDnaTitle(Component title) {
		return isDnaTitle(plain(title));
	}

	static boolean isDnaTitle(String plain) {
		if (plain == null || plain.isEmpty()) {
			return false;
		}
		String folded = plain.toLowerCase(Locale.ROOT);
		if (folded.contains("shard") || folded.contains("ultimate")) {
			return false;
		}
		if (MUTATION_DNA.matcher(plain).find()) {
			return true;
		}
		return folded.endsWith(" dna") && folded.length() > 4;
	}

	static DnaAnalyzerSolver.Color colorOf(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		boolean prior = ItemAppearance.suppress();
		try {
			Component hover = stack.getHoverName();
			Component custom = stack.get(DataComponents.CUSTOM_NAME);
			DnaAnalyzerSolver.Color fromHover = colorOfName(hover, stack);
			if (fromHover != null) {
				return fromHover;
			}
			if (custom != null && custom != hover) {
				return colorOfName(custom, stack);
			}
			return null;
		} finally {
			ItemAppearance.resume(prior);
		}
	}

	private static DnaAnalyzerSolver.Color colorOfName(Component hover, ItemStack stack) {
		if (hover == null) {
			return null;
		}
		String legacy = stripLeadingWhite(toLegacy(hover));
		if (legacy.startsWith("§cDNA") || legacy.startsWith("§4DNA")) {
			return DnaAnalyzerSolver.Color.RED;
		}
		if (legacy.startsWith("§eDNA") || legacy.startsWith("§6DNA")) {
			return DnaAnalyzerSolver.Color.YELLOW;
		}
		if (legacy.startsWith("§9DNA") || legacy.startsWith("§1DNA")
			|| legacy.startsWith("§bDNA") || legacy.startsWith("§3DNA")) {
			return DnaAnalyzerSolver.Color.BLUE;
		}
		if (legacy.startsWith("§aDNA") || legacy.startsWith("§2DNA")) {
			return DnaAnalyzerSolver.Color.GREEN;
		}
		String name = plain(hover);
		DnaAnalyzerSolver.Color fromCode = firstLegacyColor(legacy);
		if (fromCode != null && (isDnaItemName(name) || inInventory)) {
			return fromCode;
		}
		DnaAnalyzerSolver.Color styled = firstColor(hover);
		if (styled != null && (isDnaItemName(name) || inInventory)) {
			return styled;
		}
		DnaAnalyzerSolver.Color named = colorWord(name);
		if (named != null) {
			return named;
		}
		return inInventory || isDnaItemName(name) ? fromItem(stack) : null;
	}

	private static DnaAnalyzerSolver.Color colorWord(String plain) {
		String folded = plain == null ? "" : plain.toLowerCase(Locale.ROOT);
		if (folded.contains("red")) {
			return DnaAnalyzerSolver.Color.RED;
		}
		if (folded.contains("yellow") || folded.contains("gold") || folded.contains("orange")) {
			return DnaAnalyzerSolver.Color.YELLOW;
		}
		if (folded.contains("blue") || folded.contains("cyan") || folded.contains("aqua")) {
			return DnaAnalyzerSolver.Color.BLUE;
		}
		if (folded.contains("green") || folded.contains("lime")) {
			return DnaAnalyzerSolver.Color.GREEN;
		}
		return null;
	}

	private static boolean isDnaItemName(String plain) {
		if (plain == null || plain.isEmpty()) {
			return false;
		}
		String folded = plain.toUpperCase(Locale.ROOT);
		return folded.equals("DNA") || folded.startsWith("DNA") || folded.endsWith(" DNA") || folded.contains("DNA");
	}

	private static DnaAnalyzerSolver.Color fromItem(ItemStack stack) {
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		String path = id.getPath();
		if (path.contains("red")) {
			return DnaAnalyzerSolver.Color.RED;
		}
		if (path.contains("yellow") || path.contains("gold") || path.contains("orange")) {
			return DnaAnalyzerSolver.Color.YELLOW;
		}
		if (path.contains("blue") || path.contains("cyan") || path.contains("light_blue")) {
			return DnaAnalyzerSolver.Color.BLUE;
		}
		if (path.contains("green") || path.contains("lime")) {
			return DnaAnalyzerSolver.Color.GREEN;
		}
		return null;
	}

	private static String toLegacy(Component component) {
		StringBuilder out = new StringBuilder();
		component.visit((style, text) -> {
			if (text.isEmpty()) {
				return Optional.empty();
			}
			ChatFormatting formatting = formattingOf(style);
			if (formatting != null) {
				out.append('\u00A7').append(formatting.getChar());
			}
			out.append(text);
			return Optional.empty();
		}, Style.EMPTY);
		return out.toString().replaceAll("§r(§[0-9a-f])", "$1");
	}

	private static DnaAnalyzerSolver.Color firstColor(Component component) {
		DnaAnalyzerSolver.Color[] found = {null};
		component.visit((style, text) -> {
			if (text.isEmpty() || found[0] != null) {
				return Optional.empty();
			}
			found[0] = colorOf(formattingOf(style));
			return Optional.empty();
		}, Style.EMPTY);
		return found[0];
	}

	private static DnaAnalyzerSolver.Color firstLegacyColor(String legacy) {
		String rest = stripLeadingWhite(legacy);
		while (rest.length() >= 2 && rest.charAt(0) == '\u00A7') {
			DnaAnalyzerSolver.Color color = colorOf(ChatFormatting.getByCode(Character.toLowerCase(rest.charAt(1))));
			if (color != null) {
				return color;
			}
			char code = Character.toLowerCase(rest.charAt(1));
			if ("klmnor".indexOf(code) < 0) {
				break;
			}
			rest = rest.substring(2);
		}
		return null;
	}

	private static ChatFormatting formattingOf(Style style) {
		TextColor color = style.getColor();
		if (color == null) {
			return null;
		}
		ChatFormatting named = ChatFormatting.getByName(color.serialize());
		if (named != null && named.isColor()) {
			return named;
		}
		int rgb = color.getValue();
		for (ChatFormatting formatting : ChatFormatting.values()) {
			Integer value = formatting.getColor();
			if (value != null && value == rgb) {
				return formatting;
			}
		}
		return nearestNamed(rgb);
	}

	private static ChatFormatting nearestNamed(int rgb) {
		int best = Integer.MAX_VALUE;
		ChatFormatting found = null;
		for (ChatFormatting formatting : ChatFormatting.values()) {
			Integer value = formatting.getColor();
			if (value == null) {
				continue;
			}
			int dr = ((rgb >> 16) & 0xFF) - ((value >> 16) & 0xFF);
			int dg = ((rgb >> 8) & 0xFF) - ((value >> 8) & 0xFF);
			int db = (rgb & 0xFF) - (value & 0xFF);
			int dist = dr * dr + dg * dg + db * db;
			if (dist < best) {
				best = dist;
				found = formatting;
			}
		}
		return best <= 48 * 48 * 3 ? found : null;
	}

	private static DnaAnalyzerSolver.Color colorOf(ChatFormatting formatting) {
		if (formatting == null) {
			return null;
		}
		return switch (formatting) {
			case RED, DARK_RED -> DnaAnalyzerSolver.Color.RED;
			case YELLOW, GOLD -> DnaAnalyzerSolver.Color.YELLOW;
			case BLUE, DARK_BLUE, AQUA, DARK_AQUA -> DnaAnalyzerSolver.Color.BLUE;
			case GREEN, DARK_GREEN -> DnaAnalyzerSolver.Color.GREEN;
			default -> null;
		};
	}

	private static String plain(Component component) {
		if (component == null) {
			return "";
		}
		StringBuilder visited = new StringBuilder();
		component.visit((style, text) -> {
			visited.append(text);
			return Optional.empty();
		}, Style.EMPTY);
		String fromVisit = plain(visited.toString());
		String fromGet = plain(component.getString());
		if (fromVisit.toLowerCase(Locale.ROOT).contains("dna")) {
			return fromVisit;
		}
		if (fromGet.toLowerCase(Locale.ROOT).contains("dna")) {
			return fromGet;
		}
		return fromVisit.isEmpty() ? fromGet : fromVisit;
	}

	private static String plain(String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		String text = ChatFormatting.stripFormatting(value);
		if (text == null) {
			text = value;
		}
		return text.replaceAll("§.", "")
			.replace('\u00A0', ' ')
			.replace('\u202F', ' ')
			.replaceAll("\\s+", " ")
			.trim();
	}

	private static String stripLeadingWhite(String value) {
		String out = value;
		while (out.length() >= 2 && out.charAt(0) == '\u00A7') {
			char code = Character.toLowerCase(out.charAt(1));
			if (code != 'f' && code != 'r' && code != '7') {
				break;
			}
			out = out.substring(2);
		}
		return out;
	}
}
