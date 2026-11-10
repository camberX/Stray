package dev.stray.client.render;

import dev.stray.client.config.StrayConfig;
import net.minecraft.client.Minecraft;

/**
 * Smooth hotbar: the vanilla hotbar's selection box glides from the old slot to the new one
 * instead of snapping. It moves at a speed proportional to the distance left, so a long jump
 * (1 to 9) is fast and a short step gentle, and every switch settles in about the same time.
 * Only the selection box moves; the hotbar, hearts and XP bar stay vanilla.
 */
public final class SmoothHotbar {
	/** Vanilla hotbar slot pitch in GUI pixels. */
	private static final float SLOT = 20f;

	private static float shown = -1f;
	private static long lastNs;

	private SmoothHotbar() {
	}

	/** Pixels to shift the selection box drawn for {@code selected} this frame. */
	public static float offset(int selected) {
		if (!StrayConfig.get().smoothHotbar || Minecraft.getInstance().player == null) {
			shown = -1f;
			return 0f;
		}
		long now = System.nanoTime();
		if (shown < 0f) {
			shown = selected;
			lastNs = now;
			return 0f;
		}
		float dt = Math.min(0.1f, (now - lastNs) / 1_000_000_000f);
		lastNs = now;
		// Exponential approach: speed is proportional to the distance still to go.
		float rate = Math.max(1, StrayConfig.get().smoothHotbarSpeed);
		shown += (selected - shown) * (1f - (float) Math.exp(-rate * dt));
		if (Math.abs(selected - shown) < 0.01f) {
			shown = selected;
		}
		return (shown - selected) * SLOT;
	}
}
