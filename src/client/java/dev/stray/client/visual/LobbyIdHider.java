package dev.stray.client.visual;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.location.SkyblockLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hides the lobby id (for example "m151AM") that Hypixel SkyBlock shows after the date on the
 * first scoreboard line. Same idea as NoammAddons' Scoreboard "Hide Server ID": the line is
 * cut right after the date. Works for both the vanilla sidebar and Stray's scoreboard.
 */
public final class LobbyIdHider {
	/** The date (dd/mm/yy) and, after it, the lobby id. */
	private static final Pattern DATE_LINE = Pattern.compile("^(\\s*\\d{2}/\\d{2}/\\d{2})\\s+\\w{1,10}");

	private LobbyIdHider() {
	}

	public static boolean active() {
		return StrayConfig.get().hideLobbyId && (SkyblockLocation.inSkyblock || dev.stray.client.dungeon.DevMode.on());
	}

	/** The line without the lobby id, or the line itself when it is not the date line. */
	public static Component strip(Component line) {
		if (line == null || !active()) {
			return line;
		}
		String plain = line.getString();
		Matcher m = DATE_LINE.matcher(plain);
		if (!m.find()) {
			return line;
		}
		int keep = m.end(1);
		MutableComponent out = Component.empty();
		int[] seen = {0};
		line.visit((style, text) -> {
			int start = seen[0];
			seen[0] += text.length();
			if (start >= keep) {
				return Optional.of(Boolean.TRUE);
			}
			String part = start + text.length() > keep ? text.substring(0, keep - start) : text;
			out.append(Component.literal(part).setStyle(style));
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}
}
