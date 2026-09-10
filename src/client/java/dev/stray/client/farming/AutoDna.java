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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Auto-clicks the next SkyHanni DNA analyzer swap in Greenhouse menus whose
 * title ends with {@code DNA}.
 */
public final class AutoDna {
	private static final int HIGHLIGHT = 0x8000C853;
	private static final int CLOSE_SLOT = 49;
	private static final int FIRST_SLOT = 9;
	private static final int LAST_SLOT = 44;

	private static boolean inInventory;
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
		String title = screen.getTitle().getString();
		inInventory = title.endsWith(" DNA");
		fakeInventory = false;
		errorCount = 0;
		board = DnaAnalyzerSolver.Solution.none();
		clearPending();
		if (inInventory) {
			Minecraft.getInstance().execute(AutoDna::readBoard);
		}
	}

	public static void onPacket(Packet<?> packet) {
		if (!inInventory) {
			return;
		}
		if (packet instanceof ClientboundContainerSetSlotPacket
			|| packet instanceof ClientboundContainerSetContentPacket) {
			Minecraft.getInstance().execute(AutoDna::readBoard);
		}
	}

	public static boolean shouldBlock(int slotId) {
		return enabled() && StrayConfig.get().autoDnaBlockClose && slotId == CLOSE_SLOT;
	}

	public static void tick(Minecraft client) {
		if (!enabled()) {
			return;
		}
		if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
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
		if (!inInventory || fakeInventory) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
			return;
		}
		if (!screen.getTitle().getString().endsWith(" DNA")) {
			reset();
			return;
		}
		List<Slot> slots = screen.getMenu().slots;
		if (slots.size() <= LAST_SLOT) {
			return;
		}
		if (!screen.getMenu().getCarried().isEmpty()) {
			return;
		}

		DnaAnalyzerSolver.Color[][] columns = new DnaAnalyzerSolver.Color[DnaAnalyzerSolver.COLUMNS][DnaAnalyzerSolver.ROWS];
		for (int slotId = FIRST_SLOT; slotId <= LAST_SLOT; slotId++) {
			int row = (slotId / 9) - 1;
			int column = slotId % 9;
			DnaAnalyzerSolver.Color color = colorOf(slots.get(slotId).getItem());
			if (color == null) {
				fakeInventory = true;
				board = DnaAnalyzerSolver.Solution.none();
				clearPending();
				return;
			}
			columns[column][row] = color;
		}

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

	static DnaAnalyzerSolver.Color colorOf(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		boolean prior = ItemAppearance.suppress();
		try {
			Component hover = stack.getHoverName();
			String legacy = stripLeadingWhite(toLegacy(hover));
			if (legacy.startsWith("§cDNA")) {
				return DnaAnalyzerSolver.Color.RED;
			}
			if (legacy.startsWith("§eDNA")) {
				return DnaAnalyzerSolver.Color.YELLOW;
			}
			if (legacy.startsWith("§9DNA")) {
				return DnaAnalyzerSolver.Color.BLUE;
			}
			if (legacy.startsWith("§aDNA")) {
				return DnaAnalyzerSolver.Color.GREEN;
			}
			String plain = ChatFormatting.stripFormatting(hover.getString()).trim();
			if (plain.equals("DNA") || plain.endsWith(" DNA")) {
				DnaAnalyzerSolver.Color styled = firstColor(hover);
				if (styled != null) {
					return styled;
				}
			}
			return fromItem(stack, plain);
		} finally {
			ItemAppearance.resume(prior);
		}
	}

	private static DnaAnalyzerSolver.Color fromItem(ItemStack stack, String plain) {
		if (!plain.contains("DNA")) {
			return null;
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		String path = id.getPath();
		if (path.contains("red")) {
			return DnaAnalyzerSolver.Color.RED;
		}
		if (path.contains("yellow")) {
			return DnaAnalyzerSolver.Color.YELLOW;
		}
		if (path.contains("blue")) {
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
		return out.toString();
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

	private static ChatFormatting formattingOf(Style style) {
		TextColor color = style.getColor();
		if (color == null) {
			return null;
		}
		for (ChatFormatting formatting : ChatFormatting.values()) {
			Integer rgb = formatting.getColor();
			if (rgb != null && color.getValue() == rgb) {
				return formatting;
			}
		}
		return null;
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
