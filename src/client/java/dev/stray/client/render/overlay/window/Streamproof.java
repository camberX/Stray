package dev.stray.client.render.overlay.window;

import dev.stray.client.dungeon.map.DungeonMapHud;
import dev.stray.client.render.StarMobOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Streamproof drawing: things that should not show up when the game window is captured
 * (OBS window capture, gpu-screen-recorder, Discord) are drawn into a separate transparent,
 * click-through window kept exactly over the game. Window capture only records the game's
 * own window, so they stay off the recording. On Windows the overlay is also excluded from
 * display capture; on Hyprland it is hidden from screen sharing too.
 */
public final class Streamproof {
	private static final Logger LOGGER = LoggerFactory.getLogger("Stray-Streamproof");
	private static OverlayWindow window;
	private static OverlayGl gl;
	private static String failure;
	private static boolean warned;
	private static boolean drewLast;
	private static int idle;

	private Streamproof() {
	}

	/** Called on the render thread right after the game showed its frame. */
	private static boolean announced;

	public static void frame() {
		if (!announced) {
			announced = true;
			LOGGER.info("Streamproof overlay hook active");
		}
		Minecraft client = Minecraft.getInstance();
		boolean wanted = client.level != null && (DungeonMapHud.wantsOverlay() || StarMobOverlay.wants());
		if (failure != null) {
			if (wanted && !warned && client.player != null) {
				warned = true;
				client.player.sendSystemMessage(Component.literal("§bStray §7streamproof overlay is off: §c" + failure + "§7. Streamproof things are not drawn."));
			}
			return;
		}
		if (window == null) {
			if (!wanted) {
				return;
			}
			try {
				OverlayWindow made = OverlayWindow.make();
				String error = made.create();
				if (error != null) {
					failure = error;
					LOGGER.warn("Streamproof overlay unavailable: {}", error);
					return;
				}
				window = made;
				gl = null;
			} catch (Throwable t) {
				failure = t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
				LOGGER.warn("Streamproof overlay failed", t);
				return;
			}
		}
		if (!wanted) {
			// Keep the window around for a while (opening one is slow), then close it.
			window.sync(false);
			if (++idle > 1200) {
				close();
			}
			return;
		}
		idle = 0;
		boolean show = window.sync(true);
		if (!show && !drewLast) {
			return;
		}
		window.begin();
		try {
			if (gl == null) {
				gl = new OverlayGl();
				gl.init();
			}
			int pw = window.pixelWidth();
			int ph = window.pixelHeight();
			int guiW = client.getWindow().getGuiScaledWidth();
			int guiH = client.getWindow().getGuiScaledHeight();
			float scale = pw / (float) Math.max(1, guiW);
			gl.begin(pw, ph, scale);
			if (show && !client.gui.hud.isHidden()) {
				float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
				StarMobOverlay.draw(gl, guiW, guiH, partial);
				DungeonMapHud.overlay(gl, guiW, guiH, partial);
			}
			gl.end();
			drewLast = show;
		} catch (Throwable t) {
			failure = "drawing failed (" + t.getClass().getSimpleName() + ")";
			LOGGER.warn("Streamproof overlay draw failed", t);
		} finally {
			window.end();
		}
		if (failure != null) {
			close();
		}
	}

	public static boolean working() {
		return failure == null;
	}

	public static void close() {
		if (window != null) {
			window.destroy();
			window = null;
		}
		gl = null;
	}

	/** Lets the debug command try again after a failure. */
	public static String status() {
		if (failure != null) {
			return "off: " + failure;
		}
		return window == null ? "not open" : window.describe();
	}

	public static void retry() {
		close();
		failure = null;
		warned = false;
	}

	/** 26.3: events SDL reports for the overlay window must not reach the game's window code. */
	public static boolean ownsWindow(long sdlWindow) {
		//? if >=26.3 {
		return sdlWindow != 0L && sdlWindow == SdlOverlayWindow.handle;
		//?} else {
		/*return false;*///?}
	}
}
