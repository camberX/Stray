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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The world editor's block layer: purely visual. The client's world data is never changed, so
 * movement, collisions, clicks, fluids, other mods and the server all see the real blocks, as
 * if no edit existed. Edits only go into the copies of chunk sections the renderer builds its
 * meshes from (vanilla's {@code SectionCopy} and Sodium's {@code ClonedChunkSection}), see
 * {@link #decorate}.
 *
 * <p>Two kinds of edits, saved per {@link EditArea} (island, dungeon floor, Kuudra tier):
 * placed blocks (one position, one block; air means "removed") and replace rules (every block
 * of one type shows as another, in every chunk).
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

		Rule(Block from, BlockState to) {
			this.from = from;
			this.to = to;
		}

		synchronized BlockState apply(BlockState original) {
			BlockState out = forward.get(original);
			if (out == null) {
				out = copyProperties(original, to);
				forward.put(original, out);
			}
			return out;
		}
	}

	/** One area's saved edits. */
	private static final class Area {
		final String key;
		final Long2ObjectOpenHashMap<BlockState> placed = new Long2ObjectOpenHashMap<>();
		/** Section key -> placed positions in it. */
		final Long2ObjectOpenHashMap<LongOpenHashSet> bySection = new Long2ObjectOpenHashMap<>();
		final List<Rule> rules = new ArrayList<>();

		Area(String key) {
			this.key = key;
		}

		void put(long pos, BlockState state) {
			if (placed.put(pos, state) == null) {
				bySection.computeIfAbsent(sectionOf(pos), k -> new LongOpenHashSet()).add(pos);
			}
		}

		BlockState remove(long pos) {
			BlockState old = placed.remove(pos);
			if (old != null) {
				long section = sectionOf(pos);
				LongOpenHashSet set = bySection.get(section);
				if (set != null) {
					set.remove(pos);
					if (set.isEmpty()) {
						bySection.remove(section);
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
	private static final Reference2ObjectOpenHashMap<Block, Rule> BY_FROM = new Reference2ObjectOpenHashMap<>();

	private static boolean dirty;
	private static long saveAt;
	/** Where-am-I bookkeeping. */
	private static String pendingKey;
	private static int pendingTicks;
	private static int settleTicks;
	private static ClientLevel seenLevel;
	private static int redrawAgain;
	private static int redrawAt;

	private GhostBlocks() {
	}

	private static long sectionOf(long pos) {
		return SectionPos.asLong(BlockPos.getX(pos) >> 4, BlockPos.getY(pos) >> 4, BlockPos.getZ(pos) >> 4);
	}

	/** Kept so callers read clearly; block reads always see the real world now. */
	public static void visible(Runnable task) {
		task.run();
	}

	/** Edits are drawn right now (an area is loaded, edits are shown, and it's this world). */
	public static boolean applied() {
		return area != null && level != null && StrayConfig.get().editorShowEdits && Minecraft.getInstance().level == level;
	}

	// ------------------------------------------------------------------ the render hook

	/** Whether {@link #decorate} would change anything in this section (checked before copying it). */
	public static boolean touches(Level from, int sx, int sy, int sz) {
		if (from != level || !applied()) {
			return false;
		}
		return !BY_FROM.isEmpty() || area.bySection.containsKey(SectionPos.asLong(sx, sy, sz));
	}

	/**
	 * Called with the renderer's private copy of one chunk section: puts the edits into it.
	 * Rules first (every block of a type), then placed blocks on top.
	 */
	public static void decorate(Level from, int sx, int sy, int sz, PalettedContainer<BlockState> states) {
		if (states == null || from != level || !applied()) {
			return;
		}
		try {
			if (!BY_FROM.isEmpty() && states.maybeHas(state -> BY_FROM.containsKey(state.getBlock()))) {
				for (int y = 0; y < 16; y++) {
					for (int z = 0; z < 16; z++) {
						for (int x = 0; x < 16; x++) {
							BlockState state = states.get(x, y, z);
							Rule rule = BY_FROM.get(state.getBlock());
							if (rule != null) {
								states.getAndSetUnchecked(x, y, z, rule.apply(state));
							}
						}
					}
				}
			}
			LongOpenHashSet placed = area.bySection.get(SectionPos.asLong(sx, sy, sz));
			if (placed != null) {
				for (long pos : placed) {
					BlockState state = area.placed.get(pos);
					if (state != null) {
						states.getAndSetUnchecked(BlockPos.getX(pos) & 15, BlockPos.getY(pos) & 15, BlockPos.getZ(pos) & 15, state);
					}
				}
			}
		} catch (RuntimeException e) {
			// Never break chunk rendering over an edit.
		}
	}

	/**
	 * Sections that hold only air are skipped by the renderers, so a section with placed blocks
	 * is marked as "not empty" (see GhostSectionMixin).
	 */
	public static void onChunkLoaded(LevelChunk chunk) {
		if (chunk == null || area == null || chunk.getLevel() != level) {
			return;
		}
		tagChunk(chunk);
	}

	private static void tagChunk(LevelChunk chunk) {
		LevelChunkSection[] sections = chunk.getSections();
		for (int i = 0; i < sections.length; i++) {
			long key = SectionPos.asLong(chunk.getPos().x(), chunk.getSectionYFromSectionIndex(i), chunk.getPos().z());
			if (area.bySection.containsKey(key)) {
				tag(sections[i], key);
			}
		}
	}

	private static void tag(LevelChunkSection section, long key) {
		if (section instanceof GhostSection ghost && !ghost.stray$ghost()) {
			ghost.stray$setGhost(true);
			if (level != null && section.hasOnlyAir()) {
				level.getChunkSource().getLightEngine().updateSectionStatus(SectionPos.of(key), false);
			}
		}
	}

	/** Implemented on LevelChunkSection by GhostSectionMixin. */
	public interface GhostSection {
		boolean stray$ghost();

		void stray$setGhost(boolean ghost);

		boolean stray$reallyEmpty();
	}

	// ------------------------------------------------------------------ redrawing

	private static void redrawPos(long pos) {
		if (level != null) {
			level.setSectionDirtyWithNeighbors(BlockPos.getX(pos) >> 4, BlockPos.getY(pos) >> 4, BlockPos.getZ(pos) >> 4);
		}
	}

	/** Redraws every loaded section that has placed blocks or blocks the given rules touch. */
	private static void redrawAll(Area of, Set<Block> ruleBlocks) {
		if (level == null || of == null) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		LongOpenHashSet done = new LongOpenHashSet();
		for (long key : of.bySection.keySet()) {
			if (done.add(key)) {
				level.setSectionDirtyWithNeighbors(SectionPos.x(key), SectionPos.y(key), SectionPos.z(key));
			}
		}
		if (ruleBlocks.isEmpty()) {
			return;
		}
		forEachLoaded(chunk -> {
			LevelChunkSection[] sections = chunk.getSections();
			for (int i = 0; i < sections.length; i++) {
				LevelChunkSection section = sections[i];
				if (section == null || section.hasOnlyAir() || !section.maybeHas(state -> ruleBlocks.contains(state.getBlock()))) {
					continue;
				}
				level.setSectionDirtyWithNeighbors(chunk.getPos().x(), chunk.getSectionYFromSectionIndex(i), chunk.getPos().z());
			}
		});
	}

	private static Set<Block> ruleBlocks(List<Rule> rules) {
		Set<Block> out = new HashSet<>();
		for (Rule rule : rules) {
			out.add(rule.from);
		}
		return out;
	}

	/** Every chunk the client has around the player. */
	public static void forEachLoaded(java.util.function.Consumer<LevelChunk> action) {
		Minecraft client = Minecraft.getInstance();
		ClientLevel world = client.level;
		if (world == null || client.player == null) {
			return;
		}
		int radius = Math.max(client.options.renderDistance().get(), 2) + 5;
		int pcx = client.player.blockPosition().getX() >> 4;
		int pcz = client.player.blockPosition().getZ() >> 4;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				LevelChunk chunk = world.getChunkSource().getChunk(pcx + dx, pcz + dz, ChunkStatus.FULL, false);
				if (chunk != null) {
					action.accept(chunk);
				}
			}
		}
	}

	private static void showArea(Area of) {
		if (of == null) {
			return;
		}
		forEachLoaded(GhostBlocks::tagChunk);
		redrawAll(of, ruleBlocks(of.rules));
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

	/** What the world shows at {@code pos}, edits included. */
	public static BlockState shown(BlockPos pos) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return Blocks.AIR.defaultBlockState();
		}
		return shown(pos, client.level.getBlockState(pos));
	}

	/** {@code real} as it is drawn at {@code pos}. */
	public static BlockState shown(BlockPos pos, BlockState real) {
		if (!applied()) {
			return real;
		}
		BlockState placed = area.placed.get(pos.asLong());
		if (placed != null) {
			return placed;
		}
		Rule rule = BY_FROM.get(real.getBlock());
		return rule == null ? real : rule.apply(real);
	}

	/** Every placed block in this place (position as a long, state). */
	public static void forEachPlaced(java.util.function.BiConsumer<Long, BlockState> action) {
		if (area == null) {
			return;
		}
		for (Long2ObjectMap.Entry<BlockState> entry : area.placed.long2ObjectEntrySet()) {
			action.accept(entry.getLongKey(), entry.getValue());
		}
	}

	/** {@code real} with only the replace rules applied (what a whole-type search matches on). */
	public static Block ruled(Block real) {
		if (!applied()) {
			return real;
		}
		Rule rule = BY_FROM.get(real);
		return rule == null ? real : rule.to.getBlock();
	}

	/**
	 * Shows {@code state} at {@code pos} (air hides the block there). {@code null} undoes the
	 * edit and shows the real block again. Returns false when the area is full.
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
		} else {
			if (old == null && area.placed.size() >= MAX_PLACED) {
				return false;
			}
			area.put(key, state);
			if (level != null) {
				LevelChunk chunk = level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
				if (chunk != null) {
					int index = chunk.getSectionIndex(pos.getY());
					if (index >= 0 && index < chunk.getSections().length) {
						tag(chunk.getSection(index), sectionOf(key));
					}
				}
			}
		}
		if (applied()) {
			redrawPos(key);
		}
		changed();
		return true;
	}

	/** Adds (or changes) the rule that shows every {@code from} as {@code to}. */
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

	/** Swaps the rule list and redraws what it touches. */
	public static void replaceRules(List<Rule> rules) {
		if (area == null) {
			return;
		}
		Set<Block> touched = ruleBlocks(area.rules);
		touched.addAll(ruleBlocks(rules));
		area.rules.clear();
		area.rules.addAll(rules);
		rebuildRuleIndex();
		if (applied()) {
			redrawAll(new Area(area.key), touched);
		}
		changed();
	}

	private static void rebuildRuleIndex() {
		BY_FROM.clear();
		if (area == null) {
			return;
		}
		for (Rule rule : area.rules) {
			BY_FROM.put(rule.from, rule);
		}
	}

	/** Removes every edit in the current area. */
	public static void clearArea() {
		if (area == null) {
			return;
		}
		Set<Block> touched = ruleBlocks(area.rules);
		Area old = new Area(area.key);
		old.bySection.putAll(area.bySection);
		area.placed.clear();
		area.bySection.clear();
		area.rules.clear();
		rebuildRuleIndex();
		if (level != null) {
			redrawAll(old, touched);
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
		if (area != null && level != null) {
			if (shown) {
				forEachLoaded(GhostBlocks::tagChunk);
			}
			redrawAll(area, ruleBlocks(area.rules));
		}
	}

	private static void changed() {
		dirty = true;
		saveAt = System.currentTimeMillis() + 2500;
	}

	// ------------------------------------------------------------------ area tracking

	/** End of every client tick: follows the area you are in and saves pending changes. */
	public static void tick(Minecraft client) {
		if (client.level == null) {
			if (level != null || area != null) {
				saveNow();
				area = null;
				level = null;
				BY_FROM.clear();
			}
			seenLevel = null;
			return;
		}
		if (client.level != seenLevel) {
			// New world or dimension: wait a little for the scoreboard to say where we are.
			saveNow();
			area = null;
			BY_FROM.clear();
			level = client.level;
			seenLevel = client.level;
			pendingKey = null;
			pendingTicks = 0;
			settleTicks = 40;
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
					Area old = area;
					saveNow();
					area = load(key);
					rebuildRuleIndex();
					WorldEditor.forgetHistory();
					pendingKey = null;
					pendingTicks = 0;
					if (old != null) {
						redrawAll(old, ruleBlocks(old.rules));
					}
					if (StrayConfig.get().editorShowEdits) {
						showArea(area);
						// Sections the renderer only starts tracking a little later get redrawn too.
						redrawAgain = 3;
						redrawAt = 20;
					}
				}
			} else {
				pendingKey = null;
				pendingTicks = 0;
			}
		}
		if (redrawAgain > 0 && --redrawAt <= 0) {
			redrawAgain--;
			redrawAt = 40;
			if (area != null && applied()) {
				showArea(area);
			}
		}
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
