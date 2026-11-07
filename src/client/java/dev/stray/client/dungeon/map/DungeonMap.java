package dev.stray.client.dungeon.map;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.dungeon.DungeonParty;
import dev.stray.client.location.SkyblockLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NoammAddons' dungeon map, ported (CC0): rooms come from the world (names, shapes) and
 * from the map item in your last hotbar slot (what is opened, checkmarks, where your
 * teammates are). Only reads what the client already has; sends nothing.
 */
public final class DungeonMap {
	private static final Pattern SECRETS = Pattern.compile("(\\d+)/(\\d+) Secrets");
	private static final Pattern PUZZLES = Pattern.compile("Puzzles: \\((\\d)\\)");

	/** Puzzle count from the tab list, for guessing unopened 1x1s. */
	public static int maxPuzzleCount;

	private static final Map<String, MapPlayer> PLAYERS = new LinkedHashMap<>();
	private static boolean preview;
	private static ClientLevel lastLevel;
	private static boolean wasInDungeon;
	private static int ticks;
	private static Field decorationsField;
	private static boolean decorationsFailed;

	private DungeonMap() {
	}

	/** The sample dungeon (for testing outside a run, e.g. /stray debug dev map). */
	public static void setPreview(boolean on) {
		preview = on;
	}

	public static boolean previewActive() {
		return preview;
	}

	/** Full edition only: also shows rooms nobody has opened yet. */
	public static boolean cheater() {
		return !dev.stray.Edition.LEGIT && StrayConfig.get().dungeonMapCheater;
	}

	public static void tick(Minecraft client) {
		if (client.level != lastLevel) {
			lastLevel = client.level;
			resetRun();
		}
		if (client.level == null || client.player == null) {
			return;
		}
		if (!SkyblockLocation.inDungeon) {
			if (wasInDungeon) {
				resetRun();
			}
			wasInDungeon = false;
			return;
		}
		wasInDungeon = true;
		if (!StrayConfig.get().dungeonMapEnabled) {
			return;
		}
		syncPlayers(client);
		if (maxPuzzleCount == 0 && ++ticks % 20 == 0) {
			readPuzzleCount(client);
		}
		DungeonScanner.tick(client);
		readMap(client);
	}

	private static void resetRun() {
		DungeonScanner.reset();
		MapCalibration.reset();
		HotbarMapScanner.calibrate();
		SpecialColumn.reset();
		DungeonMapRenderer.reset();
		PLAYERS.clear();
		maxPuzzleCount = 0;
	}

	private static void syncPlayers(Minecraft client) {
		String self = client.getUser() == null ? "" : client.getUser().getName();
		java.util.List<DungeonParty.Member> team = DungeonParty.all();
		if (!team.isEmpty()) {
			// Drop players who left the party so their head doesn't linger on the map.
			java.util.Set<String> names = new java.util.HashSet<>();
			for (DungeonParty.Member member : team) {
				names.add(member.name);
			}
			PLAYERS.keySet().retainAll(names);
		}
		for (DungeonParty.Member member : team) {
			MapPlayer player = PLAYERS.computeIfAbsent(member.name, MapPlayer::new);
			player.clazz = member.clazz;
			player.skin = member.skin;
			player.dead = member.dead;
			player.entity = member.entity;
			player.self = member.name.equals(self);
			if (player.self && player.entity == null) {
				player.entity = client.player;
			}
		}
	}

	private static void readPuzzleCount(Minecraft client) {
		if (client.getConnection() == null) {
			return;
		}
		for (PlayerInfo info : client.getConnection().getListedOnlinePlayers()) {
			Component display = info.getTabListDisplayName();
			if (display == null) {
				continue;
			}
			Matcher m = PUZZLES.matcher(display.getString());
			if (m.find()) {
				maxPuzzleCount = Integer.parseInt(m.group(1));
				return;
			}
		}
	}

	private static void readMap(Minecraft client) {
		ItemStack stack = client.player.getInventory().getItem(8);
		MapId id = stack.get(DataComponents.MAP_ID);
		if (id == null) {
			return;
		}
		MapItemSavedData data = client.level.getMapData(id);
		if (data == null) {
			return;
		}
		if (MapCalibration.calibrate(data)) {
			updateRooms(client, data);
			updatePlayers(data);
		}
	}

	private static void updateRooms(Minecraft client, MapItemSavedData data) {
		if (SkyblockLocation.inBoss) {
			return;
		}
		MapPlayer self = selfPlayer();
		if (self != null && self.dead) {
			return;
		}
		HotbarMapScanner.updateMap(data);
		for (int x = 0; x <= 10; x++) {
			for (int z = 0; z <= 10; z++) {
				int index = z * 11 + x;
				Tile room = DungeonScanner.GRID[index];
				Tile mapTile = HotbarMapScanner.tile(x, z);
				if (room instanceof UnknownTile) {
					DungeonScanner.setTile(index, mapTile);
					if (mapTile instanceof RoomTile mapRoom) {
						for (RoomTile connected : HotbarMapScanner.connected(x, z)) {
							if (!"Unknown".equals(connected.data.name())) {
								mapRoom.addToUnique(z, x, connected.data.name());
								break;
							}
						}
					}
					continue;
				}
				boolean puzzle = mapTile instanceof RoomTile m && room instanceof RoomTile && m.data.type() == RoomType.PUZZLE;
				if (mapTile.state().ordinal() < room.state().ordinal() || puzzle) {
					room.setState(mapTile.state());
				}
				if (mapTile instanceof RoomTile m && room instanceof RoomTile r && m.data.type() != r.data.type()) {
					if (r.data.name().equals(m.data.name())) {
						r.data = m.data;
					}
				}
				if (mapTile instanceof DoorTile m && room instanceof DoorTile r && m.type == DoorType.WITHER && r.type != DoorType.WITHER) {
					r.type = m.type;
				}
				if (room instanceof DoorTile door && (door.type == DoorType.ENTRANCE || door.type == DoorType.WITHER || door.type == DoorType.BLOOD)) {
					if (mapTile instanceof DoorTile m && m.type == DoorType.WITHER) {
						door.opened = false;
					} else if (!door.opened) {
						if (DungeonScanner.loaded(client.level, door.x >> 4, door.z >> 4)) {
							BlockState state = client.level.getBlockState(new net.minecraft.core.BlockPos(door.x, 69, door.z));
							if (state.isAir() || state.is(Blocks.BARRIER)) {
								door.opened = true;
							}
						} else if (mapTile instanceof DoorTile m && m.state() == RoomState.DISCOVERED) {
							if (door.type == DoorType.BLOOD) {
								for (Tile tile : DungeonScanner.GRID) {
									if (tile instanceof RoomTile r && r.data.type() == RoomType.BLOOD) {
										if (r.state() != RoomState.UNOPENED) {
											door.opened = true;
										}
										break;
									}
								}
							} else {
								door.opened = true;
							}
						}
					}
				}
			}
		}
		SpecialColumn.update();
	}

	private static void updatePlayers(MapItemSavedData data) {
		Map<String, MapDecoration> decorations = decorations(data);
		if (decorations == null) {
			return;
		}
		List<MapPlayer> living = new ArrayList<>();
		MapPlayer self = null;
		for (MapPlayer player : PLAYERS.values()) {
			if (player.self) {
				self = player;
			} else if (!player.dead) {
				living.add(player);
			}
		}
		for (Map.Entry<String, MapDecoration> entry : decorations.entrySet()) {
			String key = entry.getKey();
			MapDecoration decoration = entry.getValue();
			if (decoration.type().value() == MapDecorationTypes.FRAME.value()) {
				if (self != null) {
					self.icon = key;
				}
				continue;
			}
			if (key.isEmpty()) {
				continue;
			}
			char last = key.charAt(key.length() - 1);
			if (Character.isDigit(last)) {
				int index = last - '0';
				if (index < living.size()) {
					living.get(index).icon = key;
				}
			}
		}
		for (MapPlayer player : PLAYERS.values()) {
			if (player.dead) {
				continue;
			}
			MapDecoration decoration = decorations.get(player.icon);
			if (decoration == null) {
				continue;
			}
			player.target((decoration.x() + 128) >> 1, (decoration.y() + 128) >> 1, decoration.rot() * 22.5f);
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, MapDecoration> decorations(MapItemSavedData data) {
		if (decorationsFailed) {
			return null;
		}
		try {
			if (decorationsField == null) {
				decorationsField = MapItemSavedData.class.getDeclaredField("decorations");
				decorationsField.setAccessible(true);
			}
			return (Map<String, MapDecoration>) decorationsField.get(data);
		} catch (ReflectiveOperationException | RuntimeException e) {
			decorationsFailed = true;
			return null;
		}
	}

	/** Action bar "6/10 Secrets": the secrets found in the room you are standing in. */
	public static void onActionBar(String plain) {
		if (plain == null || !SkyblockLocation.inDungeon || !StrayConfig.get().dungeonMapEnabled) {
			return;
		}
		Matcher m = SECRETS.matcher(plain.replaceAll("§.", ""));
		if (!m.find()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		int found = Integer.parseInt(m.group(1));
		int max = Integer.parseInt(m.group(2));
		int[] grid = DungeonScanner.roomGrid(client.player.getX(), client.player.getZ());
		if (!(DungeonScanner.GRID[grid[1] * 11 + grid[0]] instanceof RoomTile tile) || "Unknown".equals(tile.data.name())) {
			return;
		}
		if (tile.uniqueRoom != null && tile.uniqueRoom.foundSecrets() != found && tile.data.secrets() == max) {
			tile.uniqueRoom.setFoundSecrets(found);
		}
	}

	static MapPlayer selfPlayer() {
		for (MapPlayer player : PLAYERS.values()) {
			if (player.self) {
				return player;
			}
		}
		return null;
	}

	/** Heads to draw in a real run: living teammates, then yourself on top. */
	static List<MapPlayer> heads() {
		List<MapPlayer> out = new ArrayList<>();
		MapPlayer self = null;
		for (MapPlayer player : PLAYERS.values()) {
			if (player.self) {
				self = player;
			} else if (!player.dead) {
				out.add(player);
			}
		}
		if (self != null) {
			out.add(self);
		}
		return out;
	}
}
