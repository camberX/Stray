package dev.stray.client.mining;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.item.ItemIds;
import dev.stray.client.location.SkyblockLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Title + sound when an Automaton part or a Divan scavenged tool lands in
 * your inventory, and a louder one when you hold the full set. Works off
 * inventory counts so it fires for chests, drops, and trades alike.
 */
public final class NucleusAlerts {
	private static final Map<String, String> PARTS = new LinkedHashMap<>();
	private static final Map<String, String> TOOLS = new LinkedHashMap<>();

	static {
		PARTS.put("CONTROL_SWITCH", "Control Switch");
		PARTS.put("ELECTRON_TRANSMITTER", "Electron Transmitter");
		PARTS.put("FTX_3070", "FTX 3070");
		PARTS.put("ROBOTRON_REFLECTOR", "Robotron Reflector");
		PARTS.put("SUPERLITE_MOTOR", "Superlite Motor");
		PARTS.put("SYNTHETIC_HEART", "Synthetic Heart");
		TOOLS.put("DWARVEN_LAPIS_SWORD", "Lapis Sword");
		TOOLS.put("DWARVEN_DIAMOND_AXE", "Diamond Axe");
		TOOLS.put("DWARVEN_EMERALD_HAMMER", "Emerald Hammer");
		TOOLS.put("DWARVEN_GOLD_HAMMER", "Gold Hammer");
	}

	private static final Map<String, Integer> lastCounts = new HashMap<>();
	private static boolean primed;
	private static boolean allPartsShown;
	private static boolean allToolsShown;
	private static int settle;

	private NucleusAlerts() {
	}

	public static void tick(Minecraft client) {
		StrayConfig config = StrayConfig.get();
		if (!config.nucleusAlertParts && !config.nucleusAlertTools) {
			primed = false;
			return;
		}
		if (client == null || client.player == null || client.level == null || !SkyblockLocation.inSkyblock) {
			primed = false;
			return;
		}
		if (client.player.tickCount % 4 != 0) {
			return;
		}
		Map<String, Integer> counts = count(client.player);
		if (!primed) {
			// First pass after joining: remember what we have, do not alert.
			lastCounts.clear();
			lastCounts.putAll(counts);
			primed = true;
			settle = 10;
			allPartsShown = complete(counts, PARTS);
			allToolsShown = complete(counts, TOOLS);
			return;
		}
		if (settle > 0) {
			settle--;
			lastCounts.clear();
			lastCounts.putAll(counts);
			return;
		}
		if (config.nucleusAlertParts) {
			check(client, counts, PARTS, "ROBOT PART", 0xFF55FFFF);
			boolean all = complete(counts, PARTS);
			if (all && !allPartsShown) {
				title(client, "ALL ROBOT PARTS", "Professor Robot is waiting", 0xFF55FFFF);
				client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f));
			}
			allPartsShown = all;
		}
		if (config.nucleusAlertTools) {
			check(client, counts, TOOLS, "SCAVENGED", 0xFF55FF55);
			boolean all = complete(counts, TOOLS);
			if (all && !allToolsShown) {
				title(client, "ALL TOOLS", "Take them to the Keepers", 0xFF55FF55);
				client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f));
			}
			allToolsShown = all;
		}
		lastCounts.clear();
		lastCounts.putAll(counts);
	}

	public static void reset() {
		primed = false;
		lastCounts.clear();
		allPartsShown = false;
		allToolsShown = false;
	}

	public static int partsOwned() {
		return owned(PARTS);
	}

	public static int toolsOwned() {
		return owned(TOOLS);
	}

	private static int owned(Map<String, String> set) {
		int n = 0;
		for (String id : set.keySet()) {
			if (lastCounts.getOrDefault(id, 0) > 0) {
				n++;
			}
		}
		return n;
	}

	private static void check(Minecraft client, Map<String, Integer> counts, Map<String, String> set, String heading, int color) {
		for (Map.Entry<String, String> entry : set.entrySet()) {
			int now = counts.getOrDefault(entry.getKey(), 0);
			int before = lastCounts.getOrDefault(entry.getKey(), 0);
			if (now > before) {
				title(client, heading, entry.getValue().toUpperCase(Locale.ROOT), color);
				client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.4f, 1.0f));
			}
		}
	}

	private static boolean complete(Map<String, Integer> counts, Map<String, String> set) {
		for (String id : set.keySet()) {
			if (counts.getOrDefault(id, 0) <= 0) {
				return false;
			}
		}
		return true;
	}

	private static Map<String, Integer> count(LocalPlayer player) {
		Map<String, Integer> out = new HashMap<>();
		Inventory inventory = player.getInventory();
		int size = inventory.getContainerSize();
		for (int i = 0; i < size; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			String id = ItemIds.skyblockId(stack);
			if (id == null) {
				continue;
			}
			if (PARTS.containsKey(id) || TOOLS.containsKey(id)) {
				out.merge(id, stack.getCount(), Integer::sum);
			}
		}
		return out;
	}

	private static void title(Minecraft client, String main, String sub, int color) {
		Gui gui = client.gui;
		if (gui == null) {
			return;
		}
		gui.setTimes(5, 45, 10);
		gui.setTitle(Component.literal(main).withColor(color & 0xFFFFFF));
		gui.setSubtitle(sub == null || sub.isEmpty() ? Component.empty() : Component.literal(sub));
	}
}
