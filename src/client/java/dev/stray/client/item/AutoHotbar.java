package dev.stray.client.item;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.stray.Stray;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.net.ConnectionPing;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.HudLayout;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Auto hotbar, after NoammAddons' AutoHotbar (CC0; removed upstream in April 2026). A chat
 * message you pick (the trigger) moves the items you mapped into their hotbar slots, one
 * swap at a time with a delay. Unlike the original it only swaps while your inventory is
 * open: otherwise it asks you to open it, or opens and closes it for you if that option is on.
 *
 * <p>The setup (triggers and item → slot rules) is kept in {@code config/stray/autohotbar.json},
 * in NoammAddons' format, and NoammAddons' {@code autoHotbar.json} is picked up the first time.
 * Not in Stray Legit.
 */
public final class AutoHotbar {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	/** Ticks of standing still before the first swap. */
	private static final int STILL_TICKS = 10;
	/** How long to wait after the last swap before closing an inventory we opened. */
	private static final long CLOSE_AFTER_MS = 250L;

	public static final class Rule {
		public String id;
		public int hotbarSlot;

		public Rule(String id, int hotbarSlot) {
			this.id = id;
			this.hotbarSlot = hotbarSlot;
		}
	}

	public static final class Setup {
		/** Trigger name → exact chat message. */
		public Map<String, String> triggers = new LinkedHashMap<>();
		/** Trigger name → rules. */
		public Map<String, List<Rule>> rules = new LinkedHashMap<>();
		public Set<String> disabled = new LinkedHashSet<>();
	}

	public enum State { IDLE, OPEN_INVENTORY, STOP_MOVING, SWAPPING }

	private static Setup setup;
	private static final ArrayDeque<Rule> queue = new ArrayDeque<>();
	private static final Set<String> firedThisWorld = new HashSet<>();
	private static long nextSwapAt;
	private static int stillTicks;
	private static boolean openedByUs;
	private static long doneAt;
	private static State state = State.IDLE;

	private AutoHotbar() {
	}

	// ---------------------------------------------------------------- setup

	public static Setup setup() {
		if (setup == null) {
			load();
		}
		return setup;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("stray").resolve("autohotbar.json");
	}

	private static void load() {
		setup = new Setup();
		Path path = file();
		if (!Files.isRegularFile(path)) {
			path = FabricLoader.getInstance().getConfigDir().resolve("NoammAddons").resolve("autoHotbar.json");
		}
		if (!Files.isRegularFile(path)) {
			return;
		}
		try {
			Setup read = GSON.fromJson(Files.readString(path), Setup.class);
			if (read != null) {
				setup.triggers = read.triggers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(read.triggers);
				setup.rules = new LinkedHashMap<>();
				if (read.rules != null) {
					read.rules.forEach((key, list) -> {
						List<Rule> kept = new ArrayList<>();
						if (list != null) {
							for (Rule rule : list) {
								if (rule != null && rule.id != null && rule.hotbarSlot >= 0 && rule.hotbarSlot < 9) {
									kept.add(rule);
								}
							}
						}
						setup.rules.put(key, kept);
					});
				}
				setup.disabled = read.disabled == null ? new LinkedHashSet<>() : new LinkedHashSet<>(read.disabled);
			}
		} catch (Exception exception) {
			Stray.LOGGER.warn("Could not read the auto hotbar setup", exception);
		}
	}

	public static void save() {
		if (setup == null) {
			return;
		}
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(setup));
		} catch (IOException exception) {
			Stray.LOGGER.warn("Could not save the auto hotbar setup", exception);
		}
	}

	/** UUID, else SkyBlock ID, else the item name: how an item is recognised in a rule. */
	public static String idOf(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "";
		}
		String uuid = ItemIds.uuidOf(stack);
		if (uuid != null && !uuid.isBlank()) {
			return uuid;
		}
		String id = ItemIds.skyblockId(stack);
		if (id != null && !id.isBlank()) {
			return id;
		}
		return stack.getHoverName().getString();
	}

	// ---------------------------------------------------------------- input

	public static void onChat(String plain) {
		if (!StrayConfig.get().autoHotbar || plain == null) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}
		String trigger = null;
		for (Map.Entry<String, String> entry : setup().triggers.entrySet()) {
			if (plain.equals(entry.getValue())) {
				trigger = entry.getKey();
				break;
			}
		}
		if (trigger == null || setup.disabled.contains(trigger) || firedThisWorld.contains(trigger)) {
			return;
		}
		List<Rule> rules = setup.rules.get(trigger);
		if (rules == null || rules.isEmpty()) {
			return;
		}
		firedThisWorld.add(trigger);
		queue.clear();
		for (Rule rule : rules) {
			int at = findSlot(player.inventoryMenu, rule.id);
			if (at >= 0 && at != rule.hotbarSlot + 36) {
				queue.add(rule);
			}
		}
		if (!queue.isEmpty()) {
			nextSwapAt = System.currentTimeMillis() + StrayConfig.get().autoHotbarStopDelay;
			stillTicks = 0;
			doneAt = 0L;
		}
	}

	public static void onWorldChange() {
		queue.clear();
		firedThisWorld.clear();
		stillTicks = 0;
		openedByUs = false;
		doneAt = 0L;
		state = State.IDLE;
	}

	public static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		StrayConfig config = StrayConfig.get();
		long now = System.currentTimeMillis();
		if (player == null || !config.autoHotbar) {
			queue.clear();
			state = State.IDLE;
			return;
		}
		if (queue.isEmpty()) {
			state = State.IDLE;
			// Close the inventory we opened, a moment after the last swap.
			if (openedByUs) {
				if (doneAt == 0L) {
					doneAt = now;
				} else if (now - doneAt >= CLOSE_AFTER_MS) {
					openedByUs = false;
					doneAt = 0L;
					if (client.gui.screen() instanceof InventoryScreen) {
						player.closeContainer();
					}
				}
			}
			return;
		}
		if (player.getDeltaMovement().horizontalDistanceSqr() > 0.001) {
			stillTicks = 0;
			nextSwapAt = Math.max(nextSwapAt, now + 250L);
			state = State.STOP_MOVING;
			return;
		}
		stillTicks++;
		if (stillTicks < STILL_TICKS) {
			state = State.STOP_MOVING;
			return;
		}
		if (!(client.gui.screen() instanceof InventoryScreen)) {
			if (client.gui.screen() == null && config.autoHotbarAutoInventory) {
				client.gui.setScreen(new InventoryScreen(player));
				openedByUs = true;
				nextSwapAt = Math.max(nextSwapAt, now + config.autoHotbarDelay);
				state = State.SWAPPING;
			} else {
				// Another screen (or the option is off): wait until the inventory is open.
				openedByUs = false;
				state = State.OPEN_INVENTORY;
			}
			return;
		}
		state = State.SWAPPING;
		if (now < nextSwapAt) {
			return;
		}
		int ping = ConnectionPing.get();
		if (ping > 500) {
			return;
		}
		Rule rule = queue.removeFirst();
		AbstractContainerMenu menu = player.containerMenu;
		int from = findSlot(menu, rule.id);
		if (from >= 0 && from != rule.hotbarSlot + 36 && client.gameMode != null) {
			client.gameMode.handleContainerInput(menu.containerId, from, rule.hotbarSlot, ContainerInput.SWAP, player);
		}
		nextSwapAt = now + delay(config.autoHotbarDelay, config.autoHotbarDelay + config.autoHotbarJitter);
		if (queue.isEmpty()) {
			doneAt = 0L;
		}
	}

	/** Slot index in the player's inventory menu holding the item (crafting slots skipped). */
	private static int findSlot(AbstractContainerMenu menu, String id) {
		for (Slot slot : menu.slots) {
			if (slot.index < 5) {
				continue;
			}
			if (id.equals(idOf(slot.getItem()))) {
				return slot.index;
			}
		}
		return -1;
	}

	/** Bell-curve delay between {@code min} and {@code max}, as in NoammAddons. */
	private static long delay(int min, int max) {
		if (max <= min) {
			return min;
		}
		double gaussian = ThreadLocalRandom.current().nextGaussian();
		double mean = min + (max - min) / 2.0;
		double value = mean + gaussian * (max - min) / 6.0;
		return Math.round(Math.max(min, Math.min(max, value)));
	}

	public static void init() {
		net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof InventoryScreen) {
				net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterExtract(screen).register(
					(shown, graphics, mouseX, mouseY, delta) -> extractOverInventory(graphics, shown.width, shown.height));
			}
		});
	}

	// ---------------------------------------------------------------- HUD

	public static State state() {
		return state;
	}

	private static String statusText(boolean example) {
		if (example) {
			return "§bSwapping §f3 left";
		}
		return switch (state) {
			case OPEN_INVENTORY -> "§eOpen your inventory §7(" + queue.size() + " to swap)";
			case STOP_MOVING -> "§cStop moving!";
			case SWAPPING -> "§bSwapping §f" + queue.size() + " left";
			default -> "";
		};
	}

	public static boolean hudShown() {
		StrayConfig config = StrayConfig.get();
		return config.autoHotbar && config.autoHotbarHud;
	}

	public static float width(Font font) {
		return font.width(statusText(HudLayout.editorOpen() || state == State.IDLE));
	}

	public static float height() {
		return 9f;
	}

	public static void extract(GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		if (!hudShown() || client.player == null || client.gui.hud.isHidden()) {
			return;
		}
		boolean example = HudLayout.editorOpen();
		String text = statusText(example);
		if (text.isEmpty()) {
			return;
		}
		Font font = client.font;
		HudLayout.apply(graphics, font, HudLayout.Id.AUTO_HOTBAR, () -> GuiDraw.text(graphics, font, Component.literal(text), 0f, 0f, 0xFFFFFFFF, true));
	}

	/** Above the inventory while swapping, where the HUD is covered. */
	public static void extractOverInventory(GuiGraphicsExtractor graphics, int width, int height) {
		if (!hudShown() || state != State.SWAPPING) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		String text = statusText(false);
		GuiDraw.text(graphics, font, Component.literal(text), (width - font.width(text)) * 0.5f, height * 0.5f - 100f, 0xFFFFFFFF, true);
	}
}
