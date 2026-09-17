package dev.stray.client.movement;

import dev.stray.client.mining.SmoothRotate;
import dev.stray.client.mixin.ClientInputAccessor;
import dev.stray.client.ui.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Walks the local player to a coordinate with key presses only. The path is
 * A* over walkable blocks, straightened, then followed with a slightly
 * underdamped look so turns overshoot and settle like a mouse would.
 */
public final class PathWalker {
	private static final int MAX_WALK_TICKS = 20 * 60 * 5;
	private static final int REPLAN_LIMIT = 4;
	private static final Random RNG = new Random();

	private static List<Vec3> route = List.of();
	private static int index;
	private static Vec3 goal;
	private static boolean walking;
	private static int ticks;
	private static int replans;
	private static double bestDistSq;
	private static int stuckTicks;
	private static int jumpHold;
	private static int sprintPause;
	private static int settleTicks;

	private static float yaw;
	private static float pitch;
	private static float yawVel;
	private static float pitchVel;
	private static float yawStiff;
	private static float yawDamp;
	private static float pitchRest;
	private static float noiseSeedA;
	private static float noiseSeedB;

	private static Input keys = Input.EMPTY;
	private static Vec2 move = Vec2.ZERO;

	private PathWalker() {
	}

	public static void init() {
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> emit());
	}

	public static boolean walking() {
		return walking;
	}

	public static void go(double x, double y, double z) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			chat("No world.", ChatFormatting.RED);
			return;
		}
		Vec3 target = new Vec3(x, y, z);
		if (!plan(client.level, player, target, true)) {
			return;
		}
		goal = target;
		walking = true;
		ticks = 0;
		replans = 0;
		stuckTicks = 0;
		jumpHold = 0;
		sprintPause = 0;
		settleTicks = 0;
		bestDistSq = Double.MAX_VALUE;
		yaw = player.getYRot();
		pitch = player.getXRot();
		yawVel = 0f;
		pitchVel = 0f;
		rollPersonality();
		double length = length(route);
		chat(String.format(java.util.Locale.ROOT, "Walking %.0fm to %.1f %.1f %.1f.", length, x, y, z), ChatFormatting.GREEN);
	}

	public static void stop(boolean announce) {
		if (!walking) {
			return;
		}
		walking = false;
		keys = Input.EMPTY;
		move = Vec2.ZERO;
		route = List.of();
		if (announce) {
			chat("Stopped.", ChatFormatting.GRAY);
		}
	}

	public static void tick(Minecraft client) {
		if (!walking) {
			return;
		}
		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			stop(false);
			return;
		}
		if (++ticks > MAX_WALK_TICKS) {
			chat("Gave up after five minutes.", ChatFormatting.RED);
			stop(false);
			return;
		}
		if (settleTicks > 0) {
			settleTicks--;
			keys = Input.EMPTY;
			move = Vec2.ZERO;
			idleLook(player);
			if (settleTicks == 0) {
				stop(false);
			}
			return;
		}
		Vec3 pos = player.position();
		advanceWaypoint(pos);
		if (index >= route.size()) {
			arrive();
			return;
		}
		Vec3 target = route.get(index);
		Vec3 flat = new Vec3(target.x - pos.x, 0, target.z - pos.z);
		double dist = flat.length();
		double finalDist = Math.sqrt(distSqXZ(pos, route.getLast()));
		if (finalDist < 0.45 && Math.abs(route.getLast().y - pos.y) < 1.5) {
			arrive();
			return;
		}

		watchProgress(player, pos, client.level);
		if (!walking) {
			return;
		}

		Vec3 lookAt = lookPoint(pos);
		SmoothRotate.Rotation want = SmoothRotate.to(player.getEyePosition(), lookAt);
		float wantYaw = want.yaw() + wobble(noiseSeedA, 0.35f) * 3.2f;
		float wantPitch = Mth.clamp(want.pitch() * 0.55f + pitchRest + wobble(noiseSeedB, 0.21f) * 2.6f, -35f, 60f);
		spring(wantYaw, wantPitch);
		SmoothRotate.apply(player, yaw, pitch);

		if (dist > 0.05) {
			Vec3 dir = flat.scale(1.0 / dist);
			float rad = yaw * Mth.DEG_TO_RAD;
			double forwardX = -Mth.sin(rad);
			double forwardZ = Mth.cos(rad);
			double leftX = Mth.cos(rad);
			double leftZ = Mth.sin(rad);
			float forward = (float) (dir.x * forwardX + dir.z * forwardZ);
			float strafe = (float) (dir.x * leftX + dir.z * leftZ);
			boolean f = forward > 0.35f;
			boolean b = forward < -0.6f;
			boolean l = strafe > 0.42f;
			boolean r = strafe < -0.42f;
			if (!f && !b && !l && !r) {
				f = forward >= 0f;
				b = !f;
			}
			boolean jump = shouldJump(player, pos, target);
			boolean sprint = f && !b && dist > 1.6 && finalDist > 2.4 && sprintPause == 0;
			if (sprintPause > 0) {
				sprintPause--;
			} else if (sprint && RNG.nextInt(400) == 0) {
				sprintPause = 6 + RNG.nextInt(14);
			}
			keys = new Input(f, b, l, r, jump, false, sprint);
			move = new Vec2(strafe(l, r), impulse(f, b));
			if (move.lengthSquared() > 1f) {
				move = move.normalized();
			}
		} else {
			keys = Input.EMPTY;
			move = Vec2.ZERO;
		}
	}

	public static void applyInput(ClientInput input) {
		if (input == null || !walking) {
			return;
		}
		input.keyPresses = keys;
		((ClientInputAccessor) input).stray$setMoveVector(move);
	}

	private static void arrive() {
		keys = Input.EMPTY;
		move = Vec2.ZERO;
		settleTicks = 6 + RNG.nextInt(8);
		chat("Arrived.", ChatFormatting.GREEN);
	}

	private static void idleLook(LocalPlayer player) {
		spring(yaw + wobble(noiseSeedA, 0.35f) * 0.6f, pitch);
		SmoothRotate.apply(player, yaw, pitch);
	}

	private static void advanceWaypoint(Vec3 pos) {
		while (index < route.size() - 1) {
			Vec3 wp = route.get(index);
			double reach = index == route.size() - 1 ? 0.45 : 0.75;
			boolean close = distSqXZ(pos, wp) < reach * reach && Math.abs(wp.y - pos.y) < 1.6;
			if (!close) {
				Vec3 next = route.get(index + 1);
				double toNext = distSqXZ(pos, next);
				double wpToNext = distSqXZ(wp, next);
				if (toNext < wpToNext && Math.abs(next.y - pos.y) < 1.1 && distSqXZ(pos, wp) < 4.0) {
					close = true;
				}
			}
			if (!close) {
				break;
			}
			index++;
		}
	}

	private static Vec3 lookPoint(Vec3 pos) {
		Vec3 wp = route.get(index);
		double ahead = Math.sqrt(distSqXZ(pos, wp));
		if (ahead < 2.4 && index + 1 < route.size()) {
			Vec3 next = route.get(index + 1);
			double t = Mth.clamp(1.0 - ahead / 2.4, 0.0, 0.85);
			wp = wp.lerp(next, t);
		}
		return wp.add(0, 1.62 - 0.35, 0);
	}

	private static boolean shouldJump(LocalPlayer player, Vec3 pos, Vec3 target) {
		if (jumpHold > 0) {
			jumpHold--;
			return true;
		}
		if (!player.onGround()) {
			return false;
		}
		boolean stepUp = target.y > pos.y + 0.6 && distSqXZ(pos, target) < 1.9 * 1.9;
		boolean stuck = stuckTicks > 8 && stuckTicks % 12 == 0;
		if (stepUp || stuck) {
			jumpHold = 1;
			return true;
		}
		return false;
	}

	private static void watchProgress(LocalPlayer player, Vec3 pos, Level level) {
		double d = distSqXZ(pos, route.get(index)) + Math.abs(route.get(index).y - pos.y);
		if (d < bestDistSq - 0.01) {
			bestDistSq = d;
			stuckTicks = 0;
			return;
		}
		stuckTicks++;
		if (stuckTicks < 50) {
			return;
		}
		stuckTicks = 0;
		bestDistSq = Double.MAX_VALUE;
		if (++replans > REPLAN_LIMIT) {
			chat("Can't reach that spot.", ChatFormatting.RED);
			stop(false);
			return;
		}
		if (!plan(level, player, goal, false)) {
			chat("Lost the route.", ChatFormatting.RED);
			stop(false);
		}
	}

	private static void spring(float wantYaw, float wantPitch) {
		float dt = 0.05f;
		float dy = SmoothRotate.normalizeYaw(wantYaw - yaw);
		yawVel += (dy * yawStiff - yawVel * yawDamp) * dt;
		float maxTurn = 38f;
		yawVel = Mth.clamp(yawVel, -maxTurn / dt, maxTurn / dt);
		yaw += yawVel * dt + (RNG.nextFloat() - 0.5f) * 0.12f;
		float dp = wantPitch - pitch;
		pitchVel += (dp * yawStiff * 0.6f - pitchVel * yawDamp * 1.15f) * dt;
		pitch += pitchVel * dt + (RNG.nextFloat() - 0.5f) * 0.08f;
		pitch = SmoothRotate.normalizePitch(pitch);
		yaw = SmoothRotate.normalizeYaw(yaw);
	}

	private static void rollPersonality() {
		yawStiff = 55f + RNG.nextFloat() * 35f;
		yawDamp = 9.5f + RNG.nextFloat() * 3.5f;
		pitchRest = 8f + RNG.nextFloat() * 10f;
		noiseSeedA = RNG.nextFloat() * 1000f;
		noiseSeedB = RNG.nextFloat() * 1000f;
	}

	private static float wobble(float seed, float speed) {
		float t = ticks * speed + seed;
		return (Mth.sin(t) * 0.6f + Mth.sin(t * 2.31f + 1.3f) * 0.3f + Mth.sin(t * 0.37f + 4.1f) * 0.1f);
	}

	private static boolean plan(Level level, LocalPlayer player, Vec3 target, boolean announce) {
		BlockPos start = footing(level, BlockPos.containing(player.position()));
		BlockPos end = footing(level, BlockPos.containing(target));
		if (start == null) {
			chat("No solid ground under you.", ChatFormatting.RED);
			return false;
		}
		if (end == null) {
			end = nearestWalkable(level, BlockPos.containing(target), 4);
		}
		if (end == null) {
			chat("No walkable block near that spot.", ChatFormatting.RED);
			return false;
		}
		PathFinder.Result result = PathFinder.find(level, start, end, 40_000);
		if (result.nodes().isEmpty()) {
			chat("No route found.", ChatFormatting.RED);
			return false;
		}
		List<Vec3> pts = PathFinder.straighten(level, result.nodes());
		if (pts.size() > 1) {
			pts.removeFirst();
		}
		if (pts.isEmpty()) {
			chat("Already there.", ChatFormatting.GRAY);
			return false;
		}
		humanize(pts);
		if (result.partial()) {
			pts.add(pts.getLast());
			if (announce) {
				chat("Only part of the way is reachable; heading as close as I can.", ChatFormatting.YELLOW);
			}
		} else {
			pts.set(pts.size() - 1, new Vec3(target.x, pts.getLast().y, target.z));
		}
		route = pts;
		index = 0;
		bestDistSq = Double.MAX_VALUE;
		return true;
	}

	private static void humanize(List<Vec3> pts) {
		for (int i = 0; i < pts.size() - 1; i++) {
			Vec3 p = pts.get(i);
			double ox = (RNG.nextDouble() - 0.5) * 0.36;
			double oz = (RNG.nextDouble() - 0.5) * 0.36;
			pts.set(i, new Vec3(p.x + ox, p.y, p.z + oz));
		}
	}

	private static BlockPos footing(Level level, BlockPos pos) {
		for (int dy = 0; dy <= 4; dy++) {
			BlockPos test = pos.below(dy);
			if (PathFinder.standable(level, test)) {
				return test;
			}
		}
		for (int dy = 1; dy <= 2; dy++) {
			BlockPos test = pos.above(dy);
			if (PathFinder.standable(level, test)) {
				return test;
			}
		}
		return null;
	}

	private static BlockPos nearestWalkable(Level level, BlockPos center, int radius) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				for (int dy = -radius; dy <= radius; dy++) {
					BlockPos p = center.offset(dx, dy, dz);
					if (PathFinder.standable(level, p)) {
						double d = p.distSqr(center);
						if (d < bestD) {
							bestD = d;
							best = p;
						}
					}
				}
			}
		}
		return best;
	}

	private static double length(List<Vec3> pts) {
		double total = 0;
		for (int i = 1; i < pts.size(); i++) {
			total += pts.get(i - 1).distanceTo(pts.get(i));
		}
		return total;
	}

	private static double distSqXZ(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return dx * dx + dz * dz;
	}

	private static float impulse(boolean positive, boolean negative) {
		if (positive == negative) {
			return 0f;
		}
		return positive ? 1f : -1f;
	}

	private static float strafe(boolean left, boolean right) {
		return impulse(left, right);
	}

	private static void emit() {
		if (!walking || route.isEmpty()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		int color = 0xE0000000 | (Theme.ACCENT & 0xFFFFFF);
		Vec3 prev = client.player.position().add(0, 0.1, 0);
		for (int i = index; i < route.size(); i++) {
			Vec3 p = route.get(i).add(0, 0.1, 0);
			GizmoProperties line = Gizmos.line(prev, p, color, 2.5f);
			line.setAlwaysOnTop();
			prev = p;
		}
	}

	private static void chat(String text, ChatFormatting color) {
		Minecraft client = Minecraft.getInstance();
		if (client.gui == null) {
			return;
		}
		client.gui.getChat().addClientSystemMessage(
			Component.literal("Stray path ").withStyle(ChatFormatting.AQUA)
				.append(Component.literal(text).withStyle(color))
		);
	}

	static boolean passable(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
	}
}
