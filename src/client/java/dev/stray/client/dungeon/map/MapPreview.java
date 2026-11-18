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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** NoammAddons' sample dungeon, shown in the HUD editor and outside runs. */
public final class MapPreview {
	public static final int CONNECTOR_SIZE = 4;
	private static Built built;
	private static boolean builtCheater;

	public record Built(Tile[] tiles, List<UniqueRoom> rooms, List<MapPlayer> players, Map<Integer, List<RoomType>> predictions) {
	}

	private MapPreview() {
	}

	public static Built get() {
		boolean cheater = DungeonMap.cheater();
		if (built == null || builtCheater != cheater) {
			builtCheater = cheater;
			built = build();
		}
		return built;
	}

	private static Built build() {
		Tile[] tiles = new Tile[121];
		Arrays.fill(tiles, UnknownTile.EMPTY);
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
		int crypts = Scanner.cryptCount;
		int secrets = Scanner.secretCount;
		JsonArray doors = root.getAsJsonArray("doors");
		for (JsonElement el : root.getAsJsonArray("rooms")) {
			JsonObject room = el.getAsJsonObject();
			String name = room.get("name").getAsString();
			RoomType type = RoomType.parse(room.get("type").getAsString());
			RoomState state = RoomState.valueOf(room.get("state").getAsString());
			RoomData data = Rooms.byName(name);
			if (data == null) {
				data = RoomData.unknown(type);
			}
			List<int[]> cells = new ArrayList<>();
			for (JsonElement c : room.getAsJsonArray("tiles")) {
				JsonArray a = c.getAsJsonArray();
				cells.add(new int[]{a.get(0).getAsInt(), a.get(1).getAsInt()});
			}
			List<int[]> visible = cells;
			if (!builtCheater && (state == RoomState.UNOPENED || state == RoomState.UNDISCOVERED)) {
				visible = new ArrayList<>();
				for (int[] cell : cells) {
					for (JsonElement d : doors) {
						JsonObject door = d.getAsJsonObject();
						if (Math.abs(door.get("x").getAsInt() - cell[0]) + Math.abs(door.get("y").getAsInt() - cell[1]) == 1) {
							visible.add(cell);
							break;
						}
					}
				}
			}
			UniqueRoom unique = null;
			for (int[] cell : visible) {
				RoomTile tile = new RoomTile(worldX(cell[0]), worldZ(cell[1]), data);
				tile.isSeparator = cell[0] % 2 == 1 || cell[1] % 2 == 1;
				tile.setState(display(state));
				if (unique == null) {
					unique = new UniqueRoom(cell[0], cell[1], tile);
					unique.setFoundSecrets(room.get("foundSecrets").getAsInt());
				} else {
					unique.addTile(cell[0], cell[1], tile);
				}
				tile.uniqueRoom = unique;
				tiles[cell[1] * 11 + cell[0]] = tile;
			}
			if (unique != null) {
				rooms.add(unique);
			}
			JsonElement predicted = room.get("predicted");
			if (predicted != null && predicted.isJsonArray() && cells.size() == 1) {
				List<RoomType> types = new ArrayList<>();
				for (JsonElement t : predicted.getAsJsonArray()) {
					types.add(RoomType.parse(t.getAsString()));
				}
				predictions.put(cells.get(0)[1] * 11 + cells.get(0)[0], types);
			}
		}
		for (JsonElement d : doors) {
			JsonObject door = d.getAsJsonObject();
			int x = door.get("x").getAsInt();
			int y = door.get("y").getAsInt();
			DoorTile tile = new DoorTile(worldX(x), worldZ(y), DoorType.valueOf(door.get("type").getAsString()), tiles);
			tile.opened = door.get("opened").getAsBoolean();
			tile.setState(display(RoomState.valueOf(door.get("state").getAsString())));
			tiles[y * 11 + x] = tile;
		}
		Scanner.cryptCount = crypts;
		Scanner.secretCount = secrets;
		Minecraft client = Minecraft.getInstance();
		int i = 0;
		for (JsonElement p : root.getAsJsonArray("players")) {
			JsonObject obj = p.getAsJsonObject();
			DungeonParty.DungeonClass clazz = DungeonParty.DungeonClass.fromName(obj.get("clazz").getAsString());
			String name = i == 0 && client.getUser() != null ? client.getUser().getName() : clazz.label;
			MapPlayer player = new MapPlayer(name);
			player.clazz = clazz;
			player.self = i == 0;
			player.skin = i == 0 && client.player != null ? client.player.getSkin() : DefaultPlayerSkin.get(new UUID(0, i));
			float x = MapUtils.startX + obj.get("col").getAsFloat() * (MapUtils.mapRoomSize + CONNECTOR_SIZE) + MapUtils.mapRoomSize / 2f;
			float z = MapUtils.startZ + obj.get("row").getAsFloat() * (MapUtils.mapRoomSize + CONNECTOR_SIZE) + MapUtils.mapRoomSize / 2f;
			player.target(x, z, obj.get("yaw").getAsFloat());
			players.add(player);
			i++;
		}
		return new Built(tiles, rooms, players, predictions);
	}

	private static RoomState display(RoomState state) {
		if (state != RoomState.UNOPENED && state != RoomState.UNDISCOVERED) {
			return state;
		}
		return builtCheater ? RoomState.UNDISCOVERED : RoomState.UNOPENED;
	}

	private static int worldX(int arrX) {
		return Scanner.START_X + arrX * (Scanner.ROOM_SIZE >> 1);
	}

	private static int worldZ(int arrY) {
		return Scanner.START_Z + arrY * (Scanner.ROOM_SIZE >> 1);
	}
}
