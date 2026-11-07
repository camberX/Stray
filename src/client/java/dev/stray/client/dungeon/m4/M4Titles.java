package dev.stray.client.dungeon.m4;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.regex.Pattern;

/** BirdAddon's Titles: own titles for bear and bow moments, optionally hiding Hypixel's. */
public final class M4Titles {
	private static final Pattern BOW_MISS = Pattern.compile("^\\[CROWD] [^:]+: (Yeah!!! Keep dodging them Thorn!|[A-Za-z0-9_]+ missed the shot! No way!! Hahaha|My goodness, [A-Za-z0-9_]+ really can't aim!!|Alright those humans are a joke, missing easy shots like that\\.\\.\\.|[A-Za-z0-9_]+ has no thumbs!)$");
	private static final String BOW_PICKUP = "You picked up the Spirit Bow! Use it to attack Thorn!";

	private static int pickupTime = -10_000;
	/** Set while we show our own title, so hiding Hypixel's titles lets it through. */
	private static boolean own;

	private M4Titles() {
	}

	private static boolean on() {
		return M4.config().titles;
	}

	public static boolean showingOwn() {
		return own;
	}

	/** False while Hypixel's titles should be hidden. */
	public static boolean allowServer() {
		return allowServerTitle();
	}

	static boolean allowServerTitle() {
		return own || !on() || !M4.config().titlesHideDefault || !M4.inBoss();
	}

	static void title(String text) {
		Minecraft client = Minecraft.getInstance();
		if (client.gui == null || text == null || text.isEmpty()) {
			return;
		}
		own = true;
		try {
			client.gui.hud.setTimes(0, Math.max(1, M4.config().titleDurationTicks), 5);
			client.gui.hud.setSubtitle(Component.empty());
			client.gui.hud.setTitle(Component.literal(text));
		} finally {
			own = false;
		}
	}

	static void onBearSpawnStart() {
		if (on() && M4.config().titleBearTimer) {
			title(M4.config().bearTimerText);
		}
	}

	static void onBearSpawn() {
		if (on() && M4.config().titleBearSpawned) {
			title(M4.config().bearSpawnedTitleText);
		}
	}

	static void onBearKill() {
		if (on() && M4.config().titleBearKilled) {
			title(M4.config().bearKilledText);
		}
	}

	static void serverTick(Minecraft client) {
		if (!on() || !M4.config().titleDisintegrate || client.player == null || !M4.inBoss()) {
			return;
		}
		// The bow disintegrates 400 ticks after pickup; at 280 there are 6 seconds left to shoot.
		if (M4.timer - pickupTime > 280 && holdsSpiritBow(client.player.getInventory())) {
			title(M4.config().disintegrateText);
		}
	}

	static void onChat(String message) {
		if (!M4.inBoss()) {
			return;
		}
		M4Config config = M4.config();
		if (message.equals(BOW_PICKUP)) {
			pickupTime = M4.timer;
			if (on()) {
				boolean tank = M4.myClass() == dev.stray.client.dungeon.DungeonParty.DungeonClass.TANK;
				if (tank && config.titleTankPickup) {
					title(config.tankPickupText);
				} else if (!tank && config.titlePickup) {
					title(config.pickupText);
				}
			}
		}
		if (on() && config.titleMiss && BOW_MISS.matcher(message).matches()) {
			title(config.missText);
		}
	}

	static void onWorldLoad() {
		pickupTime = -10_000;
	}

	private static boolean holdsSpiritBow(Inventory inventory) {
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && stack.getHoverName().getString().contains("Spirit Bow")) {
				return true;
			}
		}
		return false;
	}
}
