package dev.stray.client.mining;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.location.SkyblockLocation;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Odin WorldScanner port: on each Hollows chunk, match the confirmed
 * vertical block sequences and snap the CH waypoint with that structure's
 * offset. Also walks already-loaded chunks so a late toggle still works.
 */
final class CrystalHollowsScanner {
	private static final int MIN_Y = 0;
	private static final int MAX_Y = 170;
	private static final int CATCH_UP = 3;
	private static final Set<Long> SCANNED = new HashSet<>();

	private enum Quarter {
		NUCLEUS,
		JUNGLE,
		PRECURSOR,
		GOBLIN,
		MITHRIL,
		MAGMA,
		ANY;

		boolean test(int x, int y, int z) {
			return switch (this) {
				case NUCLEUS -> x >= 449 && x <= 576 && z >= 449 && z <= 576;
				case JUNGLE -> x <= 576 && z <= 576;
				case PRECURSOR -> x > 448 && z > 448;
				case GOBLIN -> x <= 576 && z > 448;
				case MITHRIL -> x > 448 && z <= 576;
				case MAGMA -> y < 80;
				case ANY -> true;
			};
		}
	}

	private enum Structure {
		KING(
			CrystalStructure.KING_YOLKAR,
			List.of(Blocks.RED_WOOL, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_STAIRS),
			Quarter.GOBLIN,
			new BlockPos(1, -1, 2)
		),
		QUEEN(
			CrystalStructure.GOBLIN_QUEENS_DEN,
			List.of(Blocks.STONE, Blocks.ACACIA_WOOD, Blocks.ACACIA_WOOD, Blocks.ACACIA_WOOD, Blocks.ACACIA_WOOD, Blocks.CAULDRON),
			Quarter.ANY,
			new BlockPos(0, 5, 0)
		),
		DIVAN(
			CrystalStructure.MINES_OF_DIVAN,
			List.of(Blocks.QUARTZ_PILLAR, Blocks.QUARTZ_STAIRS, Blocks.STONE_BRICK_STAIRS, Blocks.CHISELED_STONE_BRICKS),
			Quarter.MITHRIL,
			new BlockPos(0, 5, 0)
		),
		CITY(
			CrystalStructure.LOST_PRECURSOR_CITY,
			List.of(
				Blocks.STONE_BRICKS,
				Blocks.COBBLESTONE,
				Blocks.COBBLESTONE,
				Blocks.COBBLESTONE,
				Blocks.COBBLESTONE,
				Blocks.COBBLESTONE_STAIRS,
				Blocks.POLISHED_ANDESITE,
				Blocks.POLISHED_ANDESITE,
				Blocks.DARK_OAK_STAIRS
			),
			Quarter.PRECURSOR,
			new BlockPos(24, 0, -17)
		),
		TEMPLE(
			CrystalStructure.JUNGLE_TEMPLE,
			List.of(
				Blocks.BEDROCK,
				Blocks.BEDROCK,
				Blocks.BEDROCK,
				Blocks.BEDROCK,
				Blocks.STONE,
				Blocks.CLAY,
				Blocks.CLAY,
				Blocks.CLAY,
				Blocks.OAK_LEAVES,
				Blocks.OAK_LEAVES,
				Blocks.LIME_TERRACOTTA,
				Blocks.LIME_TERRACOTTA,
				Blocks.GREEN_TERRACOTTA
			),
			Quarter.ANY,
			new BlockPos(-45, 47, -18)
		),
		BAL(
			CrystalStructure.KHAZAD_DUM,
			List.of(
				Blocks.LAVA,
				Blocks.BARRIER,
				Blocks.BARRIER,
				Blocks.BARRIER,
				Blocks.BARRIER,
				Blocks.BARRIER,
				Blocks.BARRIER,
				Blocks.BARRIER,
				Blocks.BARRIER,
				Blocks.BARRIER,
				Blocks.BARRIER
			),
			Quarter.MAGMA,
			new BlockPos(0, 1, 0)
		),
		CORLEONE_DOCK(
			CrystalStructure.CORLEONE,
			list(
				Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS,
				null, null, null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null, null, null, null,
				Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.FIRE, Blocks.STONE_BRICKS
			),
			Quarter.MITHRIL,
			new BlockPos(23, 11, 17)
		),
		CORLEONE_HOLE(
			CrystalStructure.CORLEONE,
			List.of(Blocks.SMOOTH_STONE_SLAB, Blocks.POLISHED_ANDESITE, Blocks.STONE_BRICKS, Blocks.POLISHED_GRANITE),
			Quarter.MITHRIL,
			new BlockPos(-18, -1, 29)
		),
		KEY_GUARDIAN_SPIRAL(
			CrystalStructure.KEY_GUARDIAN,
			List.of(Blocks.JUNGLE_STAIRS, Blocks.JUNGLE_PLANKS, Blocks.GLOWSTONE),
			Quarter.JUNGLE,
			BlockPos.ZERO
		),
		KEY_GUARDIAN_TOWER(
			CrystalStructure.KEY_GUARDIAN,
			List.of(Blocks.STONE, Blocks.POLISHED_GRANITE, Blocks.JUNGLE_SLAB),
			Quarter.JUNGLE,
			BlockPos.ZERO
		),
		XALX(
			CrystalStructure.XALX,
			list(
				Blocks.STONE,
				Blocks.COAL_BLOCK,
				Blocks.FIRE,
				Blocks.NETHER_QUARTZ_ORE,
				null,
				null,
				null,
				null,
				null,
				null,
				null
			),
			Quarter.GOBLIN,
			new BlockPos(-2, 1, -2)
		),
		PETE(
			CrystalStructure.PETE,
			list(Blocks.NETHERRACK, Blocks.FIRE, Blocks.IRON_BARS, null, null, null, null, null, null, null),
			Quarter.GOBLIN,
			BlockPos.ZERO
		),
		ODAWA(
			CrystalStructure.ODAWA,
			List.of(Blocks.JUNGLE_LOG, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_STAIRS, Blocks.JUNGLE_LOG),
			Quarter.JUNGLE,
			BlockPos.ZERO
		),
		GOLDEN_DRAGON(
			CrystalStructure.DRAGONS_LAIR,
			List.of(
				Blocks.STONE,
				Blocks.RED_TERRACOTTA,
				Blocks.RED_TERRACOTTA,
				Blocks.RED_TERRACOTTA,
				Blocks.PLAYER_HEAD,
				Blocks.RED_WOOL
			),
			Quarter.ANY,
			new BlockPos(0, -3, 5)
		);

		final CrystalStructure target;
		final List<Block> blocks;
		final Quarter quarter;
		final BlockPos offset;

		Structure(CrystalStructure target, List<Block> blocks, Quarter quarter, BlockPos offset) {
			this.target = target;
			this.blocks = blocks;
			this.quarter = quarter;
			this.offset = offset;
		}
	}

	private CrystalHollowsScanner() {
	}

	public static void init() {
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
			if (!enabled() || !SkyblockLocation.inCrystalHollows()) {
				return;
			}
			Minecraft client = Minecraft.getInstance();
			if (client.isSameThread()) {
				scanChunk(chunk);
			} else {
				client.execute(() -> scanChunk(chunk));
			}
		});
	}

	static void tick(Minecraft client) {
		if (!enabled() || client.level == null || !SkyblockLocation.inCrystalHollows()) {
			return;
		}
		int done = 0;
		int min = 202 >> 4;
		int max = 823 >> 4;
		for (int chunkX = min; chunkX <= max && done < CATCH_UP; chunkX++) {
			for (int chunkZ = min; chunkZ <= max && done < CATCH_UP; chunkZ++) {
				if (!client.level.hasChunk(chunkX, chunkZ)) {
					continue;
				}
				if (SCANNED.contains(chunkKey(chunkX, chunkZ))) {
					continue;
				}
				scanChunk(client.level.getChunk(chunkX, chunkZ));
				done++;
			}
		}
	}

	static void reset() {
		SCANNED.clear();
	}

	private static boolean enabled() {
		StrayConfig config = StrayConfig.get();
		return config.crystalHollowsScan && (config.crystalHollowsWaypoints || config.crystalHollowsMap);
	}

	private static void scanChunk(LevelChunk chunk) {
		if (chunk == null) {
			return;
		}
		long key = chunkKey(chunk.getPos().x(), chunk.getPos().z());
		if (!SCANNED.add(key)) {
			return;
		}
		if (!enabled()) {
			return;
		}
		if (chunkInNucleus(chunk)) {
			return;
		}
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				int worldX = chunk.getPos().getMinBlockX() + x;
				int worldZ = chunk.getPos().getMinBlockZ() + z;
				if (nucleus(worldX, worldZ)) {
					continue;
				}
				for (int y = MIN_Y; y <= MAX_Y; y++) {
					BlockState state = block(chunk, x, y, z);
					if (state.isAir()) {
						continue;
					}
					match(chunk, state.getBlock(), x, y, z, worldX, worldZ, cursor);
				}
			}
		}
	}

	private static void match(
		LevelChunk chunk,
		Block block,
		int x,
		int y,
		int z,
		int worldX,
		int worldZ,
		BlockPos.MutableBlockPos cursor
	) {
		if (block == Blocks.RED_WOOL) {
			tryMatch(chunk, x, y, z, worldX, worldZ, Structure.KING, cursor);
			return;
		}
		if (block == Blocks.STONE) {
			tryMatch(chunk, x, y, z, worldX, worldZ, Structure.QUEEN, cursor);
			tryMatch(chunk, x, y, z, worldX, worldZ, Structure.KEY_GUARDIAN_TOWER, cursor);
			tryMatch(chunk, x, y, z, worldX, worldZ, Structure.XALX, cursor);
			tryMatch(chunk, x, y, z, worldX, worldZ, Structure.GOLDEN_DRAGON, cursor);
			return;
		}
		if (block == Blocks.QUARTZ_PILLAR) {
			tryMatch(chunk, x, y, z, worldX, worldZ, Structure.DIVAN, cursor);
			return;
		}
		if (block == Blocks.STONE_BRICKS) {
			tryMatch(chunk, x, y, z, worldX, worldZ, Structure.CITY, cursor);
			tryMatch(chunk, x, y, z, worldX, worldZ, Structure.CORLEONE_DOCK, cursor);
			return;
		}
		if (block == Blocks.BEDROCK) {
			tryMatch(chunk, x, y, z, worldX, worldZ, Structure.TEMPLE, cursor);
			return;
		}
		if (block == Blocks.LAVA) {
			tryMatch(chunk, x, y, z, worldX, worldZ, Structure.BAL, cursor);
			return;
		}
		if (block == Blocks.SMOOTH_STONE_SLAB) {
			tryMatch(chunk, x, y, z, worldX, worldZ, Structure.CORLEONE_HOLE, cursor);
			return;
		}
		if (block == Blocks.JUNGLE_STAIRS) {
			tryMatch(chunk, x, y, z, worldX, worldZ, Structure.KEY_GUARDIAN_SPIRAL, cursor);
			return;
		}
		if (block == Blocks.NETHERRACK) {
			tryMatch(chunk, x, y, z, worldX, worldZ, Structure.PETE, cursor);
			return;
		}
		if (block == Blocks.JUNGLE_LOG) {
			tryMatch(chunk, x, y, z, worldX, worldZ, Structure.ODAWA, cursor);
			return;
		}
		if ((block == Blocks.MAGENTA_STAINED_GLASS || block == Blocks.MAGENTA_STAINED_GLASS_PANE)
			&& !nucleus(worldX, worldZ)
			&& !CrystalHollows.locked(CrystalStructure.FAIRY_GROTTO)) {
			CrystalHollows.refine(CrystalStructure.FAIRY_GROTTO, cursor.set(worldX, y, worldZ).immutable(), true);
		}
	}

	private static void tryMatch(
		LevelChunk chunk,
		int x,
		int y,
		int z,
		int worldX,
		int worldZ,
		Structure structure,
		BlockPos.MutableBlockPos cursor
	) {
		if (CrystalHollows.locked(structure.target) || nucleus(worldX, worldZ)) {
			return;
		}
		if (!structure.quarter.test(worldX, y, worldZ)) {
			return;
		}
		if (!sequence(chunk, x, y, z, structure.blocks)) {
			return;
		}
		BlockPos found = cursor.set(worldX, y, worldZ).offset(structure.offset).immutable();
		if (nucleus(found.getX(), found.getZ())) {
			return;
		}
		CrystalHollows.refine(structure.target, found, true);
	}

	static boolean nucleus(int x, int z) {
		return x >= 449 && x <= 576 && z >= 449 && z <= 576;
	}

	private static boolean chunkInNucleus(LevelChunk chunk) {
		int minX = chunk.getPos().getMinBlockX();
		int minZ = chunk.getPos().getMinBlockZ();
		int maxX = minX + 15;
		int maxZ = minZ + 15;
		return nucleus(minX, minZ) && nucleus(maxX, maxZ);
	}

	private static boolean sequence(LevelChunk chunk, int x, int y, int z, List<Block> blocks) {
		for (int i = 0; i < blocks.size(); i++) {
			Block expected = blocks.get(i);
			if (expected == null) {
				continue;
			}
			int py = y + i;
			if (py > 255) {
				return false;
			}
			if (chunk.getBlockState(new BlockPos(x, py, z)).getBlock() != expected) {
				return false;
			}
		}
		return true;
	}

	private static BlockState block(LevelChunk chunk, int x, int y, int z) {
		int index = chunk.getSectionIndex(y);
		LevelChunkSection[] sections = chunk.getSections();
		if (index < 0 || index >= sections.length) {
			return Blocks.AIR.defaultBlockState();
		}
		LevelChunkSection section = sections[index];
		if (section == null || section.hasOnlyAir()) {
			return Blocks.AIR.defaultBlockState();
		}
		return section.getBlockState(x, y & 15, z);
	}

	private static long chunkKey(int chunkX, int chunkZ) {
		return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
	}

	private static List<Block> list(Block... blocks) {
		return Arrays.asList(blocks);
	}
}
