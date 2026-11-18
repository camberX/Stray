package dev.stray.client.dungeon.map;

import dev.stray.client.config.StrayConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One whole room (all its grid cells). */
public final class UniqueRoom {
	private int topLeftX;
	private int topLeftY;
	private int centerX;
	private int centerY;
	public final List<RoomTile> tiles = new ArrayList<>();
	public RoomTile mainRoom;
	public final RoomData data;
	public final String name;
	public final String[] nameLines;
	public Integer highestBlock;
	private int foundSecrets;
	private boolean boundsDirty = true;
	public float maxWidth;
	public float maxHeight;

	public UniqueRoom(int arrX, int arrY, RoomTile tile) {
		topLeftX = centerX = arrX;
		topLeftY = centerY = arrY;
		tiles.add(tile);
		mainRoom = tile;
		data = tile.data;
		name = data.name();
		nameLines = name.split(" ");
		Scanner.cryptCount += data.crypts();
		Scanner.secretCount += data.secrets();
	}

	public int foundSecrets() {
		return foundSecrets;
	}

	public void setFoundSecrets(int value) {
		foundSecrets = value;
	}

	/** Space the room's text may use, in map pixels. */
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
			int wide = (maxX - minX) / Scanner.ROOM_SIZE + 1;
			int tall = (maxZ - minZ) / Scanner.ROOM_SIZE + 1;
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
		// [row, column] of the real (non-separator) cells
		Map<Integer, Integer> columns = new HashMap<>();
		Map<Integer, Integer> rows = new HashMap<>();
		for (RoomTile t : tiles) {
			if (t.isSeparator) {
				continue;
			}
			columns.merge(t.column(), 1, Integer::sum);
			rows.merge(t.row(), 1, Integer::sum);
		}
		if (columns.isEmpty()) {
			return;
		}
		List<Map.Entry<Integer, Integer>> xRooms = sorted(columns);
		List<Map.Entry<Integer, Integer>> zRooms = sorted(rows);
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

	private static List<Map.Entry<Integer, Integer>> sorted(Map<Integer, Integer> counts) {
		List<Map.Entry<Integer, Integer>> list = new ArrayList<>(counts.entrySet());
		list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
		return list;
	}

	/** [column, row] where the checkmark or text goes. */
	public int[] checkmarkPosition() {
		return StrayConfig.get().dungeonMapCenterText ? new int[]{centerX, centerY} : new int[]{topLeftX, topLeftY};
	}
}
