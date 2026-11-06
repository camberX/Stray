package dev.stray.client.dungeon.map;

import net.minecraft.core.Direction;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Reads rooms, doors and checkmarks off the vanilla dungeon map item. From NoammAddons (CC0). */
public final class HotbarMapScanner {
	private static byte[] centerColors = new byte[121];
	private static byte[] sideColors = new byte[121];
	private static Tile[] cached = new Tile[121];

	public static int halfRoom = MapCalibration.mapRoomSize / 2;
	public static int halfTile = halfRoom + 2;
	public static int quarterRoom = halfRoom / 2;
	public static int startX = MapCalibration.startX + halfRoom;
	public static int startY = MapCalibration.startZ + halfRoom;

	private HotbarMapScanner() {
	}

	public static void calibrate() {
		halfRoom = MapCalibration.mapRoomSize / 2;
		halfTile = halfRoom + 2;
		quarterRoom = halfRoom / 2;
		startX = MapCalibration.startX + halfRoom;
		startY = MapCalibration.startZ + halfRoom;
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
					side = (mapY - halfRoom) * 128 + (mapX - halfRoom);
				} else if (y % 2 == 1) {
					side = mapY * 128 + mapX - 4;
				} else {
					side = (mapY - 4) * 128 + mapX;
				}
				sideColors[y * 11 + x] = side >= 0 && side < colors.length ? colors[side] : 0;
			}
		}
	}

	public static Tile tile(int arrayX, int arrayY) {
		int index = arrayY * 11 + arrayX;
		if (index < 0 || index >= cached.length) {
			return new UnknownTile(0, 0);
		}
		if (cached[index] == null) {
			int xPos = DungeonScanner.START_X + arrayX * (DungeonScanner.ROOM_SIZE >> 1);
			int zPos = DungeonScanner.START_Z + arrayY * (DungeonScanner.ROOM_SIZE >> 1);
			cached[index] = scanTile(arrayX, arrayY, xPos, zPos);
		}
		return cached[index];
	}

	public static List<RoomTile> connected(int arrayX, int arrayY) {
		List<RoomTile> out = new ArrayList<>();
		if (!(tile(arrayX, arrayY) instanceof RoomTile)) {
			return out;
		}
		Set<Integer> visited = new HashSet<>();
		visited.add(arrayY * 11 + arrayX);
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		queue.add(new int[] {arrayX, arrayY});
		while (!queue.isEmpty()) {
			int[] cell = queue.removeFirst();
			if (!(tile(cell[0], cell[1]) instanceof RoomTile room)) {
				continue;
			}
			out.add(room);
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				int nx = cell[0] + dir.getStepX();
				int ny = cell[1] + dir.getStepZ();
				if (nx < 0 || nx > 10 || ny < 0 || ny > 10) {
					continue;
				}
				if (!visited.add(ny * 11 + nx)) {
					continue;
				}
				queue.add(new int[] {nx, ny});
			}
		}
		return out;
	}

	private static Tile scanTile(int arrayX, int arrayY, int worldX, int worldZ) {
		int center = centerColors[arrayY * 11 + arrayX];
		int side = sideColors[arrayY * 11 + arrayX];
		if (center == 0) {
			return new UnknownTile(worldX, worldZ);
		}
		if (arrayX % 2 == 0 && arrayY % 2 == 0) {
			RoomType type = RoomType.fromMapColor(side);
			if (type == null) {
				return new UnknownTile(worldX, worldZ);
			}
			RoomTile room = new RoomTile(worldX, worldZ, RoomData.unknown(type));
			RoomState state = switch (center) {
				case 18 -> type == RoomType.BLOOD ? RoomState.DISCOVERED : type == RoomType.PUZZLE ? RoomState.FAILED : room.state();
				case 30 -> type == RoomType.ENTRANCE ? RoomState.DISCOVERED : RoomState.GREEN;
				case 34 -> RoomState.CLEARED;
				case 85, 119 -> RoomState.UNOPENED;
				default -> RoomState.DISCOVERED;
			};
			room.setState(state);
			return room;
		}
		if (side == 0) {
			DoorType type = DoorType.fromMapColor(center);
			if (type == null) {
				return new UnknownTile(worldX, worldZ);
			}
			DoorTile door = new DoorTile(worldX, worldZ, type);
			door.setState(center == 85 ? RoomState.UNOPENED : RoomState.DISCOVERED);
			return door;
		}
		RoomType type = RoomType.fromMapColor(side);
		if (type == null) {
			return new UnknownTile(worldX, worldZ);
		}
		RoomTile room = new RoomTile(worldX, worldZ, RoomData.unknown(type));
		room.setState(RoomState.DISCOVERED);
		room.isSeparator = true;
		return room;
	}
}
