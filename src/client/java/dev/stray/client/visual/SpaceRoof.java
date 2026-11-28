package dev.stray.client.visual;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.stray.Stray;
import dev.stray.client.config.IslandSaves;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.dungeon.DevMode;
import dev.stray.client.dungeon.DungeonRooms;
import dev.stray.client.location.SkyblockLocation;
import dev.stray.client.mixin.RenderPipelinesInvoker;
import dev.stray.client.mixin.RenderTypeAccessor;
import dev.stray.client.render.StrayShaders;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Space Roof: dungeon rooms get a night sky under their ceiling (a shader that looks things up
 * by view direction, so it seems endlessly far away), running down the walls and pillars a
 * few blocks and dissolving there in clouds, so the bricks come back below.
 *
 * <p>The sky is a flat layer at a chosen height over the room's whole floor plan (blocks stick
 * through it), placed by hand per room with the sky painter
 * ({@code /stray debug dev roof edit}) and saved in the room's own coordinates (DungeonRooms:
 * name, corner and turn), so one placement fits every run and every rotation.
 *
 * <p>Purely visual: depth tested like vanilla, so anything in front of it stays in front.
 */
public final class SpaceRoof {
	private static final net.minecraft.resources.Identifier SHADER = Stray.id("core/space_roof");
	private static final Path FILE = IslandSaves.DIR.resolve("space-roofs.json");
	/** Rooms nearer than this (blocks, horizontally from the camera) are drawn. */
	private static final double RANGE = 96.0;
	/** Just off the faces it covers, so it never z-fights with them. */
	private static final float INSET = 0.004f;
	/** Quads grow this much past their edges, so neighbours overlap instead of leaving hairlines. */
	private static final float GROW = 0.006f;
	/** Floats per quad: four vertices of x, y, z and how far inside. */
	private static final int QUAD = 16;
	/** Floats per block-effect quad: four vertices of x, y, z, depth under the sky, and the fade plus the sideways amount. */
	private static final int GLOW_QUAD = 20;
	/** Blocks under the sky (past the fade) that the block effects reach. */
	private static final int GLOW_DEPTH = 8;
	/** The block effects sit closer to the blocks than the sky, so the sky covers them where it's solid. */
	private static final float GLOW_INSET = 0.002f;

	/** A sky layer in room coordinates: its height (the plane's y) and its x, z cells. */
	record Sheet(int y, Set<Long> cells) {
	}

	/** Layers per room name. */
	private static final Map<String, List<Sheet>> SAVED = new LinkedHashMap<>();
	/** Carved-out boxes per room name, {x0, y0, z0, x1, y1, z1} (inclusive) in room coordinates: no sky inside them. */
	private static final Map<String, List<int[]>> CARVES = new LinkedHashMap<>();
	private static boolean loaded;
	/** Bumped on every change, so baked rooms rebuild. */
	private static int version;

	/** A piece of a room's sky (about one chunk), skipped when it's out of view. */
	private record Tile(float[] quads, int count, AABB box) {
	}

	/** A room's sky: its tiles, and which world columns have a layer at which heights (for stardust). */
	private record Baked(String name, int version, float fade, int glowFlags, int floorFlags, Tile[] sheet, Tile[] tiles, Tile[] glow, Tile[] floor, Map<Long, int[]> plan,
		double minX, double minZ, double maxX, double maxZ) {
		boolean empty() {
			return tiles.length == 0 && sheet.length == 0;
		}
	}

	/** Rooms seen (with their turn) in this world, by corner, and their baked sky. */
	private static final Map<String, DungeonRooms.Room> SEEN = new HashMap<>();
	private static final Map<String, Baked> BAKED = new HashMap<>();
	private static ClientLevel lastLevel;

	private SpaceRoof() {
	}

	public static void init() {
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> benchFrame());
		SpaceDust.init();
		SpaceDebris.init();
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
			if (!active() || context.poseStack() == null || !StrayShaders.ready()) {
				return;
			}
			Vec3 camera = context.levelState().cameraRenderState.pos;
			StrayConfig config = StrayConfig.get();
			syncMeshes(config);
			List<SpaceRoofGpu.Mesh> sky = new ArrayList<>();
			List<SpaceRoofGpu.Mesh> sheet = new ArrayList<>();
			List<SpaceRoofGpu.Mesh> glow = new ArrayList<>();
			List<SpaceRoofGpu.Mesh> floor = new ArrayList<>();
			for (RoomMeshes room : MESHES.values()) {
				Baked baked = room.baked;
				if (baked.empty() || camera.x < baked.minX - RANGE || camera.x > baked.maxX + RANGE
					|| camera.z < baked.minZ - RANGE || camera.z > baked.maxZ + RANGE) {
					continue;
				}
				if (room.sky != null && !hidden(1)) {
					sky.add(room.sky);
				}
				if (room.sheet != null && !hidden(1)) {
					sheet.add(room.sheet);
				}
				if (room.glow != null && !hidden(2)) {
					glow.add(room.glow);
				}
				if (room.floor != null && !hidden(4)) {
					floor.add(room.floor);
				}
			}
			if (SpaceRoofGpu.broken()) {
				stream(context, camera, config);
				return;
			}
			// Drawn from the GPU (uploaded once), all in one batch in this order: the block and floor
			// effects under the sky, then the sky, then the specks over it. One placeholder goes
			// through the queue; each extra batch would cost its own setup every frame.
			List<SpaceRoofGpu.Layer> layers = new ArrayList<>();
			if (!floor.isEmpty()) {
				layers.add(new SpaceRoofGpu.Layer(floorType(), floor));
			}
			if (!glow.isEmpty()) {
				layers.add(new SpaceRoofGpu.Layer(glowType(), glow));
			}
			if (!sky.isEmpty()) {
				layers.add(new SpaceRoofGpu.Layer(renderType(config.spaceRoofShootingStars, false), sky));
			}
			if (!sheet.isEmpty()) {
				layers.add(new SpaceRoofGpu.Layer(renderType(config.spaceRoofShootingStars, true), sheet));
			}
			List<SpaceRoofGpu.Live> lives = new ArrayList<>();
			if (!hidden(8)) {
				if (SpaceDebris.any()) {
					lives.add(new SpaceRoofGpu.Live(SpaceDebris.renderType(), sink -> SpaceDebris.emit(sink, camera)));
				}
				if (SpaceDust.any()) {
					lives.add(new SpaceRoofGpu.Live(SpaceDust.renderType(), sink -> SpaceDust.emit(sink, camera)));
				}
			}
			if (layers.isEmpty() && lives.isEmpty()) {
				return;
			}
			RenderType carrier = !layers.isEmpty() ? layers.get(0).type() : lives.get(0).type();
			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), carrier,
				(pose, consumer) -> SpaceRoofGpu.queueAll(carrier, layers, lives, camera, consumer));
		});
	}

	/**
	 * The fallback when the GPU draws never happen (another mod changing how batches are drawn):
	 * every visible tile sent each frame, as before. Same look, more CPU.
	 */
	private static void stream(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context, Vec3 camera, StrayConfig config) {
		net.minecraft.client.renderer.culling.Frustum frustum = context.levelState().cameraRenderState.cullFrustum;
		List<Tile> near = new ArrayList<>();
		List<Tile> nearSheet = new ArrayList<>();
		List<Tile> nearGlow = new ArrayList<>();
		List<Tile> nearFloor = new ArrayList<>();
		for (Baked baked : BAKED.values()) {
			if (baked.empty() || camera.x < baked.minX - RANGE || camera.x > baked.maxX + RANGE
				|| camera.z < baked.minZ - RANGE || camera.z > baked.maxZ + RANGE) {
				continue;
			}
			visible(baked.tiles, camera, frustum, near);
			visible(baked.sheet, camera, frustum, nearSheet);
			visible(baked.glow, camera, frustum, nearGlow);
			visible(baked.floor, camera, frustum, nearFloor);
		}
		float cx = (float) camera.x;
		float cy = (float) camera.y;
		float cz = (float) camera.z;
		int rgb = config.spaceRoofRgb & 0xFFFFFF;
		int floorBits = floorFlags(config);
		if (floorBits != 0 && !nearFloor.isEmpty()) {
			int argb = floorBits << 24 | rgb;
			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), floorType(), (pose, consumer) -> streamTiles(consumer, pose, nearFloor, GLOW_QUAD, cx, cy, cz, 0f, argb));
		}
		int glowBits = glowFlags(config);
		if (glowBits != 0 && !nearGlow.isEmpty()) {
			int argb = glowBits << 24 | rgb;
			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), glowType(), (pose, consumer) -> streamTiles(consumer, pose, nearGlow, GLOW_QUAD, cx, cy, cz, 0f, argb));
		}
		if (!near.isEmpty()) {
			float opacity = StrayConfig.clamp(config.spaceRoofOpacity, 0f, 1f);
			context.submitNodeCollector().order(1).submitCustomGeometry(context.poseStack(), renderType(config.spaceRoofShootingStars, false),
				(pose, consumer) -> streamTiles(consumer, pose, near, QUAD, cx, cy, cz, opacity, 0xFF000000 | rgb));
		}
		if (!nearSheet.isEmpty()) {
			float opacity = StrayConfig.clamp(config.spaceRoofOpacity, 0f, 1f);
			context.submitNodeCollector().order(1).submitCustomGeometry(context.poseStack(), renderType(config.spaceRoofShootingStars, true),
				(pose, consumer) -> streamTiles(consumer, pose, nearSheet, QUAD, cx, cy, cz, opacity, 0xFF000000 | rgb));
		}
	}

	private static void streamTiles(VertexConsumer consumer, PoseStack.Pose pose, List<Tile> tiles, int floatsPerQuad, float cx, float cy, float cz, float fixedV, int argb) {
		int perVertex = floatsPerQuad / 4;
		for (Tile tile : tiles) {
			float[] q = tile.quads;
			int floats = tile.count * floatsPerQuad;
			for (int o = 0; o < floats; o += perVertex) {
				consumer.addVertex(pose, q[o] - cx, q[o + 1] - cy, q[o + 2] - cz).setUv(q[o + 3], perVertex >= 5 ? q[o + 4] : fixedV).setColor(argb);
			}
		}
	}

	// ------------------------------------------------------------------ benchmark

	/**
	 * Parts left out (for the benchmark): 1 sky, 2 block effects, 4 floor effects, 8 stardust,
	 * debris and ripples; 16, 32, 64 just the nebula light, glowing seams or space cracks.
	 */
	private static int hidden;

	static boolean hidden(int part) {
		return (hidden & part) != 0;
	}

	private static final String[] BENCH_NAMES = {"everything on", "no sky", "no block effects", "no floor effects", "no stardust and debris",
		"no nebula light", "no glowing seams", "no space cracks", "everything off"};
	private static final int[] BENCH_MASKS = {0, 1, 2, 4, 8, 16, 32, 64, 15};
	/** Each round measures every setup for a short while, in turn, so slow drift (clocks, heat) hits all of them alike. */
	private static final int BENCH_ROUNDS = 8;
	private static final int BENCH_WARM = 20;
	private static final int BENCH_FRAMES = 150;
	private static int benchStep = -1;
	private static int benchFrame;
	private static long benchLast;
	private static final long[][] BENCH_TIMES = new long[BENCH_MASKS.length][BENCH_ROUNDS * BENCH_FRAMES];

	/** {@code /stray debug dev roof bench}: frame times with each part left out in turn. */
	public static String bench() {
		if (benchStep >= 0) {
			return "already measuring.";
		}
		benchStep = 0;
		benchFrame = 0;
		benchLast = 0L;
		hidden = BENCH_MASKS[0];
		return "measuring, keep still and don't touch anything for a few seconds (stand where the sky is in view, FPS limit off).";
	}

	/** Every frame: times the frame; moves to the next setup when enough frames are in. */
	private static void benchFrame() {
		if (benchStep < 0) {
			return;
		}
		long now = System.nanoTime();
		int setup = benchStep % BENCH_MASKS.length;
		int round = benchStep / BENCH_MASKS.length;
		if (benchLast != 0L && benchFrame >= BENCH_WARM) {
			BENCH_TIMES[setup][round * BENCH_FRAMES + benchFrame - BENCH_WARM] = now - benchLast;
		}
		benchLast = now;
		if (++benchFrame < BENCH_WARM + BENCH_FRAMES) {
			return;
		}
		benchStep++;
		benchFrame = 0;
		benchLast = 0L;
		if (benchStep < BENCH_ROUNDS * BENCH_MASKS.length) {
			hidden = BENCH_MASKS[benchStep % BENCH_MASKS.length];
			return;
		}
		benchStep = -1;
		hidden = 0;
		double[] result = new double[BENCH_MASKS.length];
		for (int i = 0; i < result.length; i++) {
			long[] sorted = BENCH_TIMES[i].clone();
			java.util.Arrays.sort(sorted);
			result[i] = sorted[sorted.length / 2] / 1000.0;
		}
		double on = result[0];
		double off = result[result.length - 1];
		StringBuilder out = new StringBuilder("§bStray §7Space roof costs (median frame): total §f")
			.append(String.format(java.util.Locale.ROOT, "%.0f µs", on - off))
			.append(String.format(java.util.Locale.ROOT, " §7(%.0f FPS with it, %.0f without)", 1e6 / on, 1e6 / off));
		int glowOn = glowFlags(StrayConfig.get());
		for (int i = 1; i < BENCH_MASKS.length - 1; i++) {
			int mask = BENCH_MASKS[i];
			// The single block effects only when they're on.
			if (mask >= 16 && (glowOn & mask >> 4) == 0) {
				continue;
			}
			out.append("§7, ").append(mask >= 16 ? "of which " : "").append(BENCH_NAMES[i].substring(3)).append(" §f")
				.append(String.format(java.util.Locale.ROOT, "%.0f µs", on - result[i]));
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			client.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(out.toString()));
		}
	}

	/** A room's uploaded meshes, for one bake and one look (colour, opacity, effects). */
	private static final class RoomMeshes {
		Baked baked;
		long skyStyle = Long.MIN_VALUE;
		long glowStyle = Long.MIN_VALUE;
		long floorStyle = Long.MIN_VALUE;
		SpaceRoofGpu.Mesh sheet;
		SpaceRoofGpu.Mesh sky;
		SpaceRoofGpu.Mesh glow;
		SpaceRoofGpu.Mesh floor;

		void close() {
			if (sheet != null) {
				sheet.close();
			}
			if (sky != null) {
				sky.close();
			}
			if (glow != null) {
				glow.close();
			}
			if (floor != null) {
				floor.close();
			}
			sheet = sky = glow = floor = null;
		}
	}

	private static final Map<String, RoomMeshes> MESHES = new HashMap<>();

	private static void closeMeshes() {
		for (RoomMeshes room : MESHES.values()) {
			room.close();
		}
		MESHES.clear();
	}

	/** Uploads what's new or changed, frees what's gone (render thread). */
	private static void syncMeshes(StrayConfig config) {
		MESHES.entrySet().removeIf(entry -> {
			if (BAKED.get(entry.getKey()) != entry.getValue().baked) {
				entry.getValue().close();
				return true;
			}
			return false;
		});
		int rgb = config.spaceRoofRgb & 0xFFFFFF;
		float opacity = StrayConfig.clamp(config.spaceRoofOpacity, 0f, 1f);
		// The benchmark can leave single block effects out (bits 16, 32, 64 of hidden).
		int glowBits = glowFlags(config) & ~(hidden >> 4 & 7);
		int floorBits = floorFlags(config);
		long skyStyle = (long) rgb << 32 | Float.floatToIntBits(opacity) & 0xFFFFFFFFL;
		long glowStyle = (long) glowBits << 24 | rgb;
		long floorStyle = (long) floorBits << 24 | rgb;
		for (Baked baked : BAKED.values()) {
			RoomMeshes room = MESHES.computeIfAbsent(baked.name, name -> new RoomMeshes());
			room.baked = baked;
			if (room.skyStyle != skyStyle) {
				if (room.sky != null) {
					room.sky.close();
				}
				if (room.sheet != null) {
					room.sheet.close();
				}
				room.sky = upload(baked.name + " sky", baked.tiles, QUAD, opacity, 0xFF000000 | rgb);
				room.sheet = upload(baked.name + " sheet", baked.sheet, QUAD, opacity, 0xFF000000 | rgb);
				room.skyStyle = skyStyle;
			}
			if (room.glowStyle != glowStyle) {
				if (room.glow != null) {
					room.glow.close();
				}
				room.glow = glowBits == 0 ? null : upload(baked.name + " glow", baked.glow, GLOW_QUAD, 0f, glowBits << 24 | rgb);
				room.glowStyle = glowStyle;
			}
			if (room.floorStyle != floorStyle) {
				if (room.floor != null) {
					room.floor.close();
				}
				room.floor = floorBits == 0 ? null : upload(baked.name + " floor", baked.floor, GLOW_QUAD, 0f, floorBits << 24 | rgb);
				room.floorStyle = floorStyle;
			}
		}
	}

	private static SpaceRoofGpu.Mesh upload(String name, Tile[] tiles, int floatsPerQuad, float fixedV, int argb) {
		List<float[]> arrays = new ArrayList<>();
		List<Integer> counts = new ArrayList<>();
		for (Tile tile : tiles) {
			arrays.add(tile.quads);
			counts.add(tile.count);
		}
		try {
			return SpaceRoofGpu.upload(name, arrays, counts, floatsPerQuad, fixedV, argb);
		} catch (RuntimeException e) {
			Stray.LOGGER.warn("Space roof: upload failed", e);
			return null;
		}
	}

	/** The tiles in range and in view. */
	private static void visible(Tile[] tiles, Vec3 camera, net.minecraft.client.renderer.culling.Frustum frustum, List<Tile> out) {
		for (Tile tile : tiles) {
			AABB box = tile.box;
			if (camera.x < box.minX - RANGE || camera.x > box.maxX + RANGE || camera.z < box.minZ - RANGE || camera.z > box.maxZ + RANGE
				|| frustum != null && !frustum.isVisible(box)) {
				continue;
			}
			out.add(tile);
		}
	}

	/** Which block effects are on, as bits for the shader: 1 nebula light, 2 glowing seams, 4 space cracks. */
	private static int glowFlags(StrayConfig config) {
		return (config.spaceRoofLight ? 1 : 0) | (config.spaceRoofSeams ? 2 : 0) | (config.spaceRoofCracks ? 4 : 0);
	}

	/** Which floor effects are on, as bits for the shader: 1 starlit floor (the haze is fog, see CustomFog). */
	private static int floorFlags(StrayConfig config) {
		return config.spaceRoofReflect ? 1 : 0;
	}

	/** Client tick: notes the room you're in, bakes one room at a time, forgets on world change. */
	public static void tick(Minecraft client) {
		if (client.level != lastLevel) {
			lastLevel = client.level;
			SEEN.clear();
			BAKED.clear();
			closeMeshes();
			SpaceDust.clear();
			SpaceDebris.clear();
		}
		if (!active() || client.level == null) {
			SpaceDust.clear();
			SpaceDebris.clear();
			return;
		}
		load();
		SpaceDust.tick(client);
		SpaceDebris.tick(client);
		// Boss rooms only (F7: every phase and P3 section at once; each is its own area).
		List<DungeonRooms.Room> bosses = bossRooms();
		if (bosses.isEmpty()) {
			SEEN.clear();
			BAKED.clear();
			closeMeshes();
		} else {
			for (DungeonRooms.Room boss : bosses) {
				SEEN.putIfAbsent(boss.name(), boss);
			}
			SEEN.keySet().removeIf(name -> bosses.stream().noneMatch(b -> b.name().equals(name)));
			BAKED.keySet().retainAll(SEEN.keySet());
		}
		// Baking runs off the main thread, one room at a time; the old sky stays up meanwhile.
		if (baking != null) {
			if (!baking.isDone()) {
				return;
			}
			try {
				Baked done = baking.get();
				if (bakingLevel == client.level && SEEN.containsKey(done.name)) {
					BAKED.put(done.name, done);
				}
			} catch (Exception e) {
				// Reading the world from another thread failed (another mod may not allow it): log it
				// once and bake on the main thread from now on.
				if (!mainThreadBake) {
					Stray.LOGGER.warn("Space roof: background bake failed, baking on the main thread from now on", e);
				}
				mainThreadBake = true;
				bakeError = String.valueOf(e.getCause() != null ? e.getCause() : e);
			}
			baking = null;
		}
		float fade = Math.max(0f, StrayConfig.get().spaceRoofFade);
		int glow = glowFlags(StrayConfig.get());
		int floor = floorFlags(StrayConfig.get());
		for (DungeonRooms.Room seen : SEEN.values()) {
			Baked old = BAKED.get(seen.name());
			if (old != null && old.version == version && old.fade == fade && old.glowFlags == glow && old.floorFlags == floor) {
				continue;
			}
			// Everything it reads from the saves is copied here, on the main thread.
			List<Sheet> sheets = SAVED.get(seen.name());
			List<Sheet> sheetsCopy = sheets == null ? List.of() : List.copyOf(sheets);
			List<int[]> carved = carves(seen);
			ClientLevel level = client.level;
			int v = version;
			if (mainThreadBake) {
				try {
					BAKED.put(seen.name(), bake(level, seen, sheetsCopy, carved, v, fade, glow, floor));
				} catch (RuntimeException e) {
					bakeError = String.valueOf(e);
					Stray.LOGGER.warn("Space roof: bake failed", e);
					BAKED.put(seen.name(), new Baked(seen.name(), v, fade, glow, floor, new Tile[0], new Tile[0], new Tile[0], new Tile[0], Map.of(), 0, 0, 0, 0));
				}
				return;
			}
			bakingLevel = level;
			baking = BAKER.submit(() -> bake(level, seen, sheetsCopy, carved, v, fade, glow, floor));
			return;
		}
	}

	private static final java.util.concurrent.ExecutorService BAKER = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "Stray space roof");
		thread.setDaemon(true);
		thread.setPriority(Thread.MIN_PRIORITY);
		return thread;
	});
	private static java.util.concurrent.Future<Baked> baking;
	/** Set when baking off the main thread failed once: bake on the main thread instead. */
	private static boolean mainThreadBake;
	private static String bakeError;
	private static ClientLevel bakingLevel;

	static boolean active() {
		return StrayConfig.get().spaceRoof && (SkyblockLocation.inDungeon || DevMode.on() || SkyEditor.active() || debugBoss > 0);
	}

	// ------------------------------------------------------------------ boss rooms

	/** {@code /stray debug dev roof boss <floor>}: act as if you're in that floor's boss room (0: off). */
	private static int debugBoss;

	/** The boss floor you're on (debug override first, else a real boss fight), 0 outside one. */
	static int bossFloor() {
		return debugBoss > 0 ? debugBoss : SkyblockLocation.inDungeon && SkyblockLocation.inBoss ? SkyblockLocation.dungeonFloor : 0;
	}

	/**
	 * F7's parts, each tuned on its own: the phases by height (like quoi: P1 over 210, P2 over
	 * 155, P3 over 100, P4 over 45, P5 below) and P3's four sections and the Core area by place
	 * (F7Phase's boxes). P3 outside those is "p3".
	 */
	static final String[] F7_PARTS = {"p1", "p2", "p3s1", "p3s2", "p3s3", "p3s4", "p3core", "p3", "p4", "p5"};

	static String f7Part(int bx, int by, int bz) {
		double x = bx + 0.5;
		double z = bz + 0.5;
		if (by > 210) {
			return "p1";
		}
		if (by > 155) {
			return "p2";
		}
		if (by <= 45) {
			return "p5";
		}
		if (by <= 100) {
			return "p4";
		}
		if (x >= 89 && x <= 113 && z >= 30 && z <= 122) {
			return "p3s1";
		}
		if (x >= 19 && x <= 111 && z >= 121 && z <= 145) {
			return "p3s2";
		}
		if (x >= -6 && x <= 19 && z >= 51 && z <= 143) {
			return "p3s3";
		}
		if (x >= -2 && x <= 90 && z >= 27 && z <= 51) {
			return "p3s4";
		}
		if (x >= 41 && x <= 68 && z >= 59 && z <= 117) {
			return "p3core";
		}
		return "p3";
	}

	private static DungeonRooms.Room bossPart(int floor, String part) {
		return new DungeonRooms.Room("@boss:f" + floor + (part == null ? "" : ":" + part), BlockPos.ZERO, 0, true);
	}

	/**
	 * The boss room part holding world block (x, y, z): on F7 its phase or P3 section
	 * ({@code @boss:f7:p3s2}), on other floors the whole room ({@code @boss:f<floor>}). Boss rooms
	 * always sit at the same place, so their layers are kept in world coordinates. Null outside one.
	 */
	static DungeonRooms.Room bossRoom(int x, int y, int z) {
		int floor = bossFloor();
		if (floor <= 0) {
			return null;
		}
		return bossPart(floor, floor == 7 ? f7Part(x, y, z) : null);
	}

	/** Every part of the boss room you're in (all drawn; each is its own area), or none. */
	static List<DungeonRooms.Room> bossRooms() {
		int floor = bossFloor();
		List<DungeonRooms.Room> out = new ArrayList<>();
		if (floor == 7) {
			for (String part : F7_PARTS) {
				out.add(bossPart(7, part));
			}
		} else if (floor > 0) {
			out.add(bossPart(floor, null));
		}
		return out;
	}

	/** The F7 part a boss room stands for, or null. */
	private static String partOf(DungeonRooms.Room room) {
		String name = room.name();
		return name.startsWith("@boss:f7:") ? name.substring("@boss:f7:".length()) : null;
	}

	static boolean isBoss(DungeonRooms.Room room) {
		return room != null && room.name().startsWith("@boss:");
	}

	/** {@code /stray debug dev roof boss <floor>}. */
	public static String debugBoss(int floor) {
		debugBoss = Math.clamp(floor, 0, 7);
		if (debugBoss > 0) {
			StrayConfig config = StrayConfig.get();
			if (!config.spaceRoof) {
				config.spaceRoof = true;
				config.save();
			}
			return "acting as if you're in the F" + debugBoss + " / M" + debugBoss + " boss room (layers saved as @boss:f" + debugBoss
				+ (debugBoss == 7 ? ":<phase or section>" : "") + ", in world coordinates). /stray debug dev roof boss 0 to stop.";
		}
		return "boss room override off.";
	}

	// ------------------------------------------------------------------ sky painter API

	/** The boss room part where you (or freecam) are, or null. */
	static DungeonRooms.Room roomAt(Minecraft client) {
		if (client.player == null) {
			return null;
		}
		BlockPos at = dev.stray.client.movement.Freecam.active()
			? BlockPos.containing(dev.stray.client.movement.Freecam.position(1.0f)) : client.player.blockPosition();
		return bossRoom(at.getX(), at.getY(), at.getZ());
	}

	/** Boss room fill limits: blocks from where you aim, and cells. */
	private static final int BOSS_RANGE = 100;
	private static final int BOSS_MAX = 40_000;

	/**
	 * The cells a layer at height {@code y} gets when aimed at (x, z). Normal rooms: the whole
	 * floor plan of the room there, through blocks (they stick through it; the room's walls
	 * hide its edge); empty when no room is there. Boss rooms (no floor plan): spreading through
	 * the air under that height until it meets full blocks.
	 */
	static Set<Long> fill(ClientLevel level, DungeonRooms.Room room, int startX, int startZ, int y) {
		if (isBoss(room)) {
			return spread(level, startX, startZ, y, partOf(room));
		}
		Set<Long> cells = new LinkedHashSet<>();
		for (int[] box : DungeonRooms.footprint(level, startX + 0.5, startZ + 0.5)) {
			for (int x = box[0]; x <= box[2]; x++) {
				for (int z = box[1]; z <= box[3]; z++) {
					cells.add(key(x, z));
				}
			}
		}
		return cells;
	}

	/** Through the air under height {@code y}; on F7 it stays inside the aimed phase or section. */
	private static Set<Long> spread(ClientLevel level, int startX, int startZ, int y, String part) {
		Set<Long> cells = new LinkedHashSet<>();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		if (wall(level, at, startX, y, startZ)) {
			return cells;
		}
		ArrayDeque<long[]> queue = new ArrayDeque<>();
		queue.add(new long[] {startX, startZ});
		cells.add(key(startX, startZ));
		int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (!queue.isEmpty() && cells.size() < BOSS_MAX) {
			long[] cell = queue.poll();
			for (int[] step : steps) {
				int x = (int) cell[0] + step[0];
				int z = (int) cell[1] + step[1];
				if (Math.abs(x - startX) > BOSS_RANGE || Math.abs(z - startZ) > BOSS_RANGE) {
					continue;
				}
				long k = key(x, z);
				if (cells.contains(k) || part != null && !part.equals(f7Part(x, y - 1, z))
					|| wall(level, at, x, y, z)) {
					continue;
				}
				cells.add(k);
				queue.add(new long[] {x, z});
			}
		}
		// One more block all round, so thin pillars where the spread stopped are closed over
		// instead of leaving holes their tops show through.
		Set<Long> grown = new LinkedHashSet<>(cells);
		for (long cell : cells) {
			int x = x(cell);
			int z = z(cell);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					grown.add(key(x + dx, z + dz));
				}
			}
		}
		return grown;
	}

	/** Adds a layer (world cells) to the room; returns it in room coordinates, for undo. */
	static Sheet add(DungeonRooms.Room room, int y, Set<Long> worldCells) {
		load();
		Set<Long> local = new LinkedHashSet<>();
		for (long cell : worldCells) {
			BlockPos pos = room.toRoom(new BlockPos(x(cell), y, z(cell)));
			local.add(key(pos.getX(), pos.getZ()));
		}
		Sheet sheet = new Sheet(y, local);
		SAVED.computeIfAbsent(room.name(), name -> new ArrayList<>()).add(sheet);
		SEEN.put(room.name(), room);
		changed();
		return sheet;
	}

	/** Puts back layers taken away (undo). */
	static void restore(DungeonRooms.Room room, List<Sheet> sheets) {
		load();
		SAVED.computeIfAbsent(room.name(), name -> new ArrayList<>()).addAll(sheets);
		changed();
	}

	/** Takes a layer away (undo of an add). */
	static void remove(DungeonRooms.Room room, Sheet sheet) {
		load();
		List<Sheet> sheets = SAVED.get(room.name());
		if (sheets != null && sheets.remove(sheet) && sheets.isEmpty()) {
			SAVED.remove(room.name());
		}
		changed();
	}

	/** The room's layers covering world column (x, z); the one nearest height {@code y} first. */
	static List<Sheet> sheetsAt(DungeonRooms.Room room, int x, int z, int y) {
		load();
		List<Sheet> out = new ArrayList<>();
		List<Sheet> sheets = SAVED.get(room.name());
		if (sheets == null) {
			return out;
		}
		BlockPos local = room.toRoom(new BlockPos(x, 0, z));
		long k = key(local.getX(), local.getZ());
		for (Sheet sheet : sheets) {
			if (sheet.cells.contains(k)) {
				out.add(sheet);
			}
		}
		out.sort((a, b) -> Integer.compare(Math.abs(a.y - y), Math.abs(b.y - y)));
		return out;
	}

	// ------------------------------------------------------------------ carving

	/** The room's carved boxes in world coordinates, {x0, y0, z0, x1, y1, z1} inclusive. */
	static List<int[]> carves(DungeonRooms.Room room) {
		load();
		List<int[]> out = new ArrayList<>();
		List<int[]> local = CARVES.get(room.name());
		if (local == null) {
			return out;
		}
		for (int[] box : local) {
			BlockPos a = room.toWorld(new BlockPos(box[0], box[1], box[2]));
			BlockPos b = room.toWorld(new BlockPos(box[3], box[4], box[5]));
			if (a != null && b != null) {
				out.add(box(a, b));
			}
		}
		return out;
	}

	private static int[] box(BlockPos a, BlockPos b) {
		return new int[] {Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
			Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())};
	}

	private static boolean carved(List<int[]> boxes, int x, int y, int z) {
		for (int[] b : boxes) {
			if (x >= b[0] && x <= b[3] && y >= b[1] && y <= b[4] && z >= b[2] && z <= b[5]) {
				return true;
			}
		}
		return false;
	}

	/** Carves out the box between two world blocks; returns it in room coordinates, for undo. */
	static int[] addCarve(DungeonRooms.Room room, BlockPos a, BlockPos b) {
		load();
		int[] local = box(room.toRoom(a), room.toRoom(b));
		CARVES.computeIfAbsent(room.name(), name -> new ArrayList<>()).add(local);
		SEEN.put(room.name(), room);
		changed();
		return local;
	}

	/** Takes a carved box away (or undoes one). */
	static void removeCarve(DungeonRooms.Room room, int[] local) {
		load();
		List<int[]> list = CARVES.get(room.name());
		if (list != null && list.remove(local) && list.isEmpty()) {
			CARVES.remove(room.name());
		}
		changed();
	}

	/** Puts a removed carved box back (undo). */
	static void restoreCarve(DungeonRooms.Room room, int[] local) {
		load();
		CARVES.computeIfAbsent(room.name(), name -> new ArrayList<>()).add(local);
		changed();
	}

	/** The room's carved box (room coordinates) holding world block (x, y, z), the smallest one, or null. */
	static int[] carveAt(DungeonRooms.Room room, int x, int y, int z) {
		load();
		List<int[]> list = CARVES.get(room.name());
		if (list == null) {
			return null;
		}
		int[] best = null;
		long bestSize = Long.MAX_VALUE;
		for (int[] local : list) {
			BlockPos a = room.toWorld(new BlockPos(local[0], local[1], local[2]));
			BlockPos b = room.toWorld(new BlockPos(local[3], local[4], local[5]));
			if (a == null || b == null) {
				continue;
			}
			int[] w = box(a, b);
			if (carved(List.of(w), x, y, z)) {
				long size = (long) (w[3] - w[0] + 1) * (w[4] - w[1] + 1) * (w[5] - w[2] + 1);
				if (size < bestSize) {
					bestSize = size;
					best = local;
				}
			}
		}
		return best;
	}

	static long key(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	static int x(long key) {
		return (int) (key >> 32);
	}

	static int z(long key) {
		return (int) key;
	}

	/** {@code /stray debug dev roof clear}: the boss part you're in (or freecam is in) loses all its sky. */
	public static String clear(Minecraft client) {
		DungeonRooms.Room room = roomAt(client);
		if (room == null) {
			return "not in a boss room (/stray debug dev roof boss 7 to act as if).";
		}
		load();
		List<Sheet> removed = SAVED.remove(room.name());
		CARVES.remove(room.name());
		changed();
		return "cleared " + room.name() + " (" + (removed == null ? 0 : removed.size()) + " layers).";
	}

	/** {@code /stray debug dev roof}: the room detection and what's saved for it. */
	public static String describe(Minecraft client) {
		if (client.player == null) {
			return "not in a world";
		}
		load();
		DungeonRooms.Room room = roomAt(client);
		String where = "in dungeon " + SkyblockLocation.inDungeon + ", boss " + SkyblockLocation.inBoss + (debugBoss > 0 ? " (override F" + debugBoss + ")" : "")
			+ ", dev mode " + DevMode.on() + ", Space roof " + (StrayConfig.get().spaceRoof ? "on" : "off");
		if (room == null) {
			return "not in a boss room (" + where + "). Space roof only shows in the boss room; /stray debug dev roof boss 7 acts as if.";
		}
		List<Sheet> sheets = SAVED.get(room.name());
		List<int[]> carves = CARVES.get(room.name());
		int quads = 0;
		for (Baked baked : BAKED.values()) {
			for (Tile tile : baked.tiles) {
				quads += tile.count;
			}
			for (Tile tile : baked.sheet) {
				quads += tile.count;
			}
		}
		return room.name() + ": " + (sheets == null ? 0 : sheets.size()) + " sky layers, " + (carves == null ? 0 : carves.size()) + " carved boxes ("
			+ where + "). Built: " + BAKED.size() + " parts, " + quads + " sky quads" + (mainThreadBake ? " (main thread)" : "")
			+ (bakeError != null ? ", last bake error: " + bakeError : "") + ". Drawing: " + SpaceRoofGpu.status() + ". File: config/stray/space-roofs.json";
	}

	static void changed() {
		version++;
		save();
	}

	// ------------------------------------------------------------------ baking and drawing

	/** UV carries the shader's inside value and the opacity; the colour is the tint. */
	private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, int rgb, float inside, float opacity) {
		consumer.addVertex(pose, x, y, z).setUv(inside, opacity).setColor(0xFF000000 | rgb);
	}

	/**
	 * The room's layers in the world: a fully opaque quad per cell, and on every block in the
	 * {@code fade} blocks under a layer (walls, pillars, hanging pieces, stairs), sky on each
	 * visible face (never the top), following the block's real shape. How much sky goes by
	 * height alone: all of it at the layer, none {@code fade} blocks lower, so neighbouring
	 * blocks always join up. Blocks just outside a layer (walls around a boss room) get it on
	 * the faces turned to the layer.
	 */
	private static Baked bake(ClientLevel level, DungeonRooms.Room room, List<Sheet> sheets, List<int[]> carved, int version,
		float fade, int glowFlags, int floor) {
		if (sheets.isEmpty()) {
			return new Baked(room.name(), version, fade, glowFlags, floor, new Tile[0], new Tile[0], new Tile[0], new Tile[0], Map.of(), 0, 0, 0, 0);
		}
		boolean glow = glowFlags != 0;
		Map<Long, List<float[]>> tiles = new HashMap<>();
		// The flat layers themselves (drawn with their own no-discard shader).
		Map<Long, List<float[]>> sheetTiles = new HashMap<>();
		Map<Long, int[]> allPlan = new HashMap<>();
		Map<FaceKey, List<double[]>> faces = new HashMap<>();
		// The block effects (light, seams, cracks): every visible face down to GLOW_DEPTH past the fade.
		Map<Long, List<float[]>> glowTiles = new HashMap<>();
		Map<FaceKey, List<double[]>> glowFaces = new HashMap<>();
		// The floor effects: the floor under the layer, mist over it, the bottom of the walls round it.
		Map<Long, List<float[]>> floorTiles = new HashMap<>();
		double minX = Double.MAX_VALUE;
		double minZ = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		double maxZ = -Double.MAX_VALUE;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos near = new BlockPos.MutableBlockPos();
		int depth = (int) Math.ceil(fade);
		// Only as deep as the effects that are on reach: light the fade plus 8, seams the fade plus
		// 5, cracks the fade plus 3.
		int glowDepth = Math.max((glowFlags & 1) != 0 ? depth + GLOW_DEPTH : 0, Math.max((glowFlags & 2) != 0 ? depth + 5 : 0, (glowFlags & 4) != 0 ? depth + 3 : 0));
		int lowest = Math.max(depth, glowDepth);
		// How many blocks past a layer's edge the sky still reaches, thinning out sideways.
		int reach = Math.clamp(depth, 2, 5);
		Map<Integer, Map<Long, Integer>> rings = new HashMap<>();
		for (Sheet sheet : sheets) {
			int top = sheet.y;
			float y = top - INSET;
			Set<Long> plan = new java.util.HashSet<>();
			for (long cell : sheet.cells) {
				BlockPos world = room.toWorld(new BlockPos(x(cell), top, z(cell)));
				if (world == null || carved(carved, world.getX(), top, world.getZ()) || carved(carved, world.getX(), top - 1, world.getZ())) {
					continue;
				}
				long k = key(world.getX(), world.getZ());
				plan.add(k);
				int[] ys = allPlan.get(k);
				if (ys == null) {
					allPlan.put(k, new int[] {top});
				} else {
					int[] more = java.util.Arrays.copyOf(ys, ys.length + 1);
					more[ys.length] = top;
					allPlan.put(k, more);
				}
			}
			// The layer itself, as few big rectangles as it takes (cells merged, split per chunk).
			for (int[] r : rects(plan)) {
				float x0 = r[0] - GROW;
				float z0 = r[1] - GROW;
				float x1 = r[2] + 1 + GROW;
				float z1 = r[3] + 1 + GROW;
				addTile(sheetTiles, new float[] {x0, y, z0, 1f, x1, y, z0, 1f, x1, y, z1, 1f, x0, y, z1, 1f});
				minX = Math.min(minX, r[0]);
				minZ = Math.min(minZ, r[1]);
				maxX = Math.max(maxX, r[2] + 1);
				maxZ = Math.max(maxZ, r[3] + 1);
			}
			if (floor != 0) {
				bakeFloor(level, plan, top, carved, floor, floorTiles);
			}
			if (lowest <= 0) {
				continue;
			}
			// The layer's columns (0) and the rings around it (1..reach), by distance from it.
			Map<Long, Integer> ring = rings.computeIfAbsent(top, t -> new HashMap<>());
			ArrayDeque<Long> queue = new ArrayDeque<>();
			for (long cell : plan) {
				ring.put(cell, 0);
				queue.add(cell);
			}
			while (!queue.isEmpty()) {
				long cell = queue.poll();
				int d = ring.get(cell);
				if (d >= reach) {
					continue;
				}
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						long n = key(x(cell) + dx, z(cell) + dz);
						Integer old = ring.get(n);
						if (old == null || old > d + 1) {
							ring.put(n, d + 1);
							queue.add(n);
						}
					}
				}
			}
			for (Map.Entry<Long, Integer> entry : ring.entrySet()) {
				long column = entry.getKey();
				int dist = entry.getValue();
				int cx = x(column);
				int cz = z(column);
				boolean inside = dist == 0;
				for (int k = 1; k <= lowest; k++) {
					int yy = top - k;
					BlockState state = level.getBlockState(at.set(cx, yy, cz));
					if (empty(state) || carved(carved, cx, yy, cz)) {
						continue;
					}
					List<AABB> boxes = shapeOf(level, state, at);
					if (boxes.isEmpty()) {
						continue;
					}
					boolean glowHere = glow && k <= glowDepth;
					for (int f = 0; f < FACES.length; f++) {
						boolean sky = k <= depth && f < 5;
						if (!sky && !glowHere) {
							continue;
						}
						int[] face = FACES[f];
						// Outside the layer: undersides (and tops), and sides turned towards it (a wall's room side).
						if (!inside && f > 0 && f < 5) {
							Integer toward = ring.get(key(cx + face[0], cz + face[2]));
							if (toward == null || toward >= dist) {
								continue;
							}
						}
						// Open unless the neighbour covers that whole face (walls, fences and stairs from
						// the side leave most of it showing).
						BlockState next = level.getBlockState(near.set(cx + face[0], yy + face[1], cz + face[2]));
						boolean open = empty(next) || !next.isFaceSturdy(level, near, SIDES[f].getOpposite());
						for (AABB box : boxes) {
							// A face on the block's own edge shows only if the neighbour there is open;
							// one inside the block's space (a stair's step) always can.
							boolean onEdge = face[0] > 0 ? box.maxX >= 1 : face[0] < 0 ? box.minX <= 0
								: face[2] > 0 ? box.maxZ >= 1 : face[2] < 0 ? box.minZ <= 0 : face[1] > 0 ? box.maxY >= 1 : box.minY <= 0;
							if (onEdge && !open) {
								continue;
							}
							long group = inside ? Long.MIN_VALUE : column;
							if (sky) {
								addFace(faces, f, cx, yy, cz, box, top, group, INSET);
							}
							if (glowHere) {
								addFace(glowFaces, f, cx, yy, cz, box, top, group, GLOW_INSET);
							}
						}
					}
				}
			}
		}
		// Faces in one plane at the same heights, side by side (a wall's blocks), become one strip.
		int reachF = reach;
		merge(faces, false, (key, a0, a1) -> addTile(tiles, sideways(faceQuad(key, a0, a1, fade), rings.get(key.top), reachF)));
		int fadeBlocks = Math.round(fade);
		merge(glowFaces, false, (key, a0, a1) -> addTile(glowTiles, glowQuad(key, a0, a1, fadeBlocks, rings.get(key.top), reachF)));
		return new Baked(room.name(), version, fade, glowFlags, floor, pack(sheetTiles, QUAD), pack(tiles, QUAD), pack(glowTiles, GLOW_QUAD), pack(floorTiles, GLOW_QUAD), allPlan, minX, minZ, maxX, maxZ);
	}

	private interface Strip {
		void add(FaceKey key, double a0, double a1);
	}

	/**
	 * Joins the faces of each plane into as few rectangles as it can: first side by side
	 * (along a), then the strips with the same span stacked (along b). Exact: everything a quad
	 * carries changes in a straight line along both, except the sideways fade on undersides
	 * outside the layer, which isn't stacked.
	 */
	private static void merge(Map<FaceKey, List<double[]>> faces, boolean alwaysStack, Strip strip) {
		Map<FaceKey, List<double[]>> planes = new HashMap<>();
		for (Map.Entry<FaceKey, List<double[]>> entry : faces.entrySet()) {
			FaceKey key = entry.getKey();
			FaceKey plane = new FaceKey(key.face, key.plane, 0, 0, key.chunk, key.top, key.column);
			List<double[]> rows = planes.computeIfAbsent(plane, k -> new ArrayList<>());
			List<double[]> spans = entry.getValue();
			spans.sort((a, b) -> Double.compare(a[0], b[0]));
			double a0 = spans.get(0)[0];
			double a1 = spans.get(0)[1];
			for (int i = 1; i < spans.size(); i++) {
				double[] span = spans.get(i);
				if (span[0] <= a1 + 1e-6) {
					a1 = Math.max(a1, span[1]);
					continue;
				}
				rows.add(new double[] {a0, a1, key.b0, key.b1});
				a0 = span[0];
				a1 = span[1];
			}
			rows.add(new double[] {a0, a1, key.b0, key.b1});
		}
		for (Map.Entry<FaceKey, List<double[]>> entry : planes.entrySet()) {
			FaceKey p = entry.getKey();
			List<double[]> rows = entry.getValue();
			boolean stack = alwaysStack || p.column == Long.MIN_VALUE || p.face >= 1 && p.face <= 4;
			if (!stack) {
				for (double[] r : rows) {
					strip.add(new FaceKey(p.face, p.plane, r[2], r[3], p.chunk, p.top, p.column), r[0], r[1]);
				}
				continue;
			}
			rows.sort((a, b) -> a[0] != b[0] ? Double.compare(a[0], b[0]) : a[1] != b[1] ? Double.compare(a[1], b[1]) : Double.compare(a[2], b[2]));
			double[] cur = rows.get(0).clone();
			for (int i = 1; i < rows.size(); i++) {
				double[] r = rows.get(i);
				if (Math.abs(r[0] - cur[0]) < 1e-6 && Math.abs(r[1] - cur[1]) < 1e-6 && r[2] <= cur[3] + 1e-6) {
					cur[3] = Math.max(cur[3], r[3]);
					continue;
				}
				strip.add(new FaceKey(p.face, p.plane, cur[2], cur[3], p.chunk, p.top, p.column), cur[0], cur[1]);
				cur = r.clone();
			}
			strip.add(new FaceKey(p.face, p.plane, cur[2], cur[3], p.chunk, p.top, p.column), cur[0], cur[1]);
		}
	}

	/** Tiles packed into arrays with their bounds; {@code quad} floats per quad, x, y, z first in each vertex. */
	private static Tile[] pack(Map<Long, List<float[]>> tiles, int quadFloats) {
		int stride = quadFloats / 4;
		List<Tile> out = new ArrayList<>();
		for (List<float[]> quads : tiles.values()) {
			float[] packed = new float[quads.size() * quadFloats];
			double bx0 = Double.MAX_VALUE;
			double by0 = Double.MAX_VALUE;
			double bz0 = Double.MAX_VALUE;
			double bx1 = -Double.MAX_VALUE;
			double by1 = -Double.MAX_VALUE;
			double bz1 = -Double.MAX_VALUE;
			for (int i = 0; i < quads.size(); i++) {
				float[] q = quads.get(i);
				System.arraycopy(q, 0, packed, i * quadFloats, quadFloats);
				for (int o = 0; o < quadFloats; o += stride) {
					bx0 = Math.min(bx0, q[o]);
					by0 = Math.min(by0, q[o + 1]);
					bz0 = Math.min(bz0, q[o + 2]);
					bx1 = Math.max(bx1, q[o]);
					by1 = Math.max(by1, q[o + 1]);
					bz1 = Math.max(bz1, q[o + 2]);
				}
			}
			out.add(new Tile(packed, quads.size(), new AABB(bx0, by0 - 0.01, bz0, bx1, by1 + 0.01, bz1)));
		}
		return out.toArray(new Tile[0]);
	}

	/** Blocks scanned down from a layer for the floor. */
	private static final int FLOOR_SCAN = 80;
	/** Top faces up to this high over the floor count as floor (slabs, stairs, carpets). */
	private static final double FLOOR_TOPS = 0.6;

	/**
	 * The room under a layer, for the floor effects. Per column, the floors are the solid blocks
	 * with open space above them, under the ceiling's blocks. Then every visible face of every
	 * block from the lowest floor up to the layer, in the layer's columns and on the walls around them,
	 * following the blocks' real shapes, each corner carrying its height over its column's floor
	 * (v). With only the starlit floor or constellations on, just the floor's top faces.
	 */
	private static void bakeFloor(ClientLevel level, Set<Long> plan, int top, List<int[]> carved, int flags, Map<Long, List<float[]>> tiles) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos near = new BlockPos.MutableBlockPos();
		// Floors per column, from the top down, {block y, surface height}: under the ceiling's
		// blocks and the air below them, every solid block with open space above it (the floor, but
		// also a beam or platform between the roof and the floor, with the floor under it found too).
		// Stops at solid ground 4 thick.
		Map<Long, List<double[]>> floorsAt = new HashMap<>();
		for (long cell : plan) {
			int cx = x(cell);
			int cz = z(cell);
			List<double[]> floors = new ArrayList<>();
			boolean air = false;
			boolean above = false;
			int run = 0;
			for (int yy = top - 1; yy >= top - FLOOR_SCAN; yy--) {
				BlockState state = level.getBlockState(at.set(cx, yy, cz));
				net.minecraft.world.phys.shapes.VoxelShape shape = state.getCollisionShape(level, at);
				boolean solid = !shape.isEmpty() && !empty(state);
				if (!air) {
					air = !solid;
					continue;
				}
				if (solid) {
					if (!above) {
						floors.add(new double[] {yy, yy + shape.max(net.minecraft.core.Direction.Axis.Y)});
					}
					if (++run >= 4) {
						break;
					}
				} else {
					run = 0;
				}
				above = solid;
			}
			if (!floors.isEmpty()) {
				floorsAt.put(cell, floors);
			}
		}
		// Walls round the floor take the floors of the column they stand next to (the lowest one).
		Map<Long, List<double[]>> columns = new HashMap<>(floorsAt);
		for (Map.Entry<Long, List<double[]>> entry : floorsAt.entrySet()) {
			int cx = x(entry.getKey());
			int cz = z(entry.getKey());
			List<double[]> floors = entry.getValue();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					long n = key(cx + dx, cz + dz);
					if (plan.contains(n)) {
						continue;
					}
					List<double[]> old = columns.get(n);
					if (old == null || old.get(old.size() - 1)[1] > floors.get(floors.size() - 1)[1]) {
						columns.put(n, floors);
					}
				}
			}
		}
		boolean all = (flags & 8) != 0;
		Map<FaceKey, List<double[]>> faces = new HashMap<>();
		for (Map.Entry<Long, List<double[]>> entry : columns.entrySet()) {
			long column = entry.getKey();
			int cx = x(column);
			int cz = z(column);
			boolean inside = plan.contains(column);
			List<double[]> floors = entry.getValue();
			int lowestBase = (int) floors.get(floors.size() - 1)[0];
			int from = inside ? lowestBase : lowestBase + 1;
			int to = all ? top - 1 : (int) floors.get(0)[0];
			for (int yy = from; yy <= to; yy++) {
				// The floor this block stands over (the nearest one at or under it).
				double[] floor = floors.get(floors.size() - 1);
				for (double[] f : floors) {
					if (f[0] <= yy) {
						floor = f;
						break;
					}
				}
				if (!all && (!inside || (int) floor[0] != yy)) {
					continue;
				}
				double surface = floor[1];
				int surfaceKey = (int) Math.round(surface * 1000.0);
				BlockState state = level.getBlockState(at.set(cx, yy, cz));
				if (empty(state) || carved(carved, cx, yy, cz)) {
					continue;
				}
				List<AABB> boxes = shapeOf(level, state, at);
				if (boxes.isEmpty()) {
					continue;
				}
				for (int f = 0; f < FACES.length; f++) {
					if (!all && f != 5) {
						continue;
					}
					int[] face = FACES[f];
					// Wall columns: undersides (overhangs, stair steps), and the sides turned into the room.
					if (!inside && (f == 5 || f != 0 && !plan.contains(key(cx + face[0], cz + face[2])))) {
						continue;
					}
					BlockState next = level.getBlockState(near.set(cx + face[0], yy + face[1], cz + face[2]));
					boolean open = empty(next) || !next.isFaceSturdy(level, near, SIDES[f].getOpposite());
					for (AABB box : boxes) {
						boolean onEdge = face[0] > 0 ? box.maxX >= 1 : face[0] < 0 ? box.minX <= 0
							: face[2] > 0 ? box.maxZ >= 1 : face[2] < 0 ? box.minZ <= 0 : face[1] > 0 ? box.maxY >= 1 : box.minY <= 0;
						if (onEdge && !open) {
							continue;
						}
						if (!all && yy + box.maxY - surface > FLOOR_TOPS) {
							continue;
						}
						// Tops keep their block's reflectivity (and only merge with the same).
						addFace(faces, f, cx, yy, cz, box, surfaceKey, f == 5 ? reflectivity(state) : Long.MIN_VALUE, GLOW_INSET);
					}
				}
			}
		}
		// Floor faces: the column field is a reflectivity, never a place, so they always stack.
		merge(faces, true, (key, a0, a1) -> {
			double[][] corners = corners(key, a0, a1);
			double surface = key.top / 1000.0;
			float[] quad = new float[GLOW_QUAD];
			for (int i = 0; i < 4; i++) {
				quad[i * 5] = (float) corners[i][0];
				quad[i * 5 + 1] = (float) corners[i][1];
				quad[i * 5 + 2] = (float) corners[i][2];
				// Tops: 1 plus how reflective the block is (0..1); undersides -1, sides 0.
				quad[i * 5 + 3] = key.face == 5 ? 1f + key.column / 100f * 0.998f : key.face == 0 ? -1f : 0f;
				quad[i * 5 + 4] = (float) (corners[i][1] - surface);
			}
			addTile(tiles, quad);
		});
	}

	private static final Map<net.minecraft.world.level.block.Block, Integer> REFLECT = new HashMap<>();

	/**
	 * How much a block's top reflects the sky, in percent, by what it's made of: polished and
	 * smooth stone, quartz, obsidian, ice, glazed and metal blocks a lot; bricks, tiles, raw stone
	 * and concrete some; cobble, mossy, cracked, wood and terracotta a little; cloth, earth,
	 * plants and snow not at all.
	 */
	private static int reflectivity(BlockState state) {
		return REFLECT.computeIfAbsent(state.getBlock(), block -> {
			String n = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath();
			if (has(n, "carpet", "wool", "concrete_powder", "dirt", "grass", "mycelium", "podzol", "farmland", "mud", "soul_s", "sand", "gravel",
				"leaves", "hay", "snow", "sponge", "slime", "honey", "coral", "clay", "moss_block", "moss_carpet", "_bed", "banner", "vine", "web")
				&& !has(n, "sandstone")) {
				return 0;
			}
			if (has(n, "mossy", "cracked", "cobble", "bedrock", "netherrack", "tuff", "rooted")) {
				return 15;
			}
			if (has(n, "polished", "smooth", "quartz", "glazed", "obsidian", "ice", "prismarine", "purpur", "amethyst", "calcite")
				|| n.equals("iron_block") || n.equals("gold_block") || n.equals("diamond_block") || n.equals("emerald_block")
				|| n.equals("netherite_block") || n.equals("lapis_block") || has(n, "copper")) {
				return 85;
			}
			if (has(n, "concrete")) {
				return 45;
			}
			if (has(n, "brick", "tile", "stone", "deepslate", "blackstone", "basalt", "andesite", "diorite", "granite", "chiseled", "end_stone")) {
				return 38;
			}
			if (has(n, "plank", "log", "wood", "stem", "hyphae", "bamboo")) {
				return 20;
			}
			if (has(n, "terracotta")) {
				return 18;
			}
			return 25;
		});
	}

	private static boolean has(String name, String... parts) {
		for (String part : parts) {
			if (name.contains(part)) {
				return true;
			}
		}
		return false;
	}

	/** Chunk-sized tiles, by where a quad starts. */
	private static void addTile(Map<Long, List<float[]>> tiles, float[] quad) {
		long k = key(Math.floorDiv((int) Math.floor(quad[0]), 16), Math.floorDiv((int) Math.floor(quad[2]), 16));
		tiles.computeIfAbsent(k, t -> new ArrayList<>()).add(quad);
	}

	/**
	 * The plan as rectangles {x0, z0, x1, z1} (inclusive): runs along x, stacked along z while
	 * they line up, never across a chunk border (so tiles stay small).
	 */
	private static List<int[]> rects(Set<Long> plan) {
		java.util.TreeMap<Integer, java.util.TreeSet<Integer>> rows = new java.util.TreeMap<>();
		for (long cell : plan) {
			rows.computeIfAbsent(z(cell), z -> new java.util.TreeSet<>()).add(x(cell));
		}
		List<int[]> out = new ArrayList<>();
		Map<Long, int[]> open = new HashMap<>();
		for (Map.Entry<Integer, java.util.TreeSet<Integer>> row : rows.entrySet()) {
			int z = row.getKey();
			List<int[]> runs = new ArrayList<>();
			int start = 0;
			int prev = 0;
			boolean any = false;
			for (int x : row.getValue()) {
				if (any && (x != prev + 1 || Math.floorDiv(x, 16) != Math.floorDiv(start, 16))) {
					runs.add(new int[] {start, prev});
					start = x;
				} else if (!any) {
					start = x;
					any = true;
				}
				prev = x;
			}
			if (any) {
				runs.add(new int[] {start, prev});
			}
			Map<Long, int[]> next = new HashMap<>();
			for (int[] run : runs) {
				long k = key(run[0], run[1]);
				int[] rect = open.remove(k);
				if (rect != null && rect[3] == z - 1 && Math.floorDiv(rect[1], 16) == Math.floorDiv(z, 16)) {
					rect[3] = z;
					next.put(k, rect);
				} else {
					if (rect != null) {
						out.add(rect);
					}
					next.put(k, new int[] {run[0], z, run[1], z});
				}
			}
			out.addAll(open.values());
			open = next;
		}
		out.addAll(open.values());
		return out;
	}

	/**
	 * A face's plane: which way it faces, where the plane is, its extent across (b: height for
	 * the sides, z for the underside), the chunk it's in along its length, and the layer above.
	 * Faces with the same key side by side are merged along their length (a).
	 */
	private record FaceKey(int face, double plane, double b0, double b1, int chunk, int top, long column) {
	}

	/**
	 * Thins the sky out past a layer's edge: each corner's amount is multiplied by how close it
	 * is to the layer (1 on and at its edge, down to 0 {@code reach + 1} columns out), blended
	 * smoothly between columns so there's no hard line anywhere.
	 */
	private static float[] sideways(float[] quad, Map<Long, Integer> ring, int reach) {
		if (ring == null) {
			return quad;
		}
		for (int o = 0; o < QUAD; o += 4) {
			quad[o + 3] *= (float) near(ring, reach, quad[o], quad[o + 2]);
		}
		return quad;
	}

	/** How close a point is to the layer sideways: 1 on it and at its edge, fading to 0 past {@code reach} columns. */
	private static double near(Map<Long, Integer> ring, int reach, double px, double pz) {
		double x = px - 0.5;
		double z = pz - 0.5;
		// Bilinear over column centres.
		int x0 = (int) Math.floor(x);
		int z0 = (int) Math.floor(z);
		double fx = x - x0;
		double fz = z - z0;
		double h = column(ring, x0, z0, reach) * (1 - fx) * (1 - fz) + column(ring, x0 + 1, z0, reach) * fx * (1 - fz)
			+ column(ring, x0, z0 + 1, reach) * (1 - fx) * fz + column(ring, x0 + 1, z0 + 1, reach) * fx * fz;
		// Plan columns count fully up to their own edges.
		h = Math.max(h, edgeOfPlan(ring, px, pz) ? 1.0 : 0.0);
		return h * h * (3 - 2 * h);
	}

	private static double column(Map<Long, Integer> ring, int x, int z, int reach) {
		Integer d = ring.get(key(x, z));
		return d == null ? 0.0 : 1.0 - d / (reach + 1.0);
	}

	/** A point on the border of (or in) a plan column. */
	private static boolean edgeOfPlan(Map<Long, Integer> ring, double x, double z) {
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		for (int dx = -1; dx <= 0; dx++) {
			for (int dz = -1; dz <= 0; dz++) {
				int cx = bx + dx;
				int cz = bz + dz;
				if (x >= cx - 0.02 && x <= cx + 1.02 && z >= cz - 0.02 && z <= cz + 1.02) {
					Integer d = ring.get(key(cx, cz));
					if (d != null && d == 0) {
						return true;
					}
				}
			}
		}
		Integer d = ring.get(key(bx, bz));
		return d != null && d == 0;
	}

	private static void addFace(Map<FaceKey, List<double[]>> faces, int f, int x, int y, int z, AABB box, int top, long column, float inset) {
		double plane;
		double a0;
		double a1;
		double b0;
		double b1;
		int along;
		if (f == 0 || f == 5) {
			plane = f == 0 ? y + box.minY - inset : y + box.maxY + inset;
			a0 = x + box.minX;
			a1 = x + box.maxX;
			b0 = z + box.minZ;
			b1 = z + box.maxZ;
			along = x;
		} else if (f <= 2) {
			plane = f == 1 ? x + box.maxX + inset : x + box.minX - inset;
			a0 = z + box.minZ;
			a1 = z + box.maxZ;
			b0 = y + box.minY;
			b1 = y + box.maxY;
			along = z;
		} else {
			plane = f == 3 ? z + box.maxZ + inset : z + box.minZ - inset;
			a0 = x + box.minX;
			a1 = x + box.maxX;
			b0 = y + box.minY;
			b1 = y + box.maxY;
			along = x;
		}
		faces.computeIfAbsent(new FaceKey(f, plane, b0, b1, Math.floorDiv(along, 16), top, column), k -> new ArrayList<>()).add(new double[] {a0, a1});
	}

	/** Down and the four sides (never up: inside the ceiling). */
	private static final int[][] FACES = {{0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}};
	private static final net.minecraft.core.Direction[] SIDES = {
		net.minecraft.core.Direction.DOWN, net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.WEST,
		net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.UP
	};

	/** A merged face strip, just off the blocks and grown a little; each corner gets the sky amount for its height. */
	private static float[] faceQuad(FaceKey key, double a0, double a1, float fade) {
		double[][] corners = corners(key, a0, a1);
		float[] quad = new float[QUAD];
		for (int i = 0; i < 4; i++) {
			double cy = corners[i][1];
			quad[i * 4] = (float) corners[i][0];
			quad[i * 4 + 1] = (float) cy;
			quad[i * 4 + 2] = (float) corners[i][2];
			quad[i * 4 + 3] = (float) Math.clamp(1.0 - (key.top - cy) / fade, 0.0, 1.0);
		}
		return quad;
	}

	/**
	 * A block-effect strip: per corner its depth under the layer, and the fade (whole blocks)
	 * plus how close it is to the layer sideways (the fraction, under 1).
	 */
	private static float[] glowQuad(FaceKey key, double a0, double a1, int fade, Map<Long, Integer> ring, int reach) {
		double[][] corners = corners(key, a0, a1);
		float[] quad = new float[GLOW_QUAD];
		for (int i = 0; i < 4; i++) {
			double cy = corners[i][1];
			quad[i * 5] = (float) corners[i][0];
			quad[i * 5 + 1] = (float) cy;
			quad[i * 5 + 2] = (float) corners[i][2];
			quad[i * 5 + 3] = (float) (key.top - cy);
			quad[i * 5 + 4] = fade + (float) Math.min(ring == null ? 1.0 : near(ring, reach, corners[i][0], corners[i][2]), 0.999);
		}
		return quad;
	}

	private static double[][] corners(FaceKey key, double a0, double a1) {
		double[][] corners;
		double g = GROW;
		if (key.face == 0 || key.face == 5) {
			double py = key.plane;
			corners = new double[][] {{a0 - g, py, key.b0 - g}, {a1 + g, py, key.b0 - g}, {a1 + g, py, key.b1 + g}, {a0 - g, py, key.b1 + g}};
		} else if (key.face <= 2) {
			double px = key.plane;
			corners = new double[][] {{px, key.b1 + g, a0 - g}, {px, key.b1 + g, a1 + g}, {px, key.b0 - g, a1 + g}, {px, key.b0 - g, a0 - g}};
		} else {
			double pz = key.plane;
			corners = new double[][] {{a0 - g, key.b1 + g, pz}, {a1 + g, key.b1 + g, pz}, {a1 + g, key.b0 - g, pz}, {a0 - g, key.b0 - g, pz}};
		}
		return corners;
	}

	/** The point is inside a boss part that has sky (its floor plan's bounds, under its layers). */
	static boolean underSky(Vec3 at) {
		if (!active()) {
			return false;
		}
		for (Baked baked : BAKED.values()) {
			if (!baked.empty() && at.x >= baked.minX && at.x <= baked.maxX && at.z >= baked.minZ && at.z <= baked.maxZ
				&& skyAbove((int) Math.floor(at.x), at.y, (int) Math.floor(at.z)) != Integer.MIN_VALUE) {
				return true;
			}
		}
		return false;
	}

	/** The lowest layer at or above height {@code y} over world column (x, z), or {@code Integer.MIN_VALUE}. */
	static int skyAbove(int x, double y, int z) {
		long k = key(x, z);
		int best = Integer.MIN_VALUE;
		for (Baked baked : BAKED.values()) {
			int[] ys = baked.plan.get(k);
			if (ys == null) {
				continue;
			}
			for (int top : ys) {
				if (top >= y && (best == Integer.MIN_VALUE || top < best)) {
					best = top;
				}
			}
		}
		return best;
	}

	/**
	 * A block's boxes as drawn for the effects: its outline shape (stairs, slabs, walls), but
	 * nothing for blocks you walk through (torches, flowers, signs, vines, rails), which would
	 * otherwise get a box drawn round them.
	 */
	private static List<AABB> shapeOf(ClientLevel level, BlockState state, BlockPos pos) {
		if (state.getCollisionShape(level, pos).isEmpty()) {
			return List.of();
		}
		return state.getShape(level, pos).toAabbs();
	}

	/**
	 * Nothing to put sky on: air, invisible blocks, and see-through glass (blocks, panes, bars),
	 * which stays visible as glass instead of turning into a sky-coloured box.
	 */
	private static boolean empty(BlockState state) {
		// Invisible blocks (barriers, light blocks, structure voids; also block entities drawn
		// their own way, like skulls and chests) count as air: no sky drawn on them, and they
		// don't hide the faces of the blocks next to them.
		return state.isAir() || state.getRenderShape() == net.minecraft.world.level.block.RenderShape.INVISIBLE
			|| state.getBlock() instanceof net.minecraft.world.level.block.TransparentBlock
			|| state.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock;
	}

	/** Invisible blocks (barriers, light blocks, structure voids): Space Roof acts as if they weren't there. */
	static boolean invisible(BlockState state) {
		net.minecraft.world.level.block.Block block = state.getBlock();
		return block instanceof net.minecraft.world.level.block.BarrierBlock
			|| block instanceof net.minecraft.world.level.block.LightBlock
			|| block instanceof net.minecraft.world.level.block.StructureVoidBlock;
	}

	/** Blocks under a layer that must all be solid for a column to count as wall. */
	private static final int WALL_DEPTH = 6;

	/**
	 * A column the boss fill stops at: solid all the way down for {@code WALL_DEPTH} blocks under
	 * the layer. A column solid only near the ceiling, with open space lower down (stairs or a
	 * ledge running up to the roof), is still part of the room and gets the sky.
	 */
	private static boolean wall(ClientLevel level, BlockPos.MutableBlockPos at, int x, int y, int z) {
		for (int k = 1; k <= WALL_DEPTH; k++) {
			if (!stops(level, at.set(x, y - k, z))) {
				return false;
			}
		}
		return true;
	}

	/** A full block the boss fill stops at (not an invisible one like a barrier). */
	private static boolean stops(ClientLevel level, BlockPos.MutableBlockPos at) {
		BlockState state = level.getBlockState(at);
		return !empty(state) && state.isCollisionShapeFullBlock(level, at);
	}

	// ------------------------------------------------------------------ storage

	/**
	 * {"Room name": [{"y": 92, "cells": [[x, z], ...]}, {"carve": [x0, y0, z0, x1, y1, z1]}, ...]},
	 * in room coordinates.
	 */
	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		JsonObject rooms = IslandSaves.readIslands(FILE);
		for (String key : rooms.keySet()) {
			JsonElement element = rooms.get(key);
			if (!element.isJsonArray()) {
				continue;
			}
			List<Sheet> sheets = new ArrayList<>();
			List<int[]> carves = new ArrayList<>();
			for (JsonElement entry : element.getAsJsonArray()) {
				if (entry.isJsonObject() && entry.getAsJsonObject().has("carve")) {
					JsonArray c = entry.getAsJsonObject().getAsJsonArray("carve");
					if (c.size() == 6) {
						int[] b = new int[6];
						for (int i = 0; i < 6; i++) {
							b[i] = c.get(i).getAsInt();
						}
						carves.add(b);
					}
					continue;
				}
				// Older files held painted blocks ([x, y, z]); those are skipped.
				if (!entry.isJsonObject() || !entry.getAsJsonObject().has("y") || !entry.getAsJsonObject().has("cells")) {
					continue;
				}
				JsonObject object = entry.getAsJsonObject();
				Set<Long> cells = new LinkedHashSet<>();
				for (JsonElement cell : object.getAsJsonArray("cells")) {
					if (cell.isJsonArray() && cell.getAsJsonArray().size() == 2) {
						cells.add(key(cell.getAsJsonArray().get(0).getAsInt(), cell.getAsJsonArray().get(1).getAsInt()));
					}
				}
				if (!cells.isEmpty()) {
					sheets.add(new Sheet(object.get("y").getAsInt(), cells));
				}
			}
			if (!sheets.isEmpty()) {
				SAVED.put(key, sheets);
			}
			if (!carves.isEmpty()) {
				CARVES.put(key, carves);
			}
		}
		splitOldF7();
		loadBundled();
		version++;
	}

	/**
	 * Layers placed before F7 was split into phases and sections ({@code @boss:f7}) go to the
	 * part each of their cells is in; old carved boxes go to every part (they only act where they are).
	 */
	private static void splitOldF7() {
		List<Sheet> old = SAVED.remove("@boss:f7");
		List<int[]> oldCarves = CARVES.remove("@boss:f7");
		if (old == null && oldCarves == null) {
			return;
		}
		if (old != null) {
			for (Sheet sheet : old) {
				Map<String, Set<Long>> byPart = new LinkedHashMap<>();
				for (long cell : sheet.cells) {
					byPart.computeIfAbsent(f7Part(x(cell), sheet.y - 1, z(cell)), part -> new LinkedHashSet<>()).add(cell);
				}
				for (Map.Entry<String, Set<Long>> entry : byPart.entrySet()) {
					SAVED.computeIfAbsent("@boss:f7:" + entry.getKey(), name -> new ArrayList<>()).add(new Sheet(sheet.y, entry.getValue()));
				}
			}
		}
		if (oldCarves != null) {
			for (String part : F7_PARTS) {
				for (int[] box : oldCarves) {
					CARVES.computeIfAbsent("@boss:f7:" + part, name -> new ArrayList<>()).add(box.clone());
				}
			}
		}
		save();
	}

	/**
	 * The layers that ship with Stray (the F7 boss, every phase and P3 section), stored as
	 * rectangles: {"@boss:f7:p1": [{"y": 246, "rects": [[x0, z0, x1, z1], ...]}, ...]}. A part in
	 * the local file (from the sky painter) replaces the shipped one, so they can still be edited.
	 */
	private static void loadBundled() {
		try (java.io.InputStream in = SpaceRoof.class.getResourceAsStream("/assets/stray/space_roof/f7.json")) {
			if (in == null) {
				return;
			}
			JsonObject rooms = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
			for (String name : rooms.keySet()) {
				if (SAVED.containsKey(name) || CARVES.containsKey(name)) {
					continue;
				}
				List<Sheet> sheets = new ArrayList<>();
				List<int[]> carves = new ArrayList<>();
				for (JsonElement entry : rooms.getAsJsonArray(name)) {
					JsonObject object = entry.getAsJsonObject();
					if (object.has("carve")) {
						JsonArray c = object.getAsJsonArray("carve");
						int[] b = new int[6];
						for (int i = 0; i < 6; i++) {
							b[i] = c.get(i).getAsInt();
						}
						carves.add(b);
						continue;
					}
					Set<Long> cells = new LinkedHashSet<>();
					for (JsonElement rect : object.getAsJsonArray("rects")) {
						JsonArray r = rect.getAsJsonArray();
						for (int x = r.get(0).getAsInt(); x <= r.get(2).getAsInt(); x++) {
							for (int z = r.get(1).getAsInt(); z <= r.get(3).getAsInt(); z++) {
								cells.add(key(x, z));
							}
						}
					}
					if (!cells.isEmpty()) {
						sheets.add(new Sheet(object.get("y").getAsInt(), cells));
					}
				}
				if (!sheets.isEmpty()) {
					SAVED.put(name, sheets);
				}
				if (!carves.isEmpty()) {
					CARVES.put(name, carves);
				}
			}
		} catch (Exception e) {
			Stray.LOGGER.warn("Space roof: the shipped layers couldn't be read", e);
		}
	}

	private static void save() {
		JsonObject rooms = new JsonObject();
		Set<String> names = new LinkedHashSet<>(SAVED.keySet());
		names.addAll(CARVES.keySet());
		for (String name : names) {
			JsonArray list = new JsonArray();
			for (Sheet sheet : SAVED.getOrDefault(name, List.of())) {
				JsonObject object = new JsonObject();
				object.addProperty("y", sheet.y);
				JsonArray cells = new JsonArray();
				for (long cell : sheet.cells) {
					JsonArray xz = new JsonArray();
					xz.add(x(cell));
					xz.add(z(cell));
					cells.add(xz);
				}
				object.add("cells", cells);
				list.add(object);
			}
			for (int[] b : CARVES.getOrDefault(name, List.of())) {
				JsonObject object = new JsonObject();
				JsonArray c = new JsonArray();
				for (int v : b) {
					c.add(v);
				}
				object.add("carve", c);
				list.add(object);
			}
			rooms.add(name, list);
		}
		IslandSaves.writeIslands(FILE, rooms);
	}

	// ------------------------------------------------------------------ pipeline

	/** Sky render types by variant: bit 1 shooting stars, 2 cached nebula, 4 the flat layer (no discard). */
	private static final Map<Integer, RenderType> SKY_TYPES = new HashMap<>();

	/**
	 * The sky's render type. The flat layer gets its own shader without discard: a shader that can
	 * discard keeps the GPU from skipping pixels hidden behind blocks early, so every hidden sky
	 * pixel would run the whole sky first.
	 */
	private static synchronized RenderType renderType(boolean shooting, boolean sheet) {
		boolean cached = NebulaCache.ready();
		int bits = (shooting ? 1 : 0) | (cached ? 2 : 0) | (sheet ? 4 : 0);
		return SKY_TYPES.computeIfAbsent(bits, k -> {
			String name = "space_roof" + (cached ? "_cached" : "") + (shooting ? "_shooting" : "") + (sheet ? "_sheet" : "");
			RenderPipeline built = build("pipeline/" + name, shooting, cached, sheet);
			RenderSetup.RenderSetupBuilder setup = RenderSetup.builder(built);
			if (cached) {
				setup.withTexture("Sampler0", NebulaCache.TEXTURE, () -> com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getClampToEdge(com.mojang.renderpearl.api.textures.FilterMode.LINEAR));
			}
			RenderType made = RenderTypeAccessor.stray$create("stray_" + name, setup.createRenderSetup());
			SpaceRoofGpu.own(made);
			return made;
		});
	}

	private static RenderType glowType;
	private static RenderType floorType;

	/** The floor effects: over the room's blocks, light added and shade, fog and reflection painted (premultiplied). */
	private static synchronized RenderType floorType() {
		if (floorType == null) {
			RenderPipeline floorPipeline = RenderPipelinesInvoker.stray$register(RenderPipeline.builder()
				.withLocation(Stray.id("pipeline/space_floor"))
				.withVertexShader(SHADER)
				.withFragmentShader(Stray.id("core/space_floor"))
				.withBindGroupLayout(BindGroupLayouts.GLOBALS)
				.withBindGroupLayout(BindGroupLayouts.PROJECTION)
				.withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
				// Faded into the fog (Space Roof haze) like the blocks under them.
				.withBindGroupLayout(BindGroupLayouts.FOG)
				.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
				.withPrimitiveTopology(PrimitiveTopology.QUADS)
				.withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
				.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
				.withCull(false)
				.build());
			floorType = RenderTypeAccessor.stray$create("stray_space_floor", RenderSetup.builder(floorPipeline).createRenderSetup());
			SpaceRoofGpu.own(floorType);
		}
		return floorType;
	}

	private static RenderType glowTextured;

	/**
	 * The block effects: over the blocks, under the sky, light added and cracks painted
	 * (premultiplied). Once the crack pattern texture is ready (CrackCache), the variant that
	 * reads it instead of working it out per pixel.
	 */
	private static synchronized RenderType glowType() {
		boolean textured = CrackCache.ready();
		if (textured ? glowTextured != null : glowType != null) {
			return textured ? glowTextured : glowType;
		}
		RenderPipeline.Builder builder = RenderPipeline.builder()
			.withLocation(Stray.id(textured ? "pipeline/space_glow_cracks" : "pipeline/space_glow"))
			.withVertexShader(SHADER)
			.withFragmentShader(Stray.id("core/space_glow"))
			.withBindGroupLayout(BindGroupLayouts.GLOBALS)
			.withBindGroupLayout(BindGroupLayouts.PROJECTION)
			.withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
			// Faded into the fog (Space Roof haze) like the blocks under them.
			.withBindGroupLayout(BindGroupLayouts.FOG)
			.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
			.withPrimitiveTopology(PrimitiveTopology.QUADS)
			.withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
			.withCull(false);
		if (textured) {
			builder.withShaderDefine("CRACK_TEX");
			builder.withBindGroupLayout(BindGroupLayouts.SAMPLER0);
		}
		RenderSetup.RenderSetupBuilder setup = RenderSetup.builder(RenderPipelinesInvoker.stray$register(builder.build()));
		if (textured) {
			setup.withTexture("Sampler0", CrackCache.TEXTURE, () -> com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getRepeat(com.mojang.renderpearl.api.textures.FilterMode.LINEAR));
		}
		RenderType made = RenderTypeAccessor.stray$create(textured ? "stray_space_glow_cracks" : "stray_space_glow", setup.createRenderSetup());
		SpaceRoofGpu.own(made);
		if (textured) {
			glowTextured = made;
		} else {
			glowType = made;
		}
		return made;
	}

	private static RenderPipeline build(String location, boolean shooting, boolean cachedNebula, boolean sheet) {
		RenderPipeline.Builder builder = RenderPipeline.builder()
			.withLocation(Stray.id(location))
			.withVertexShader(SHADER)
			.withFragmentShader(SHADER)
			.withBindGroupLayout(BindGroupLayouts.GLOBALS)
			.withBindGroupLayout(BindGroupLayouts.PROJECTION)
			.withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
			.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
			.withPrimitiveTopology(PrimitiveTopology.QUADS)
			// Depth tested like vanilla (26.x uses reversed depth: nearer is greater), so blocks and
			// things in front hide it. The flat layer is written like a real surface, so glass and
			// other translucent blocks behind it (drawn later) are hidden too. The fade on the blocks
			// isn't: it lies on solid faces with nothing see-through behind, and without depth writes
			// the GPU can skip its hidden pixels early even though it can discard.
			.withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), sheet))
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			// The flat layer only from below (its front faces down into the room): from above, e.g. in
			// freecam over the roof, it isn't drawn and the real roof shows.
			.withCull(sheet);
		if (shooting) {
			builder.withShaderDefine("SHOOTING_STARS");
		}
		if (cachedNebula) {
			builder.withShaderDefine("CACHED_NEBULA");
			builder.withBindGroupLayout(BindGroupLayouts.SAMPLER0);
		}
		if (sheet) {
			builder.withShaderDefine("SKY_SHEET");
		}
		return RenderPipelinesInvoker.stray$register(builder.build());
	}
}
