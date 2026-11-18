package dev.stray.client.dungeon;

import dev.stray.client.dungeon.map.Scanner;
import dev.stray.client.dungeon.map.RoomData;
import dev.stray.client.dungeon.map.Rooms;
import dev.stray.client.dungeon.map.RoomShape;
import dev.stray.client.dungeon.map.RoomType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Which dungeon room you stand in and which way it is turned, read from the blocks around you
 * the way NoammAddons does it (ScanUtils / UniqueRoom.findRotation): the room is named by
 * hashing the block column at the middle of its 32x32 cell, and its turn is the corner of the
 * roof that has the blue terracotta. Works anywhere rooms sit on the dungeon grid, so also on
 * servers that paste single rooms, not only in a real run.
 */
public final class DungeonRooms {
	private static final int HALF = Scanner.HALF_ROOM_SIZE;
	private static final int SIZE = Scanner.ROOM_SIZE;
	private static final net.minecraft.world.level.block.Block BLUE = Blocks.DYED_TERRACOTTA.pick(net.minecraft.world.item.DyeColor.BLUE);

	private static Room current;
	private static int cellX = Integer.MIN_VALUE;
	private static int cellZ = Integer.MIN_VALUE;
	private static long nextLook;

	private DungeonRooms() {
	}

	/**
	 * A room: its name, the corner its points are measured from and its turn (0, 90, 180 or
	 * 270), or no turn yet when the roof corner wasn't found (chunks still loading).
	 */
	public record Room(String name, BlockPos corner, int rotation, boolean rotated) {
		/** A world block to the room's own coordinates. */
		public BlockPos toRoom(BlockPos world) {
			return rotate(world.offset(-corner.getX(), 0, -corner.getZ()), rotation);
		}

		/** A point in the room's coordinates to the world. */
		public BlockPos toWorld(BlockPos local) {
			if (!rotated) {
				return null;
			}
			return rotate(local, 360 - rotation).offset(corner.getX(), 0, corner.getZ());
		}
	}

	public static Room current() {
		return current;
	}

	public static void reset() {
		current = null;
		cellX = Integer.MIN_VALUE;
		cellZ = Integer.MIN_VALUE;
	}

	/** Once a tick: looks again when you change cell, and every second while the turn is unknown. */
	public static void update(Minecraft client) {
		ClientLevel level = client.level;
		if (level == null || client.player == null) {
			reset();
			return;
		}
		int cx = (int) Math.rint((client.player.getX() - Scanner.START_X) / SIZE);
		int cz = (int) Math.rint((client.player.getZ() - Scanner.START_Z) / SIZE);
		long now = System.currentTimeMillis();
		boolean moved = cx != cellX || cz != cellZ;
		if (!moved && (current != null && current.rotated() || now < nextLook)) {
			return;
		}
		cellX = cx;
		cellZ = cz;
		nextLook = now + 1000L;
		current = find(level, cx, cz);
	}

	private static int centerX(int cell) {
		return Scanner.START_X + cell * SIZE;
	}

	private static int centerZ(int cell) {
		return Scanner.START_Z + cell * SIZE;
	}

	private static RoomData dataAt(ClientLevel level, int cx, int cz) {
		int x = centerX(cx);
		int z = centerZ(cz);
		if (!Scanner.loaded(level, x >> 4, z >> 4)) {
			return null;
		}
		return Rooms.byCore(Scanner.core(level, x, z));
	}

	private static Room find(ClientLevel level, int cx, int cz) {
		RoomData data = dataAt(level, cx, cz);
		if (data == null) {
			return null;
		}
		// The room's cells: this one and every touching cell with the same room in it.
		List<int[]> cells = new ArrayList<>();
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		queue.add(new int[] {cx, cz});
		while (!queue.isEmpty() && cells.size() < 4) {
			int[] cell = queue.poll();
			boolean seen = false;
			for (int[] c : cells) {
				seen |= c[0] == cell[0] && c[1] == cell[1];
			}
			if (seen) {
				continue;
			}
			if (cell[0] != cx || cell[1] != cz) {
				RoomData there = dataAt(level, cell[0], cell[1]);
				if (there == null || !there.name().equals(data.name())) {
					continue;
				}
			}
			cells.add(cell);
			queue.add(new int[] {cell[0] + 1, cell[1]});
			queue.add(new int[] {cell[0] - 1, cell[1]});
			queue.add(new int[] {cell[0], cell[1] + 1});
			queue.add(new int[] {cell[0], cell[1] - 1});
		}
		int minX = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (int[] cell : cells) {
			minX = Math.min(minX, centerX(cell[0]));
			maxX = Math.max(maxX, centerX(cell[0]));
			minZ = Math.min(minZ, centerZ(cell[1]));
			maxZ = Math.max(maxZ, centerZ(cell[1]));
		}
		if (data.type() == RoomType.FAIRY) {
			return new Room(data.name(), new BlockPos(minX - HALF, 0, minZ - HALF), 0, true);
		}
		Room unturned = new Room(data.name(), BlockPos.ZERO, 0, false);
		if (cells.size() < data.shape().tileCount) {
			// Not all of it loaded yet.
			return unturned;
		}
		int roof = Scanner.highestY(level, centerX(cx), centerZ(cz));
		if (roof <= 0) {
			return unturned;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		if (data.shape() != RoomShape.SL) {
			int[] xs = {minX - HALF, maxX + HALF, maxX + HALF, minX - HALF};
			int[] zs = {minZ - HALF, minZ - HALF, maxZ + HALF, maxZ + HALF};
			for (int i = 0; i < 4; i++) {
				pos.set(xs[i], roof, zs[i]);
				if (Scanner.loaded(level, xs[i] >> 4, zs[i] >> 4) && level.getBlockState(pos).getBlock() == BLUE) {
					return new Room(data.name(), new BlockPos(xs[i], 0, zs[i]), i * 90, true);
				}
			}
			return unturned;
		}
		int[][] corners = {{-HALF, -HALF}, {HALF, -HALF}, {HALF, HALF}, {-HALF, HALF}};
		for (int[] cell : cells) {
			for (int i = 0; i < 4; i++) {
				int x = centerX(cell[0]) + corners[i][0];
				int z = centerZ(cell[1]) + corners[i][1];
				if (!Scanner.loaded(level, x >> 4, z >> 4)) {
					return unturned;
				}
				if (level.getBlockState(pos.set(x, roof, z)).getBlock() == BLUE) {
					return new Room(data.name(), new BlockPos(x, 0, z), i * 90, true);
				}
			}
		}
		return unturned;
	}

	/** Turns a position about the origin, like NoammAddons' BlockPos.rotate. */
	static BlockPos rotate(BlockPos pos, int degrees) {
		return switch (((degrees % 360) + 360) % 360) {
			case 90 -> new BlockPos(pos.getZ(), pos.getY(), -pos.getX());
			case 180 -> new BlockPos(-pos.getX(), pos.getY(), -pos.getZ());
			case 270 -> new BlockPos(-pos.getZ(), pos.getY(), pos.getX());
			default -> pos;
		};
	}
}
