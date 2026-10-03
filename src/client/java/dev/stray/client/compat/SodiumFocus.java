package dev.stray.client.compat;

import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;

public final class SodiumFocus {
	private SodiumFocus() {
	}

	public static void rebuild() {
		SodiumWorldRenderer sodium = SodiumWorldRenderer.instanceNullable();
		if (sodium != null) {
			sodium.reload();
		}
	}
}
