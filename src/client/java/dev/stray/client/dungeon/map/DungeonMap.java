package dev.stray.client.dungeon.map;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.dungeon.DungeonParty;
import dev.stray.client.location.SkyblockLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NoammAddons' dungeon map (ported with noamm's permission): rooms come from the loaded
 * world, what's opened and where teammates are comes from the map item in hotbar slot 9.
 * Only reads what the client already has.
 */
public final class DungeonMap {
	private static final Pattern SECRETS = Pattern.compile("(\\d+)/(\\d+) Secrets");
	private static final Pattern PUZZLES = Pattern.compile("Puzzles: \\((\\d)\\)");
	public static int maxPuzzleCount;
	public static boolean started;
	private static final Map<String, MapPlayer> PLAYERS = new LinkedHashMap<>();
	private static ClientLevel lastLevel;
	private static boolean wasInDungeon;
	private static int ticks;
	private static Field decorationsField;
	private static boolean decorationsFailed;

	private DungeonMap() {
	}

	private static boolean preview = Boolean.getBoolean("stray.mapPreview");

	public static boolean previewActive() {
		return preview;
	}

	public static void setPreview(boolean on) {
		preview = on;
	}

	public static boolean cheater() {
		return !dev.stray.Edition.LEGIT && StrayConfig.get().dungeonMapCheater;
	}

	public static void tick(Minecraft client) {
		if (client.level != lastLevel) {
			lastLevel = client.level;
			reset();
		}
		if (client.level == null || client.player == null) {
			return;
		}
		if (!SkyblockLocation.inDungeon) {
			if (wasInDungeon) {
				reset();
			}
			wasInDungeon = false;
			return;
		}
		wasInDungeon = true;
		// The scan also feeds Etherwarp aim's room names, so it runs with the map off too.
		Scanner.tick(client);
		DungeonTree.tick(client);
		if (!StrayConfig.get().dungeonMapEnabled) {
			return;
		}
		syncPlayers(client);
		if (maxPuzzleCount == 0 && ++ticks % 20 == 0) {
			readPuzzleCount(client);
		}
		readMap(client);
	}

	public static void reset() {
		Scanner.reset();
		MapUtils.reset();
		SpecialColumn.reset();
		DungeonTree.resetRooms();
		PLAYERS.clear();
		maxPuzzleCount = 0;
		started = false;
	}

	private static void syncPlayers(Minecraft client) {
		String self = client.getUser() == null ? "" : client.getUser().getName();
		List<DungeonParty.Member> team = DungeonParty.all();
		if (!team.isEmpty()) {
			Set<String> names = new HashSet<>();
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
			if (player.self) {
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
		if (data == null || !MapUtils.calibrate(data)) {
			return;
		}
		started = true;
		updateRooms(client, data);
		updatePlayers(data);
	}

	private static void updateRooms(Minecraft client, MapItemSavedData data) {
		if (SkyblockLocation.inBoss) {
			return;
		}
		MapPlayer self = self();
		if (self != null && self.dead) {
			return;
		}
		HotbarScanner.updateMap(data);
		for (int x = 0; x <= 10; x++) {
			for (int z = 0; z <= 10; z++) {
				int index = z * 11 + x;
				Tile room = Scanner.GRID[index];
				Tile mapTile = HotbarScanner.tile(x, z);
				if (room instanceof UnknownTile) {
					Scanner.setTile(index, mapTile);
					if (mapTile instanceof RoomTile mapRoom) {
						for (RoomTile connected : HotbarScanner.connected(x, z)) {
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
				if (mapTile instanceof RoomTile m && room instanceof RoomTile r && m.data.type() != r.data.type() && r.data.name().equals(m.data.name())) {
					r.data = m.data;
				}
				if (mapTile instanceof DoorTile m && room instanceof DoorTile r && m.type == DoorType.WITHER && r.type != DoorType.WITHER) {
					r.type = m.type;
				}
				if (room instanceof DoorTile door && (door.type == DoorType.ENTRANCE || door.type == DoorType.WITHER || door.type == DoorType.BLOOD)) {
					if (mapTile instanceof DoorTile m && m.type == DoorType.WITHER) {
						door.opened = false;
					} else if (!door.opened) {
						if (Scanner.loaded(client.level, door.x >> 4, door.z >> 4)) {
							BlockState state = client.level.getBlockState(new BlockPos(door.x, 69, door.z));
							if (state.isAir() || state.is(Blocks.BARRIER)) {
								door.opened = true;
							}
						} else if (mapTile instanceof DoorTile m && m.state() == RoomState.DISCOVERED) {
							if (door.type == DoorType.BLOOD) {
								for (Tile tile : Scanner.GRID) {
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
		// The markers are numbered in tab-list order. The party is read from an unordered set of
		// tab entries, so its order can differ, which put the wrong head on a marker.
		List<String> order = dev.stray.client.dungeon.DungeonParty.tabOrder(Minecraft.getInstance());
		living.sort(java.util.Comparator.comparingInt(p -> {
			int at = order.indexOf(p.name);
			return at < 0 ? Integer.MAX_VALUE : at;
		}));
		for (Map.Entry<String, MapDecoration> entry : decorations.entrySet()) {
			String key = entry.getKey();
			if (entry.getValue().type().value() == MapDecorationTypes.FRAME.value()) {
				if (self != null) {
					self.icon = key;
				}
				continue;
			}
			if (!key.isEmpty() && Character.isDigit(key.charAt(key.length() - 1))) {
				int index = key.charAt(key.length() - 1) - '0';
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
			if (decoration != null) {
				player.target((decoration.x() + 128) >> 1, (decoration.y() + 128) >> 1, decoration.rot() * 22.5f);
			}
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

	/** Action bar "6/10 Secrets": secrets found in the room you stand in. */
	public static void onActionBar(String plain) {
		if (plain == null || !SkyblockLocation.inDungeon || !StrayConfig.get().dungeonMapEnabled) {
			return;
		}
		Matcher m = SECRETS.matcher(plain.replaceAll("§.", ""));
		Minecraft client = Minecraft.getInstance();
		if (!m.find() || client.player == null) {
			return;
		}
		int found = Integer.parseInt(m.group(1));
		int max = Integer.parseInt(m.group(2));
		int[] grid = Scanner.roomGrid(client.player.getX(), client.player.getZ());
		if (Scanner.GRID[grid[1] * 11 + grid[0]] instanceof RoomTile tile && !"Unknown".equals(tile.data.name())
			&& tile.uniqueRoom != null && tile.data.secrets() == max) {
			tile.uniqueRoom.setFoundSecrets(found);
		}
	}

	public static MapPlayer self() {
		for (MapPlayer player : PLAYERS.values()) {
			if (player.self) {
				return player;
			}
		}
		return null;
	}

	/** Heads to draw: living teammates, then yourself on top. */
	public static List<MapPlayer> heads() {
		List<MapPlayer> out = new ArrayList<>();
		MapPlayer self = null;
		for (MapPlayer player : PLAYERS.values()) {
			if (player.self) {
				self = player;
			} else if (!player.dead && player.placed()) {
				out.add(player);
			}
		}
		if (self != null && self.placed()) {
			out.add(self);
		}
		return out;
	}
}
