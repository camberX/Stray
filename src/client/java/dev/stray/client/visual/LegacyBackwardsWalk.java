package dev.stray.client.visual;

import dev.stray.client.config.StrayConfig;

/**
 * 1.11-style backwards walk from Animatium {@code rotateBackwardsWalking}:
 * the body turns sideways when walking backwards instead of staying facing
 * the camera.
 */
public final class LegacyBackwardsWalk {
	private LegacyBackwardsWalk() {
	}

	public static boolean enabled() {
		return StrayConfig.get().legacyBackwardsWalk;
	}
}
