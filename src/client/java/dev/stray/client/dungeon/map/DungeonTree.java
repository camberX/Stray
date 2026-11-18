package dev.stray.client.dungeon.map;

import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Room graph from the doors: the blood rush path and the fairy room's wither door. */
public final class DungeonTree {
	private static Map<UniqueRoom, Set<UniqueRoom>> splits;
	private static List<UniqueRoom> bloodRush;
	private static UniqueRoom fairyRoom;
	private static UniqueRoom nextAfterFairy;
	private static UniqueRoom beforeFairy;
	private static UniqueRoom currentRoom;
	private static UniqueRoom lastRoom;

	private DungeonTree() {
	}

	public static void clearCache() {
		splits = null;
		bloodRush = null;
		fairyRoom = null;
		nextAfterFairy = null;
		beforeFairy = null;
	}

	public static void resetRooms() {
		currentRoom = null;
		lastRoom = null;
	}

	/** Tracks which room you are in (NoammAddons' currentRoom / lastKnownRoom). */
	public static void tick(Minecraft client) {
		if (client.player == null) {
			return;
		}
		UniqueRoom room = Scanner.roomAt(client.player.getX(), client.player.getZ());
		if (room != currentRoom) {
			lastRoom = currentRoom;
			currentRoom = room;
		}
	}

	public static UniqueRoom currentRoom() {
		return currentRoom;
	}

	public static boolean isFairy(DoorTile door) {
		if (currentRoom == null || lastRoom == null) {
			return false;
		}
		bloodRush();
		if (fairyRoom == null || beforeFairy == null || nextAfterFairy == null) {
			return false;
		}
		if (beforeFairy != currentRoom && beforeFairy != lastRoom) {
			return false;
		}
		List<UniqueRoom> rooms = rooms(door);
		if (rooms.size() != 2) {
			return false;
		}
		UniqueRoom a = rooms.get(0);
		UniqueRoom b = rooms.get(1);
		return (a == fairyRoom && b == nextAfterFairy) || (a == nextAfterFairy && b == fairyRoom);
	}

	private static List<UniqueRoom> rooms(DoorTile door) {
		List<UniqueRoom> out = new ArrayList<>(2);
		for (RoomTile tile : door.roomTiles()) {
			if (tile.uniqueRoom != null) {
				out.add(tile.uniqueRoom);
			}
		}
		return out;
	}

	public static List<UniqueRoom> bloodRush() {
		if (bloodRush != null) {
			return bloodRush;
		}
		Map<UniqueRoom, Set<UniqueRoom>> graph = splits();
		UniqueRoom start = null;
		UniqueRoom target = null;
		for (UniqueRoom room : graph.keySet()) {
			if (room.data.type() == RoomType.ENTRANCE) {
				start = room;
			} else if (room.data.type() == RoomType.BLOOD) {
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
			for (UniqueRoom next : graph.getOrDefault(room, Set.of())) {
				if (!previous.containsKey(next)) {
					previous.put(next, room);
					queue.add(next);
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
		Collections.reverse(path);
		bloodRush = path;
		for (int i = 0; i < path.size(); i++) {
			if (path.get(i).data.type() == RoomType.FAIRY) {
				if (i > 0 && i < path.size() - 1) {
					beforeFairy = path.get(i - 1);
					fairyRoom = path.get(i);
					nextAfterFairy = path.get(i + 1);
				}
				break;
			}
		}
		return path;
	}

	private static Map<UniqueRoom, Set<UniqueRoom>> splits() {
		if (splits != null) {
			return splits;
		}
		Map<UniqueRoom, Set<UniqueRoom>> graph = new HashMap<>();
		for (Tile tile : Scanner.GRID) {
			if (!(tile instanceof DoorTile door)) {
				continue;
			}
			List<UniqueRoom> rooms = rooms(door);
			if (rooms.size() != 2 || rooms.get(0) == rooms.get(1)) {
				continue;
			}
			graph.computeIfAbsent(rooms.get(0), k -> new HashSet<>()).add(rooms.get(1));
			graph.computeIfAbsent(rooms.get(1), k -> new HashSet<>()).add(rooms.get(0));
		}
		splits = graph;
		return graph;
	}
}
