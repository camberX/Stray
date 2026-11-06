package dev.stray.client.dungeon.map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All the cells of one room, with where its checkmark or name goes. From NoammAddons (CC0). */
public final class UniqueRoom {
	private int topLeftX;
	private int topLeftY;
	private int centerX;
	private int centerY;
	public final List<RoomTile> tiles = new ArrayList<>();
	public RoomTile mainRoom;
	public final RoomData data;
	public final String name;
	public final String[] splitName;
	private int foundSecrets;

	private boolean boundsDirty = true;
	public float maxWidth;
	public float maxHeight;

	public UniqueRoom(int arrX, int arrY, RoomTile tile) {
		topLeftX = arrX;
		topLeftY = arrY;
		centerX = arrX;
		centerY = arrY;
		tiles.add(tile);
		mainRoom = tile;
		data = tile.data;
		name = data.name();
		splitName = name.split(" ");
		DungeonScanner.cryptCount += data.crypts();
		DungeonScanner.secretCount += data.secrets();
	}

	public int foundSecrets() {
		return foundSecrets;
	}

	public void setFoundSecrets(int value) {
		foundSecrets = value;
	}

	/** The room's size on the map, for fitting its name inside. */
	public void updateBounds(float roomSize, float gapSize) {
		if (!boundsDirty) {
			return;
		}
		boundsDirty = false;
		float w = roomSize;
		float h = roomSize;
		if (data.shape() != RoomShape.SL && data.shape() != RoomShape.S1x1 && data.shape() != RoomShape.S2x2) {
			int minX = Integer.MAX_VALUE;
			int maxX = Integer.MIN_VALUE;
			int minZ = Integer.MAX_VALUE;
			int maxZ = Integer.MIN_VALUE;
			for (RoomTile tile : tiles) {
				minX = Math.min(minX, tile.x);
				maxX = Math.max(maxX, tile.x);
				minZ = Math.min(minZ, tile.z);
				maxZ = Math.max(maxZ, tile.z);
			}
			int wide = (maxX - minX) / DungeonScanner.ROOM_SIZE + 1;
			int tall = (maxZ - minZ) / DungeonScanner.ROOM_SIZE + 1;
			w = wide * roomSize + Math.max(0, wide - 1) * gapSize;
			h = tall * roomSize + Math.max(0, tall - 1) * gapSize;
		}
		if (data.shape() == RoomShape.SL) {
			w = roomSize * 2;
		} else if (data.shape() == RoomShape.S2x2) {
			w = roomSize * 2;
			h = roomSize * 2;
		}
		maxWidth = w;
		maxHeight = h;
	}

	public void addTile(int x, int y, RoomTile tile) {
		boundsDirty = true;
		tiles.removeIf(t -> t.x == tile.x && t.z == tile.z);
		tiles.add(tile);
		if (x < topLeftX || (x == topLeftX && y < topLeftY)) {
			topLeftX = x;
			topLeftY = y;
			mainRoom = tile;
		}
		if (tiles.size() == 1) {
			centerX = x;
			centerY = y;
			return;
		}
		// (row, column) of each real cell
		List<int[]> cells = new ArrayList<>();
		for (RoomTile t : tiles) {
			if (!t.isSeparator) {
				cells.add(new int[] {t.row(), t.column()});
			}
		}
		if (cells.isEmpty()) {
			return;
		}
		List<Map.Entry<Integer, Integer>> xRooms = grouped(cells, 1);
		List<Map.Entry<Integer, Integer>> zRooms = grouped(cells, 0);
		if (zRooms.size() == 1 || !zRooms.get(0).getValue().equals(zRooms.get(1).getValue())) {
			int sum = 0;
			for (Map.Entry<Integer, Integer> e : xRooms) {
				sum += e.getKey();
			}
			centerX = sum / xRooms.size();
			centerY = zRooms.get(0).getKey();
		} else if (xRooms.size() == 1 || !xRooms.get(0).getValue().equals(xRooms.get(1).getValue())) {
			int sum = 0;
			for (Map.Entry<Integer, Integer> e : zRooms) {
				sum += e.getKey();
			}
			centerX = xRooms.get(0).getKey();
			centerY = sum / zRooms.size();
		} else {
			centerX = (xRooms.get(0).getKey() + xRooms.get(1).getKey()) / 2;
			centerY = (zRooms.get(0).getKey() + zRooms.get(1).getKey()) / 2;
		}
	}

	/** Groups cells by row (0) or column (1): key and count, biggest group first, stable otherwise. */
	private static List<Map.Entry<Integer, Integer>> grouped(List<int[]> cells, int axis) {
		Map<Integer, Integer> counts = new LinkedHashMap<>();
		for (int[] cell : cells) {
			counts.merge(cell[axis], 1, Integer::sum);
		}
		List<Map.Entry<Integer, Integer>> list = new ArrayList<>(counts.entrySet());
		list.sort(Comparator.comparingInt((Map.Entry<Integer, Integer> e) -> e.getValue()).reversed());
		return list;
	}

	/** Grid column and row where the checkmark or name goes (the room's middle). */
	public int checkColumn() {
		return centerX;
	}

	public int checkRow() {
		return centerY;
	}
}
