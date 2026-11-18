package dev.stray.client.dungeon.map;

import dev.stray.client.location.SkyblockLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the dungeon layout from the loaded world: each room's "core" (a hash of the
 * block column in its middle) names it, doorways come from roof heights. Ported from
 * NoammAddons' DungeonScanner/ScanUtils with noamm's permission. Only reads blocks.
 */
public final class Scanner {
	public static final int START_X = -185;
	public static final int START_Z = -185;
	public static final int ROOM_SIZE = 32;
	public static final int HALF_ROOM_SIZE = 15;

	public static final Tile[] GRID = new Tile[121];
	public static final List<DoorTile> DOOR_TILES = new ArrayList<>();
	public static final Map<String, UniqueRoom> UNIQUE_ROOMS = new LinkedHashMap<>();
	public static int witherDoors;
	public static int cryptCount;
	public static int secretCount;
	public static boolean hasScanned;
	private static long lastScan;

	private static final Set<String> IGNORED = Set.of(
		"minecraft:chest", "minecraft:trapped_chest",
		"minecraft:piston_head", "minecraft:moving_piston",
		"minecraft:water", "minecraft:lava",
		"minecraft:fire", "minecraft:soul_fire"
	);
	private static final Map<Block, Integer> TOKENS = new IdentityHashMap<>();

	static {
		Arrays.fill(GRID, UnknownTile.EMPTY);
	}

	private Scanner() {
	}

	public static void reset() {
		Arrays.fill(GRID, UnknownTile.EMPTY);
		DOOR_TILES.clear();
		UNIQUE_ROOMS.clear();
		hasScanned = false;
		witherDoors = 0;
		cryptCount = 0;
		secretCount = 0;
		lastScan = 0L;
		DungeonTree.clearCache();
	}

	public static void tick(Minecraft client) {
		if (SkyblockLocation.inBoss || client.level == null) {
			return;
		}
		if (System.currentTimeMillis() - lastScan >= 250L) {
			scan(client.level);
		}
	}

	private static void scan(ClientLevel level) {
		boolean allLoaded = true;
		if (!hasScanned) {
			for (int x = 0; x <= 10; x++) {
				for (int z = 0; z <= 10; z++) {
					int wx = START_X + x * (ROOM_SIZE >> 1);
					int wz = START_Z + z * (ROOM_SIZE >> 1);
					if (!loaded(level, wx >> 4, wz >> 4)) {
						allLoaded = false;
						continue;
					}
					Tile there = GRID[x + z * 11];
					if (!(there instanceof UnknownTile) && !(there instanceof RoomTile r && r.data.isUnknown())) {
						continue;
					}
					int roof = highestY(level, wx, wz);
					if (roof <= 0) {
						continue;
					}
					Tile tile = scanTile(level, wx, wz, z, x, roof);
					if (tile != null) {
						setTile(z * 11 + x, tile);
					}
				}
			}
		}
		lastScan = System.currentTimeMillis();
		if (allLoaded) {
			hasScanned = true;
		}
	}

	public static void setTile(int index, Tile tile) {
		if (GRID[index] instanceof DoorTile old) {
			DOOR_TILES.remove(old);
		}
		GRID[index] = tile;
		if (tile instanceof DoorTile door) {
			DOOR_TILES.add(door);
		}
		DungeonTree.clearCache();
	}

	private static Tile scanTile(ClientLevel level, int x, int z, int row, int column, int roof) {
		boolean rowEven = (row & 1) == 0;
		boolean columnEven = (column & 1) == 0;
		if (rowEven && columnEven) {
			RoomData data = Rooms.byCore(core(level, x, z));
			if (data == null) {
				return null;
			}
			RoomTile tile = new RoomTile(x, z, data);
			tile.addToUnique(row, column);
			tile.uniqueRoom.highestBlock = roof;
			if (data.type() == RoomType.FAIRY) {
				int counted = 0;
				for (DoorTile door : DOOR_TILES) {
					if (door.type == DoorType.WITHER && contains(door.roomTileIndices, row * 11 + column)) {
						counted++;
					}
				}
				witherDoors += 1 - counted;
			}
			return tile;
		}
		if (!rowEven && !columnEven) {
			// Only the middle of a 2x2.
			if (GRID[column - 1 + (row - 1) * 11] instanceof RoomTile room) {
				RoomTile tile = new RoomTile(x, z, room.data);
				tile.isSeparator = true;
				tile.addToUnique(row, column);
				return tile;
			}
			return null;
		}
		if (roof == 73 || roof == 74 || roof == 81 || roof == 82) {
			DoorType type = DoorType.fromBlock(level.getBlockState(new BlockPos(x, 69, z)).getBlock());
			DoorTile door = new DoorTile(x, z, type);
			if (type == DoorType.WITHER) {
				boolean fairy = false;
				for (RoomTile room : door.roomTiles()) {
					fairy |= room.data.type() == RoomType.FAIRY;
				}
				if (!fairy) {
					witherDoors++;
				}
			}
			return door;
		}
		Tile before = GRID[rowEven ? row * 11 + column - 1 : (row - 1) * 11 + column];
		if (!(before instanceof RoomTile room)) {
			return null;
		}
		if (room.data.type() == RoomType.ENTRANCE) {
			return new DoorTile(x, z, DoorType.ENTRANCE);
		}
		RoomTile tile = new RoomTile(x, z, room.data);
		tile.isSeparator = true;
		tile.addToUnique(row, column);
		return tile;
	}

	private static boolean contains(int[] array, int value) {
		for (int v : array) {
			if (v == value) {
				return true;
			}
		}
		return false;
	}

	public static boolean loaded(ClientLevel level, int chunkX, int chunkZ) {
		return level.hasChunk(chunkX, chunkZ);
	}

	/** NoammAddons' room core: a hash of the block names from y 140 down to 12. */
	public static int core(ClientLevel level, int x, int z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, 0, z);
		int hash = 1;
		for (int y = 140; y >= 12; y--) {
			Block block = level.getBlockState(pos.setY(y)).getBlock();
			Integer token = TOKENS.get(block);
			if (token == null) {
				String name = BuiltInRegistries.BLOCK.getKey(block).toString();
				token = (IGNORED.contains(name) || name.endsWith("_planks") ? "minecraft:air" : name).hashCode();
				TOKENS.put(block, token);
			}
			hash = hash * 31 + token;
		}
		return hash;
	}

	public static int highestY(ClientLevel level, int x, int z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, 0, z);
		for (int y = 256; y >= 0; y--) {
			BlockState state = level.getBlockState(pos.setY(y));
			if (state.isAir() || state.getBlock() == Blocks.GOLD_BLOCK) {
				continue;
			}
			return y;
		}
		return 0;
	}

	/** [gridX, gridZ] of the room cell a world position is in. */
	public static int[] roomGrid(double x, double z) {
		int gx = (int) Math.round((x - START_X) / ROOM_SIZE) * 2;
		int gz = (int) Math.round((z - START_Z) / ROOM_SIZE) * 2;
		return new int[]{Math.max(0, Math.min(10, gx)), Math.max(0, Math.min(10, gz))};
	}

	public static UniqueRoom roomAt(double x, double z) {
		int[] g = roomGrid(x, z);
		return GRID[g[1] * 11 + g[0]] instanceof RoomTile tile ? tile.uniqueRoom : null;
	}
}
