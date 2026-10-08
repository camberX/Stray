package dev.stray.client.mining;

import dev.stray.client.config.StrayConfig;
import net.minecraft.client.Minecraft;

/**
 * Vanilla waits five ticks after a block breaks before the next one can
 * start. That pause stacks on top of ping. Skipping it does not change how
 * fast a block breaks and does not send any extra dig packets: the same
 * start and stop packets go out when vanilla would send them.
 */
public final class PinglessBreak {
	private PinglessBreak() {
	}

	public static boolean active() {
		if (!StrayConfig.get().pinglessBreak) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.gameMode == null) {
			return false;
		}
		if (client.player.getAbilities().instabuild || client.gameMode.isSpectator()) {
			return false;
		}
		return true;
	}
}
