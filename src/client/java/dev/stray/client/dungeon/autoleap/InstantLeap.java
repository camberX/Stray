package dev.stray.client.dungeon.autoleap;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.dungeon.DungeonParty;
import dev.stray.client.dungeon.LeapMenu;
import dev.stray.client.item.ItemIds;
import dev.stray.client.location.SkyblockLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Instant leap menu, after Noamm's leap-menu change for NoammAddons (CC0): using a Spirit Leap
 * or InfiniLeap opens Stray's leap menu on this client at once, before the server's menu
 * arrives. Until the heads load, each teammate has a placeholder; a click on one is kept, the
 * server's menu is then taken without a screen and that teammate's head is clicked as soon as
 * it arrives, then the menu closes. If the server's menu comes first it simply replaces this
 * one. Full edition only.
 */
public final class InstantLeap {
	/** Give up on a queued leap, or close the stand-in menu, after this many ticks. */
	private static final int TIMEOUT = 40;

	/** The teammate a placeholder click picked, waiting for the server's leap menu. */
	private static String awaiting;
	private static int awaitingTicks;
	/** The server's leap menu, taken without a screen for {@link #awaiting}. */
	private static int menuId = -1;
	/** Client ticks, for the leap cooldown since the last leap (Noamm's lastLeap). */
	private static long now;
	private static long lastLeap = -1_000L;
	private static ClientLevel lastLevel;

	private InstantLeap() {
	}

	/** The stand-in leap menu: Stray's leap menu, drawn and clicked with no container behind it. */
	public static final class MenuScreen extends net.minecraft.client.gui.screens.Screen {
		private int ticks;

		MenuScreen() {
			super(Component.literal("Spirit Leap"));
		}

		@Override
		public boolean isPauseScreen() {
			return false;
		}

		@Override
		public boolean isInGameUi() {
			return true;
		}

		@Override
		public void tick() {
			if (++ticks > TIMEOUT && minecraft != null) {
				// The server's menu never came (on cooldown, or the leap didn't go through).
				minecraft.gui.setScreen(null);
			}
		}

		@Override
		public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
			LeapMenu.updateLeapMenu();
			dev.stray.client.dungeon.LeapMenuView.render(graphics, width, height);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
			LeapMenu.mouseClicked(event.button());
			return true;
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			if (LeapMenu.keyPressed(event.key())) {
				return true;
			}
			return super.keyPressed(event);
		}
	}

	public static boolean enabled() {
		StrayConfig config = StrayConfig.get();
		return config.leapMenuEnabled && config.leapInstant;
	}

	public static boolean isScreen(net.minecraft.client.gui.screens.Screen screen) {
		return screen instanceof MenuScreen;
	}

	public static void tick(Minecraft client) {
		now++;
		if (client.level != lastLevel) {
			lastLevel = client.level;
			lastLeap = -1_000L;
			reset();
		}
		if (awaiting != null && ++awaitingTicks > TIMEOUT) {
			LocalPlayer player = client.player;
			if (menuId >= 0 && player != null && player.containerMenu.containerId == menuId) {
				player.closeContainer();
			}
			message("§cLeap to §f" + awaiting + " §cdidn't go through.");
			reset();
		}
	}

	private static void reset() {
		awaiting = null;
		awaitingTicks = 0;
		menuId = -1;
	}

	/** A use of the held item on its way to the server (client thread): open the stand-in menu. */
	public static void onUseItem() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (!enabled() || player == null || client.gui.screen() != null || !SkyblockLocation.inDungeon
			|| LeapRunner.busy() || awaiting != null || DungeonParty.others().isEmpty()) {
			return;
		}
		ItemStack stack = player.getMainHandItem();
		String id = ItemIds.skyblockId(stack);
		int cooldown;
		if ("SPIRIT_LEAP".equals(id)) {
			cooldown = 100;
		} else if ("INFINITE_SPIRIT_LEAP".equals(id)) {
			cooldown = 40;
		} else {
			return;
		}
		if (now - lastLeap < cooldown) {
			return;
		}
		client.gui.setScreen(new MenuScreen());
	}

	/**
	 * A placeholder was clicked (the heads aren't there yet): remember the teammate and get the
	 * screen out of the way. Returns false when this isn't one.
	 */
	public static boolean queue(LeapMenu.Entry entry) {
		if (entry.slotIndex() >= 0) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null) {
			return true;
		}
		awaiting = entry.player().name;
		awaitingTicks = 0;
		AbstractContainerMenu open = player.containerMenu;
		if (open != player.inventoryMenu) {
			// The server's menu is already open but still empty: keep it, without the screen.
			menuId = open.containerId;
		}
		// Closing the screen this way sends nothing; the container (if any) stays open.
		client.gui.setScreen(null);
		return true;
	}

	/** The server opening a menu: the leap menu is taken without a screen while a leap is queued. */
	public static boolean onOpenScreen(ClientboundOpenScreenPacket packet) {
		if (awaiting == null || !packet.getTitle().getString().contains("Spirit Leap")) {
			return false;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return false;
		}
		player.containerMenu = packet.getType().create(packet.getContainerId(), player.getInventory());
		menuId = packet.getContainerId();
		return true;
	}

	/** The leap menu's items arrived: click the queued teammate's head, then close. */
	public static void onMenuItems(int containerId) {
		if (awaiting == null || containerId != menuId) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.gameMode == null || player.containerMenu.containerId != menuId) {
			return;
		}
		AbstractContainerMenu menu = player.containerMenu;
		int size = menu.slots.size() - 36;
		for (int i = 0; i < size; i++) {
			ItemStack stack = menu.slots.get(i).getItem();
			if (!stack.isEmpty() && stack.is(Items.PLAYER_HEAD) && stack.getHoverName().getString().contains(awaiting)) {
				client.gameMode.handleContainerInput(menu.containerId, i, 0, ContainerInput.PICKUP, player);
				player.closeContainer();
				lastLeap = now;
				reset();
				return;
			}
		}
	}

	/** A leap went through ("You have teleported to ..."), for the cooldown. */
	public static void onLeap() {
		lastLeap = now;
	}

	/** A head clicked in the leap menu the normal way, for the cooldown. */
	public static void onHeadClicked() {
		lastLeap = now;
	}

	private static void message(String text) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			player.sendSystemMessage(Component.literal("§bStray §7» " + text));
		}
	}
}
