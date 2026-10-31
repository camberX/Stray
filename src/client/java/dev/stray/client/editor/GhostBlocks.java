package dev.stray.client.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stray.client.config.StrayConfig;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.material.FluidState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The world editor's client-side block layer. Nothing here is ever sent to the server: blocks
 * are swapped in the client's own copy of the chunks, so they render like real blocks, but
 * the player's movement, collisions, clicks and fluids keep using what the server really has
 * there (see {@link #physical}). That keeps edits walk-through and keeps the client's
 * movement exactly what the server expects.
 *
 * <p>Two kinds of edits, saved per {@link EditArea} (island, dungeon floor, Kuudra tier):
 * <ul>
 * <li>placed blocks: one position, one block (air means "removed");</li>
 * <li>replace rules: every block of one type becomes another, in every chunk that loads.</li>
 * </ul>
 */
public final class GhostBlocks {
	/** Biggest number of placed positions one area keeps. */
	public static final int MAX_PLACED = 2_000_000;
	public static final int MAX_RULES = 64;
	private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("stray").resolve("editor");

	public static final class Rule {
		public final Block from;
		public final BlockState to;
		private final Reference2ObjectOpenHashMap<BlockState, BlockState> forward = new Reference2ObjectOpenHashMap<>();
		private final Reference2ObjectOpenHashMap<BlockState, BlockState> back = new Reference2ObjectOpenHashMap<>();

		Rule(Block from, BlockState to) {
			this.from = from;
			this.to = to;
		}

		BlockState apply(BlockState original) {
			BlockState out = forward.get(original);
			if (out == null) {
				out = copyProperties(original, to);
				forward.put(original, out);
			}
			return out;
		}

		BlockState undo(BlockState visible) {
			BlockState out = back.get(visible);
			if (out == null) {
				out = copyProperties(visible, from.defaultBlockState());
				back.put(visible, out);
			}
			return out;
		}
	}

	/** One area's saved edits. */
	private static final class Area {
		final String key;
		final Long2ObjectOpenHashMap<BlockState> placed = new Long2ObjectOpenHashMap<>();
		final Long2ObjectOpenHashMap<LongOpenHashSet> byChunk = new Long2ObjectOpenHashMap<>();
		final List<Rule> rules = new ArrayList<>();

		Area(String key) {
			this.key = key;
		}

		void put(long pos, BlockState state) {
			if (placed.put(pos, state) == null) {
				int x = BlockPos.getX(pos);
				int z = BlockPos.getZ(pos);
				byChunk.computeIfAbsent(ChunkPos.pack(x >> 4, z >> 4), k -> new LongOpenHashSet()).add(pos);
			}
		}

		BlockState remove(long pos) {
			BlockState old = placed.remove(pos);
			if (old != null) {
				long chunk = ChunkPos.pack(BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4);
				LongOpenHashSet set = byChunk.get(chunk);
				if (set != null) {
					set.remove(pos);
					if (set.isEmpty()) {
						byChunk.remove(chunk);
					}
				}
			}
			return old;
		}

		boolean empty() {
			return placed.isEmpty() && rules.isEmpty();
		}
	}

	private static Area area;
	private static ClientLevel level;
	private static boolean applied;
	/** Server's real block at every placed position in a loaded chunk. */
	private static final Long2ObjectOpenHashMap<BlockState> TRUTH = new Long2ObjectOpenHashMap<>();
	/** Section key -> 4096-bit set of positions a replace rule changed. */
	private static final Long2ObjectOpenHashMap<long[]> MARKS = new Long2ObjectOpenHashMap<>();
	private static final Reference2ObjectOpenHashMap<Block, Rule> BY_FROM = new Reference2ObjectOpenHashMap<>();
	private static final Reference2ObjectOpenHashMap<Block, Rule> BY_TO = new Reference2ObjectOpenHashMap<>();

	/** True while anything is applied in the current level: the hot-path gate. */
	private static volatile boolean any;
	/** True during the client tick and the crosshair pick: block reads see the server's blocks. */
	private static boolean physics;
	private static boolean pickSaved;
	private static int suspend;
	private static boolean reentry;
	private static Thread main;

	private static boolean dirty;
	private static long saveAt;
	/** Where-am-I bookkeeping. */
	private static String pendingKey;
	private static int pendingTicks;
	private static int settleTicks;
	private static ClientLevel seenLevel;

	private GhostBlocks() {
	}

	// ------------------------------------------------------------------ physics view

	/** Client tick start: from here on block reads made by vanilla see the server's blocks. */
	public static void beginTick() {
		main = Thread.currentThread();
		physics = true;
	}

	public static void endTick() {
		physics = false;
	}

	public static void beginPick() {
		pickSaved = physics;
		physics = true;
	}

	public static void endPick() {
		physics = pickSaved;
	}

	/** Runs {@code task} seeing the edited (visible) blocks even inside the client tick. */
	public static void visible(Runnable task) {
		suspend++;
		try {
			task.run();
		} finally {
			suspend--;
		}
	}

	private static boolean physicsNow(LevelChunk chunk) {
		return any && physics && suspend == 0 && chunk.getLevel() == level && Thread.currentThread() == main;
	}

	/** {@code LevelChunk.getBlockState} return value: the server's block while in the physics view. */
	public static BlockState physical(LevelChunk chunk, BlockPos pos, BlockState visible) {
		if (!physicsNow(chunk)) {
			return visible;
		}
		return truthAt(pos.getX(), pos.getY(), pos.getZ(), visible);
	}

	public static FluidState physicalFluid(LevelChunk chunk, int x, int y, int z, FluidState visible) {
		if (!physicsNow(chunk)) {
			return visible;
		}
		BlockState truth = truthOrNull(x, y, z, null);
		return truth == null ? visible : truth.getFluidState();
	}

	private static BlockState truthAt(int x, int y, int z, BlockState visible) {
		BlockState truth = truthOrNull(x, y, z, visible);
		return truth == null ? visible : truth;
	}

	/** Server's block at a changed position, or null when the position isn't changed. */
	private static BlockState truthOrNull(int x, int y, int z, BlockState visibleOrNull) {
		BlockState placed = TRUTH.get(BlockPos.asLong(x, y, z));
		if (placed != null) {
			return placed;
		}
		if (!MARKS.isEmpty() && marked(x, y, z)) {
			BlockState visible = visibleOrNull;
			if (visible == null) {
				LevelChunk chunk = loadedChunk(x >> 4, z >> 4);
				if (chunk == null) {
					return null;
				}
				LevelChunkSection section = sectionAt(chunk, y);
				if (section == null) {
					return null;
				}
				visible = section.getBlockState(x & 15, y & 15, z & 15);
			}
			Rule rule = BY_TO.get(visible.getBlock());
			return rule == null ? null : rule.undo(visible);
		}
		return null;
	}

	// ------------------------------------------------------------------ server updates

	/**
	 * A block update from the server (or a prediction being settled). Returns {@code state}
	 * to let it through unchanged, null to swallow it (the position shows a placed block;
	 * only the remembered server block changes), or another state to show instead.
	 */
	public static BlockState fromServer(ClientLevel from, BlockPos pos, BlockState state) {
		if (reentry || !any || from != level) {
			return state;
		}
		long key = pos.asLong();
		if (area.placed.containsKey(key)) {
			TRUTH.put(key, state);
			return null;
		}
		unmark(pos.getX(), pos.getY(), pos.getZ());
		Rule rule = BY_FROM.get(state.getBlock());
		if (rule == null) {
			return state;
		}
		mark(pos.getX(), pos.getY(), pos.getZ());
		return rule.apply(state);
	}

	/** Lets a substituted server update through without being looked at again. */
	public static void passThrough(Runnable task) {
		boolean saved = reentry;
		reentry = true;
		try {
			task.run();
		} finally {
			reentry = saved;
		}
	}

	// ------------------------------------------------------------------ chunks

	/** A chunk arrived (or was re-sent) from the server. */
	public static void onChunkLoaded(LevelChunk chunk) {
		if (chunk == null || !applied || chunk.getLevel() != level) {
			return;
		}
		forgetChunk(chunk.getPos().x(), chunk.getPos().z());
		applyChunk(chunk, true);
	}

	public static void onChunkDropped(ChunkPos pos) {
		if (level == null || pos == null) {
			return;
		}
		forgetChunk(pos.x(), pos.z());
	}

	private static void forgetChunk(int cx, int cz) {
		if (area != null) {
			LongOpenHashSet placed = area.byChunk.get(ChunkPos.pack(cx, cz));
			if (placed != null) {
				for (long pos : placed) {
					TRUTH.remove(pos);
				}
			}
		}
		if (!MARKS.isEmpty() && level != null) {
			for (int sy = level.getMinSectionY(); sy <= level.getMaxSectionY(); sy++) {
				MARKS.remove(SectionPos.asLong(cx, sy, cz));
			}
		}
	}

	private static void applyChunk(LevelChunk chunk, boolean redraw) {
		int cx = chunk.getPos().x();
		int cz = chunk.getPos().z();
		LongOpenHashSet placed = area.byChunk.get(ChunkPos.pack(cx, cz));
		suspend++;
		try {
			BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
			if (placed != null) {
				for (long pos : placed) {
					cursor.set(pos);
					TRUTH.put(pos, chunk.getBlockState(cursor));
				}
			}
			LevelChunkSection[] sections = chunk.getSections();
			boolean[] changed = new boolean[sections.length];
			if (!BY_FROM.isEmpty()) {
				for (int i = 0; i < sections.length; i++) {
					changed[i] = rulesOnSection(chunk, i, placed, cursor);
				}
			}
			if (placed != null) {
				for (long pos : placed) {
					cursor.set(pos);
					BlockState want = area.placed.get(pos);
					if (want != null && chunk.getBlockState(cursor) != want) {
						chunk.setBlockState(cursor, want, 0);
						int index = chunk.getSectionIndex(cursor.getY());
						if (index >= 0 && index < changed.length) {
							changed[index] = true;
						}
					}
				}
			}
			if (redraw) {
				redraw(chunk, changed);
			}
		} finally {
			suspend--;
		}
	}

	private static boolean rulesOnSection(LevelChunk chunk, int index, LongOpenHashSet placed, BlockPos.MutableBlockPos cursor) {
		LevelChunkSection section = chunk.getSection(index);
		if (section == null || section.hasOnlyAir() || !section.maybeHas(state -> BY_FROM.containsKey(state.getBlock()))) {
			return false;
		}
		int bx = chunk.getPos().x() << 4;
		int bz = chunk.getPos().z() << 4;
		int sy = chunk.getSectionYFromSectionIndex(index);
		int by = sy << 4;
		long sectionKey = SectionPos.asLong(chunk.getPos().x(), sy, chunk.getPos().z());
		long[] bits = MARKS.get(sectionKey);
		boolean changed = false;
		for (int y = 0; y < 16; y++) {
			for (int z = 0; z < 16; z++) {
				for (int x = 0; x < 16; x++) {
					BlockState state = section.getBlockState(x, y, z);
					Rule rule = BY_FROM.get(state.getBlock());
					if (rule == null) {
						continue;
					}
					int bit = (y << 8) | (z << 4) | x;
					if (bits != null && (bits[bit >> 6] & (1L << bit)) != 0) {
						continue;
					}
					if (placed != null && placed.contains(BlockPos.asLong(bx + x, by + y, bz + z))) {
						continue;
					}
					BlockState next = rule.apply(state);
					if (next == state) {
						continue;
					}
					setInSection(chunk, section, x, y, z, bx, by, bz, state, next, cursor);
					if (bits == null) {
						bits = new long[64];
						MARKS.put(sectionKey, bits);
					}
					bits[bit >> 6] |= 1L << bit;
					changed = true;
				}
			}
		}
		return changed;
	}

	/**
	 * Swaps one block. Blocks that change how light passes go through the chunk so the light
	 * is redone (grass to glass lights up the dirt below); the rest go straight into the
	 * section, which is much faster for big replace rules.
	 */
	private static void setInSection(LevelChunk chunk, LevelChunkSection section, int x, int y, int z, int bx, int by, int bz,
		BlockState old, BlockState next, BlockPos.MutableBlockPos cursor) {
		suspend++;
		try {
			if (LightEngine.hasDifferentLightProperties(old, next)) {
				chunk.setBlockState(cursor.set(bx + x, by + y, bz + z), next, 0);
				return;
			}
			if (old.hasBlockEntity()) {
				chunk.removeBlockEntity(cursor.set(bx + x, by + y, bz + z));
			}
			section.setBlockState(x, y, z, next, false);
		} finally {
			suspend--;
		}
	}

	/** Puts the server's blocks back in one chunk (placed and replaced). */
	private static void revertChunk(LevelChunk chunk) {
		int cx = chunk.getPos().x();
		int cz = chunk.getPos().z();
		LevelChunkSection[] sections = chunk.getSections();
		boolean[] changed = new boolean[sections.length];
		suspend++;
		try {
			BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
			LongOpenHashSet placed = area == null ? null : area.byChunk.get(ChunkPos.pack(cx, cz));
			if (placed != null) {
				for (long pos : placed) {
					BlockState truth = TRUTH.remove(pos);
					if (truth == null) {
						continue;
					}
					cursor.set(pos);
					if (chunk.getBlockState(cursor) != truth) {
						chunk.setBlockState(cursor, truth, 0);
						int index = chunk.getSectionIndex(cursor.getY());
						if (index >= 0 && index < changed.length) {
							changed[index] = true;
						}
					}
				}
			}
			for (int i = 0; i < sections.length; i++) {
				int sy = chunk.getSectionYFromSectionIndex(i);
				long[] bits = MARKS.remove(SectionPos.asLong(cx, sy, cz));
				if (bits == null) {
					continue;
				}
				LevelChunkSection section = sections[i];
				for (int bit = 0; bit < 4096; bit++) {
					if ((bits[bit >> 6] & (1L << bit)) == 0) {
						continue;
					}
					int x = bit & 15;
					int z = (bit >> 4) & 15;
					int y = bit >> 8;
					BlockState visible = section.getBlockState(x, y, z);
					Rule rule = BY_TO.get(visible.getBlock());
					if (rule != null) {
						setInSection(chunk, section, x, y, z, cx << 4, chunk.getSectionYFromSectionIndex(i) << 4, cz << 4, visible, rule.undo(visible), cursor);
						changed[i] = true;
					}
				}
			}
		} finally {
			suspend--;
		}
		redraw(chunk, changed);
	}

	private static void redraw(LevelChunk chunk, boolean[] changed) {
		if (level == null) {
			return;
		}
		for (int i = 0; i < changed.length; i++) {
			if (changed[i]) {
				level.setSectionDirtyWithNeighbors(chunk.getPos().x(), chunk.getSectionYFromSectionIndex(i), chunk.getPos().z());
			}
		}
	}

	private static void forEachLoaded(java.util.function.Consumer<LevelChunk> action) {
		Minecraft client = Minecraft.getInstance();
		if (level == null || client.player == null) {
			return;
		}
		int radius = Math.max(client.options.renderDistance().get(), 2) + 5;
		int pcx = client.player.blockPosition().getX() >> 4;
		int pcz = client.player.blockPosition().getZ() >> 4;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				LevelChunk chunk = level.getChunkSource().getChunk(pcx + dx, pcz + dz, ChunkStatus.FULL, false);
				if (chunk != null) {
					action.accept(chunk);
				}
			}
		}
	}

	private static LevelChunk loadedChunk(int cx, int cz) {
		return level == null ? null : level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
	}

	private static LevelChunkSection sectionAt(LevelChunk chunk, int y) {
		int index = chunk.getSectionIndex(y);
		return index < 0 || index >= chunk.getSections().length ? null : chunk.getSection(index);
	}

	private static void applyAll() {
		if (area == null || level == null) {
			return;
		}
		applied = true;
		refreshAny();
		forEachLoaded(chunk -> applyChunk(chunk, true));
	}

	private static void revertAll() {
		if (!applied) {
			return;
		}
		forEachLoaded(GhostBlocks::revertChunk);
		TRUTH.clear();
		MARKS.clear();
		applied = false;
		refreshAny();
	}

	private static void refreshAny() {
		any = applied && area != null && !area.empty();
	}

	// ------------------------------------------------------------------ marks

	private static boolean marked(int x, int y, int z) {
		long[] bits = MARKS.get(SectionPos.asLong(x >> 4, y >> 4, z >> 4));
		if (bits == null) {
			return false;
		}
		int bit = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);
		return (bits[bit >> 6] & (1L << bit)) != 0;
	}

	private static void mark(int x, int y, int z) {
		long[] bits = MARKS.computeIfAbsent(SectionPos.asLong(x >> 4, y >> 4, z >> 4), k -> new long[64]);
		int bit = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);
		bits[bit >> 6] |= 1L << bit;
	}

	private static void unmark(int x, int y, int z) {
		if (MARKS.isEmpty()) {
			return;
		}
		long[] bits = MARKS.get(SectionPos.asLong(x >> 4, y >> 4, z >> 4));
		if (bits != null) {
			int bit = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);
			bits[bit >> 6] &= ~(1L << bit);
		}
	}

	// ------------------------------------------------------------------ edits

	public static boolean ready() {
		return area != null && level != null;
	}

	public static String areaKey() {
		return area == null ? null : area.key;
	}

	public static int placedCount() {
		return area == null ? 0 : area.placed.size();
	}

	public static List<Rule> rules() {
		return area == null ? List.of() : Collections.unmodifiableList(area.rules);
	}

	/** What the editor put at {@code pos}, or null when the position isn't edited. */
	public static BlockState placedAt(BlockPos pos) {
		return area == null ? null : area.placed.get(pos.asLong());
	}

	/** What the world shows at {@code pos} (edits included). */
	public static BlockState shown(BlockPos pos) {
		if (level == null) {
			return Blocks.AIR.defaultBlockState();
		}
		BlockState[] out = new BlockState[1];
		visible(() -> out[0] = level.getBlockState(pos));
		return out[0];
	}

	/**
	 * Shows {@code state} at {@code pos} (air removes the block). {@code null} undoes the edit
	 * there and brings back the server's block. Returns false when the area is full.
	 */
	public static boolean set(BlockPos pos, BlockState state) {
		if (area == null) {
			return false;
		}
		long key = pos.asLong();
		BlockState old = area.placed.get(key);
		if (Objects.equals(old, state)) {
			return true;
		}
		if (state == null) {
			area.remove(key);
			if (applied) {
				BlockState truth = TRUTH.remove(key);
				if (truth != null) {
					Rule rule = BY_FROM.get(truth.getBlock());
					if (rule != null) {
						mark(pos.getX(), pos.getY(), pos.getZ());
						show(pos, rule.apply(truth));
					} else {
						show(pos, truth);
					}
				}
			}
		} else {
			if (old == null && area.placed.size() >= MAX_PLACED) {
				return false;
			}
			area.put(key, state);
			if (applied && loadedChunk(pos.getX() >> 4, pos.getZ() >> 4) != null) {
				if (!TRUTH.containsKey(key)) {
					BlockState visible = shown(pos);
					BlockState truth = visible;
					if (marked(pos.getX(), pos.getY(), pos.getZ())) {
						Rule rule = BY_TO.get(visible.getBlock());
						if (rule != null) {
							truth = rule.undo(visible);
						}
						unmark(pos.getX(), pos.getY(), pos.getZ());
					}
					TRUTH.put(key, truth);
				}
				show(pos, state);
			}
		}
		changed();
		return true;
	}

	private static void show(BlockPos pos, BlockState state) {
		suspend++;
		boolean saved = reentry;
		reentry = true;
		try {
			level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		} finally {
			reentry = saved;
			suspend--;
		}
	}

	/** Adds (or changes) the rule that turns every {@code from} into {@code to}. */
	public static boolean addRule(Block from, BlockState to) {
		if (area == null || from == null || to == null || from == Blocks.AIR || to.getBlock() == from) {
			return false;
		}
		List<Rule> next = new ArrayList<>();
		for (Rule rule : area.rules) {
			if (rule.from != from) {
				next.add(rule);
			}
		}
		if (next.size() >= MAX_RULES) {
			return false;
		}
		next.add(new Rule(from, to));
		replaceRules(next);
		return true;
	}

	public static void removeRule(Block from) {
		if (area == null) {
			return;
		}
		List<Rule> next = new ArrayList<>();
		for (Rule rule : area.rules) {
			if (rule.from != from) {
				next.add(rule);
			}
		}
		replaceRules(next);
	}

	/** The rule for {@code from} blocks, or null. */
	public static Rule ruleFor(Block from) {
		if (area == null) {
			return null;
		}
		for (Rule rule : area.rules) {
			if (rule.from == from) {
				return rule;
			}
		}
		return null;
	}

	/** Swaps the rule list, redoing the replaced blocks in every loaded chunk. */
	public static void replaceRules(List<Rule> rules) {
		if (area == null) {
			return;
		}
		boolean wasApplied = applied;
		if (wasApplied) {
			// Only the replaced blocks go back; placed blocks stay as they are.
			forEachLoaded(GhostBlocks::revertMarks);
			MARKS.clear();
		}
		area.rules.clear();
		area.rules.addAll(rules);
		rebuildRuleIndex();
		refreshAny();
		if (wasApplied && !BY_FROM.isEmpty()) {
			forEachLoaded(chunk -> {
				LevelChunkSection[] sections = chunk.getSections();
				boolean[] changed = new boolean[sections.length];
				LongOpenHashSet placed = area.byChunk.get(ChunkPos.pack(chunk.getPos().x(), chunk.getPos().z()));
				BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
				suspend++;
				try {
					for (int i = 0; i < sections.length; i++) {
						changed[i] = rulesOnSection(chunk, i, placed, cursor);
					}
				} finally {
					suspend--;
				}
				redraw(chunk, changed);
			});
		}
		changed();
	}

	private static void revertMarks(LevelChunk chunk) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		LevelChunkSection[] sections = chunk.getSections();
		boolean[] changed = new boolean[sections.length];
		int cx = chunk.getPos().x();
		int cz = chunk.getPos().z();
		for (int i = 0; i < sections.length; i++) {
			long[] bits = MARKS.remove(SectionPos.asLong(cx, chunk.getSectionYFromSectionIndex(i), cz));
			if (bits == null) {
				continue;
			}
			LevelChunkSection section = sections[i];
			for (int bit = 0; bit < 4096; bit++) {
				if ((bits[bit >> 6] & (1L << bit)) == 0) {
					continue;
				}
				int x = bit & 15;
				int z = (bit >> 4) & 15;
				int y = bit >> 8;
				BlockState visible = section.getBlockState(x, y, z);
				Rule rule = BY_TO.get(visible.getBlock());
				if (rule != null) {
					setInSection(chunk, section, x, y, z, cx << 4, chunk.getSectionYFromSectionIndex(i) << 4, cz << 4, visible, rule.undo(visible), cursor);
					changed[i] = true;
				}
			}
		}
		redraw(chunk, changed);
	}

	private static void rebuildRuleIndex() {
		BY_FROM.clear();
		BY_TO.clear();
		if (area == null) {
			return;
		}
		for (Rule rule : area.rules) {
			BY_FROM.put(rule.from, rule);
			BY_TO.putIfAbsent(rule.to.getBlock(), rule);
		}
	}

	/** Removes every edit in the current area. */
	public static void clearArea() {
		if (area == null) {
			return;
		}
		boolean wasApplied = applied;
		revertAll();
		area.placed.clear();
		area.byChunk.clear();
		area.rules.clear();
		rebuildRuleIndex();
		if (wasApplied) {
			applyAll();
		}
		changed();
	}

	/** Show / hide all edits without forgetting them. */
	public static void setShown(boolean shown) {
		StrayConfig config = StrayConfig.get();
		if (config.editorShowEdits == shown) {
			return;
		}
		config.editorShowEdits = shown;
		config.save();
		if (!shown) {
			revertAll();
		} else if (area != null) {
			applyAll();
		}
	}

	public static boolean applied() {
		return applied;
	}

	private static void changed() {
		refreshAny();
		dirty = true;
		saveAt = System.currentTimeMillis() + 2500;
	}

	// ------------------------------------------------------------------ area tracking

	/** End of every client tick: follows the area you are in and saves pending changes. */
	public static void tick(Minecraft client) {
		if (client.level == null) {
			if (level != null || area != null) {
				saveNow();
				TRUTH.clear();
				MARKS.clear();
				area = null;
				level = null;
				applied = false;
				refreshAny();
			}
			seenLevel = null;
			return;
		}
		if (client.level != seenLevel) {
			// New world or dimension: the old chunks are gone, so nothing to put back. Wait a
			// little for the scoreboard to say where we are before loading anything.
			saveNow();
			TRUTH.clear();
			MARKS.clear();
			applied = false;
			area = null;
			level = client.level;
			seenLevel = client.level;
			pendingKey = null;
			pendingTicks = 0;
			settleTicks = 40;
			refreshAny();
		}
		if (settleTicks > 0) {
			settleTicks--;
		} else {
			String key = EditArea.key();
			String current = area == null ? null : area.key;
			if (key != null && !key.equals(current)) {
				if (key.equals(pendingKey)) {
					pendingTicks++;
				} else {
					pendingKey = key;
					pendingTicks = 0;
				}
				// A new place has to stay the same for a second, so a flickering scoreboard
				// doesn't swap edits back and forth.
				if (pendingTicks >= 20 || current == null && pendingTicks >= 5) {
					revertAll();
					saveNow();
					area = load(key);
					rebuildRuleIndex();
					WorldEditor.forgetHistory();
					pendingKey = null;
					pendingTicks = 0;
				}
			} else {
				pendingKey = null;
				pendingTicks = 0;
			}
		}
		boolean want = area != null && StrayConfig.get().editorShowEdits;
		if (want && !applied) {
			applyAll();
		} else if (!want && applied) {
			revertAll();
		}
		refreshAny();
		if (dirty && System.currentTimeMillis() >= saveAt) {
			saveNow();
		}
	}

	// ------------------------------------------------------------------ saving

	public static void saveNow() {
		if (!dirty || area == null) {
			dirty = false;
			return;
		}
		dirty = false;
		Path file = DIR.resolve(area.key + ".json");
		try {
			Files.createDirectories(DIR);
			if (area.empty()) {
				Files.deleteIfExists(file);
				return;
			}
			JsonObject root = new JsonObject();
			root.addProperty("v", 1);
			Object2IntOpenHashMap<BlockState> ids = new Object2IntOpenHashMap<>();
			ids.defaultReturnValue(-1);
			JsonArray states = new JsonArray();
			StringBuilder placed = new StringBuilder(area.placed.size() * 16);
			for (Long2ObjectMap.Entry<BlockState> entry : area.placed.long2ObjectEntrySet()) {
				int id = ids.getInt(entry.getValue());
				if (id < 0) {
					id = states.size();
					ids.put(entry.getValue(), id);
					states.add(BlockStateParser.serialize(entry.getValue()));
				}
				long pos = entry.getLongKey();
				if (placed.length() > 0) {
					placed.append(' ');
				}
				placed.append(BlockPos.getX(pos)).append(',').append(BlockPos.getY(pos)).append(',').append(BlockPos.getZ(pos)).append(',').append(id);
			}
			root.add("states", states);
			root.addProperty("placed", placed.toString());
			JsonArray rules = new JsonArray();
			for (Rule rule : area.rules) {
				JsonObject row = new JsonObject();
				row.addProperty("from", BuiltInRegistries.BLOCK.getKey(rule.from).toString());
				row.addProperty("to", BlockStateParser.serialize(rule.to));
				rules.add(row);
			}
			root.add("rules", rules);
			Path tmp = DIR.resolve(area.key + ".json.tmp");
			Files.writeString(tmp, root.toString(), StandardCharsets.UTF_8);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException | RuntimeException e) {
			dirty = true;
			saveAt = System.currentTimeMillis() + 10_000;
		}
	}

	private static Area load(String key) {
		Area out = new Area(key);
		Path file = DIR.resolve(key + ".json");
		if (!Files.isRegularFile(file)) {
			return out;
		}
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			List<BlockState> states = new ArrayList<>();
			if (root.has("states")) {
				for (JsonElement element : root.getAsJsonArray("states")) {
					states.add(parseState(element.getAsString()));
				}
			}
			String placed = root.has("placed") ? root.get("placed").getAsString() : "";
			for (String part : placed.split(" ")) {
				if (part.isEmpty()) {
					continue;
				}
				String[] bits = part.split(",");
				if (bits.length != 4) {
					continue;
				}
				int id = Integer.parseInt(bits[3]);
				if (id < 0 || id >= states.size() || states.get(id) == null) {
					continue;
				}
				out.put(BlockPos.asLong(Integer.parseInt(bits[0]), Integer.parseInt(bits[1]), Integer.parseInt(bits[2])), states.get(id));
				if (out.placed.size() >= MAX_PLACED) {
					break;
				}
			}
			if (root.has("rules")) {
				for (JsonElement element : root.getAsJsonArray("rules")) {
					JsonObject row = element.getAsJsonObject();
					BlockState from = parseState(row.get("from").getAsString());
					BlockState to = parseState(row.get("to").getAsString());
					if (from != null && to != null && from.getBlock() != to.getBlock() && out.rules.size() < MAX_RULES) {
						out.rules.add(new Rule(from.getBlock(), to));
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			// A broken file starts the area empty; the next save replaces it.
		}
		return out;
	}

	public static BlockState parseState(String text) {
		try {
			return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, text, false).blockState();
		} catch (Exception e) {
			return null;
		}
	}

	public static String describe(BlockState state) {
		return state == null ? "" : BlockStateParser.serialize(state);
	}

	// ------------------------------------------------------------------ helpers

	static BlockState copyProperties(BlockState source, BlockState target) {
		BlockState out = target;
		for (Property<?> property : source.getProperties()) {
			if (out.hasProperty(property)) {
				out = copy(source, out, property);
			}
		}
		return out;
	}

	private static <T extends Comparable<T>> BlockState copy(BlockState source, BlockState target, Property<T> property) {
		return target.setValue(property, source.getValue(property));
	}
}
