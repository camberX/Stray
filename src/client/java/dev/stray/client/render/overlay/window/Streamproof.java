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
	private static long lastSyncNs;
	private static long lastHash;
	/** The frame being recorded; text is measured with the overlay's own font. */
	private static final dev.stray.client.render.overlay.RecordingCanvas RECORD =
		new dev.stray.client.render.overlay.RecordingCanvas(text -> gl == null ? 0f : gl.measure(text));

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
			// Kept open (hidden / empty) for the rest of the session: reopening one every time
			// something wants it would flash a new window each time.
			boolean visible = window.sync(false);
			if (drewLast || visible) {
				window.begin();
				try {
					if (gl != null) {
						gl.begin(window.pixelWidth(), window.pixelHeight(), 1f);
						gl.end();
					}
				} finally {
					window.end();
				}
				drewLast = false;
				lastHash = 0L;
			}
			return;
		}
		// The overlay's own context, Wayland calls and a full-screen swap cost about a
		// millisecond. They used to run every game frame (1000 times a second at 1000 fps); now
		// at most once per monitor refresh, and only when what it shows changed.
		long now = System.nanoTime();
		if (now - lastSyncNs < frameInterval(client)) {
			return;
		}
		lastSyncNs = now;
		boolean show = window.sync(true);
		if (!show && !drewLast) {
			return;
		}
		int pw = window.pixelWidth();
		int ph = window.pixelHeight();
		int guiW = client.getWindow().getGuiScaledWidth();
		int guiH = client.getWindow().getGuiScaledHeight();
		float scale = pw / (float) Math.max(1, guiW);
		boolean content = show && !client.gui.hud.isHidden() && !menuOpen(client);
		OverlayGl measuring = gl;
		RECORD.reset();
		RECORD.mix(pw);
		RECORD.mix(ph);
		RECORD.mix(Float.floatToIntBits(scale));
		RECORD.mix(content ? 1 : 0);
		if (content) {
			float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
			StarMobOverlay.draw(RECORD, guiW, guiH, partial);
			DungeonMapHud.overlay(RECORD, guiW, guiH, partial);
		}
		if (measuring != null && RECORD.hash() == lastHash) {
			// Same picture as on screen: nothing to do.
			return;
		}
		window.begin();
		try {
			if (gl == null) {
				gl = new OverlayGl();
				gl.init();
			}
			gl.begin(pw, ph, scale);
			RECORD.replay(gl);
			gl.end();
			drewLast = show;
			// Text measured before the font existed: draw again next time with real widths.
			lastHash = measuring == null ? 0L : RECORD.hash();
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

	/** One monitor refresh in nanoseconds (240 Hz when the game can't tell). */
	private static long frameInterval(Minecraft client) {
		int hz = 0;
		if (client.getWindow() != null) {
			//? if >=26.3 {
			com.mojang.blaze3d.platform.VideoMode mode = client.getWindow().getActiveVideoMode();
			hz = mode == null ? 0 : Math.round(mode.getRefreshRate());
			//?} else {
			/*hz = client.getWindow().getRefreshRate();*///?}
		}
		return 1_000_000_000L / Math.max(30, hz <= 0 ? 240 : hz);
	}

	/** Any menu but chat: streamproof things hide (the overlay can't go under the game's GUI). */
	private static boolean menuOpen(Minecraft client) {
		net.minecraft.client.gui.screens.Screen screen = client.gui.screen();
		return screen != null && !(screen instanceof net.minecraft.client.gui.screens.ChatScreen);
	}

	public static boolean working() {
		return failure == null;
	}

	public static void close() {
		lastHash = 0L;
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
