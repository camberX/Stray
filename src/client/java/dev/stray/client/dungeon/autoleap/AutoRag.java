package dev.stray.client.dungeon.autoleap;

import dev.stray.client.combat.OdinClicks;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.dungeon.DevMode;
import dev.stray.client.item.ItemAppearance;
import dev.stray.client.item.ItemIds;
import dev.stray.client.location.SkyblockLocation;
import dev.stray.client.net.SlotGuard;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auto Rag (M7 P5): once the relic you carried is placed, pick your Ragnarock in the hotbar
 * and right click it with the normal use key, as soon as your crosshair isn't on an entity or
 * a block you can use (else that would take the click). Until the action bar says CASTING (or
 * CANCELLED), your own hotbar keys and scrolling are ignored so you can't cancel the cast by
 * swapping. Mouse look is never touched.
 *
 * Pets: after the right click, Golden Dragon from /pets, then the wardrobe set; after
 * CASTING, Ender Dragon. Loadout mode: no wardrobe, and after CASTING the loadout instead of
 * Ender Dragon (it has its own pet).
 *
 * Placed is quoi's relic check the other way round: a relic was in the last hotbar slot and
 * now isn't.
 */
public final class AutoRag {
	private static final Set<String> RELICS = Set.of("GREEN_KING_RELIC", "PURPLE_KING_RELIC", "BLUE_KING_RELIC", "ORANGE_KING_RELIC", "RED_KING_RELIC");
	private static final String RAGNAROCK = "RAGNAROCK_AXE";
	/** Menu job for wardrobe set N: "wardrobe:N". Anything else is a pet name. */
	private static final String WARDROBE = "wardrobe:";
	/** Menu job for loadout N: "loadout:N". */
	private static final String LOADOUT = "loadout:";
	/** Settings dropdowns. */
	public static final String[] MODES = {"Wardrobe", "Loadout"};
	public static final String[] WARDROBE_SETS = numbered("Set ", 18);
	public static final String[] LOADOUTS = numbered("Loadout ", 12);
	private static final int LOADOUTS_PER_PAGE = 12;

	private static String[] numbered(String prefix, int count) {
		String[] names = new String[count + 1];
		names[0] = "Off";
		for (int i = 1; i < names.length; i++) {
			names[i] = prefix + i;
		}
		return names;
	}

	private static boolean loadoutMode() {
		StrayConfig config = StrayConfig.get();
		return config.autoRagMode == 1 && config.autoRagLoadout > 0;
	}
	/** No "CASTING IN" after the click (cooldown, no mana, click didn't land): stop blocking. */
	private static final int START_TIMEOUT = 20;
	/** The same in client ticks, in case server ticks aren't counted (so it always ends). */
	private static final int START_CAP = 100;
	/** Never block longer than this after the click, whatever the action bar says. */
	private static final int MAX_BLOCK = 100;
	private static final int BLOCK_CAP = 200;
	/**
	 * After the block ends without CASTING or CANCELLED (lag): keep listening this long after
	 * the click, so a late countdown blocks again and a late CASTING still swaps back.
	 */
	private static final int LISTEN = 200;
	private static final int LISTEN_CAP = 400;

	private enum Step { IDLE, SWAP, CLICK, BLOCK }

	private static Step step = Step.IDLE;
	private static boolean hadRelic;
	private static boolean started;
	private static int ticks;
	/** Server tick count at the click. */
	private static long clickMark;
	private static boolean listening;
	private static ClientLevel lastLevel;

	private AutoRag() {
	}

	/** /stray debug dev rag: act as if you just placed your relic (anywhere, any floor). */
	public static void simulate() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}
		StrayConfig config = StrayConfig.get();
		if (!config.autoRag) {
			config.autoRag = true;
			config.save();
		}
		reset();
		// Commands run outside tick start: the swap and click wait for the next tick start.
		relicPlaced(false);
	}

	/** Hotbar keys and scrolling are ignored right now. */
	public static boolean blocking() {
		return step != Step.IDLE;
	}

	/** handleKeybinds HEAD (tick start, before vanilla reads the hotbar keys and use key). */
	public static void beforeKeybinds(Minecraft client) {
		LocalPlayer player = client.player;
		if (client.level != lastLevel) {
			lastLevel = client.level;
			reset();
		}
		if (player == null || !StrayConfig.get().autoRag) {
			reset();
			return;
		}
		if (listening && step != Step.BLOCK) {
			ticks++;
			if (dev.stray.client.net.ServerTicks.seen() - clickMark > LISTEN || ticks > LISTEN_CAP) {
				listening = false;
			}
		}
		switch (step) {
			case IDLE -> watchRelic(player);
			case SWAP -> swap(client, player);
			case CLICK -> click(client, player);
			case BLOCK -> {
				drainHotbarKeys(client);
				ticks++;
				long server = dev.stray.client.net.ServerTicks.seen() - clickMark;
				if (!started && (server > START_TIMEOUT || ticks > START_CAP) || server > MAX_BLOCK || ticks > BLOCK_CAP) {
					step = Step.IDLE;
					listening = true;
				}
			}
			default -> {
			}
		}
	}

	private static void watchRelic(LocalPlayer player) {
		if (!DevMode.on() && !(F7Phase.inF7Boss() && SkyblockLocation.dungeonMaster)) {
			hadRelic = false;
			return;
		}
		String id = ItemIds.skyblockId(player.getInventory().getItem(8));
		boolean relic = id != null && RELICS.contains(id);
		if (hadRelic && !relic && player.isAlive()) {
			relicPlaced(true);
		}
		hadRelic = relic;
	}

	/** Swap and click from the next step on (this tick when called from tick start). */
	private static void relicPlaced(boolean now) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || ragSlot(player) < 0) {
			message("§cAuto Rag: no Ragnarock in your hotbar.");
			return;
		}
		step = Step.SWAP;
		if (now) {
			swap(Minecraft.getInstance(), player);
		}
	}

	private static void swap(Minecraft client, LocalPlayer player) {
		int slot = ragSlot(player);
		if (slot < 0) {
			message("§cAuto Rag: no Ragnarock in your hotbar.");
			step = Step.IDLE;
			return;
		}
		// Your own number keys this tick would be a second slot change: dropped.
		drainHotbarKeys(client);
		if (player.getInventory().getSelectedSlot() != slot) {
			// A menu is open, or another feature (the leap swapping back) is mid swap: wait.
			if (client.gui.screen() != null || LeapRunner.busy() || !SlotGuard.canSwap()) {
				return;
			}
			player.getInventory().setSelectedSlot(slot);
			SlotGuard.markSwapped();
		}
		step = Step.CLICK;
		click(client, player);
	}

	private static void click(Minecraft client, LocalPlayer player) {
		drainHotbarKeys(client);
		int slot = ragSlot(player);
		if (slot < 0 || player.getInventory().getSelectedSlot() != slot) {
			step = Step.SWAP;
			return;
		}
		// Queued: wait until menus are closed and the crosshair is free.
		if (client.gui.screen() != null || LeapRunner.busy() || !crosshairFree(client)) {
			return;
		}
		// The normal use key, handled by vanilla right after this in the same tick, after the
		// new slot goes out: like pressing the number key and right clicking in one tick.
		OdinClicks.rightClick();
		step = Step.BLOCK;
		started = false;
		listening = false;
		ticks = 0;
		clickMark = dev.stray.client.net.ServerTicks.seen();
		Pets.queue("Golden Dragon");
		// The armor swap right after, still during the cast's countdown (wardrobe mode).
		int set = StrayConfig.get().autoRagWardrobe;
		if (StrayConfig.get().autoRagMode == 0 && set > 0) {
			Pets.queue(WARDROBE + set);
		}
	}

	/** Not on an entity, and not on a block that does something when used (it'd take the click). */
	private static boolean crosshairFree(Minecraft client) {
		HitResult hit = client.hitResult;
		if (hit == null || hit.getType() == HitResult.Type.MISS) {
			return true;
		}
		if (hit.getType() == HitResult.Type.ENTITY || client.level == null) {
			return false;
		}
		BlockState state = client.level.getBlockState(((BlockHitResult) hit).getBlockPos());
		return !usable(state.getBlock());
	}

	private static final Map<Class<?>, Boolean> USABLE = new ConcurrentHashMap<>();

	/** The block's class (or a parent below BlockBehaviour) has its own use code or a menu. */
	private static boolean usable(Block block) {
		return USABLE.computeIfAbsent(block.getClass(), type -> {
			for (Class<?> c = type; c != null && c != BlockBehaviour.class && c != Block.class; c = c.getSuperclass()) {
				for (java.lang.reflect.Method method : c.getDeclaredMethods()) {
					String name = method.getName();
					if (name.equals("useWithoutItem") || name.equals("useItemOn") || name.equals("getMenuProvider")) {
						return true;
					}
				}
			}
			return false;
		});
	}

	/** Action bar text (packet or overlay chat). */
	public static void onActionBar(Component message) {
		if (step != Step.BLOCK && !listening || message == null) {
			return;
		}
		String text = OdinClicks.noControlCodes(message.getString()).toUpperCase(Locale.ROOT);
		if (text.contains("CANCELLED")) {
			step = Step.IDLE;
			listening = false;
		} else if (text.contains("CASTING IN")) {
			if (step != Step.BLOCK) {
				// The countdown came late: block your hotbar again, timed from now.
				step = Step.BLOCK;
				listening = false;
				ticks = 0;
				clickMark = dev.stray.client.net.ServerTicks.seen();
			}
			started = true;
		} else if (text.contains("CASTING")) {
			step = Step.IDLE;
			listening = false;
			// Loadout mode: the loadout brings its own pet (and armor) back.
			if (loadoutMode()) {
				Pets.queue(LOADOUT + StrayConfig.get().autoRagLoadout);
			} else {
				Pets.queue("Ender Dragon");
			}
		}
	}

	/** Tick start, also with a screen open (after Gui.tick): the pet swaps. */
	public static void screenStep(Minecraft client) {
		if (client.player == null || !StrayConfig.get().autoRag) {
			Pets.clear();
			return;
		}
		Pets.tick(client, client.player);
	}

	private static int ragSlot(LocalPlayer player) {
		for (int i = 0; i < 9; i++) {
			if (RAGNAROCK.equals(ItemIds.skyblockId(player.getInventory().getItem(i)))) {
				return i;
			}
		}
		return -1;
	}

	private static void drainHotbarKeys(Minecraft client) {
		for (KeyMapping key : client.options.keyHotbarSlots) {
			while (key.consumeClick()) {
				// ignored while casting
			}
		}
	}

	private static void reset() {
		step = Step.IDLE;
		hadRelic = false;
		started = false;
		listening = false;
		ticks = 0;
		Pets.clear();
	}

	private static void message(String text) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			player.sendSystemMessage(Component.literal("§bStray §7» " + text));
		}
	}

	/**
	 * Pet swaps through /pets: open it, find the pet (next pages too), click it, close the menu
	 * if Hypixel didn't. Wardrobe sets the same way through /wardrobe. One at a time, in order;
	 * waits while you have another menu open.
	 */
	private static final class Pets {
		/**
		 * Waits count server ticks (they stretch with lag and stop while the server freezes),
		 * each with a cap in client ticks so it always ends.
		 */
		private static final int OPEN_TIMEOUT = 60;
		private static final int OPEN_CAP = 400;
		/** After a page turn: wait for the new page; past this, look anyway. */
		private static final int PAGE_WAIT = 40;
		private static final int PAGE_CAP = 100;
		/** After the click: close the menu if Hypixel didn't (the click is already sent). */
		private static final int CLOSE_WAIT = 2;
		/**
		 * Hypixel fills some items in a moment after the menu shows: keep looking this long
		 * before calling a loadout, set or page arrow missing.
		 */
		private static final int MISSING_WAIT = 20;
		private static final int MISSING_CAP = 100;
		private static final int MAX_PAGES = 6;

		private enum PStep { IDLE, OPEN, WAIT, SCAN, CLOSE }

		private static final ArrayDeque<String> QUEUE = new ArrayDeque<>();
		private static PStep pstep = PStep.IDLE;
		private static String target;
		private static int waited;
		private static int pages;
		/** A page turn was clicked: wait for the new page before looking. */
		private static boolean turned;
		/** Server tick count when {@link #waited} was reset. */
		private static long mark;
		/** The page before the turn: container, title, items, page label. */
		private static int turnId;
		private static String turnTitle;
		private static int turnPrint;
		private static String turnPage;

		private static void restart() {
			waited = 0;
			mark = dev.stray.client.net.ServerTicks.seen();
		}

		/** This many server ticks since {@link #restart}, or the client tick cap. */
		private static boolean over(int server, int cap) {
			return dev.stray.client.net.ServerTicks.seen() - mark >= server || waited >= cap;
		}

		/** Remember the page, right as its turn is clicked. */
		private static void turning(Minecraft client, LocalPlayer player, String page) {
			turned = true;
			turnId = player.containerMenu.containerId;
			turnTitle = client.gui.screen() == null ? "" : client.gui.screen().getTitle().getString();
			turnPrint = print(player.containerMenu);
			turnPage = page;
			pages++;
			restart();
		}

		/**
		 * The page turn arrived (wardrobe and loadouts: the page label changed; pets: the menu,
		 * title or items changed), or it's been long enough to look anyway.
		 */
		private static boolean turnDone(Minecraft client, LocalPlayer player, String pageNow) {
			if (!turned) {
				return true;
			}
			boolean arrived;
			if (turnPage != null) {
				arrived = !turnPage.equals(pageNow);
			} else {
				String title = client.gui.screen() == null ? "" : client.gui.screen().getTitle().getString();
				arrived = player.containerMenu.containerId != turnId || !title.equals(turnTitle) || print(player.containerMenu) != turnPrint;
			}
			if (arrived || over(PAGE_WAIT, PAGE_CAP)) {
				turned = false;
				restart();
				return true;
			}
			return false;
		}

		private static int print(net.minecraft.world.inventory.AbstractContainerMenu menu) {
			int hash = 1;
			for (int i = 0; i < menu.slots.size() - 36; i++) {
				ItemStack stack = menu.slots.get(i).getItem();
				hash = hash * 31 + (stack.isEmpty() ? 0 : name(stack).hashCode());
			}
			return hash;
		}

		static void queue(String pet) {
			QUEUE.remove(pet);
			QUEUE.add(pet);
		}

		static void clear() {
			QUEUE.clear();
			pstep = PStep.IDLE;
			target = null;
		}

		static void tick(Minecraft client, LocalPlayer player) {
			if (pstep != PStep.IDLE) {
				waited++;
			}
			switch (pstep) {
				case IDLE -> {
					if (!QUEUE.isEmpty()) {
						target = QUEUE.poll();
						pstep = PStep.OPEN;
						open(client, player);
					}
				}
				case OPEN -> open(client, player);
				case WAIT -> {
					if (wardrobe() || loadout()) {
						if (wardrobe() ? wardrobeOpen(client, player) : loadoutsOpen(client, player)) {
							// Loaded: click in this same tick.
							pstep = PStep.SCAN;
							restart();
							pages = 0;
							turned = false;
							if (wardrobe()) {
								scanWardrobe(client, player);
							} else {
								scanLoadouts(client, player);
							}
						} else if (over(OPEN_TIMEOUT, OPEN_CAP)) {
							message(wardrobe() ? "§cAuto Rag: the wardrobe didn't open." : "§cAuto Rag: the loadouts menu didn't open.");
							pstep = PStep.IDLE;
						}
						return;
					}
					net.minecraft.world.inventory.AbstractContainerMenu menu = petsMenu(client);
					if (menu != null && hasItems(menu)) {
						pstep = PStep.SCAN;
						restart();
						pages = 0;
						turned = false;
						scan(client, player, menu);
					} else if (over(OPEN_TIMEOUT, OPEN_CAP)) {
						message("§cAuto Rag: the pets menu didn't open.");
						pstep = PStep.IDLE;
					}
				}
				case SCAN -> {
					// Closed (by you or the server): done. Open but not filled in yet (a page
					// turn under lag): wait.
					if (client.gui.screen() == null) {
						pstep = PStep.IDLE;
						return;
					}
					if (jobMenu(client, player) == null || petsJob() && !hasItems(player.containerMenu)) {
						if (over(OPEN_TIMEOUT, OPEN_CAP)) {
							pstep = PStep.IDLE;
						}
						return;
					}
					if (wardrobe()) {
						if (turnDone(client, player, wardrobePage(client, player))) {
							scanWardrobe(client, player);
						}
						return;
					}
					if (loadout()) {
						if (turnDone(client, player, loadoutsPage(client, player))) {
							scanLoadouts(client, player);
						}
						return;
					}
					net.minecraft.world.inventory.AbstractContainerMenu menu = petsMenu(client);
					if (turnDone(client, player, null)) {
						scan(client, player, menu);
					}
				}
				case CLOSE -> {
					// Hypixel closes the menu after summoning; if it's still open, close it.
					if (waited >= CLOSE_WAIT) {
						if (jobMenu(client, player) != null) {
							player.closeContainer();
						}
						pstep = PStep.IDLE;
					} else if (jobMenu(client, player) == null) {
						pstep = PStep.IDLE;
					}
				}
				default -> {
				}
			}
		}

		private static void open(Minecraft client, LocalPlayer player) {
			// Your own menu is open, or a leap is running: wait.
			if (client.gui.screen() != null || LeapRunner.busy()) {
				return;
			}
			player.connection.sendCommand(wardrobe() ? "wardrobe" : loadout() ? "loadouts" : "pets");
			pstep = PStep.WAIT;
			restart();
		}

		private static void scan(Minecraft client, LocalPlayer player, net.minecraft.world.inventory.AbstractContainerMenu menu) {
			List<Slot> slots = menu.slots;
			int size = slots.size() - 36;
			int next = -1;
			for (int i = 0; i < size; i++) {
				ItemStack stack = slots.get(i).getItem();
				if (stack.isEmpty()) {
					continue;
				}
				String name = name(stack);
				if (name.contains(target)) {
					if (lore(stack).contains("click to despawn")) {
						// Already your active pet: clicking would despawn it.
						player.closeContainer();
						pstep = PStep.IDLE;
						return;
					}
					OdinClicks.guiClick(menu.containerId, i, 0, ContainerInput.PICKUP);
					pstep = PStep.CLOSE;
					restart();
					return;
				}
				if (name.equals("Next Page")) {
					next = i;
				}
			}
			if (next >= 0 && pages < MAX_PAGES) {
				OdinClicks.guiClick(menu.containerId, next, 0, ContainerInput.PICKUP);
				turning(client, player, null);
				return;
			}
			if (next < 0 && !over(MISSING_WAIT, MISSING_CAP)) {
				return;
			}
			message("§cAuto Rag: no " + target + " in your pets menu.");
			player.closeContainer();
			pstep = PStep.IDLE;
		}

		private static boolean wardrobe() {
			return target != null && target.startsWith(WARDROBE);
		}

		private static String wardrobePage(Minecraft client, LocalPlayer player) {
			return client.gui.screen() == null ? "" : dev.stray.client.item.WardrobeMenus.read(player.containerMenu, client.gui.screen().getTitle()).page();
		}

		private static String loadoutsPage(Minecraft client, LocalPlayer player) {
			return client.gui.screen() == null ? "" : dev.stray.client.item.LoadoutsMenus.read(player.containerMenu, client.gui.screen().getTitle()).page();
		}

		private static boolean petsJob() {
			return !wardrobe() && !loadout();
		}

		private static boolean loadout() {
			return target != null && target.startsWith(LOADOUT);
		}

		/** The loadouts menu is the server's open container, with its loadouts read. */
		private static boolean loadoutsOpen(Minecraft client, LocalPlayer player) {
			if (client.gui.screen() == null || player.containerMenu == player.inventoryMenu) {
				return false;
			}
			dev.stray.client.item.LoadoutsMenus.Snapshot snap = dev.stray.client.item.LoadoutsMenus.read(player.containerMenu, client.gui.screen().getTitle());
			return snap.hasLoadouts() && snap.hasItems();
		}

		private static void scanLoadouts(Minecraft client, LocalPlayer player) {
			int number = Integer.parseInt(target.substring(LOADOUT.length()));
			dev.stray.client.item.LoadoutsMenus.Snapshot snap = dev.stray.client.item.LoadoutsMenus.read(player.containerMenu, client.gui.screen().getTitle());
			int page = 1;
			String[] label = snap.page().split("/");
			if (label.length == 2) {
				try {
					page = Integer.parseInt(label[0].trim());
				} catch (NumberFormatException ignored) {
					page = 1;
				}
			}
			int wantPage = (number - 1) / LOADOUTS_PER_PAGE + 1;
			int id = player.containerMenu.containerId;
			if (page != wantPage) {
				dev.stray.client.item.LoadoutsMenus.Piece turn = page < wantPage ? snap.next() : snap.prev();
				if (turn == null && !over(MISSING_WAIT, MISSING_CAP)) {
					return;
				}
				if (turn == null || pages >= MAX_PAGES) {
					message("§cAuto Rag: loadout " + number + " isn't there.");
					player.closeContainer();
					pstep = PStep.IDLE;
					return;
				}
				OdinClicks.guiClick(id, turn.slot(), 0, ContainerInput.PICKUP);
				turning(client, player, snap.page());
				return;
			}
			int index = (number - 1) % LOADOUTS_PER_PAGE;
			if (index >= snap.loadouts().size() || snap.loadouts().get(index).stack().isEmpty()) {
				if (!over(MISSING_WAIT, MISSING_CAP)) {
					return;
				}
				message("§cAuto Rag: loadout " + number + " isn't there.");
				player.closeContainer();
				pstep = PStep.IDLE;
				return;
			}
			dev.stray.client.item.LoadoutsMenus.Piece piece = snap.loadouts().get(index);
			if (piece.selected()) {
				// Already your loadout.
				player.closeContainer();
				pstep = PStep.IDLE;
				return;
			}
			OdinClicks.guiClick(id, piece.slot(), 0, ContainerInput.PICKUP);
			pstep = PStep.CLOSE;
			restart();
		}

		/** The menu this job works in, if it's open. */
		private static Object jobMenu(Minecraft client, LocalPlayer player) {
			if (wardrobe()) {
				return wardrobeOpen(client, player) ? player.containerMenu : null;
			}
			if (loadout()) {
				return loadoutsOpen(client, player) ? player.containerMenu : null;
			}
			return petsMenu(client);
		}

		/**
		 * The wardrobe is the server's open container (Stray's own Wardrobe screen may be shown
		 * over it, with the same title), with its items in.
		 */
		private static boolean wardrobeOpen(Minecraft client, LocalPlayer player) {
			if (client.gui.screen() == null || player.containerMenu == player.inventoryMenu) {
				return false;
			}
			dev.stray.client.item.WardrobeMenus.Snapshot snap = dev.stray.client.item.WardrobeMenus.read(player.containerMenu, client.gui.screen().getTitle());
			// Hypixel sends all items at once: any item means the menu is filled in.
			return snap.hasSets() && hasItems(player.containerMenu);
		}

		private static void scanWardrobe(Minecraft client, LocalPlayer player) {
			int set = Integer.parseInt(target.substring(WARDROBE.length()));
			dev.stray.client.item.WardrobeMenus.Snapshot snap = dev.stray.client.item.WardrobeMenus.read(player.containerMenu, client.gui.screen().getTitle());
			int page = 1;
			String[] label = snap.page().split("/");
			if (label.length == 2) {
				try {
					page = Integer.parseInt(label[0].trim());
				} catch (NumberFormatException ignored) {
					page = 1;
				}
			}
			int wantPage = (set - 1) / 9 + 1;
			int id = player.containerMenu.containerId;
			if (page != wantPage) {
				dev.stray.client.item.WardrobeMenus.Piece turn = page < wantPage ? snap.next() : snap.prev();
				if (turn == null && !over(MISSING_WAIT, MISSING_CAP)) {
					return;
				}
				if (turn == null || pages >= MAX_PAGES) {
					message("§cAuto Rag: wardrobe set " + set + " isn't there.");
					player.closeContainer();
					pstep = PStep.IDLE;
					return;
				}
				OdinClicks.guiClick(id, turn.slot(), 0, ContainerInput.PICKUP);
				turning(client, player, snap.page());
				return;
			}
			int col = (set - 1) % 9;
			for (dev.stray.client.item.WardrobeMenus.ArmorSet armor : snap.sets()) {
				if (armor.index() != col) {
					continue;
				}
				if (armor.locked()) {
					message("§cAuto Rag: wardrobe set " + set + " is locked.");
				} else if (!armor.selected()) {
					// Not already worn (clicking a worn set takes it off).
					OdinClicks.guiClick(id, armor.slot(), 0, ContainerInput.PICKUP);
					pstep = PStep.CLOSE;
					restart();
					return;
				}
				player.closeContainer();
				pstep = PStep.IDLE;
				return;
			}
			if (!over(MISSING_WAIT, MISSING_CAP)) {
				return;
			}
			message("§cAuto Rag: wardrobe set " + set + " isn't there.");
			player.closeContainer();
			pstep = PStep.IDLE;
		}

		private static final java.util.regex.Pattern PETS_TITLE = java.util.regex.Pattern.compile("\\bPets\\b");

		/**
		 * The pets menu: the server's open container while the shown screen's title says Pets
		 * ("Pets", "Pets (1/2)", "(1/2) Pets"). Read from the container itself, so it works
		 * under any screen drawn over it.
		 */
		private static net.minecraft.world.inventory.AbstractContainerMenu petsMenu(Minecraft client) {
			LocalPlayer player = client.player;
			if (player == null || client.gui.screen() == null || player.containerMenu == player.inventoryMenu) {
				return null;
			}
			String title = OdinClicks.noControlCodes(client.gui.screen().getTitle().getString());
			return PETS_TITLE.matcher(title).find() ? player.containerMenu : null;
		}

		private static boolean hasItems(net.minecraft.world.inventory.AbstractContainerMenu menu) {
			List<Slot> slots = menu.slots;
			for (int i = 0; i < slots.size() - 36; i++) {
				if (!slots.get(i).getItem().isEmpty()) {
					return true;
				}
			}
			return false;
		}

		private static String name(ItemStack stack) {
			boolean prior = ItemAppearance.suppress();
			try {
				return OdinClicks.noControlCodes(stack.getHoverName().getString());
			} finally {
				ItemAppearance.resume(prior);
			}
		}

		private static String lore(ItemStack stack) {
			boolean prior = ItemAppearance.suppress();
			try {
				ItemLore lore = stack.get(DataComponents.LORE);
				if (lore == null) {
					return "";
				}
				StringBuilder text = new StringBuilder();
				for (Component line : lore.lines()) {
					text.append(OdinClicks.noControlCodes(line.getString()).toLowerCase(Locale.ROOT)).append('\n');
				}
				return text.toString();
			} finally {
				ItemAppearance.resume(prior);
			}
		}
	}
}
