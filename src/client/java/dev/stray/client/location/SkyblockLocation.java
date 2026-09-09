package dev.stray.client.location;

import dev.stray.client.config.StrayConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.phys.AABB;

/**
 * Hypixel / Skyblock / island flags. Tab and scoreboard are walked at most a
 * few times a second, and each walk stops as soon as it has what it needs.
 */
public final class SkyblockLocation {
	private static final String AREA_PREFIX = "Area: ";
	private static final String DUNGEON_PREFIX = "Dungeon: ";
	private static final int REFRESH_TICKS = 5;

	public static boolean onHypixel;
	public static boolean inSkyblock;
	public static boolean inTheEnd;
	public static boolean inDungeon;
	public static boolean inBoss;
	public static int dungeonFloor;
	public static String area = "";
	public static String serverBrand = "";

	private static int lastTick = Integer.MIN_VALUE;

	private SkyblockLocation() {
	}

	public static void tick(Minecraft client) {
		if (client.player == null || client.level == null) {
			reset();
			return;
		}

		int t = client.player.tickCount;
		if (lastTick != Integer.MIN_VALUE && t - lastTick < REFRESH_TICKS && t >= lastTick) {
			return;
		}
		lastTick = t;

		ClientPacketListener connection = client.player.connection;
		serverBrand = brand(connection);
		onHypixel = serverBrand.toLowerCase().contains("hypixel");

		Sidebar sidebar = readSidebar(client);
		area = readArea(connection, sidebar);
		inSkyblock = onHypixel && (sidebar.skyblock || !area.isEmpty());
		inTheEnd = inSkyblock && isTheEnd(area, sidebar);
		inDungeon = onHypixel && (sidebar.dungeon || dungeonTab(connection));
		if (sidebar.floor > 0) {
			dungeonFloor = sidebar.floor;
		}
		if (!inDungeon) {
			dungeonFloor = 0;
			inBoss = false;
		} else if (client.player != null) {
			inBoss = bossRoom(dungeonFloor, client.player.getX(), client.player.getY(), client.player.getZ());
		}
	}

	public static boolean shouldMarkNodes() {
		StrayConfig config = StrayConfig.get();
		if (!config.markersEnabled) {
			return false;
		}
		if (config.forceEnable) {
			return true;
		}
		if (!inSkyblock) {
			return false;
		}
		return !config.onlyInTheEnd || inTheEnd;
	}

	public static void reset() {
		onHypixel = false;
		inSkyblock = false;
		inTheEnd = false;
		inDungeon = false;
		inBoss = false;
		dungeonFloor = 0;
		area = "";
		serverBrand = "";
		lastTick = Integer.MIN_VALUE;
	}

	private static String brand(ClientPacketListener connection) {
		if (connection == null) {
			return "";
		}
		String brand = connection.serverBrand();
		return brand == null ? "" : brand;
	}

	private static String readArea(ClientPacketListener connection, Sidebar sidebar) {
		if (connection != null) {
			for (PlayerInfo info : connection.getListedOnlinePlayers()) {
				Component display = info.getTabListDisplayName();
				if (display == null) {
					continue;
				}
				String text = plain(display);
				if (text.startsWith(AREA_PREFIX)) {
					return text.substring(AREA_PREFIX.length()).trim();
				}
				if (text.startsWith(DUNGEON_PREFIX)) {
					return text.substring(DUNGEON_PREFIX.length()).trim();
				}
			}
		}
		if (!sidebar.endArea.isEmpty()) {
			return sidebar.endArea;
		}
		return area;
	}

	private static boolean dungeonTab(ClientPacketListener connection) {
		if (connection == null) {
			return false;
		}
		for (PlayerInfo info : connection.getListedOnlinePlayers()) {
			Component display = info.getTabListDisplayName();
			if (display != null && plain(display).startsWith(DUNGEON_PREFIX)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isTheEnd(String currentArea, Sidebar sidebar) {
		if (currentArea.equalsIgnoreCase("The End") || currentArea.toLowerCase().contains("end island")) {
			return true;
		}
		return sidebar.end;
	}

	private static Sidebar readSidebar(Minecraft client) {
		if (client.level == null) {
			return Sidebar.EMPTY;
		}
		Scoreboard scoreboard = client.level.getScoreboard();
		Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (sidebar == null) {
			return Sidebar.EMPTY;
		}
		boolean skyblock = "SBScoreboard".equals(sidebar.getName());
		boolean end = false;
		boolean dungeon = false;
		int floor = 0;
		String endArea = "";
		String title = plain(sidebar.getDisplayName());
		String titleKey = title.toUpperCase();
		if (titleKey.contains("SKYBLOCK")) {
			skyblock = true;
		}
		if (title.contains("The End")) {
			end = true;
			endArea = "The End";
		}
		if (catacombsLine(title)) {
			dungeon = true;
			floor = catacombsFloor(title);
		}
		for (var score : scoreboard.listPlayerScores(sidebar)) {
			Component line = PlayerTeam.formatNameForTeam(
				scoreboard.getPlayersTeam(score.owner()),
				Component.literal(score.owner())
			);
			String text = plain(line);
			if (!skyblock && text.toUpperCase().contains("SKYBLOCK")) {
				skyblock = true;
			}
			if (text.contains("The End") || text.contains("End Island")) {
				end = true;
				if (endArea.isEmpty()) {
					endArea = "The End";
				}
			}
			if (catacombsLine(text)) {
				dungeon = true;
				int lineFloor = catacombsFloor(text);
				if (lineFloor > 0) {
					floor = lineFloor;
				}
			}
		}
		return new Sidebar(skyblock, end, endArea, dungeon, floor);
	}

	private static boolean catacombsLine(String text) {
		return text != null && text.contains("The Catacombs (") && !text.contains("Queue");
	}

	private static int catacombsFloor(String text) {
		if (!catacombsLine(text)) {
			return 0;
		}
		int open = text.lastIndexOf('(');
		int close = text.lastIndexOf(')');
		if (open < 0 || close <= open) {
			return 0;
		}
		String inside = text.substring(open + 1, close);
		for (int i = inside.length() - 1; i >= 0; i--) {
			char c = inside.charAt(i);
			if (c >= '1' && c <= '7') {
				return c - '0';
			}
		}
		return 0;
	}

	private static boolean bossRoom(int floor, double x, double y, double z) {
		if (floor < 1 || floor > BOSS_ROOMS.length) {
			return false;
		}
		return BOSS_ROOMS[floor - 1].contains(x, y, z);
	}

	private static AABB box(double x1, double y1, double z1, double x2, double y2, double z2) {
		return new AABB(
			Math.min(x1, x2),
			Math.min(y1, y2),
			Math.min(z1, z2),
			Math.max(x1, x2),
			Math.max(y1, y2),
			Math.max(z1, z2)
		);
	}

	private static String plain(Component component) {
		return component == null ? "" : component.getString().replaceAll("§.", "");
	}

	private record Sidebar(boolean skyblock, boolean end, String endArea, boolean dungeon, int floor) {
		private static final Sidebar EMPTY = new Sidebar(false, false, "", false, 0);
	}

	/** NoammAddons boss-room AABBs, floors 1–7. */
	private static final AABB[] BOSS_ROOMS = {
		box(-14, 55, 49, -72, 146, -40),
		box(-40, 99, -40, 24, 54, 59),
		box(-40, 118, -40, 42, 64, 37),
		box(-40, 112, -40, 50, 53, 47),
		box(-40, 112, -8, 50, 53, 118),
		box(-40, 51, -8, 22, 110, 134),
		box(-8, 0, -8, 134, 254, 147)
	};
}
