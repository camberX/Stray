package dev.stray.client.dungeon;

import com.mojang.blaze3d.platform.InputConstants;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.location.SkyblockLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Custom Spirit Leap / "Teleport to Player" menu. The leap logic is NoammAddons'
 * LeapMenu (CC0), ported as-is: the heads are read from the real menu, and a leap
 * is the same single left click on that player's head slot, followed by the same
 * close. Only the drawing ({@link LeapMenuView}) is Stray's own.
 */
public final class LeapMenu {
	public static final String[] SORTING = {"A-Z Class", "A-Z Name", "Odin Sorting", "No Sorting"};

	public record Entry(int slotIndex, DungeonParty.Member player) {
	}

	private static final Pattern HEAD_NAME = Pattern.compile("(?:\\[.+?] )?(\\w+)");
	private static volatile boolean testMode;
	private static final List<DungeonParty.Member> TEST_TEAM = new ArrayList<>();
	private static final Entry[] PLAYERS = new Entry[4];
	private static boolean clicked;

	private LeapMenu() {
	}

	public static Entry[] players() {
		return PLAYERS;
	}

	public static boolean testMode() {
		return testMode;
	}

	/**
	 * Test mode: any chest or container you open is shown as the leap menu with four
	 * made-up teammates on fixed slots, so the click and close can be compared with a
	 * click by hand anywhere, without a dungeon. Clicking a panel sends the same click
	 * a real leap would, on that slot.
	 */
	public static void setTestMode(boolean on) {
		testMode = on;
		TEST_TEAM.clear();
	}

	public static boolean isLeapMenu(Screen screen) {
		if (testMode && screen instanceof AbstractContainerScreen<?>
			&& !(screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)
			&& !(screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen)) {
			return true;
		}
		if (!(screen instanceof AbstractContainerScreen<?>) || !StrayConfig.get().leapMenuEnabled || !SkyblockLocation.inDungeon) {
			return false;
		}
		String title = screen.getTitle().getString().toLowerCase(Locale.ROOT);
		return title.contains("spirit leap") || title.contains("teleport to player");
	}

	public static boolean active() {
		return isLeapMenu(Minecraft.getInstance().gui.screen());
	}

	public static void updateLeapMenu() {
		java.util.Arrays.fill(PLAYERS, null);
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		AbstractContainerMenu menu = client.player.containerMenu;
		if (testMode) {
			fillTest(client, menu);
			return;
		}
		Map<String, Integer> loadedHeads = new HashMap<>();
		for (int i = 0; i < menu.slots.size() - 36; i++) {
			ItemStack stack = menu.slots.get(i).getItem();
			if (stack.isEmpty() || !stack.is(Items.PLAYER_HEAD)) {
				continue;
			}
			Matcher m = HEAD_NAME.matcher(stack.getHoverName().getString());
			if (!m.find()) {
				continue;
			}
			loadedHeads.put(m.group(1), i);
		}
		List<DungeonParty.Member> sorted = sortTeammates(DungeonParty.others());
		for (int i = 0; i < Math.min(4, sorted.size()); i++) {
			DungeonParty.Member player = sorted.get(i);
			Integer slotIndex = loadedHeads.get(player.name);
			if (slotIndex != null) {
				PLAYERS[i] = new Entry(slotIndex, player);
			}
		}
	}

	private static void fillTest(Minecraft client, AbstractContainerMenu menu) {
		if (TEST_TEAM.isEmpty()) {
			String[] names = {"TestMage", "TestArcher", "TestHealer", "TestTank"};
			DungeonParty.DungeonClass[] classes = {
				DungeonParty.DungeonClass.MAGE, DungeonParty.DungeonClass.ARCHER, DungeonParty.DungeonClass.HEALER, DungeonParty.DungeonClass.TANK
			};
			for (int i = 0; i < 4; i++) {
				TEST_TEAM.add(DungeonParty.testMember(names[i], classes[i], client.player.getSkin()));
			}
		}
		for (DungeonParty.Member member : TEST_TEAM) {
			member.entity = client.player;
		}
		int size = menu.slots.size() - 36;
		int[] slots = size >= 27 ? new int[] {11, 12, 14, 15} : new int[] {0, 1, 2, 3};
		for (int i = 0; i < 4; i++) {
			if (slots[i] < size) {
				PLAYERS[i] = new Entry(slots[i], TEST_TEAM.get(i));
			}
		}
	}

	public static List<DungeonParty.Member> sortTeammates(List<DungeonParty.Member> teammates) {
		List<DungeonParty.Member> out = new ArrayList<>(teammates);
		switch (StrayConfig.get().leapSorting) {
			case 0 -> out.sort(Comparator.<DungeonParty.Member>comparingInt(m -> m.clazz.ordinal()).thenComparing(m -> m.name));
			case 1 -> out.sort(Comparator.comparing(m -> m.name));
			case 2 -> {
				return odinSorting(out);
			}
			default -> {
			}
		}
		return out;
	}

	private static final Map<DungeonParty.DungeonClass, List<DungeonParty.DungeonClass>> ODIN = Map.of(
		DungeonParty.DungeonClass.ARCHER, List.of(DungeonParty.DungeonClass.MAGE, DungeonParty.DungeonClass.BERSERK, DungeonParty.DungeonClass.HEALER, DungeonParty.DungeonClass.TANK),
		DungeonParty.DungeonClass.MAGE, List.of(DungeonParty.DungeonClass.ARCHER, DungeonParty.DungeonClass.BERSERK, DungeonParty.DungeonClass.HEALER, DungeonParty.DungeonClass.TANK),
		DungeonParty.DungeonClass.BERSERK, List.of(DungeonParty.DungeonClass.ARCHER, DungeonParty.DungeonClass.MAGE, DungeonParty.DungeonClass.HEALER, DungeonParty.DungeonClass.TANK),
		DungeonParty.DungeonClass.HEALER, List.of(DungeonParty.DungeonClass.ARCHER, DungeonParty.DungeonClass.BERSERK, DungeonParty.DungeonClass.MAGE, DungeonParty.DungeonClass.TANK),
		DungeonParty.DungeonClass.TANK, List.of(DungeonParty.DungeonClass.ARCHER, DungeonParty.DungeonClass.BERSERK, DungeonParty.DungeonClass.HEALER, DungeonParty.DungeonClass.MAGE)
	);

	private static List<DungeonParty.Member> odinSorting(List<DungeonParty.Member> teammates) {
		DungeonParty.Member self = DungeonParty.self();
		List<DungeonParty.DungeonClass> needed = self == null ? null : ODIN.get(self.clazz);
		if (needed == null) {
			return teammates;
		}
		DungeonParty.Member[] quadrants = new DungeonParty.Member[4];
		List<DungeonParty.Member> secondRound = new ArrayList<>();
		for (DungeonParty.Member player : teammates) {
			int preferred = needed.indexOf(player.clazz);
			if (preferred != -1 && preferred < 4 && quadrants[preferred] == null) {
				quadrants[preferred] = player;
			} else {
				secondRound.add(player);
			}
		}
		for (int i = 0; i < quadrants.length; i++) {
			if (quadrants[i] == null && !secondRound.isEmpty()) {
				quadrants[i] = secondRound.remove(0);
			}
		}
		List<DungeonParty.Member> out = new ArrayList<>();
		for (DungeonParty.Member member : quadrants) {
			if (member != null) {
				out.add(member);
			}
		}
		out.addAll(secondRound);
		return out;
	}

	/** Mouse click on the leap menu. Returns true when the click is consumed. */
	public static boolean mouseClicked(int button) {
		if (button != InputConstants.MOUSE_BUTTON_LEFT && StrayConfig.get().leapLeftClickOnly) {
			return true;
		}
		Integer i = LeapMenuView.hoveredIndex();
		if (i == null) {
			return false;
		}
		triggerLeap(i);
		return true;
	}

	/** Key press on the leap menu. Returns true when the key is consumed. */
	public static boolean keyPressed(int key) {
		Minecraft client = Minecraft.getInstance();
		if (clicked) {
			clicked = false;
			if (client.player != null) {
				client.player.closeContainer();
			}
		}
		if (!StrayConfig.get().leapKeybinds) {
			return false;
		}
		int index = key - InputConstants.KEY_1;
		if (index < 0 || index >= PLAYERS.length) {
			return false;
		}
		triggerLeap(index);
		return true;
	}

	private static void triggerLeap(int index) {
		Entry entry = index >= 0 && index < PLAYERS.length ? PLAYERS[index] : null;
		if (entry == null) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.gameMode == null) {
			return;
		}
		if (entry.player().dead) {
			client.player.sendSystemMessage(Component.literal("§3LeapMenu >> §c" + entry.player().name + " is dead!"));
			return;
		}
		dev.stray.client.net.PacketDebug.note("§b[leap] §7panel " + (index + 1) + " → slot " + entry.slotIndex());
		client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.25f, 1f);
		int containerId = client.player.containerMenu.containerId;
		client.gameMode.handleContainerInput(containerId, entry.slotIndex(), 0, ContainerInput.PICKUP, client.player);
		if (!dev.stray.Edition.LEGIT) {
			client.player.closeContainer();
		} else {
			clicked = true;
		}
	}

	/** "You have teleported to X!" announces the leap in party chat when that is on. */
	public static void onChat(String plain) {
		if (plain == null || !plain.endsWith("!") || !plain.startsWith("You have teleported to ")) {
			return;
		}
		String name = plain.substring("You have teleported to ".length(), plain.length() - 1);
		StrayConfig config = StrayConfig.get();
		// Like the original, only with someone to tell: in a party of more than one.
		if (config.leapAnnounce && config.leapMenuEnabled && !DungeonParty.others().isEmpty()) {
			String message = (config.leapMessage == null ? "" : config.leapMessage).replace("{name}", name);
			Minecraft client = Minecraft.getInstance();
			if (!message.isBlank() && client.player != null) {
				client.player.connection.sendCommand("pc " + message);
			}
		}
	}

	/** Any container close resets the pending close, like the original. */
	public static void onContainerClosed() {
		clicked = false;
	}
}
