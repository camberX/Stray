package dev.stray.client.editor;

import dev.stray.client.location.SkyblockLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which saved set of world edits belongs to where you are. On SkyBlock that is the island
 * (Hub, Dwarven Mines, ...), the dungeon floor (F7 and M7 are separate) or the Kuudra tier;
 * elsewhere the singleplayer world or server plus the dimension.
 */
public final class EditArea {
	private static final Pattern KUUDRA = Pattern.compile("Kuudra's Hollow \\(T(\\d)\\)");

	private EditArea() {
	}

	/** Stable file-safe key, or null while it isn't known yet (e.g. right after joining). */
	public static String key() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return null;
		}
		if (SkyblockLocation.onHypixel) {
			if (SkyblockLocation.inDungeon) {
				int floor = SkyblockLocation.dungeonFloor;
				if (floor <= 0) {
					return null;
				}
				return "dungeon_" + (SkyblockLocation.dungeonMaster ? "m" : "f") + floor;
			}
			Matcher kuudra = KUUDRA.matcher(SkyblockLocation.poi == null ? "" : SkyblockLocation.poi);
			if (kuudra.find()) {
				return "kuudra_t" + kuudra.group(1);
			}
			if (SkyblockLocation.inSkyblock) {
				String area = SkyblockLocation.area;
				if (area == null || area.isBlank()) {
					return null;
				}
				return "island_" + slug(area);
			}
			return "hypixel_" + dimension(client);
		}
		if (client.getSingleplayerServer() != null) {
			return "world_" + slug(client.getSingleplayerServer().getWorldData().getLevelName()) + "_" + dimension(client);
		}
		ServerData server = client.getCurrentServer();
		String host = server == null ? "server" : server.ip;
		return "server_" + slug(host) + "_" + dimension(client);
	}

	/** What the key is called in the menu. */
	public static String label(String key) {
		if (key == null) {
			return "Finding out where you are...";
		}
		if (key.startsWith("dungeon_")) {
			String floor = key.substring("dungeon_".length()).toUpperCase(Locale.ROOT);
			return "Catacombs " + floor;
		}
		if (key.startsWith("kuudra_t")) {
			return "Kuudra T" + key.substring("kuudra_t".length());
		}
		if (key.startsWith("island_")) {
			return SkyblockLocation.area == null || SkyblockLocation.area.isBlank() ? key.substring(7) : SkyblockLocation.area;
		}
		if (key.startsWith("world_")) {
			Minecraft client = Minecraft.getInstance();
			return client.getSingleplayerServer() != null ? client.getSingleplayerServer().getWorldData().getLevelName() : key;
		}
		return key;
	}

	private static String dimension(Minecraft client) {
		return slug(client.level.dimension().identifier().getPath());
	}

	static String slug(String raw) {
		String value = raw == null ? "" : dev.stray.client.util.Text.strip(raw).toLowerCase(Locale.ROOT).trim();
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < value.length() && out.length() < 48; i++) {
			char c = value.charAt(i);
			if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
				out.append(c);
			} else if (out.length() > 0 && out.charAt(out.length() - 1) != '_') {
				out.append('_');
			}
		}
		while (out.length() > 0 && out.charAt(out.length() - 1) == '_') {
			out.setLength(out.length() - 1);
		}
		return out.length() == 0 ? "unknown" : out.toString();
	}
}
