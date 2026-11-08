package dev.stray.client.farming;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.util.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Greenhouse growth stage timer, like SkyHanni's Growth Cycle Timer. Opening the Crop
 * Diagnostics menu in the Greenhouse reads "Next Stage: 1h 40m 20s" from its status item
 * (slot 20) and counts down to it. When the stage is ready it pings once (optional), and the
 * overlay says so until an hour after.
 */
public final class GreenhouseGrowth {
	private static final String MENU = "Crop Diagnostics";
	private static final int STATUS_SLOT = 20;
	private static final Pattern NEXT_STAGE = Pattern.compile("Next Stage: ((?:\\d\\d?[hms] ?)+)");
	private static final Pattern PART = Pattern.compile("(\\d+)([hms])");
	private static final long FORGET_AFTER_MS = 60L * 60L * 1000L;

	private static int ticks;

	private GreenhouseGrowth() {
	}

	public static void tick(Minecraft client) {
		StrayConfig config = StrayConfig.get();
		if (!config.greenhouseGrowthHud || client.player == null) {
			return;
		}
		if (++ticks % 10 == 0) {
			read(client, config);
		}
		long at = config.greenhouseNextStageAt;
		if (at > 0L && !config.greenhouseStageNotified && System.currentTimeMillis() >= at) {
			config.greenhouseStageNotified = true;
			config.save();
			if (config.greenhouseGrowthAlert) {
				client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f));
				client.gui.hud.getChat().addClientSystemMessage(Component.literal("§bStray §8» §aThe Greenhouse growth stage is ready."));
			}
		}
	}

	/** In the Crop Diagnostics menu, takes the time left from the status item. */
	private static void read(Minecraft client, StrayConfig config) {
		if (!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
			return;
		}
		if (!MENU.equals(Text.strip(screen.getTitle().getString()).trim())) {
			return;
		}
		AbstractContainerMenu menu = screen.getMenu();
		if (menu.slots.size() <= STATUS_SLOT) {
			return;
		}
		ItemStack status = menu.slots.get(STATUS_SLOT).getItem();
		ItemLore lore = status.get(DataComponents.LORE);
		if (lore == null) {
			return;
		}
		for (Component line : lore.lines()) {
			Matcher matcher = NEXT_STAGE.matcher(Text.strip(line.getString()));
			if (matcher.find()) {
				long ms = duration(matcher.group(1));
				if (ms > 0L) {
					long at = System.currentTimeMillis() + ms;
					// The menu shows whole seconds; only save when it moved by more than that.
					if (Math.abs(at - config.greenhouseNextStageAt) > 1500L || config.greenhouseStageNotified) {
						config.greenhouseNextStageAt = at;
						config.greenhouseStageNotified = false;
						config.save();
					}
				}
				return;
			}
		}
	}

	private static long duration(String text) {
		long ms = 0L;
		Matcher part = PART.matcher(text);
		while (part.find()) {
			long value = Long.parseLong(part.group(1));
			ms += switch (part.group(2)) {
				case "h" -> value * 3_600_000L;
				case "m" -> value * 60_000L;
				default -> value * 1_000L;
			};
		}
		return ms;
	}

	// ---------------------------------------------------------------- overlay

	/** Shown when a time is known, not more than an hour overdue, and (optionally) only when ready. */
	public static boolean shown() {
		StrayConfig config = StrayConfig.get();
		long at = config.greenhouseNextStageAt;
		if (at <= 0L) {
			return false;
		}
		long now = System.currentTimeMillis();
		if (now - at > FORGET_AFTER_MS) {
			return false;
		}
		return now >= at || !config.greenhouseGrowthOnlyReady;
	}

	public static boolean ready() {
		long at = StrayConfig.get().greenhouseNextStageAt;
		return at > 0L && System.currentTimeMillis() >= at;
	}

	public static String label() {
		long at = StrayConfig.get().greenhouseNextStageAt;
		if (at <= 0L) {
			return "Growth stage 12m 30s";
		}
		long left = at - System.currentTimeMillis();
		if (left <= 0L) {
			return "Growth stage ready!";
		}
		return "Growth stage " + format(left);
	}

	/** Two units at most: 1h 40m, 12m 30s, 45s. */
	private static String format(long ms) {
		long seconds = (ms + 999L) / 1000L;
		long hours = seconds / 3600L;
		long minutes = seconds % 3600L / 60L;
		long secs = seconds % 60L;
		if (hours > 0) {
			return hours + "h " + minutes + "m";
		}
		if (minutes > 0) {
			return minutes + "m " + secs + "s";
		}
		return secs + "s";
	}
}
