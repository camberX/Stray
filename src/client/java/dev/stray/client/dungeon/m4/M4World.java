package dev.stray.client.dungeon.m4;

import dev.stray.client.dungeon.DungeonParty.DungeonClass;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything BirdAddon draws in the world, with vanilla gizmos. "Depth off" (through walls)
 * is never used in Stray Legit.
 */
public final class M4World {
	private static final float LINE_WIDTH = 2f;
	private static final Vec3[] BOW_SPOTS = {
		new Vec3(5.0, 69.0, 4.0), new Vec3(9.0, 69.0, 14.0), new Vec3(16.0, 70.0, 7.0), new Vec3(-2.0, 69.0, 0.0),
		new Vec3(23.0, 69.0, 15.0), new Vec3(3.0, 69.0, 21.0), new Vec3(25.0, 69.0, 3.0), new Vec3(24.0, 69.0, 25.0),
		new Vec3(-2.0, 69.0, -6.0), new Vec3(5.0, 71.0, -11.0), new Vec3(12.0, 69.0, -4.0), new Vec3(-1.0, 69.0, 11.0),
		new Vec3(-18.0, 69.0, 6.0), new Vec3(-15.0, 69.0, 24.0), new Vec3(24.0, 69.0, -15.0), new Vec3(-15.0, 69.0, -16.0),
		new Vec3(-7.0, 69.0, 28.0), new Vec3(0.0, 69.0, -15.0)
	};

	private static float partial;
	private static double displayedProgress;
	private static long lastFrameNanos;

	private M4World() {
	}

	public static void init() {
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
			try {
				emit();
			} catch (RuntimeException exception) {
				dev.stray.Stray.LOGGER.debug("M4 world render failed", exception);
			}
		});
	}

	private static void emit() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			return;
		}
		partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		M4Config c = M4.config();
		boolean boss = M4.inBoss();
		if (c.waypoints) {
			waypoints(c, boss);
		}
		if (!boss) {
			return;
		}
		if (c.highlight) {
			highlight(c);
		}
		if (c.stun && c.stunHelper && M4Features.stunShown()) {
			if (M4Features.firstStunTimer != -1) {
				text("§5" + M4Stats.fixed(M4Features.firstStunTimer / 20.0, c.stunDecimals) + "s", M4Features.FIRST_STUN, 2f, false);
			}
			if (M4Features.secondStunTimer != -1) {
				text("§5" + M4Stats.fixed(M4Features.secondStunTimer / 20.0, c.stunDecimals) + "s", M4Features.SECOND_STUN, 2f, false);
			}
		}
		if (c.decoy) {
			decoy(c);
		}
		if (c.renderOptimizer && c.npcHighlight != 0) {
			for (Entity npc : new ArrayList<>(M4Features.npcs)) {
				if (c.npcHighlight == 2 || M4Features.relevantNpc(npc)) {
					box(renderBox(npc), 0xFFAAAAAA, c.npcStyle, true);
				}
			}
		}
		if (c.trajectories) {
			trajectories(c);
		}
		if (c.rabbits && c.rabbitsInHutch) {
			hutches(c);
		}
		if (c.spiritBear && c.bearExperimental && c.bearExperimentalRender) {
			bearPrediction(c);
		}
	}

	// ---------------------------------------------------------------- highlight

	private static void highlight(M4Config c) {
		int style = c.highlightStyle;
		if (c.highlightSheep) {
			mobs(Sheep.class, style);
		}
		if (c.highlightWolves) {
			mobs(Wolf.class, style);
		}
		if (c.highlightBats) {
			mobs(Bat.class, style);
		}
		if (c.highlightChickens) {
			mobs(Chicken.class, style);
		}
		if (c.highlightRabbits) {
			mobs(Rabbit.class, style);
		}
		if (c.highlightCows) {
			mobs(Cow.class, style);
		}
		if (c.highlightThorn && M4.thorn != null) {
			AABB thorn = renderBox(M4.thorn);
			if (c.highlightThornFlash && M4Features.damaged >= 0) {
				box(thorn, M4.THORN_DAMAGED_COLOR, style, true);
			} else {
				box(thorn, M4.THORN_COLOR, style, true);
			}
		}
		if (c.highlightBear && M4.bear != null && !(c.highlightBearOnlyMage && M4.myClass() != DungeonClass.MAGE)) {
			box(c.highlightBearNoInterpolation ? M4.bear.getBoundingBox() : renderBox(M4.bear), M4.BEAR_COLOR, style, true);
		}
	}

	private static void mobs(Class<? extends Entity> type, int style) {
		int color = M4.mobColor(type);
		for (Entity entity : M4.animalsOf(type)) {
			box(renderBox(entity), color, style, true);
		}
	}

	// ---------------------------------------------------------------- waypoints

	private static boolean onPractice(M4Config c) {
		return M4.onCgm4 && c.waypointsOnCgm4 || M4.onM4Miku && c.waypointsOnM4Miku;
	}

	private static void waypoints(M4Config c, boolean boss) {
		if (!boss && !M4.onCgm4 && !M4.onM4Miku) {
			return;
		}
		M4Waypoints.ensureLoaded();
		DungeonClass mine = M4.myClass();
		for (M4Waypoints.Waypoint waypoint : M4Waypoints.waypoints) {
			DungeonClass clazz = M4Waypoints.classFromName(waypoint.clazz);
			if (clazz == null) {
				continue;
			}
			if (M4.onCgm4 && c.waypointsOnCgm4) {
				classBox(waypoint.pos.x + 2, waypoint.pos.y + 41, waypoint.pos.z + 2, clazz, c);
			} else if (M4.onM4Miku && c.waypointsOnM4Miku) {
				classBox(waypoint.pos.x, waypoint.pos.y, waypoint.pos.z, clazz, c);
			} else if (boss && clazz == mine && M4Waypoints.shouldRenderNow(waypoint.start, waypoint.end)) {
				classBox(waypoint.pos.x, waypoint.pos.y, waypoint.pos.z, mine, c);
			}
		}
		if (!boss && !onPractice(c)) {
			return;
		}
		boolean practice = M4.onCgm4 || M4.onM4Miku;
		if (c.bearSpawnWaypoint && (!c.bearSpawnOnlyMage || practice || mine == DungeonClass.MAGE)) {
			bearSpawn(c);
		}
		if (c.bowPickupWaypoint && (!c.bowPickupOnlyTank || practice || mine == DungeonClass.TANK)) {
			bowPickup(c);
		}
	}

	private static void classBox(int x, int y, int z, DungeonClass clazz, M4Config c) {
		int color = switch (clazz) {
			case HEALER -> 0xFFFF55FF;
			case TANK -> 0xFF55FF55;
			case BERSERK -> 0xFFAA0000;
			case ARCHER -> 0xFFFFAA00;
			case MAGE -> 0xFF5555FF;
			default -> 0;
		};
		if (color != 0) {
			box(new AABB(x, y, z, x + 1, y + 1, z + 1), color, c.waypointStyle, c.waypointDepth);
		}
	}

	private static void bearSpawn(M4Config c) {
		// "Only when spawning": shown only while the bear is spawning or alive.
		if (c.bearSpawnOnlyWhenSpawning && !M4.spawning() && !M4.spawned()) {
			return;
		}
		Vec3 spot = M4.spawnSpot;
		AABB spawn = M4.onCgm4
			? new AABB(spot.x + 1.7, spot.y, spot.z + 1.7, spot.x + 2.3, spot.y + 1.8, spot.z + 2.3)
			: new AABB(spot.x - 0.3, spot.y, spot.z - 0.3, spot.x + 0.3, spot.y + 1.8, spot.z + 0.3);
		int color;
		if (!c.bearSpawnColors || !M4.spawned() && !M4.spawning()) {
			color = 0xFF5555FF;
		} else if (lookingAt(spawn, 3.0)) {
			color = 0xFFFFAA00;
		} else if (lookingAt(spawn, 100.0)) {
			color = 0xFF55FF55;
		} else {
			color = 0xFFFF5555;
		}
		box(spawn, color, c.waypointStyle, c.waypointDepth);
	}

	private static boolean lookingAt(AABB box, double range) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return false;
		}
		Vec3 eye = client.player.getEyePosition(partial);
		Vec3 end = eye.add(client.player.getViewVector(partial).scale(range));
		return box.clip(eye, end).isPresent();
	}

	private static void bowPickup(M4Config c) {
		Vec3 next = BOW_SPOTS[0];
		if (M4.bear != null && M4.spawned()) {
			double best = Double.MAX_VALUE;
			for (Vec3 spot : BOW_SPOTS) {
				double dx = spot.x - M4.bear.getX();
				double dz = spot.z - M4.bear.getZ();
				if (dx * dx + dz * dz < best) {
					best = dx * dx + dz * dz;
					next = spot;
				}
			}
		}
		List<Vec3> spots = new ArrayList<>();
		for (ArmorStand bow : M4.bows) {
			spots.add(new Vec3(bow.getX() - 0.5, Math.ceil(bow.getY()), bow.getZ() - 0.5));
		}
		// The next bow spot only when there is no bow, or a bear is coming.
		if (spots.isEmpty() || M4.spawning() || M4.spawned()) {
			spots.add(next);
		}
		for (Vec3 spot : spots) {
			Vec3 center = M4.onCgm4 ? spot.add(2.0, 41.0, 2.0) : spot;
			Vec3 previous = null;
			for (int i = 0; i <= 64; i++) {
				double angle = 2.0 * Math.PI * i / 64;
				Vec3 point = new Vec3(center.x + 1.5 * Math.cos(angle), center.y, center.z + 1.5 * Math.sin(angle));
				if (previous != null) {
					line(previous, point, 0xFF55FF55, c.waypointDepth, LINE_WIDTH);
				}
				previous = point;
			}
		}
	}

	// ---------------------------------------------------------------- decoy

	private static void decoy(M4Config c) {
		if (c.decoyOnlyBers && M4.myClass() != DungeonClass.BERSERK) {
			return;
		}
		Vec3 best = M4Features.bestSpot;
		if (c.decoyBestSpot && best != null && (!c.decoyHideAfterB1 || M4.spawnTimes.isEmpty())) {
			box(new AABB(best.x - 0.5, best.y, best.z - 0.5, best.x + 0.5, best.y, best.z + 0.5), 0xFFFF5555, c.decoyStyle, c.decoyDepth);
		}
		Vec3 cave = M4Features.caveSpot;
		if (c.decoyCave && cave != null && M4.spawnStartTimes.size() >= 4 && M4.spawnStartTimes.size() < 6) {
			box(new AABB(cave.x, cave.y, cave.z, cave.x + 1, cave.y, cave.z + 1), 0xFFFF5555, c.decoyStyle, c.decoyDepth);
		}
	}

	// ---------------------------------------------------------------- trajectories

	private static void trajectories(M4Config c) {
		for (Vec3 landing : M4Features.cowLandings) {
			box(flat(landing.x, landing.y, landing.z), M4.mobColor(Cow.class), c.trajectoryStyle, true);
		}
		for (Vec3 landing : M4Features.sheepLandings) {
			box(flat(landing.x, landing.y, landing.z), M4.mobColor(Sheep.class), c.trajectoryStyle, true);
		}
		if (c.trajectoryChickens) {
			for (Entity chicken : M4.animalsOf(Chicken.class)) {
				double y = M4Features.groundY(chicken.getX(), chicken.getZ());
				box(flat(chicken.getX(), y, chicken.getZ()), M4.mobColor(Chicken.class), c.trajectoryStyle, true);
			}
		}
	}

	private static AABB flat(double x, double y, double z) {
		return new AABB(x - 0.2, y, z - 0.2, x + 0.2, y, z + 0.2);
	}

	// ---------------------------------------------------------------- rabbits

	private static void hutches(M4Config c) {
		int[] counts = M4Features.rabbitCounts;
		for (int i = 0; i < M4Features.HUTCHES.length && i < counts.length; i++) {
			int count = counts[i];
			Vec3 hutch = M4Features.HUTCHES[i];
			if (count == 0 && c.rabbitsHideEmptyHutch) {
				continue;
			}
			if (count < 5) {
				text("§a" + count, hutch, 2f, true);
			} else if (count < 10) {
				text("§e" + count, hutch, 3f, true);
			} else {
				text("§4" + count, hutch.add(0.0, 0.5, 0.0), 5f, true);
			}
		}
	}

	// ---------------------------------------------------------------- bear prediction

	private static void bearPrediction(M4Config c) {
		List<Vec3> predictions = M4Particles.predictions;
		int remaining = M4Particles.predictionsRemaining;
		if (predictions.size() < 2 || remaining < 0) {
			return;
		}
		int total = predictions.size();
		double target = Math.max(0.0, Math.min(1.0, (total - remaining) / (double) total));
		long now = System.nanoTime();
		double dt = lastFrameNanos == 0L ? 0.0 : Math.min(0.1, (now - lastFrameNanos) / 1_000_000_000.0);
		lastFrameNanos = now;
		double progress;
		if (c.bearSmoothLine) {
			displayedProgress += (target - displayedProgress) * (1 - Math.exp(-12 * dt));
			progress = displayedProgress;
		} else {
			displayedProgress = target;
			progress = target;
		}
		Vec3 start = predictions.getFirst();
		Vec3 end = predictions.getLast();
		Vec3 split = start.add(end.subtract(start).scale(progress));
		line(start, split, 0xFFFF55FF, false, 10f);
		line(split, end, 0xFFAAAAAA, false, 10f);
	}

	// ---------------------------------------------------------------- drawing

	private static AABB renderBox(Entity entity) {
		Vec3 offset = entity.getPosition(partial).subtract(entity.position());
		return entity.getBoundingBox().move(offset);
	}

	private static boolean onTop(boolean depth) {
		return !depth && !dev.stray.Edition.LEGIT;
	}

	/** Style 0 outline, 1 filled, 2 filled + outline. Fills are see-through. */
	static void box(AABB box, int argb, int style, boolean depth) {
		int fill = (Math.round(0.35f * ((argb >>> 24) & 0xFF)) << 24) | (argb & 0xFFFFFF);
		GizmoStyle gizmo = switch (style) {
			case 1 -> GizmoStyle.fill(fill);
			case 2 -> GizmoStyle.strokeAndFill(argb, LINE_WIDTH, fill);
			default -> GizmoStyle.stroke(argb, LINE_WIDTH);
		};
		GizmoProperties properties = Gizmos.cuboid(box, gizmo);
		if (onTop(depth)) {
			properties.setAlwaysOnTop();
		}
	}

	static void line(Vec3 from, Vec3 to, int argb, boolean depth, float width) {
		GizmoProperties properties = Gizmos.line(from, to, argb, width);
		if (onTop(depth)) {
			properties.setAlwaysOnTop();
		}
	}

	/** Odin's world text: {@code scale} 1 is 0.025 blocks per pixel. */
	static void text(String text, Vec3 pos, float scale, boolean depth) {
		GizmoProperties properties = Gizmos.billboardText(text, pos, TextGizmo.Style.whiteAndCentered().withScale(0.4f * scale));
		if (onTop(depth)) {
			properties.setAlwaysOnTop();
		}
	}
}
