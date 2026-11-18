package dev.stray.client.dungeon.map;

import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Reads each grid cell's colours off the dungeon map item. */
public final class HotbarScanner {
	private static byte[] centerColors = new byte[121];
	private static byte[] sideColors = new byte[121];
	private static Tile[] cached = new Tile[121];
	public static int halfRoom = 8;
	public static int halfTile = 10;
	public static int quarterRoom = 4;
	private static int startX = 13;
	private static int startY = 13;

	private HotbarScanner() {
	}

	public static void calibrate() {
		halfRoom = MapUtils.mapRoomSize / 2;
		halfTile = halfRoom + 2;
		quarterRoom = halfRoom / 2;
		startX = MapUtils.startX + halfRoom;
		startY = MapUtils.startZ + halfRoom;
		centerColors = new byte[121];
		sideColors = new byte[121];
		cached = new Tile[121];
	}

	public static void updateMap(MapItemSavedData data) {
		cached = new Tile[121];
		byte[] colors = data.colors;
		for (int x = 0; x <= 10; x++) {
			for (int y = 0; y <= 10; y++) {
				int mapX = startX + x * halfTile;
				int mapY = startY + y * halfTile;
				if (mapX >= 128 || mapY >= 128) {
					continue;
				}
				centerColors[y * 11 + x] = colors[mapY * 128 + mapX];
				int side;
				if (x % 2 == 0 && y % 2 == 0) {
					side = (mapY - halfRoom) * 128 + mapX - halfRoom;
				} else if (y % 2 == 1) {
					side = mapY * 128 + mapX - 4;
				} else {
					side = (mapY - 4) * 128 + mapX;
				}
				sideColors[y * 11 + x] = side >= 0 && side < colors.length ? colors[side] : 0;
			}
		}
	}

	public static Tile tile(int ax, int ay) {
		int index = ay * 11 + ax;
		if (index < 0 || index >= 121) {
			return UnknownTile.EMPTY;
		}
		if (cached[index] == null) {
			cached[index] = scanTile(ax, ay, Scanner.START_X + ax * (Scanner.ROOM_SIZE >> 1), Scanner.START_Z + ay * (Scanner.ROOM_SIZE >> 1));
		}
		return cached[index];
	}

	public static List<RoomTile> connected(int ax, int ay) {
		List<RoomTile> out = new ArrayList<>();
		if (!(tile(ax, ay) instanceof RoomTile)) {
			return out;
		}
		Set<Integer> visited = new HashSet<>();
		visited.add(ay * 11 + ax);
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		queue.add(new int[]{ax, ay});
		int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
		while (!queue.isEmpty()) {
			int[] c = queue.removeFirst();
			if (!(tile(c[0], c[1]) instanceof RoomTile room)) {
				continue;
			}
			out.add(room);
			for (int[] d : dirs) {
				int nx = c[0] + d[0];
				int ny = c[1] + d[1];
				if (nx < 0 || nx > 10 || ny < 0 || ny > 10 || !visited.add(ny * 11 + nx)) {
					continue;
				}
				queue.add(new int[]{nx, ny});
			}
		}
		return out;
	}

	private static Tile scanTile(int ax, int ay, int wx, int wz) {
		int center = centerColors[ay * 11 + ax];
		int side = sideColors[ay * 11 + ax];
		if (center == 0) {
			return new UnknownTile(wx, wz);
		}
		if (ax % 2 == 0 && ay % 2 == 0) {
			RoomType type = RoomType.fromMapColor(side);
			if (type == null) {
				return new UnknownTile(wx, wz);
			}
			RoomTile tile = new RoomTile(wx, wz, RoomData.unknown(type));
			RoomState state = switch (center) {
				case 18 -> type == RoomType.BLOOD ? RoomState.DISCOVERED : type == RoomType.PUZZLE ? RoomState.FAILED : tile.state();
				case 30 -> type == RoomType.ENTRANCE ? RoomState.DISCOVERED : RoomState.GREEN;
				case 34 -> RoomState.CLEARED;
				case 85, 119 -> RoomState.UNOPENED;
				default -> RoomState.DISCOVERED;
			};
			tile.setState(state);
			return tile;
		}
		if (side == 0) {
			DoorType type = DoorType.fromMapColor(center);
			if (type == null) {
				return new UnknownTile(wx, wz);
			}
			DoorTile door = new DoorTile(wx, wz, type);
			door.setState(center == 85 ? RoomState.UNOPENED : RoomState.DISCOVERED);
			return door;
		}
		RoomType type = RoomType.fromMapColor(side);
		if (type == null) {
			return new UnknownTile(wx, wz);
		}
		RoomTile tile = new RoomTile(wx, wz, RoomData.unknown(type));
		tile.setState(RoomState.DISCOVERED);
		tile.isSeparator = true;
		return tile;
	}
}
