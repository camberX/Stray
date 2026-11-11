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

	/**
	 * The line without the lobby id, or the line itself when it is not the date line. Hypixel's
	 * lines carry raw § colour codes inside the text (team prefix + owner + suffix), so the date
	 * is matched on the visible characters and the cut is made at the matching visible position.
	 */
	public static Component strip(Component line) {
		if (line == null || !active()) {
			return line;
		}
		StringBuilder visible = new StringBuilder();
		line.visit((style, text) -> {
			appendVisible(visible, text);
			return Optional.empty();
		}, Style.EMPTY);
		Matcher m = DATE_LINE.matcher(visible);
		if (!m.find()) {
			return line;
		}
		int keep = m.end(1);
		MutableComponent out = Component.empty();
		int[] seen = {0};
		line.visit((style, text) -> {
			if (seen[0] >= keep) {
				return Optional.of(Boolean.TRUE);
			}
			StringBuilder part = new StringBuilder();
			for (int i = 0; i < text.length() && seen[0] < keep; i++) {
				char c = text.charAt(i);
				if (c == '\u00A7' && i + 1 < text.length()) {
					part.append(c).append(text.charAt(++i));
					continue;
				}
				part.append(c);
				seen[0]++;
			}
			if (!part.isEmpty()) {
				out.append(Component.literal(part.toString()).setStyle(style));
			}
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	private static void appendVisible(StringBuilder out, String text) {
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\u00A7' && i + 1 < text.length()) {
				i++;
				continue;
			}
			out.append(c);
		}
	}
}
