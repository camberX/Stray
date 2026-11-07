package dev.stray.client.render;

import dev.stray.Stray;
import net.minecraft.client.Minecraft;

/**
 * Whether Stray's own shaders can be loaded yet. Mods like Remove Reloading Screen draw
 * menus before the first resource load has finished, while only vanilla's files are
 * there; a Stray pipeline compiled then cannot find its shader and the game crashes. Until
 * the files are present, the drawing code uses its plain fallbacks instead.
 */
public final class StrayShaders {
	private static final String[] PROBES = {
		"shaders/core/gui_round_rect.vsh",
		"shaders/post/title_marble.fsh"
	};
	private static volatile boolean ready;
	private static long nextCheckNs;

	private StrayShaders() {
	}

	public static boolean ready() {
		if (ready) {
			return true;
		}
		Minecraft client = Minecraft.getInstance();
		// While the loading overlay is up the shader cache still reads the old (vanilla-only)
		// resources; compiling then only logs "Couldn't preload shader" errors. Not throttled, so
		// the first frame after the overlay already checks.
		if (client == null || client.getResourceManager() == null || client.gui.overlay() != null) {
			return false;
		}
		long now = System.nanoTime();
		if (now < nextCheckNs) {
			return false;
		}
		nextCheckNs = now + 500_000_000L;
		try {
			for (String path : PROBES) {
				if (client.getResourceManager().getResource(Stray.id(path)).isEmpty()) {
					return false;
				}
			}
		} catch (RuntimeException e) {
			return false;
		}
		// The files can show up before the reload has rebuilt the shader cache (most of all with
		// Remove Reloading Screen), so also check the shader really compiles now.
		if (!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread() || !RoundRect.compiles()) {
			return false;
		}
		ready = true;
		return true;
	}
}
