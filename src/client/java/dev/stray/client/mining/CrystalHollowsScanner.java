package dev.stray.client.mining;

import dev.stray.client.config.StrayConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Walks loaded Hollows chunks and NPC nametags, then snaps structure
 * waypoints onto the real building when the first mark was only an
 * entrance, compass guess, or shared coordinate.
 */
final class CrystalHollowsScanner {
	private static final int RADIUS = 56;
	private static final int PERIOD = 20;
	private static final int CELL = 16;
	private static final int MID = 512;

	private static int tick;

	private CrystalHollowsScanner() {
	}

	static void tick(Minecraft client) {
		if (!StrayConfig.get().crystalHollowsScan) {
			return;
		}
		if (client.player == null || client.level == null) {
			return;
		}
		scanEntities(client.level);
		if (++tick % PERIOD != 0) {
			return;
		}
		scanBlocks(client.level, client.player);
	}

	static void reset() {
		tick = 0;
	}

	private static void scanEntities(ClientLevel level) {
		for (Entity entity : level.entitiesForRendering()) {
			String name = plain(entity);
			CrystalStructure structure = fromName(name);
			if (structure == null) {
				continue;
			}
			BlockPos pos = entity.blockPosition();
			if (structure == CrystalStructure.MINES_OF_DIVAN) {
				pos = keeperCenter(name, pos);
			}
			CrystalHollows.refine(structure, pos, true);
		}
	}

	private static void scanBlocks(ClientLevel level, LocalPlayer player) {
		Map<CrystalStructure, List<BlockPos>> hits = new EnumMap<>(CrystalStructure.class);
		BlockPos origin = player.blockPosition();
		int minX = origin.getX() - RADIUS;
		int maxX = origin.getX() + RADIUS;
		int minY = Math.max(31, origin.getY() - RADIUS);
		int maxY = Math.min(188, origin.getY() + RADIUS);
		int minZ = origin.getZ() - RADIUS;
		int maxZ = origin.getZ() + RADIUS;
		int radiusSq = RADIUS * RADIUS;
		int minChunkX = minX >> 4;
		int maxChunkX = maxX >> 4;
		int minChunkZ = minZ >> 4;
		int maxChunkZ = maxZ >> 4;
		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				if (!level.hasChunk(chunkX, chunkZ)) {
					continue;
				}
				scanChunk(
					level.getChunk(chunkX, chunkZ),
					origin,
					radiusSq,
					minX,
					maxX,
					minY,
					maxY,
					minZ,
					maxZ,
					hits
				);
			}
		}
		for (var entry : hits.entrySet()) {
			BlockPos center = cluster(entry.getValue());
			if (center != null && CrystalHollows.inside(center)) {
				CrystalHollows.refine(entry.getKey(), center, true);
			}
		}
	}

	private static void scanChunk(
		LevelChunk chunk,
		BlockPos origin,
		int radiusSq,
		int minX,
		int maxX,
		int minY,
		int maxY,
		int minZ,
		int maxZ,
		Map<CrystalStructure, List<BlockPos>> hits
	) {
		LevelChunkSection[] sections = chunk.getSections();
		int minSectionY = chunk.getMinSectionY();
		for (int index = 0; index < sections.length; index++) {
			LevelChunkSection section = sections[index];
			if (section == null || section.hasOnlyAir() || !section.maybeHas(CrystalHollowsScanner::interesting)) {
				continue;
			}
			int baseY = (minSectionY + index) << 4;
			if (baseY + 15 < minY || baseY > maxY) {
				continue;
			}
			int baseX = chunk.getPos().getMinBlockX();
			int baseZ = chunk.getPos().getMinBlockZ();
			for (int ly = 0; ly < 16; ly++) {
				int y = baseY + ly;
				if (y < minY || y > maxY) {
					continue;
				}
				for (int lx = 0; lx < 16; lx++) {
					int x = baseX + lx;
					if (x < minX || x > maxX) {
						continue;
					}
					for (int lz = 0; lz < 16; lz++) {
						int z = baseZ + lz;
						if (z < minZ || z > maxZ) {
							continue;
						}
						long dx = x - origin.getX();
						long dy = y - origin.getY();
						long dz = z - origin.getZ();
						if (dx * dx + dy * dy + dz * dz > radiusSq) {
							continue;
						}
						CrystalStructure structure = classify(section.getBlockState(lx, ly, lz), x, y, z);
						if (structure == null) {
							continue;
						}
						hits.computeIfAbsent(structure, key -> new ArrayList<>()).add(new BlockPos(x, y, z));
					}
				}
			}
		}
	}

	private static boolean interesting(BlockState state) {
		Block block = state.getBlock();
		return block == Blocks.LIME_TERRACOTTA
			|| block == Blocks.GREEN_TERRACOTTA
			|| block == Blocks.DARK_PRISMARINE
			|| block == Blocks.GOLD_BLOCK
			|| block == Blocks.QUARTZ_STAIRS
			|| block == Blocks.ORANGE_TERRACOTTA
			|| block == Blocks.LIGHT_BLUE_TERRACOTTA
			|| block == Blocks.CYAN_TERRACOTTA
			|| block == Blocks.SEA_LANTERN
			|| block == Blocks.NETHER_BRICKS
			|| block == Blocks.RED_NETHER_BRICKS
			|| block == Blocks.NETHER_BRICK_FENCE
			|| block == Blocks.END_STONE
			|| block == Blocks.PURPUR_BLOCK
			|| fairy(state);
	}

	private static CrystalStructure classify(BlockState state, int x, int y, int z) {
		if (state == null) {
			return null;
		}
		Block block = state.getBlock();
		if (in(x, z, true, true) && (block == Blocks.LIME_TERRACOTTA || block == Blocks.GREEN_TERRACOTTA)) {
			return CrystalStructure.JUNGLE_TEMPLE;
		}
		if (in(x, z, false, true) && (block == Blocks.DARK_PRISMARINE || block == Blocks.GOLD_BLOCK || block == Blocks.QUARTZ_STAIRS)) {
			return CrystalStructure.MINES_OF_DIVAN;
		}
		if (in(x, z, true, false) && block == Blocks.ORANGE_TERRACOTTA) {
			return CrystalStructure.GOBLIN_QUEENS_DEN;
		}
		if (in(x, z, false, false) && (block == Blocks.LIGHT_BLUE_TERRACOTTA || block == Blocks.CYAN_TERRACOTTA || block == Blocks.SEA_LANTERN)) {
			return CrystalStructure.LOST_PRECURSOR_CITY;
		}
		if (y <= 90 && (block == Blocks.NETHER_BRICKS || block == Blocks.RED_NETHER_BRICKS || block == Blocks.NETHER_BRICK_FENCE)) {
			return CrystalStructure.KHAZAD_DUM;
		}
		if (in(x, z, false, false) && (block == Blocks.END_STONE || block == Blocks.PURPUR_BLOCK)) {
			return CrystalStructure.DRAGONS_LAIR;
		}
		if (fairy(state)) {
			return CrystalStructure.FAIRY_GROTTO;
		}
		return null;
	}

	private static boolean fairy(BlockState state) {
		String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		return path.contains("stained_glass") && !path.contains("pane");
	}

	private static boolean in(int x, int z, boolean west, boolean north) {
		boolean isWest = x < MID;
		boolean isNorth = z < MID;
		return isWest == west && isNorth == north;
	}

	private static BlockPos cluster(List<BlockPos> points) {
		if (points == null || points.size() < 6) {
			return null;
		}
		int bestCount = 0;
		int bestCx = 0;
		int bestCy = 0;
		int bestCz = 0;
		Map<Long, Integer> cells = new HashMap<>();
		for (BlockPos pos : points) {
			int next = cells.merge(cellKey(pos), 1, Integer::sum);
			if (next > bestCount) {
				bestCount = next;
				bestCx = Math.floorDiv(pos.getX(), CELL);
				bestCy = Math.floorDiv(pos.getY(), CELL);
				bestCz = Math.floorDiv(pos.getZ(), CELL);
			}
		}
		long sumX = 0;
		long sumY = 0;
		long sumZ = 0;
		int used = 0;
		for (BlockPos pos : points) {
			int cx = Math.floorDiv(pos.getX(), CELL);
			int cy = Math.floorDiv(pos.getY(), CELL);
			int cz = Math.floorDiv(pos.getZ(), CELL);
			if (Math.abs(cx - bestCx) > 1 || Math.abs(cy - bestCy) > 1 || Math.abs(cz - bestCz) > 1) {
				continue;
			}
			sumX += pos.getX();
			sumY += pos.getY();
			sumZ += pos.getZ();
			used++;
		}
		if (used < 6) {
			return null;
		}
		return new BlockPos((int) (sumX / used), (int) (sumY / used), (int) (sumZ / used));
	}

	private static long cellKey(BlockPos pos) {
		int cx = Math.floorDiv(pos.getX(), CELL) + 512;
		int cy = Math.floorDiv(pos.getY(), CELL) + 64;
		int cz = Math.floorDiv(pos.getZ(), CELL) + 512;
		return ((long) cx << 32) ^ ((long) cy << 16) ^ cz;
	}

	private static BlockPos keeperCenter(String name, BlockPos pos) {
		String lower = name.toLowerCase(Locale.ROOT);
		if (lower.contains("diamond")) {
			return pos.offset(33, 0, 3);
		}
		if (lower.contains("lapis")) {
			return pos.offset(-33, 0, -3);
		}
		if (lower.contains("emerald")) {
			return pos.offset(-3, 0, 33);
		}
		if (lower.contains("gold")) {
			return pos.offset(3, 0, -33);
		}
		return pos;
	}

	private static CrystalStructure fromName(String name) {
		if (name.isEmpty()) {
			return null;
		}
		String lower = name.toLowerCase(Locale.ROOT);
		if (lower.contains("keeper of")) {
			return CrystalStructure.MINES_OF_DIVAN;
		}
		if (lower.contains("kalhuiki")) {
			return CrystalStructure.JUNGLE_TEMPLE;
		}
		if (lower.contains("king yolkar") || lower.equals("yolkar")) {
			return CrystalStructure.KING_YOLKAR;
		}
		if (lower.contains("odawa")) {
			return CrystalStructure.ODAWA;
		}
		if (lower.contains("xalx")) {
			return CrystalStructure.XALX;
		}
		if (lower.contains("golden dragon")) {
			return CrystalStructure.DRAGONS_LAIR;
		}
		if (lower.contains("corleone")) {
			return CrystalStructure.CORLEONE;
		}
		if (lower.contains("key guardian")) {
			return CrystalStructure.KEY_GUARDIAN;
		}
		return null;
	}

	private static String plain(Entity entity) {
		if (entity.getCustomName() != null) {
			String custom = entity.getCustomName().getString().replaceAll("§.", "").trim();
			if (!custom.isEmpty()) {
				return custom;
			}
		}
		return entity.getName().getString().replaceAll("§.", "").trim();
	}
}
