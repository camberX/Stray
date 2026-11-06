package dev.stray.client.dungeon.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stray.client.dungeon.DungeonParty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A finished-looking sample dungeon for the HUD editor and /stray debug dev map. From NoammAddons (CC0). */
final class MapPreview {
	static final int CONNECTOR_SIZE = 4;

	private static Built built;
	private static boolean builtCheater;

	record Built(Tile[] tiles, List<UniqueRoom> rooms, List<MapPlayer> players, Map<Integer, List<RoomType>> predictions) {
	}

	private MapPreview() {
	}

	static Built get() {
		boolean cheater = DungeonMap.cheater();
		if (built != null && builtCheater == cheater) {
			return built;
		}
		builtCheater = cheater;
		built = build();
		return built;
	}

	private static Built build() {
		Tile[] tiles = new Tile[121];
		for (int i = 0; i < tiles.length; i++) {
			tiles[i] = new UnknownTile(0, 0);
		}
		List<UniqueRoom> rooms = new ArrayList<>();
		Map<Integer, List<RoomType>> predictions = new HashMap<>();
		List<MapPlayer> players = new ArrayList<>();
		JsonObject root;
		try (InputStream in = MapPreview.class.getResourceAsStream("/assets/stray/dungeon/map-preview.json")) {
			if (in == null) {
				return new Built(tiles, rooms, players, predictions);
			}
			root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			return new Built(tiles, rooms, players, predictions);
		}
		int crypts = DungeonScanner.cryptCount;
		int secrets = DungeonScanner.secretCount;
		JsonArray doors = root.getAsJsonArray("doors");
		for (JsonElement element : root.getAsJsonArray("rooms")) {
			JsonObject entry = element.getAsJsonObject();
			String name = entry.get("name").getAsString();
			RoomType type = RoomType.parse(entry.get("type").getAsString());
			RoomState state = RoomState.valueOf(entry.get("state").getAsString());
			int foundSecrets = entry.get("foundSecrets").getAsInt();
			List<int[]> cells = new ArrayList<>();
			for (JsonElement cell : entry.getAsJsonArray("tiles")) {
				JsonArray pair = cell.getAsJsonArray();
				cells.add(new int[] {pair.get(0).getAsInt(), pair.get(1).getAsInt()});
			}
			RoomData data = RoomDatabase.byName(name);
			if (data == null) {
				// Without the room list, still show the sample's names.
				int real = 0;
				for (int[] cell : cells) {
					if (cell[0] % 2 == 0 && cell[1] % 2 == 0) {
						real++;
					}
				}
				data = new RoomData(name, type, real <= 1 ? RoomShape.S1x1 : RoomShape.S2x1, List.of(), Math.max(foundSecrets, 0), 0, 0);
			}
			UniqueRoom unique = null;
			for (int[] cell : visibleCells(state, cells, doors)) {
				int arrX = cell[0];
				int arrY = cell[1];
				RoomTile tile = new RoomTile(worldX(arrX), worldZ(arrY), data);
				tile.isSeparator = arrX % 2 == 1 || arrY % 2 == 1;
				tile.setState(displayState(state));
				if (unique == null) {
					unique = new UniqueRoom(arrX, arrY, tile);
					unique.setFoundSecrets(foundSecrets);
				} else {
					unique.addTile(arrX, arrY, tile);
				}
				tile.uniqueRoom = unique;
				tiles[arrY * 11 + arrX] = tile;
			}
			if (unique != null) {
				rooms.add(unique);
			}
			JsonElement predicted = entry.get("predicted");
			if (predicted != null && predicted.isJsonArray() && cells.size() == 1) {
				List<RoomType> types = new ArrayList<>();
				for (JsonElement t : predicted.getAsJsonArray()) {
					types.add(RoomType.parse(t.getAsString()));
				}
				predictions.put(cells.get(0)[1] * 11 + cells.get(0)[0], types);
			}
		}
		for (JsonElement element : doors) {
			JsonObject door = element.getAsJsonObject();
			int x = door.get("x").getAsInt();
			int y = door.get("y").getAsInt();
			DoorTile tile = new DoorTile(worldX(x), worldZ(y), DoorType.valueOf(door.get("type").getAsString()), tiles);
			tile.opened = door.get("opened").getAsBoolean();
			tile.setState(displayState(RoomState.valueOf(door.get("state").getAsString())));
			tiles[y * 11 + x] = tile;
		}
		DungeonScanner.cryptCount = crypts;
		DungeonScanner.secretCount = secrets;

		Minecraft client = Minecraft.getInstance();
		JsonArray list = root.getAsJsonArray("players");
		for (int i = 0; i < list.size(); i++) {
			JsonObject entry = list.get(i).getAsJsonObject();
			DungeonParty.DungeonClass clazz = DungeonParty.DungeonClass.fromName(entry.get("clazz").getAsString());
			String name = i == 0 && client.getUser() != null ? client.getUser().getName() : clazz.label;
			MapPlayer player = new MapPlayer(name);
			player.clazz = clazz;
			player.self = i == 0;
			if (i == 0 && client.player != null) {
				player.skin = client.player.getSkin();
			} else {
				player.skin = DefaultPlayerSkin.get(new UUID(0, i));
			}
			float col = entry.get("col").getAsFloat();
			float row = entry.get("row").getAsFloat();
			float mx = MapCalibration.startX + col * (MapCalibration.mapRoomSize + CONNECTOR_SIZE) + MapCalibration.mapRoomSize / 2f;
			float mz = MapCalibration.startZ + row * (MapCalibration.mapRoomSize + CONNECTOR_SIZE) + MapCalibration.mapRoomSize / 2f;
			player.place(mx, mz, entry.get("yaw").getAsFloat());
			players.add(player);
		}
		// Yourself last, so your head is drawn on top.
		if (!players.isEmpty()) {
			players.add(players.remove(0));
		}
		return new Built(tiles, rooms, players, predictions);
	}

	/** Unopened rooms only show the cells next to a door, like on the real map. */
	private static List<int[]> visibleCells(RoomState state, List<int[]> cells, JsonArray doors) {
		if (builtCheater || (state != RoomState.UNOPENED && state != RoomState.UNDISCOVERED)) {
			return cells;
		}
		List<int[]> out = new ArrayList<>();
		for (int[] cell : cells) {
			for (JsonElement element : doors) {
				JsonObject door = element.getAsJsonObject();
				if (Math.abs(door.get("x").getAsInt() - cell[0]) + Math.abs(door.get("y").getAsInt() - cell[1]) == 1) {
					out.add(cell);
					break;
				}
			}
		}
		return out;
	}

	private static RoomState displayState(RoomState state) {
		if (state != RoomState.UNOPENED && state != RoomState.UNDISCOVERED) {
			return state;
		}
		return builtCheater ? RoomState.UNDISCOVERED : RoomState.UNOPENED;
	}

	private static int worldX(int arrX) {
		return DungeonScanner.START_X + arrX * (DungeonScanner.ROOM_SIZE >> 1);
	}

	private static int worldZ(int arrY) {
		return DungeonScanner.START_Z + arrY * (DungeonScanner.ROOM_SIZE >> 1);
	}
}
