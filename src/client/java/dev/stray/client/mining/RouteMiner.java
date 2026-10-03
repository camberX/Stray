package dev.stray.client.mining;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.location.SkyblockLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Magma Fields ruby route. Each waypoint stays up while stained glass or a
 * glass pane is still inside a 3-block cube around it, and hides once that
 * glass is gone. A later scan that finds glass again puts the waypoint back,
 * which is the vein respawning.
 *
 * At most one cube is read per tick. The vein you are standing in is preferred
 * and refreshes about twice a second; the rest of the route cycles behind it.
 */
public final class RouteMiner {
	private static final int RADIUS = 3;
	private static final int NEAR_SQ = 48 * 48;
	private static final int CLOSE_SQ = 16 * 16;
	private static final int NEAR_GAP = 10;
	private static final int FAR_GAP = 40;
	private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
	private static Set<Block> glassBlocks;
	private static boolean[] glass = new boolean[0];
	private static boolean[] seen = new boolean[0];
	private static int[] lastTick = new int[0];
	private static List<CrystalHollows.Mark> visible = List.of();
	private static boolean dirty = true;
	private static int tick;
	private static int cursor;
	private static int far;
	private static String server = "";

	private RouteMiner() {
	}

	public static boolean enabled() {
		StrayConfig config = StrayConfig.get();
		return config.routeMiner && config.crystalHollowsRubyRoute && SkyblockLocation.inCrystalHollows();
	}

	public static List<CrystalHollows.Mark> marks() {
		if (!enabled()) {
			return List.of();
		}
		if (dirty) {
			rebuild();
		}
		return visible;
	}

	public static void tick(Minecraft client) {
		if (client == null) {
			return;
		}
		String now = SkyblockLocation.server;
		if (!now.equals(server)) {
			server = now;
			if (!now.isBlank()) {
				reset();
			}
		}
		if (!enabled() || client.level == null || client.player == null) {
			return;
		}
		List<CrystalHollows.Mark> all = CrystalHollows.rubyMarks();
		ensure(all.size());
		tick++;
		int index = pick(client.level, client.player.blockPosition(), all, tick % 5 == 0);
		if (index < 0) {
			return;
		}
		cursor = (index + 1) % all.size();
		sample(client.level, all.get(index).pos(), index);
		lastTick[index] = tick;
	}

	public static void reset() {
		for (int i = 0; i < seen.length; i++) {
			seen[i] = false;
			glass[i] = false;
			lastTick[i] = -1000;
		}
		cursor = 0;
		far = 0;
		dirty = true;
	}

	private static int pick(Level level, BlockPos player, List<CrystalHollows.Mark> all, boolean farTurn) {
		int size = all.size();
		if (size == 0) {
			return -1;
		}
		if (farTurn) {
			for (int step = 0; step < size; step++) {
				int index = (far + step) % size;
				BlockPos pos = all.get(index).pos();
				if (distSq(player, pos) <= NEAR_SQ || !areaLoaded(level, pos)) {
					continue;
				}
				if (tick - lastTick[index] < FAR_GAP) {
					continue;
				}
				far = (index + 1) % size;
				return index;
			}
		}
		int near = -1;
		for (int step = 0; step < size; step++) {
			int index = (cursor + step) % size;
			BlockPos pos = all.get(index).pos();
			int gap = distSq(player, pos) <= CLOSE_SQ ? NEAR_GAP : NEAR_GAP * 2;
			if (distSq(player, pos) > NEAR_SQ || tick - lastTick[index] < gap || !areaLoaded(level, pos)) {
				continue;
			}
			near = index;
			break;
		}
		if (near >= 0) {
			return near;
		}
		for (int step = 0; step < size; step++) {
			int index = (cursor + step) % size;
			BlockPos pos = all.get(index).pos();
			if (tick - lastTick[index] < FAR_GAP || !areaLoaded(level, pos)) {
				continue;
			}
			return index;
		}
		return -1;
	}

	private static void sample(Level level, BlockPos pos, int index) {
		boolean found = hasGlass(level, pos);
		if (!seen[index] || glass[index] != found) {
			dirty = true;
		}
		seen[index] = true;
		glass[index] = found;
	}

	private static void rebuild() {
		List<CrystalHollows.Mark> all = CrystalHollows.rubyMarks();
		ensure(all.size());
		List<CrystalHollows.Mark> out = new ArrayList<>();
		for (int i = 0; i < all.size(); i++) {
			if (!seen[i] || glass[i]) {
				out.add(all.get(i));
			}
		}
		visible = List.copyOf(out);
		dirty = false;
	}

	private static boolean hasGlass(Level level, BlockPos pos) {
		int x = pos.getX();
		int y = pos.getY();
		int z = pos.getZ();
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dy = -RADIUS; dy <= RADIUS; dy++) {
				for (int dz = -RADIUS; dz <= RADIUS; dz++) {
					Block block = level.getBlockState(CURSOR.set(x + dx, y + dy, z + dz)).getBlock();
					if (isGlass(block)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static boolean areaLoaded(Level level, BlockPos pos) {
		int x = pos.getX();
		int y = pos.getY();
		int z = pos.getZ();
		return level.hasChunkAt(pos)
			&& level.hasChunkAt(CURSOR.set(x - RADIUS, y, z - RADIUS))
			&& level.hasChunkAt(CURSOR.set(x - RADIUS, y, z + RADIUS))
			&& level.hasChunkAt(CURSOR.set(x + RADIUS, y, z - RADIUS))
			&& level.hasChunkAt(CURSOR.set(x + RADIUS, y, z + RADIUS));
	}

	private static boolean isGlass(Block block) {
		if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR) {
			return false;
		}
		return glassSet().contains(block);
	}

	private static Set<Block> glassSet() {
		Set<Block> set = glassBlocks;
		if (set != null) {
			return set;
		}
		set = new HashSet<>();
		for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
			String path = id.getPath();
			if (path.equals("glass")
				|| path.equals("glass_pane")
				|| path.equals("tinted_glass")
				|| path.endsWith("_stained_glass")
				|| path.endsWith("_stained_glass_pane")) {
				set.add(BuiltInRegistries.BLOCK.getValue(id));
			}
		}
		glassBlocks = set;
		return set;
	}

	private static void ensure(int size) {
		if (seen.length == size) {
			return;
		}
		glass = new boolean[size];
		seen = new boolean[size];
		lastTick = new int[size];
		for (int i = 0; i < size; i++) {
			lastTick[i] = -1000;
		}
		dirty = true;
	}

	private static double distSq(BlockPos player, BlockPos pos) {
		double dx = player.getX() - pos.getX();
		double dy = player.getY() - pos.getY();
		double dz = player.getZ() - pos.getZ();
		return dx * dx + dy * dy + dz * dz;
	}
}
