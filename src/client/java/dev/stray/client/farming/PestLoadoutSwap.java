package dev.stray.client.farming;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.ui.LoadoutSwap;
import net.minecraft.client.Minecraft;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Pest-farming loadout cycle. Swap B equips when the cooldown reaches the
 * configured seconds left. After pests spawn, TriSwap waits 1–2 seconds and
 * equips Swap C. Swap A comes back once every pest is dead.
 */
public final class PestLoadoutSwap {
	private enum Phase {
		FARM,
		WAIT_SPAWN,
		DELAY,
		WAIT_CLEAR
	}

	private static Phase phase = Phase.FARM;
	private static boolean hold;
	private static long waitSince;
	private static long delayUntil;

	private PestLoadoutSwap() {
	}

	public static void reset() {
		phase = Phase.FARM;
		hold = false;
		waitSince = 0L;
		delayUntil = 0L;
		LoadoutSwap.cancelQueue();
	}

	public static void tick(Minecraft client) {
		StrayConfig config = StrayConfig.get();
		if (!config.pestLoadoutSwap || client == null || client.player == null) {
			if (phase != Phase.FARM || hold) {
				reset();
			}
			return;
		}
		long now = System.currentTimeMillis();
		int lead = StrayConfig.clamp(config.pestLoadoutLead, 0, 30);
		int left = PestCooldown.secondsLeft();
		switch (phase) {
			case FARM -> {
				if (left < 0) {
					return;
				}
				if (hold) {
					if (left > lead) {
						hold = false;
					}
					return;
				}
				if (left <= lead && LoadoutSwap.queue(slot(config.loadoutSwapSlotB))) {
					phase = Phase.WAIT_SPAWN;
					waitSince = now;
				}
			}
			case WAIT_SPAWN -> {
				if (pestsPresent()) {
					if (config.loadoutSwapTri) {
						delayUntil = now + 1000L + ThreadLocalRandom.current().nextInt(1001);
						phase = Phase.DELAY;
					} else {
						phase = Phase.WAIT_CLEAR;
					}
					return;
				}
				if (now - waitSince > (lead + 20L) * 1000L && LoadoutSwap.queue(slot(config.loadoutSwapSlotA))) {
					hold = true;
					phase = Phase.FARM;
				}
			}
			case DELAY -> {
				if (!config.loadoutSwapTri) {
					phase = Phase.WAIT_CLEAR;
					return;
				}
				if (pestsClear()) {
					if (LoadoutSwap.queue(slot(config.loadoutSwapSlotA))) {
						hold = true;
						phase = Phase.FARM;
					}
					return;
				}
				if (now >= delayUntil && LoadoutSwap.queue(slot(config.loadoutSwapSlotC))) {
					phase = Phase.WAIT_CLEAR;
				}
			}
			case WAIT_CLEAR -> {
				if (pestsClear() && LoadoutSwap.queue(slot(config.loadoutSwapSlotA))) {
					hold = true;
					phase = Phase.FARM;
				}
			}
		}
	}

	private static boolean pestsPresent() {
		if (PestCooldown.alive() > 0) {
			return true;
		}
		return StrayConfig.get().pestEspEnabled && !PestEsp.snapshot().isEmpty();
	}

	private static boolean pestsClear() {
		if (PestCooldown.alive() != 0) {
			return false;
		}
		return !StrayConfig.get().pestEspEnabled || PestEsp.snapshot().isEmpty();
	}

	private static int slot(int configured) {
		return StrayConfig.clampLoadoutSwapSlot(configured) - 1;
	}
}
