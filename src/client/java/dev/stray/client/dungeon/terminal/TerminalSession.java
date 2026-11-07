package dev.stray.client.dungeon.terminal;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.dungeon.DevMode;
import dev.stray.client.location.SkyblockLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The open terminal: which one it is, what the server has shown so far, and the clicks
 * we sent that the server has not answered yet. Same flow as NoammAddons'
 * TerminalListener/TerminalSolver: solve on open and on every slot change, take our own
 * clicks off the solution right away, wait the first-click delay after opening, and
 * re-read the whole window if the server goes quiet after a click.
 *
 * <p>Clicks are made with the game's own container click (the same call as clicking a
 * slot by hand): middle click for normal slots, right click for Rubix steps back. Only a
 * slot that is part of the solution can be clicked, so a miss-click does nothing.
 */
public final class TerminalSession {
	private static final long BREAK_MS = 500L;
	private static final int MAX_CPS = 15;

	private static Term term;
	private static String title = "";
	private static AbstractContainerMenu menu;
	private static int windowId = -1;
	private static boolean loaded;
	private static long openMs;
	private static long lastClickMs;
	private static long lastDropMs;
	private static final Map<Integer, ItemStack> ITEMS = new HashMap<>();
	private static final ItemStack[] SEEN = new ItemStack[54];
	private static final List<Term.Click> CLICKED = new ArrayList<>();
	/** True while our own click is going out, so {@link #onClickSent} skips it. */
	private static boolean sending;

	// Simulator (offline) state.
	private static TerminalSim sim;
	private static boolean simRandom;
	private static final List<Object[]> PENDING = new ArrayList<>();
	private static long simStartMs;
	private static long solvedAtMs;
	private static String doneText;

	private TerminalSession() {
	}

	public static boolean enabled() {
		StrayConfig config = StrayConfig.get();
		return config.terminalGuiEnabled
			&& (DevMode.on() || SkyblockLocation.inDungeon && SkyblockLocation.inBoss && SkyblockLocation.dungeonFloor == 7);
	}

	/** Whether this container screen is a terminal we draw ourselves (starts the session on first sight). */
	public static boolean handles(Screen screen) {
		if (sim != null || !(screen instanceof AbstractContainerScreen<?> container)
			|| screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen
			|| screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen) {
			return false;
		}
		if (!enabled()) {
			if (term != null) {
				reset();
			}
			return false;
		}
		Term match = Term.fromTitle(screen.getTitle().getString());
		if (match == null) {
			if (term != null) {
				reset();
			}
			return false;
		}
		AbstractContainerMenu current = container.getMenu();
		if (term != match || menu != current || windowId != current.containerId) {
			start(match, screen.getTitle().getString(), current);
		}
		return true;
	}

	private static void start(Term match, String newTitle, AbstractContainerMenu current) {
		reset();
		term = match;
		title = newTitle;
		menu = current;
		windowId = current == null ? -1 : current.containerId;
		openMs = System.currentTimeMillis();
	}

	public static void reset() {
		for (Term t : Term.ALL) {
			t.reset();
		}
		term = null;
		title = "";
		menu = null;
		windowId = -1;
		loaded = false;
		lastClickMs = 0L;
		ITEMS.clear();
		java.util.Arrays.fill(SEEN, null);
		CLICKED.clear();
		PENDING.clear();
		doneText = null;
		solvedAtMs = 0L;
		TerminalGui.reset();
	}

	// ------------------------------------------------------------------ reading the window

	private static ItemStack current(int slot) {
		if (sim != null) {
			ItemStack stack = sim.items.get(slot);
			return stack == null ? ItemStack.EMPTY : stack;
		}
		if (menu == null || slot >= menu.slots.size() - 36 || slot < 0) {
			return ItemStack.EMPTY;
		}
		return menu.slots.get(slot).getItem();
	}

	private static int slots() {
		if (sim != null) {
			return term.slotCount;
		}
		return menu == null ? 0 : Math.min(term.slotCount, Math.max(0, menu.slots.size() - 36));
	}

	private static boolean black(ItemStack stack) {
		return Term.pane(stack, DyeColor.BLACK);
	}

	/** Reads what changed since last time and solves again; first call once items have arrived. */
	private static void poll() {
		if (term == null) {
			return;
		}
		int count = slots();
		if (!loaded) {
			boolean any = false;
			for (int i = 0; i < count; i++) {
				any |= !current(i).isEmpty();
			}
			if (!any) {
				return;
			}
			loaded = true;
			openMs = System.currentTimeMillis();
			for (int i = 0; i < count; i++) {
				ItemStack stack = current(i).copy();
				ITEMS.put(i, stack);
				SEEN[i] = stack.copy();
			}
			if (term == Term.MELODY) {
				for (int i = 0; i < count; i++) {
					term.solve(ITEMS, title, i, ITEMS.get(i));
				}
			} else {
				term.solve(ITEMS, title, 0, ItemStack.EMPTY);
			}
			return;
		}
		for (int i = 0; i < count; i++) {
			ItemStack stack = current(i);
			if (SEEN[i] != null && ItemStack.matches(SEEN[i], stack)) {
				continue;
			}
			SEEN[i] = stack.copy();
			// Always keep our copy current (a pane turning black matters too: Melody's
			// marked column moves by turning the old one black).
			ITEMS.put(i, stack.copy());
			boolean untouched = CLICKED.isEmpty() && lastClickMs == 0L;
			if (term == Term.RUBIX && !untouched) {
				// Rubix follows our own clicks after the first one (like Noamm) and re-reads on a stall.
				continue;
			}
			if (black(stack) && term != Term.MELODY && !untouched) {
				continue;
			}
			// Nothing clicked yet: the window may still be filling in, so solve it whole.
			term.solve(ITEMS, title, untouched && term != Term.MELODY ? 0 : i, untouched && term != Term.MELODY ? ItemStack.EMPTY : stack);
			if (term != Term.MELODY) {
				for (Term.Click click : CLICKED) {
					term.predict(click);
				}
			}
		}
	}

	/** Server went quiet after a click: read the whole window again (Noamm's "terminal broke"). */
	private static void resync() {
		int count = slots();
		for (int i = 0; i < count; i++) {
			ITEMS.put(i, current(i).copy());
		}
		term.solve(ITEMS, title, 0, ItemStack.EMPTY);
		CLICKED.clear();
	}

	// ------------------------------------------------------------------ drawing and input

	public static void render(GuiGraphicsExtractor graphics, int width, int height, double mouseX, double mouseY) {
		if (term == null) {
			return;
		}
		processPending();
		poll();
		checkSimSolved();
		TerminalGui.render(graphics, width, height, term, loaded, firstClickBlocked(), doneText, mouseX, mouseY);
	}

	private static boolean firstClickBlocked() {
		int delay = Math.max(0, StrayConfig.get().terminalFirstClickDelay);
		if (term == Term.MELODY && delay != 0) {
			delay += 3;
		}
		return System.currentTimeMillis() - openMs < delay * 50L;
	}

	public static boolean mouseClicked(double mouseX, double mouseY) {
		if (term == null) {
			return false;
		}
		click(TerminalGui.slotAt(mouseX, mouseY));
		return true;
	}

	/** Returns true when the key is used up: everything but closing keys; drop clicks the hovered slot. */
	public static boolean keyPressed(KeyEvent event) {
		Minecraft client = Minecraft.getInstance();
		if (term == null) {
			return false;
		}
		if (event.isEscape() || client.options.keyInventory.matches(event)) {
			return false;
		}
		if (client.options.keyDrop.matches(event)) {
			long now = System.currentTimeMillis();
			if (now - lastDropMs >= 1000L / MAX_CPS) {
				lastDropMs = now;
				Minecraft mc = Minecraft.getInstance();
				double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / Math.max(1, mc.getWindow().getScreenWidth());
				double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / Math.max(1, mc.getWindow().getScreenHeight());
				click(TerminalGui.slotAt(mx, my));
			}
		}
		return true;
	}

	private static void click(int at) {
		if (!loaded || doneText != null || firstClickBlocked()) {
			return;
		}
		Term.Click click = term.clickFor(at);
		if (click == null) {
			return;
		}
		term.predict(click);
		Minecraft client = Minecraft.getInstance();
		if (sim != null) {
			PENDING.add(new Object[] {System.currentTimeMillis() + 50L, click});
		} else {
			if (client.gameMode == null || client.player == null || menu == null) {
				return;
			}
			// The game's own slot click: middle click (2, CLONE) or right click (1, PICKUP).
			int button = click.btn() == 0 ? 2 : click.btn();
			ContainerInput input = click.btn() == 0 ? ContainerInput.CLONE : ContainerInput.PICKUP;
			// A right click (Rubix step back) would also pick the pane up onto our own cursor
			// in the client's copy of the window; the server answers for the real one. Put
			// our copy back as it was, so a second click or a resync never sees that cursor pane.
			ItemStack before = click.btn() != 0 && click.slot() < menu.slots.size() ? menu.slots.get(click.slot()).getItem().copy() : null;
			sending = true;
			try {
				client.gameMode.handleContainerInput(windowId, click.slot(), button, input, client.player);
			} finally {
				sending = false;
			}
			if (before != null) {
				menu.slots.get(click.slot()).set(before);
				menu.setCarried(ItemStack.EMPTY);
			}
			if (term != Term.MELODY) {
				lastClickMs = System.currentTimeMillis();
			}
		}
		CLICKED.add(click);
		term.onSlotClick(click.slot());
		TerminalGui.pulse(click.slot());
		if (StrayConfig.get().terminalClickSound) {
			client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6f, 0.35f));
		}
	}

	static void simTick() {
		if (sim != null) {
			sim.tick();
		}
	}

	/**
	 * A container click leaving the client that we did not make, e.g. from NoammAddons'
	 * auto terminals: take it off our solution the same way as our own clicks, so the
	 * window follows along (Rubix especially, which does not resolve from slot updates).
	 * Network thread: hand it to the main thread.
	 */
	public static void onClickSent(int containerId, int slot, int button, ContainerInput input) {
		if (sending) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> foreignClick(containerId, slot, button, input));
	}

	private static void foreignClick(int containerId, int slot, int button, ContainerInput input) {
		if (term == null || sim != null || !loaded || containerId != windowId || slot < 0) {
			return;
		}
		// Same mapping as ours: middle click (CLONE) = 0, right click = 1.
		Term.Click click = new Term.Click(slot, input == ContainerInput.PICKUP && button == 1 ? 1 : 0);
		term.predict(click);
		CLICKED.add(click);
		term.onSlotClick(slot);
		TerminalGui.pulse(slot);
		if (term != Term.MELODY) {
			lastClickMs = System.currentTimeMillis();
		}
	}

	public static void tick(Minecraft client) {
		if (sim != null || term == null) {
			return;
		}
		if (term == null) {
			return;
		}
		if (!(client.gui.screen() instanceof AbstractContainerScreen<?>)) {
			reset();
			return;
		}
		long now = System.currentTimeMillis();
		if (lastClickMs != 0L && now - lastClickMs >= BREAK_MS) {
			resync();
			lastClickMs = now;
		}
	}

	// ------------------------------------------------------------------ simulator

	/** Opens the offline terminal: {@code name} is a terminal name or "random". */
	public static boolean openSim(String name) {
		TerminalSim next = TerminalSim.of(name);
		if (next == null) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		simRandom = "random".equalsIgnoreCase(name);
		client.execute(() -> client.gui.setScreen(new TerminalSimScreen(next)));
		return true;
	}

	public static String[] simNames() {
		return TerminalSim.NAMES;
	}

	static void startSim(TerminalSim next) {
		reset();
		sim = next;
		sim.generate();
		term = sim.term;
		title = sim.title;
		openMs = System.currentTimeMillis();
		simStartMs = openMs;
	}

	static void stopSim() {
		sim = null;
		reset();
	}

	private static void processPending() {
		if (sim == null) {
			return;
		}
		long now = System.currentTimeMillis();
		for (Iterator<Object[]> it = PENDING.iterator(); it.hasNext(); ) {
			Object[] entry = it.next();
			if ((Long) entry[0] <= now) {
				sim.click((Term.Click) entry[1]);
				it.remove();
			}
		}
	}

	private static void checkSimSolved() {
		if (sim == null || !loaded) {
			return;
		}
		long now = System.currentTimeMillis();
		if (doneText == null) {
			boolean solved = sim.solved() || term != Term.MELODY && term.solution.isEmpty();
			if (solved) {
				solvedAtMs = now;
				doneText = String.format(Locale.ROOT, "Solved in %.2fs", (now - simStartMs) / 1000.0);
				Minecraft client = Minecraft.getInstance();
				if (client.gui != null) {
					client.gui.hud.getChat().addClientSystemMessage(simLine("§f" + term.name + " §7solved in §f" + doneText.substring(10)));
				}
			}
			return;
		}
		if (now - solvedAtMs > 1100L) {
			TerminalSim next = simRandom ? TerminalSim.random() : TerminalSim.of(nameOf(sim));
			startSim(next == null ? sim : next);
		}
	}

	private static String nameOf(TerminalSim current) {
		return current.term.name.replace(" ", "");
	}

	/** Chat line for the debug command. */
	static Component simLine(String text) {
		return Component.literal("§bStray §7" + text);
	}
}
