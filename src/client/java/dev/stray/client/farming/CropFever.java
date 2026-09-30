package dev.stray.client.farming;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Crop Fever lasts 60 seconds. Hypixel announces the start and the cure in chat.
 */
public final class CropFever {
	public static final int DEFAULT_SECONDS = 60;
	private static final Pattern START = Pattern.compile(
		"(?i)caught a case of the crop fever(?: for (\\d+) seconds?)?"
	);
	private static final Pattern CURED = Pattern.compile("(?i)crop fever has been cured");
	private static long endMs;

	private CropFever() {
	}

	public static void onMessage(Component message, boolean overlay) {
		if (message == null) {
			return;
		}
		String plain = ChatFormatting.stripFormatting(message.getString());
		if (plain == null || plain.isEmpty()) {
			return;
		}
		if (CURED.matcher(plain).find()) {
			endMs = 0L;
			return;
		}
		Matcher start = START.matcher(plain);
		if (!start.find()) {
			return;
		}
		int seconds = DEFAULT_SECONDS;
		if (start.group(1) != null) {
			try {
				seconds = Integer.parseInt(start.group(1));
			} catch (NumberFormatException ignored) {
				seconds = DEFAULT_SECONDS;
			}
		}
		if (seconds < 1 || seconds > 600) {
			seconds = DEFAULT_SECONDS;
		}
		endMs = System.currentTimeMillis() + seconds * 1000L;
	}

	public static void reset() {
		endMs = 0L;
	}

	public static boolean active() {
		return secondsLeft() > 0;
	}

	public static int secondsLeft() {
		long left = endMs - System.currentTimeMillis();
		if (left <= 0L) {
			return 0;
		}
		return (int) ((left + 999L) / 1000L);
	}

	public static String label() {
		int seconds = active() ? secondsLeft() : DEFAULT_SECONDS;
		int minutes = seconds / 60;
		int rest = seconds % 60;
		String clock = minutes > 0
			? minutes + ":" + (rest < 10 ? "0" : "") + rest
			: rest + "s";
		return "Crop fever  " + clock;
	}
}
