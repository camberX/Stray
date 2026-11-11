package dev.stray.client.dungeon.autoleap;

import dev.stray.client.combat.OdinClicks;
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
 * a screen, so you keep moving and see nothing.
 *
 * <p>Every step runs at the start of a tick (before the game handles keys and sends this
 * tick's movement), one step per tick, through vanilla paths: the hotbar slot the way the
 * number keys pick it, the use key for the leap, and the same container click and close
 * a mouse click on the menu sends. Nothing is sent that a fast player could not send.
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
	private static int menuId = -1;
	private static AbstractContainerMenu silentMenu;
	/** A leap asked for while a screen or another leap was in the way; tried each tick. */
	private static String queued;
	private static String queuedLabel;
	private static int cooldownTicks;
	private static ClientLevel lastLevel;

	private LeapRunner() {
	}

	public static boolean busy() {
		return step != Step.IDLE;
	}

	/** Leap to a teammate by name. Messages in chat when it can't. */
	public static void leapToName(String name) {
		if (name == null || name.isBlank() || !inDungeon()) {
			return;
		}
		DungeonParty.Member member = find(name, null);
		request(member, name);
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
		return SkyblockLocation.inDungeon || DevMode.on();
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
		if (member == null) {
			message("§cFailed to leap! §f" + label + " §cnot found");
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (busy() || client.gui.screen() != null || client.player == null
			|| client.player.containerMenu != client.player.inventoryMenu) {
			queued = member.name;
			queuedLabel = label;
			message("§eQueued leap to §f" + member.name);
			return;
		}
		start(member.name, label);
	}

	private static void start(String name, String label) {
		if (cooldownTicks > 0) {
			message("§cFailed to leap! On cooldown: " + String.format(Locale.ROOT, "%.1f", cooldownTicks / 20.0) + "s");
			return;
		}
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null) {
			return;
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

	private static int leapSlot(LocalPlayer player) {
		for (int i = 0; i < 9; i++) {
			if (isLeap(player.getInventory().getItem(i))) {
				return i;
			}
		}
		return -1;
	}

	public static boolean isLeap(ItemStack stack) {
		String id = ItemIds.skyblockId(stack);
		if (id == null) {
			return false;
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
		if (client.level != lastLevel) {
			lastLevel = client.level;
			cancel(false);
			queued = null;
			cooldownTicks = 0;
		}
		if (cooldownTicks > 0) {
			cooldownTicks--;
		}
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
		// Your own right clicks would use the leap a second time; drop them while leaping.
		while (client.options.keyUse.consumeClick()) {
			// drained
		}
		switch (step) {
			case SWAP -> {
				int slot = leapSlot(player);
				if (slot < 0) {
					fail("could not select a Spirit Leap");
					return;
				}
				// The same as pressing that hotbar number key.
				player.getInventory().setSelectedSlot(slot);
				step = Step.USE;
			}
			case USE -> {
				if (!isLeap(player.getMainHandItem())) {
					fail("could not select a Spirit Leap");
					return;
				}
				if (client.gui.screen() != null) {
					return;
				}
				OdinClicks.rightClick();
				step = Step.AWAIT_MENU;
				waitTicks = 0;
			}
			case AWAIT_MENU -> {
				if (++waitTicks > MENU_TIMEOUT) {
					fail("Spirit Leap menu did not open in time");
				}
			}
			case CLICK -> click(client, player);
			case SWAP_BACK -> {
				if (previousSlot >= 0 && previousSlot < 9) {
					player.getInventory().setSelectedSlot(previousSlot);
				}
				finish();
			}
			default -> {
			}
		}
	}

	private static void click(Minecraft client, LocalPlayer player) {
		AbstractContainerMenu menu = player.containerMenu;
		if (menu != silentMenu || menu.containerId != menuId) {
			fail("the leap menu closed");
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
		cooldownTicks = (int) Math.round(48 * mageMultiplier());
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
		if (!title.contains("Spirit Leap")) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null) {
			return false;
		}
		AbstractContainerMenu menu = packet.getType().create(packet.getContainerId(), player.getInventory());
		player.containerMenu = menu;
		silentMenu = menu;
		menuId = packet.getContainerId();
		step = Step.CLICK;
		waitTicks = 0;
		return true;
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
		if (closeMenu && client.player != null && silentMenu != null && client.player.containerMenu == silentMenu) {
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
		step = Step.IDLE;
		target = null;
		targetLabel = null;
		previousSlot = -1;
		silentMenu = null;
		menuId = -1;
		waitTicks = 0;
	}

	private static void message(String text) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			client.player.sendSystemMessage(Component.literal("§bStray §7» " + text));
		}
	}
}
