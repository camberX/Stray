package dev.stray.client.mining;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.regex.Pattern;

/**
 * Worms announce themselves in chat, then spawn 30 seconds of server ticks later.
 */
public final class ScathaTimer {
	public static final int SECONDS = 30;
	private static final int TICKS = SECONDS * 20;
	private static final Pattern APPROACH = Pattern.compile("(?i)you hear the sound of something approaching");
	private static long endTick = Long.MIN_VALUE;

	private ScathaTimer() {
	}

	public static void onMessage(Component message, boolean overlay) {
		if (overlay || message == null) {
			return;
		}
		String plain = ChatFormatting.stripFormatting(message.getString());
		if (plain == null || plain.isEmpty() || !APPROACH.matcher(plain).find()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return;
		}
		endTick = client.level.getGameTime() + TICKS;
	}

	public static void reset() {
		endTick = Long.MIN_VALUE;
	}

	public static boolean active() {
		return ticksLeft() > 0;
	}

	public static int ticksLeft() {
		if (endTick == Long.MIN_VALUE) {
			return 0;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return 0;
		}
		long left = endTick - client.level.getGameTime();
		if (left <= 0L) {
			return 0;
		}
		return (int) Math.min(left, Integer.MAX_VALUE);
	}

	public static int secondsLeft() {
		int ticks = ticksLeft();
		if (ticks <= 0) {
			return 0;
		}
		return (ticks + 19) / 20;
	}

	public static String label() {
		int seconds = active() ? secondsLeft() : SECONDS;
		return "Scatha  " + seconds + "s";
	}
}
