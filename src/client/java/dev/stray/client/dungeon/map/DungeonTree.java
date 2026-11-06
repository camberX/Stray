package dev.stray.client.dungeon.map;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The rooms as a graph: the blood rush path, and which door leads out of the fairy room. From NoammAddons (CC0). */
public final class DungeonTree {
	private static Map<UniqueRoom, Set<UniqueRoom>> splits;
	private static List<UniqueRoom> bloodRush;
	private static UniqueRoom fairyRoom;
	private static UniqueRoom nextAfterFairy;
	private static UniqueRoom beforeFairy;

	private DungeonTree() {
	}

	public static void clearCache() {
		splits = null;
		bloodRush = null;
		fairyRoom = null;
		nextAfterFairy = null;
		beforeFairy = null;
	}

	public static boolean isFairy(DoorTile door) {
		UniqueRoom current = DungeonScanner.currentRoom;
		UniqueRoom last = DungeonScanner.lastKnownRoom;
		if (current == null || last == null) {
			return false;
		}
		bloodRush();
		if (fairyRoom == null || beforeFairy == null || nextAfterFairy == null) {
			return false;
		}
		if (beforeFairy != current && beforeFairy != last) {
			return false;
		}
		List<UniqueRoom> rooms = new ArrayList<>();
		for (RoomTile tile : door.roomTiles()) {
			if (tile.uniqueRoom != null) {
				rooms.add(tile.uniqueRoom);
			}
		}
		if (rooms.size() != 2) {
			return false;
		}
		UniqueRoom r1 = rooms.get(0);
		UniqueRoom r2 = rooms.get(1);
		return (r1 == fairyRoom && r2 == nextAfterFairy) || (r1 == nextAfterFairy && r2 == fairyRoom);
	}

	public static List<UniqueRoom> bloodRush() {
		if (bloodRush != null) {
			return bloodRush;
		}
		Map<UniqueRoom, Set<UniqueRoom>> graph = splits();
		UniqueRoom start = null;
		UniqueRoom target = null;
		for (UniqueRoom room : graph.keySet()) {
			if (start == null && room.data.type() == RoomType.ENTRANCE) {
				start = room;
			}
			if (target == null && room.data.type() == RoomType.BLOOD) {
				target = room;
			}
		}
		if (start == null || target == null) {
			return List.of();
		}
		ArrayDeque<UniqueRoom> queue = new ArrayDeque<>();
		Map<UniqueRoom, UniqueRoom> previous = new HashMap<>();
		queue.add(start);
		previous.put(start, null);
		while (!queue.isEmpty()) {
			UniqueRoom room = queue.removeFirst();
			if (room == target) {
				break;
			}
			for (UniqueRoom neighbor : graph.getOrDefault(room, Set.of())) {
				if (!previous.containsKey(neighbor)) {
					previous.put(neighbor, room);
					queue.add(neighbor);
				}
			}
		}
		if (!previous.containsKey(target)) {
			return List.of();
		}
		List<UniqueRoom> path = new ArrayList<>();
		for (UniqueRoom room = target; room != null; room = previous.get(room)) {
			path.add(room);
		}
		java.util.Collections.reverse(path);
		bloodRush = path;
		int fairy = -1;
		for (int i = 0; i < path.size(); i++) {
			if (path.get(i).data.type() == RoomType.FAIRY) {
				fairy = i;
				break;
			}
		}
		if (fairy > 0 && fairy < path.size() - 1) {
			beforeFairy = path.get(fairy - 1);
			fairyRoom = path.get(fairy);
			nextAfterFairy = path.get(fairy + 1);
		}
		return path;
	}

	private static Map<UniqueRoom, Set<UniqueRoom>> splits() {
		if (splits != null) {
			return splits;
		}
		Map<UniqueRoom, Set<UniqueRoom>> graph = new LinkedHashMap<>();
		for (Tile tile : DungeonScanner.GRID) {
			if (!(tile instanceof DoorTile door)) {
				continue;
			}
			List<UniqueRoom> rooms = new ArrayList<>();
			for (RoomTile room : door.roomTiles()) {
				if (room.uniqueRoom != null) {
					rooms.add(room.uniqueRoom);
				}
			}
			if (rooms.size() != 2 || rooms.get(0) == rooms.get(1)) {
				continue;
			}
			graph.computeIfAbsent(rooms.get(0), k -> new LinkedHashSet<>()).add(rooms.get(1));
			graph.computeIfAbsent(rooms.get(1), k -> new LinkedHashSet<>()).add(rooms.get(0));
		}
		splits = graph;
		return graph;
	}
}
