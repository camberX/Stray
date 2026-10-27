package dev.stray.client.render;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.net.ConnectionPing;
import dev.stray.client.visual.NickHider;
import net.minecraft.client.Minecraft;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class HudStats {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

	private HudStats() {
	}

	public static int fps() {
		return Minecraft.getInstance().getFps();
	}

	public static int ping() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.isLocalServer()) {
			return -1;
		}
		return ConnectionPing.get();
	}

	public static String pingLabel() {
		if (Minecraft.getInstance().isLocalServer()) {
			return "singleplayer";
		}
		int ping = ping();
		return ping < 0 ? "…" : ping + " ms";
	}

	private static long timeSecond = Long.MIN_VALUE;
	private static String timeText = "";

	/** Formatted at most once a second; the HUD asks every frame. */
	public static String time() {
		long second = System.currentTimeMillis() / 1000L;
		if (second != timeSecond) {
			timeText = LocalTime.now().format(TIME);
			timeSecond = second;
		}
		return timeText;
	}

	public static String playerName() {
		if (StrayConfig.get().nickEnabled) {
			String nick = NickHider.plainNick();
			return nick.isBlank() ? "" : nick;
		}
		Minecraft client = Minecraft.getInstance();
		String name = client.getGameProfile().name();
		if (name == null || name.isBlank()) {
			return "Player";
		}
		return name;
	}
}
