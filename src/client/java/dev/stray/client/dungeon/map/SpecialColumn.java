package dev.stray.client.dungeon.map;

import dev.stray.client.location.SkyblockLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Guesses what an unopened 1x1 room is (puzzle, trap or miniboss) from what the map
 * already shows. Based on dtMap by rice.who (BSD-3, https://github.com/ricedotwho/dtMap),
 * through NoammAddons.
 */
public final class SpecialColumn {
	private static int column = -1;
	private static int roomsX;
	private static int roomsZ;
	private static boolean hasTrap;
	private static int discoveredFullSpecialColumn;
	private static final Set<Integer> KNOWN_1X1 = new HashSet<>();
	private static final Map<Integer, RoomType> OPENED_1X1 = new HashMap<>();

	private SpecialColumn() {
	}

	public static void reset() {
		column = -1;
		roomsX = 0;
		roomsZ = 0;
		hasTrap = false;
		discoveredFullSpecialColumn = 0;
		KNOWN_1X1.clear();
		OPENED_1X1.clear();
	}

	public static void update() {
		if (!SkyblockLocation.inDungeon) {
			return;
		}
		int floor = SkyblockLocation.dungeonFloor;
		roomsX = roomCount(MapCalibration.startX);
		roomsZ = roomCount(MapCalibration.startZ);
		hasTrap = floor >= 3;
		column = roomsX == 6 && (((floor == 5 || floor == 6) && roomsZ == 6) || (floor == 4 && roomsZ == 5)) ? 5 : -1;
		for (int x = 0; x < roomsX; x++) {
			for (int z = 0; z < roomsZ; z++) {
				int index = index(x, z);
				if (!(DungeonScanner.GRID[index] instanceof RoomTile tile)) {
					continue;
				}
				RoomType type = tile.data.type();
				if (type == RoomType.BLOOD || type == RoomType.ENTRANCE) {
					continue;
				}
				if (tile.state() == RoomState.UNOPENED) {
					if (x == column || surrounded(x, z)) {
						KNOWN_1X1.add(index);
					}
					continue;
				}
				if (tile.state() == RoomState.UNDISCOVERED) {
					continue;
				}
				if (type == RoomType.CHAMPION || type == RoomType.TRAP || type == RoomType.PUZZLE) {
					OPENED_1X1.put(index, type);
				}
			}
		}
		if (KNOWN_1X1.size() == specialRoomCount(DungeonMap.maxPuzzleCount)) {
			discoveredFullSpecialColumn = roomsZ;
		} else if (column != -1) {
			int unsure = 0;
			for (int z = 0; z < roomsZ; z++) {
				int index = index(column - 1, z);
				if (KNOWN_1X1.contains(index)) {
					continue;
				}
				Tile tile = DungeonScanner.GRID[index];
				boolean blood = tile instanceof RoomTile room && room.data.type() == RoomType.BLOOD;
				if (tile.state() == RoomState.UNDISCOVERED || (tile.state() == RoomState.UNOPENED && !blood)) {
					unsure++;
				}
			}
			discoveredFullSpecialColumn = roomsZ - unsure;
		}
	}

	public static List<RoomType> predict(int index) {
		if (!KNOWN_1X1.contains(index)) {
			return null;
		}
		int puzzleCount = DungeonMap.maxPuzzleCount;
		if (puzzleCount <= 0 || seenDoors(index) != 1) {
			return null;
		}
		int columnRooms = 0;
		for (int i : KNOWN_1X1) {
			if (special(i)) {
				columnRooms++;
			}
		}
		if (column != -1 && columnRooms == 0) {
			columnRooms = 1;
		}
		boolean openedTrap = !hasTrap || OPENED_1X1.containsValue(RoomType.TRAP);
		boolean openedChampion = OPENED_1X1.containsValue(RoomType.CHAMPION);

		if (special(index)) {
			int specialSize = roomsZ - discoveredFullSpecialColumn + columnRooms;
			boolean trapOutside = false;
			for (Map.Entry<Integer, RoomType> e : OPENED_1X1.entrySet()) {
				trapOutside |= e.getValue() == RoomType.TRAP && !special(e.getKey());
			}
			if (specialSize <= puzzleCount || KNOWN_1X1.size() - columnRooms >= 2) {
				return List.of(RoomType.PUZZLE);
			}
			if (trapOutside) {
				return List.of(RoomType.PUZZLE);
			}
			if (specialSize == puzzleCount + 1) {
				return List.of(RoomType.TRAP, RoomType.PUZZLE);
			}
			if (KNOWN_1X1.size() - columnRooms == 1) {
				return List.of(RoomType.TRAP, RoomType.PUZZLE);
			}
			if (openedChampion) {
				return List.of(RoomType.CHAMPION, RoomType.TRAP);
			}
			return List.of(RoomType.CHAMPION, RoomType.TRAP, RoomType.PUZZLE);
		}

		int totalPuzzles = columnRooms;
		for (Map.Entry<Integer, RoomType> e : OPENED_1X1.entrySet()) {
			if (e.getValue() == RoomType.PUZZLE && !special(e.getKey())) {
				totalPuzzles++;
			}
		}
		if (totalPuzzles == puzzleCount) {
			if (openedTrap) {
				return List.of(RoomType.CHAMPION);
			}
			if (openedChampion) {
				return List.of(RoomType.TRAP);
			}
			return List.of(RoomType.CHAMPION, RoomType.TRAP);
		}
		// The miniboss is generated first: puzzles + 1 rooms in the column means this one is it.
		if (columnRooms == puzzleCount + 1) {
			return List.of(RoomType.CHAMPION);
		}
		if (openedTrap && openedChampion) {
			return List.of(RoomType.PUZZLE);
		}
		if (OPENED_1X1.size() == specialRoomCount(puzzleCount) - 1) {
			return List.of(openedTrap ? RoomType.CHAMPION : RoomType.TRAP);
		}
		List<RoomType> out = new ArrayList<>();
		out.add(RoomType.PUZZLE);
		if (!openedTrap) {
			out.add(RoomType.TRAP);
		}
		if (!openedChampion) {
			out.add(RoomType.CHAMPION);
		}
		return out;
	}

	private static boolean surrounded(int x, int z) {
		if (x != roomsX - 1 && x + 1 != column && !revealed(x + 1, z)) {
			return false;
		}
		if (x != 0 && !revealed(x - 1, z)) {
			return false;
		}
		if (z != roomsZ - 1 && !revealed(x, z + 1)) {
			return false;
		}
		return z == 0 || revealed(x, z - 1);
	}

	private static boolean revealed(int x, int z) {
		return DungeonScanner.GRID[index(x, z)].state() != RoomState.UNDISCOVERED;
	}

	private static int seenDoors(int index) {
		int x = index % 11;
		int z = index / 11;
		int[][] around = {{x - 1, z}, {x + 1, z}, {x, z - 1}, {x, z + 1}};
		int count = 0;
		for (int[] cell : around) {
			if (cell[0] < 0 || cell[0] > 10 || cell[1] < 0 || cell[1] > 10) {
				continue;
			}
			if (DungeonScanner.GRID[cell[1] * 11 + cell[0]] instanceof DoorTile door && door.state() != RoomState.UNDISCOVERED) {
				count++;
			}
		}
		return count;
	}

	private static boolean special(int index) {
		return column != -1 && (index % 11) / 2 == column;
	}

	private static int specialRoomCount(int puzzleCount) {
		return puzzleCount + (hasTrap ? 2 : 1);
	}

	private static int index(int x, int z) {
		return z * 2 * 11 + x * 2;
	}

	private static int roomCount(int start) {
		return (128 - start * 2 + 4) / (MapCalibration.mapRoomSize + 4);
	}
}
