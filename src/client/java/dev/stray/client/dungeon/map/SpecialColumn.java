package dev.stray.client.dungeon.map;

import dev.stray.client.location.SkyblockLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.stray.client.dungeon.map.RoomType.CHAMPION;
import static dev.stray.client.dungeon.map.RoomType.PUZZLE;
import static dev.stray.client.dungeon.map.RoomType.TRAP;

/**
 * Guesses what unopened 1x1 rooms are (puzzle, trap, miniboss).
 * Based off dtMap by rice.who, Copyright (c) 2026, rice.who, under the BSD-3 Clause license
 * https://github.com/ricedotwho/dtMap/blob/main/LICENSE.txt, via NoammAddons. Modified.
 */
public final class SpecialColumn {
	private static int column = -1;
	private static int roomsX;
	private static int roomsZ;
	private static boolean hasTrap;
	private static int discoveredFullSpecialColumn;
	private static final Set<Integer> KNOWN_1X1S = new HashSet<>();
	private static final Map<Integer, RoomType> OPENED_1X1S = new HashMap<>();

	private SpecialColumn() {
	}

	public static void reset() {
		column = -1;
		roomsX = 0;
		roomsZ = 0;
		hasTrap = false;
		discoveredFullSpecialColumn = 0;
		KNOWN_1X1S.clear();
		OPENED_1X1S.clear();
	}

	public static void update() {
		int floor = SkyblockLocation.dungeonFloor;
		roomsX = roomCount(MapUtils.startX);
		roomsZ = roomCount(MapUtils.startZ);
		hasTrap = floor >= 3;
		column = roomsX == 6 && ((floor >= 5 && floor <= 6 && roomsZ == 6) || (floor == 4 && roomsZ == 5)) ? 5 : -1;
		for (int x = 0; x < roomsX; x++) {
			for (int z = 0; z < roomsZ; z++) {
				int index = index(x, z);
				if (index < 0 || index >= 121 || !(Scanner.GRID[index] instanceof RoomTile tile)) {
					continue;
				}
				RoomType type = tile.data.type();
				if (type == RoomType.BLOOD || type == RoomType.ENTRANCE) {
					continue;
				}
				if (tile.state() == RoomState.UNOPENED) {
					if (x == column || surrounded(x, z)) {
						KNOWN_1X1S.add(index);
					}
					continue;
				}
				if (tile.state() == RoomState.UNDISCOVERED) {
					continue;
				}
				if (type == CHAMPION || type == TRAP || type == PUZZLE) {
					OPENED_1X1S.put(index, type);
				}
			}
		}
		if (KNOWN_1X1S.size() == specialRoomCount(DungeonMap.maxPuzzleCount)) {
			discoveredFullSpecialColumn = roomsZ;
		} else if (column != -1) {
			int missing = 0;
			for (int z = 0; z < roomsZ; z++) {
				int index = index(column - 1, z);
				if (KNOWN_1X1S.contains(index)) {
					continue;
				}
				Tile tile = Scanner.GRID[index];
				if (tile.state() == RoomState.UNDISCOVERED
					|| (tile.state() == RoomState.UNOPENED && !(tile instanceof RoomTile r && r.data.type() == RoomType.BLOOD))) {
					missing++;
				}
			}
			discoveredFullSpecialColumn = roomsZ - missing;
		}
	}

	public static List<RoomType> predict(int index) {
		if (!KNOWN_1X1S.contains(index)) {
			return null;
		}
		int puzzleCount = DungeonMap.maxPuzzleCount;
		if (puzzleCount <= 0 || seenDoors(index) != 1) {
			return null;
		}
		int columnRooms = 0;
		for (int i : KNOWN_1X1S) {
			if (special(i)) {
				columnRooms++;
			}
		}
		if (column != -1 && columnRooms == 0) {
			columnRooms = 1;
		}
		boolean openedTrap = !hasTrap || OPENED_1X1S.containsValue(TRAP);
		boolean openedChampion = OPENED_1X1S.containsValue(CHAMPION);
		if (special(index)) {
			int specialSize = roomsZ - discoveredFullSpecialColumn + columnRooms;
			boolean trapOutside = false;
			for (Map.Entry<Integer, RoomType> e : OPENED_1X1S.entrySet()) {
				trapOutside |= e.getValue() == TRAP && !special(e.getKey());
			}
			if (specialSize <= puzzleCount || KNOWN_1X1S.size() - columnRooms >= 2) {
				return List.of(PUZZLE);
			}
			if (trapOutside) {
				return List.of(PUZZLE);
			}
			if (specialSize == puzzleCount + 1 || KNOWN_1X1S.size() - columnRooms == 1) {
				return List.of(TRAP, PUZZLE);
			}
			return openedChampion ? List.of(CHAMPION, TRAP) : List.of(CHAMPION, TRAP, PUZZLE);
		}
		int totalPuzzles = columnRooms;
		for (Map.Entry<Integer, RoomType> e : OPENED_1X1S.entrySet()) {
			if (e.getValue() == PUZZLE && !special(e.getKey())) {
				totalPuzzles++;
			}
		}
		if (totalPuzzles == puzzleCount) {
			if (openedTrap) {
				return List.of(CHAMPION);
			}
			return openedChampion ? List.of(TRAP) : List.of(CHAMPION, TRAP);
		}
		// The miniboss is placed first, so a special column with puzzles + 1 rooms means this 1x1 is it.
		if (columnRooms == puzzleCount + 1) {
			return List.of(CHAMPION);
		}
		if (openedTrap && openedChampion) {
			return List.of(PUZZLE);
		}
		if (OPENED_1X1S.size() == specialRoomCount(puzzleCount) - 1) {
			return openedTrap ? List.of(CHAMPION) : List.of(TRAP);
		}
		List<RoomType> out = new ArrayList<>(3);
		out.add(PUZZLE);
		if (!openedTrap) {
			out.add(TRAP);
		}
		if (!openedChampion) {
			out.add(CHAMPION);
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
		int index = index(x, z);
		return index >= 0 && index < 121 && Scanner.GRID[index].state() != RoomState.UNDISCOVERED;
	}

	private static int seenDoors(int index) {
		int x = index % 11;
		int z = index / 11;
		int[][] around = {{x - 1, z}, {x + 1, z}, {x, z - 1}, {x, z + 1}};
		int count = 0;
		for (int[] p : around) {
			if (p[0] < 0 || p[0] > 10 || p[1] < 0 || p[1] > 10) {
				continue;
			}
			if (Scanner.GRID[p[1] * 11 + p[0]] instanceof DoorTile door && door.state() != RoomState.UNDISCOVERED) {
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
		return (128 - start * 2 + 4) / (MapUtils.mapRoomSize + 4);
	}
}
