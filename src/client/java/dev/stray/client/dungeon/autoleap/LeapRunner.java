package dev.stray.client.dungeon.autoleap;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.dungeon.DevMode;
import dev.stray.client.dungeon.DungeonParty;
import dev.stray.client.item.ItemIds;
import dev.stray.client.location.SkyblockLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

/**
 * Does one Spirit Leap to a teammate, the way quoi's LeapManager does (jcnlk/quoi, used
 * with permission): select the leap in the hotbar, use it, wait for the "Spirit Leap" menu,
 * click the teammate's head, close. The menu is silent: it is opened as a container without
 * a screen, so you keep moving and see nothing. With "Show leap menu" on it opens as the normal
 * screen instead; the clicks and their timing are the same either way.
 *
 * <p>Every step runs at the start of a tick (before the game handles keys and sends this
 * tick's movement), one step per tick, through vanilla paths: the hotbar slot the way the
 * number keys pick it, the leap used the way a right click into the air uses it (quoi's
 * PlayerUtils.interact: whatever is under the crosshair can't take the click), and the same
 * container click and close a mouse click on the menu sends. Like quoi, your own use key is
 * held up while a leap runs, so a held right click can't use the leap a second time.
 */
public final class LeapRunner {
	private static final String[] LEAP_IDS = {"INFINITE_SPIRIT_LEAP", "SPIRIT_LEAP"};
	private static final int MENU_TIMEOUT = 60;
	private static final int HEAD_TIMEOUT = 20;

	private enum Step { IDLE, SWAP, USE, AWAIT_MENU, CLICK, SWAP_BACK }

	private static Step step = Step.IDLE;
	/** The teammate to leap to, by exact name. */
	private static String target;
	private static String targetLabel;
	private static int previousSlot = -1;
	private static int waitTicks;
	/** Ticks the leap menu has been open, for "Wait in menu". */
	private static int menuTicks;
	private static int menuId = -1;
	/** The leap goes through a leap menu you opened yourself: click at tick start only. */
	private static boolean preOpened;
	private static AbstractContainerMenu silentMenu;
	/** A leap asked for while a screen or another leap was in the way; tried each tick. */
	private static String queued;
	private static String queuedLabel;
	private static int cooldownTicks;
	/**
	 * Double leaps, as quoi's LeapManager does them: the leap item this leap uses (its SkyBlock
	 * id, null when the setting is off) and each item's own cooldown in ticks. Spirit Leap and
	 * InfiniLeap cool down separately.
	 */
	private static String leapId;
	private static final java.util.Map<String, Integer> itemCooldown = new java.util.HashMap<>();
	/** Which leap is tried first when both are ready: InfiniLeap, as in quoi. */
	private static final String[] LEAP_ORDER = {"INFINITE_SPIRIT_LEAP", "SPIRIT_LEAP"};
	private static ClientLevel lastLevel;
	/**
	 * Debug leaps ({@code /stray debug dev leap}): no dungeon or party needed, the name is used
	 * as typed, and any hotbar item named "Spirit Leap" counts. Until you change worlds.
	 */
	private static boolean loose;

	private LeapRunner() {
	}

	public static boolean busy() {
		return step != Step.IDLE;
	}

	/** Set while a leap holds up your use key (quoi's suppressUseInput). */
	private static boolean useHeld;

	/** Lets go of the use key and drops queued use clicks, for as long as the leap runs. */
	private static void holdUseKey(Minecraft client) {
		useHeld = true;
		client.options.keyUse.setDown(false);
		while (client.options.keyUse.consumeClick()) {
			// drained
		}
	}

	/** The leap is over: the use key is down again only if you still hold it (quoi's restoreUseInput). */
	private static void releaseUseKey() {
		if (!useHeld) {
			return;
		}
		useHeld = false;
		Minecraft client = Minecraft.getInstance();
		boolean down = dev.stray.client.combat.OdinClicks.isPressed(((dev.stray.client.mixin.KeyMappingAccessor) client.options.keyUse).stray$boundKey());
		client.options.keyUse.setDown(down);
	}

	/** Leap to a teammate by name. Messages in chat when it can't. */
	public static void leapToName(String name) {
		if (name == null || name.isBlank() || !inDungeon()) {
			return;
		}
		DungeonParty.Member member = find(name, null);
		if (member == null && loose) {
			request(name, name);
			return;
		}
		request(member, name);
	}

	/** Debug: leap to this player by name on any server, e.g. a test server with your alt. */
	public static void debugLeap(String name) {
		loose = true;
		leapToName(name);
	}

	public static void setLoose() {
		loose = true;
	}

	/** Leap to the first living teammate of a class. */
	public static void leapToClass(DungeonParty.DungeonClass clazz) {
		if (clazz == null || clazz == DungeonParty.DungeonClass.EMPTY || !inDungeon()) {
			return;
		}
		DungeonParty.Member member = find(null, clazz);
		request(member, clazz.label);
	}

	private static boolean inDungeon() {
		return loose || SkyblockLocation.inDungeon || DevMode.on();
	}

	private static DungeonParty.Member find(String name, DungeonParty.DungeonClass clazz) {
		for (DungeonParty.Member member : DungeonParty.others()) {
			if (member.dead) {
				continue;
			}
			if (name != null && member.name.equals(name) || clazz != null && member.clazz == clazz) {
				return member;
			}
		}
		return null;
	}

	private static void request(DungeonParty.Member member, String label) {
		who = member == null ? null : member.name;
		if (member == null) {
			message("§cFailed to leap! §f" + label + " §cnot found"
				+ (loose ? "" : " §7(outside a dungeon: §f/stray debug dev leap <name>§7)"));
			return;
		}
		request(member.name, label);
	}

	private static void request(String name, String label) {
		who = name;
		Minecraft client = Minecraft.getInstance();
		if (!busy() && openLeapMenu(client) != null) {
			startPreOpened(client, name, label);
			return;
		}
		if (busy() || client.gui.screen() != null || client.player == null
			|| client.player.containerMenu != client.player.inventoryMenu) {
			queued = name;
			queuedLabel = label;
			if (busy() || !(client.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen)) {
				message("§eQueued leap to §f" + name);
			}
			return;
		}
		start(name, label);
	}

	/**
	 * The "Spirit Leap" menu you opened yourself and still have open, or null. Like quoi's
	 * pre-opened leap: a leap then only clicks the head in it.
	 */
	private static AbstractContainerMenu openLeapMenu(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || !(client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen)) {
			return null;
		}
		AbstractContainerMenu menu = screen.getMenu();
		if (menu != player.containerMenu || menu == player.inventoryMenu || !screen.getTitle().getString().contains("Spirit Leap")) {
			return null;
		}
		return menu;
	}

	/**
	 * Leap through your own open leap menu: no swap, no use, no waiting for the menu. The head
	 * is clicked at the next tick start ({@link #screenStep}), then the menu closes, as a mouse
	 * click on the head and closing it would. The cooldown isn't checked here (as in quoi): the
	 * menu being open means the leap was usable.
	 */
	private static void startPreOpened(Minecraft client, String name, String label) {
		AbstractContainerMenu menu = openLeapMenu(client);
		if (menu == null) {
			return;
		}
		target = name;
		targetLabel = label;
		leapId = null;
		previousSlot = -1;
		silentMenu = null;
		preOpened = true;
		menuId = menu.containerId;
		step = Step.CLICK;
		waitTicks = 0;
		// The menu's been open a while: no "Wait in menu".
		menuTicks = StrayConfig.get().leapMenuWait;
	}

	private static void start(String name, String label) {
		boolean twoLeaps = StrayConfig.get().leapDouble;
		if (!twoLeaps && cooldownTicks > 0) {
			message("§cFailed to leap! On cooldown: " + String.format(Locale.ROOT, "%.1f", cooldownTicks / 20.0) + "s");
			return;
		}
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}
		leapId = null;
		if (twoLeaps) {
			// The leap in the hotbar that is ready soonest; InfiniLeap when both are equal.
			java.util.List<String> held = leapIdsInHotbar(player);
			String pick = null;
			for (String id : LEAP_ORDER) {
				if (held.contains(id) && (pick == null || itemCooldown.getOrDefault(id, 0) < itemCooldown.getOrDefault(pick, 0))) {
					pick = id;
				}
			}
			if (pick != null && itemCooldown.getOrDefault(pick, 0) > 0) {
				message("§cFailed to leap! On cooldown: " + String.format(Locale.ROOT, "%.1f", itemCooldown.get(pick) / 20.0) + "s");
				return;
			}
			leapId = pick;
		}
		int slot = leapSlot(player);
		if (slot < 0) {
			message("§cFailed to leap: no Spirit Leap in your hotbar");
			return;
		}
		target = name;
		targetLabel = label;
		int selected = player.getInventory().getSelectedSlot();
		previousSlot = StrayConfig.get().leapSwapBack && slot != selected ? selected : -1;
		step = slot == selected ? Step.USE : Step.SWAP;
		waitTicks = 0;
	}

	/**
	 * Plays the arm swing on your screen only: the swing's state is set directly, not through
	 * {@code swing(...)}, so nothing about it can be sent to the server.
	 */
	static void swingOnScreen(LocalPlayer player) {
		//? if >=26.3 {
		dev.stray.client.mixin.LivingEntitySwingAccessor self = (dev.stray.client.mixin.LivingEntitySwingAccessor) player;
		net.minecraft.world.item.component.SwingAnimation animation = net.minecraft.world.item.component.SwingAnimation.DEFAULT;
		self.stray$swingState().startIfAble(net.minecraft.world.InteractionHand.MAIN_HAND, animation, self.stray$swingDuration(animation));
		//?} else {
		/*player.swingTime = -1;
		player.swinging = true;
		player.swingingArm = net.minecraft.world.InteractionHand.MAIN_HAND;*///?}
	}

	private static int leapSlot(LocalPlayer player) {
		for (int i = 0; i < 9; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (isLeap(stack) && (leapId == null || leapId.equals(leapKind(stack)))) {
				return i;
			}
		}
		return -1;
	}

	/** Which leap item this is, for the separate cooldowns: its SkyBlock id ("SPIRIT_LEAP", "INFINITE_SPIRIT_LEAP"). */
	private static String leapKind(ItemStack stack) {
		String id = ItemIds.skyblockId(stack);
		if (id == null) {
			return loose && isLeap(stack) ? "SPIRIT_LEAP" : null;
		}
		return id;
	}

	/** The different leap items in the hotbar, left to right. */
	private static java.util.List<String> leapIdsInHotbar(LocalPlayer player) {
		java.util.List<String> ids = new java.util.ArrayList<>();
		for (int i = 0; i < 9; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (isLeap(stack)) {
				String kind = leapKind(stack);
				if (kind != null && !ids.contains(kind)) {
					ids.add(kind);
				}
			}
		}
		return ids;
	}

	public static boolean isLeap(ItemStack stack) {
		String id = ItemIds.skyblockId(stack);
		if (id == null) {
			return loose && !stack.isEmpty() && stack.getHoverName().getString().contains("Spirit Leap");
		}
		for (String leap : LEAP_IDS) {
			if (leap.equals(id)) {
				return true;
			}
		}
		return false;
	}

	/** Every tick, end: cooldown, world change. Actions wait for {@link #beforeKeybinds}. */
	public static void tick(Minecraft client) {
		SwingCheck.tick();
		if (client.level != lastLevel) {
			lastLevel = client.level;
			cancel(false);
			queued = null;
			loose = false;
			cooldownTicks = 0;
			itemCooldown.clear();
		}
		if (cooldownTicks > 0) {
			cooldownTicks--;
		}
		itemCooldown.replaceAll((id, left) -> Math.max(0, left - 1));
	}

	/** Tick start, before keys are handled: one step of the leap. */
	public static void beforeKeybinds(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || client.gameMode == null) {
			return;
		}
		if (step == Step.IDLE) {
			if (queued != null && client.gui.screen() == null && player.containerMenu == player.inventoryMenu) {
				String name = queued;
				String label = queuedLabel;
				queued = null;
				start(name, label);
			}
			if (step == Step.IDLE) {
				return;
			}
		}
		// Your own right clicks (or a held right click) would use the leap a second time.
		holdUseKey(client);
		switch (step) {
			case SWAP -> {
				int slot = leapSlot(player);
				if (slot < 0) {
					fail("could not select a Spirit Leap");
					return;
				}
				if (!dev.stray.client.net.SlotGuard.canSwap()) {
					// One slot change per tick: swap next tick.
					return;
				}
				// Your own number keys this tick would be a second slot change: dropped.
				for (net.minecraft.client.KeyMapping key : client.options.keyHotbarSlots) {
					while (key.consumeClick()) {
						// drained
					}
				}
				// The same as pressing that hotbar number key. Then use it in this same tick, as a
				// number key and a right click in one tick do (vanilla handles the hotbar keys
				// first, and useItem sends the new slot before the use), like quoi.
				player.getInventory().setSelectedSlot(slot);
				dev.stray.client.net.SlotGuard.markSwapped();
				step = Step.USE;
				use(client, player);
			}
			case USE -> use(client, player);
			case AWAIT_MENU -> {
				if (++waitTicks > MENU_TIMEOUT) {
					fail("Spirit Leap menu did not open in time");
				}
			}
			case CLICK -> click(client, player);
			case SWAP_BACK -> {
				if (!dev.stray.client.net.SlotGuard.canSwap()) {
					return;
				}
				if (previousSlot >= 0 && previousSlot < 9) {
					player.getInventory().setSelectedSlot(previousSlot);
					dev.stray.client.net.SlotGuard.markSwapped();
				}
				finish();
			}
			default -> {
			}
		}
	}

	private static void use(Minecraft client, LocalPlayer player) {
		if (!isLeap(player.getMainHandItem()) || leapId != null && (!leapId.equals(leapKind(player.getMainHandItem()))
			|| itemCooldown.getOrDefault(leapId, 0) > 0)) {
			fail("could not select a Spirit Leap");
			return;
		}
		if (client.gui.screen() != null) {
			return;
		}
		// Like quoi: use the item itself, as a right click into the air does, so the
		// terminal or teammate under the crosshair can't take the click.
		client.gameMode.useItem(player, net.minecraft.world.InteractionHand.MAIN_HAND);
		if (StrayConfig.get().leapSwing) {
			swingOnScreen(player);
		}
		step = Step.AWAIT_MENU;
		waitTicks = 0;
	}

	/**
	 * The leap menu's items just arrived from the server (after vanilla filled them in): click
	 * the head right away, like quoi and like a mouse click in a menu, instead of waiting for
	 * the next tick start. Only with "Wait in menu" at 0; with a wait, tick start counts it.
	 */
	public static void onMenuItems(int containerId) {
		if (step != Step.CLICK || preOpened || containerId != menuId || StrayConfig.get().leapMenuWait > 0) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.gameMode == null || !ourMenu(player.containerMenu)
			|| headSlot(player.containerMenu, target) < 0) {
			return;
		}
		holdUseKey(client);
		click(client, player);
	}

	/** The leap menu we wait to click in: the silent one, or the screen's when the menu is shown. */
	private static boolean ourMenu(AbstractContainerMenu menu) {
		if (menu == null || menuId < 0 || menu.containerId != menuId) {
			return false;
		}
		return silentMenu != null ? menu == silentMenu : menu != Minecraft.getInstance().player.inventoryMenu;
	}

	/**
	 * Tick start with a screen open, where {@link #beforeKeybinds} doesn't run: the head click
	 * when the leap menu is shown as a screen ("Show leap menu"). Nothing else runs here.
	 */
	public static void screenStep(Minecraft client) {
		LocalPlayer player = client.player;
		// A queued leap and you open the leap menu yourself: use that menu.
		if (step == Step.IDLE && queued != null && openLeapMenu(client) != null) {
			String name = queued;
			String label = queuedLabel;
			queued = null;
			startPreOpened(client, name, label);
		}
		if (step != Step.CLICK || silentMenu != null || client.gui.screen() == null || player == null || client.gameMode == null) {
			return;
		}
		holdUseKey(client);
		click(client, player);
	}

	private static void click(Minecraft client, LocalPlayer player) {
		AbstractContainerMenu menu = player.containerMenu;
		if (!ourMenu(menu)) {
			fail("the leap menu closed");
			return;
		}
		// "Wait in menu": extra ticks before the click; 0 clicks on the first tick, as before.
		if (menuTicks++ < StrayConfig.get().leapMenuWait) {
			return;
		}
		int slot = headSlot(menu, target);
		if (slot < 0) {
			if (++waitTicks > HEAD_TIMEOUT) {
				player.closeContainer();
				fail(target + " is not in the leap menu");
			}
			return;
		}
		client.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.PICKUP, player);
		player.closeContainer();
		silentMenu = null;
		menuId = -1;
		if (leapId == null) {
			cooldownTicks = (int) Math.round(48 * mageMultiplier());
		}
		// Double leaps: the item's cooldown starts from the sent click, in onClickSent.
		message("§aLeaping to §f" + targetLabel + (targetLabel.equals(target) ? "" : " §7(" + target + ")"));
		step = previousSlot >= 0 ? Step.SWAP_BACK : Step.IDLE;
		if (step == Step.IDLE) {
			finish();
		}
	}

	private static int headSlot(AbstractContainerMenu menu, String name) {
		int size = menu.slots.size() - 36;
		for (int i = 0; i < size; i++) {
			ItemStack stack = menu.slots.get(i).getItem();
			if (!stack.isEmpty() && stack.is(Items.PLAYER_HEAD) && stack.getHoverName().getString().contains(name)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * The server opening a menu while we wait for the leap: the "Spirit Leap" menu becomes a
	 * container with no screen (silent). Returns true when it was taken.
	 */
	public static boolean onOpenScreen(ClientboundOpenScreenPacket packet) {
		if (step != Step.AWAIT_MENU) {
			return false;
		}
		String title = packet.getTitle().getString();
		if (!title.contains("Spirit Leap") && !(loose && title.contains("Leap"))) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null) {
			return false;
		}
		if (StrayConfig.get().leapShowMenu) {
			// Let vanilla open it as usual; the next tick start clicks in the screen's menu.
			silentMenu = null;
			menuId = packet.getContainerId();
			step = Step.CLICK;
			waitTicks = 0;
			menuTicks = 0;
			return false;
		}
		AbstractContainerMenu menu = packet.getType().create(packet.getContainerId(), player.getInventory());
		player.containerMenu = menu;
		silentMenu = menu;
		menuId = packet.getContainerId();
		step = Step.CLICK;
		waitTicks = 0;
		menuTicks = 0;
		return true;
	}

	/**
	 * A container click on its way to the server (looks only, changes nothing): a click on a
	 * head in the "Spirit Leap" menu starts the cooldown of the leap in your hand, yours or
	 * this mod's, like quoi. Spirit Leap 5 s, InfiniLeap 2 s, plus 7 ticks for lag, less for Mage.
	 */
	public static void onClickSent(int containerId, int slot, ContainerInput input) {
		if (!StrayConfig.get().leapDouble || input != ContainerInput.PICKUP || slot < 11 || slot > 15) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}
		boolean ours = menuId >= 0 && containerId == menuId;
		boolean shown = client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen
			&& player.containerMenu.containerId == containerId && screen.getTitle().getString().contains("Spirit Leap");
		if (!ours && !shown) {
			return;
		}
		String id = leapKind(player.getMainHandItem());
		if (id == null || itemCooldown.getOrDefault(id, 0) > 0) {
			return;
		}
		int base;
		if ("SPIRIT_LEAP".equals(id)) {
			base = 107;
		} else if ("INFINITE_SPIRIT_LEAP".equals(id)) {
			base = 47;
		} else {
			return;
		}
		long percent = Math.round(mageMultiplier() * 100);
		itemCooldown.put(id, (int) Math.ceil(base * percent / 100.0));
	}

	/** Chat: the server refusing the leap. */
	public static void onChat(String plain) {
		if (!busy()) {
			return;
		}
		if ("You cannot use this in a solo dungeon!".equals(plain) || "There are no other players to teleport to!".equals(plain)) {
			message("§cFailed to leap! You're in a solo dungeon!");
			cancel(true);
		}
	}

	/** Mage cooldown reduction, as quoi works it out. */
	private static double mageMultiplier() {
		DungeonParty.Member self = DungeonParty.self();
		if (self == null || self.clazz != DungeonParty.DungeonClass.MAGE) {
			return 1.0;
		}
		int mages = 0;
		for (DungeonParty.Member member : DungeonParty.all()) {
			if (member.clazz == DungeonParty.DungeonClass.MAGE) {
				mages++;
			}
		}
		return 1 - 0.25 - (Math.floor(self.clazzLevel / 2.0) / 100.0) * (mages == 1 ? 2 : 1);
	}

	private static void fail(String why) {
		message("§cFailed to leap to §f" + (targetLabel == null ? "?" : targetLabel) + "§c: " + why);
		cancel(true);
	}

	private static void cancel(boolean closeMenu) {
		Minecraft client = Minecraft.getInstance();
		if (closeMenu && client.player != null && ourMenu(client.player.containerMenu)) {
			client.player.closeContainer();
		}
		if (client.player != null && previousSlot >= 0 && previousSlot < 9 && step != Step.IDLE) {
			// Swap back on the next tick start, like a finished leap.
			step = Step.SWAP_BACK;
			silentMenu = null;
			menuId = -1;
			return;
		}
		finish();
	}

	private static void finish() {
		releaseUseKey();
		step = Step.IDLE;
		target = null;
		targetLabel = null;
		previousSlot = -1;
		silentMenu = null;
		menuId = -1;
		waitTicks = 0;
		leapId = null;
		preOpened = false;
	}

	/** Which leap this is ("Auto leap", "Fast leap"), for its notification; set by whoever asks. */
	static String source = "Leap";

	/** Who the leap is to, for the notification's face; display only. */
	private static String who;

	/**
	 * A leap note: a notification when those are on, and the "Stray »" line in your own chat
	 * unless that is turned off. Only shown to you; nothing here is sent anywhere.
	 */
	private static void message(String text) {
		StrayConfig config = StrayConfig.get();
		if (config.leapNotifications) {
			LeapToasts.push(source, text, target != null ? target : who);
		}
		if (config.leapHideChat) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			client.player.sendSystemMessage(Component.literal("§bStray §7» " + text));
		}
	}
}
