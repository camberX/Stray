package dev.stray.client.fairy;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.location.SkyblockLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.PlayerHeadBlock;
import net.minecraft.world.level.block.PlayerWallHeadBlock;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;

public final class FairySoulTracker {
	private static final double CLICK_RANGE = 4.5;
	private static final double PATH_RANGE = 5.0;
	private static FairySouls.Soul lastClicked;
	private static List<Vec3> path = List.of();
	private static BlockPos pathSoul;
	private static int pathTick;

	private FairySoulTracker() {
	}

	public static void init() {
		FairySouls.texture();
	}

	public static boolean active() {
		return StrayConfig.get().fairySoulEsp && SkyblockLocation.inSkyblock;
	}

	public static List<FairySouls.Soul> visible() {
		if (!active()) {
			return List.of();
		}
		List<FairySouls.Soul> out = new ArrayList<>();
		for (FairySouls.Soul soul : FairySouls.forArea(SkyblockLocation.area)) {
			if (!FairySoulProgress.found(soul)) {
				out.add(soul);
			}
		}
		return out;
	}

	public static List<Vec3> path() {
		return path;
	}

	public static void tick(Minecraft client) {
		if (!active() || client.player == null || client.level == null) {
			path = List.of();
			pathSoul = null;
			return;
		}
		int t = client.player.tickCount;
		if (pathTick != Integer.MIN_VALUE && t - pathTick < 3 && t >= pathTick) {
			return;
		}
		pathTick = t;
		Vec3 at = client.player.position();
		FairySouls.Soul near = null;
		double best = PATH_RANGE;
		for (FairySouls.Soul soul : visible()) {
			double d = at.distanceTo(soul.center());
			if (d <= best) {
				best = d;
				near = soul;
			}
		}
		if (near == null) {
			path = List.of();
			pathSoul = null;
			return;
		}
		if (near.pos().equals(pathSoul) && !path.isEmpty()) {
			return;
		}
		pathSoul = near.pos();
		path = route(client.level, BlockPos.containing(at.x, at.y, at.z), near);
	}

	public static void onUseBlock(BlockHitResult hit) {
		if (!active() || hit == null) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}
		BlockPos pos = hit.getBlockPos();
		boolean head = looksLikeSoul(client.level, pos);
		FairySouls.Soul soul = nearest(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, head ? CLICK_RANGE : 1.35);
		if (soul == null) {
			return;
		}
		lastClicked = soul;
		FairySoulProgress.mark(soul);
		path = List.of();
		pathSoul = null;
	}

	public static void onChat(Component message) {
		if (message == null) {
			return;
		}
		String text = message.getString().replaceAll("§.", "").toLowerCase(Locale.ROOT);
		if (!text.contains("you found a fairy soul")) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		FairySouls.Soul soul = nearestUnfound(client.player.getX(), client.player.getZ());
		if (soul == null) {
			return;
		}
		FairySoulProgress.mark(soul);
		path = List.of();
		pathSoul = null;
	}

	private static FairySouls.Soul nearestUnfound(double x, double z) {
		FairySouls.Soul best = null;
		double bestD = 16.0;
		for (FairySouls.Soul soul : FairySouls.forArea(SkyblockLocation.area)) {
			if (FairySoulProgress.found(soul)) {
				continue;
			}
			double dx = soul.x() + 0.5 - x;
			double dz = soul.z() + 0.5 - z;
			double d = Math.sqrt(dx * dx + dz * dz);
			if (d <= bestD) {
				bestD = d;
				best = soul;
			}
		}
		return best;
	}

	public static boolean looksLikeSoul(ClientLevel level, BlockPos pos) {
		if (level == null || pos == null) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof PlayerHeadBlock) && !(state.getBlock() instanceof PlayerWallHeadBlock)) {
			return false;
		}
		if (!(level.getBlockEntity(pos) instanceof SkullBlockEntity skull)) {
			return true;
		}
		var profile = skull.getOwnerProfile();
		if (profile == null) {
			return true;
		}
		String blob = String.valueOf(profile).toLowerCase(Locale.ROOT);
		return blob.contains(FairySouls.texture()) || blob.contains("fairy");
	}

	private static FairySouls.Soul nearest(double x, double y, double z, double range) {
		FairySouls.Soul best = null;
		double bestD = range;
		Vec3 at = new Vec3(x, y, z);
		for (FairySouls.Soul soul : visible()) {
			double d = at.distanceTo(soul.center());
			if (d <= bestD) {
				bestD = d;
				best = soul;
			}
		}
		return best;
	}

	private static List<Vec3> route(ClientLevel level, BlockPos start, FairySouls.Soul soul) {
		BlockPos goal = soul.pos();
		if (start.equals(goal)) {
			return List.of(soul.center());
		}
		record Node(int x, int y, int z, int g, int px, int py, int pz) {
		}
		PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingInt(n -> n.g + heuristic(n.x, n.y, n.z, goal)));
		Map<Long, Node> best = new HashMap<>();
		Node origin = new Node(start.getX(), start.getY(), start.getZ(), 0, start.getX(), start.getY(), start.getZ());
		open.add(origin);
		best.put(pack(origin.x, origin.y, origin.z), origin);
		Node found = null;
		int guard = 0;
		while (!open.isEmpty() && guard++ < 2500) {
			Node cur = open.poll();
			if (cur.x == goal.getX() && cur.y == goal.getY() && cur.z == goal.getZ()) {
				found = cur;
				break;
			}
			if (dist(cur.x, cur.y, cur.z, goal) > PATH_RANGE + 1.5) {
				continue;
			}
			for (int[] step : steps()) {
				int nx = cur.x + step[0];
				int ny = cur.y + step[1];
				int nz = cur.z + step[2];
				if (dist(nx, ny, nz, goal) > PATH_RANGE + 1.5) {
					continue;
				}
				BlockPos next = new BlockPos(nx, ny, nz);
				boolean dest = nx == goal.getX() && ny == goal.getY() && nz == goal.getZ();
				if (!dest && blocked(level, next)) {
					continue;
				}
				if (!dest && blocked(level, next.above()) && !looksLikeSoul(level, next.above())) {
					continue;
				}
				int g = cur.g + 1 + Math.abs(step[1]);
				long key = pack(nx, ny, nz);
				Node prev = best.get(key);
				if (prev != null && prev.g <= g) {
					continue;
				}
				Node node = new Node(nx, ny, nz, g, cur.x, cur.y, cur.z);
				best.put(key, node);
				open.add(node);
			}
		}
		if (found == null) {
			return List.of(new Vec3(start.getX() + 0.5, start.getY() + 0.2, start.getZ() + 0.5), soul.center());
		}
		List<Vec3> out = new ArrayList<>();
		Node walk = found;
		while (true) {
			out.add(new Vec3(walk.x + 0.5, walk.y + 0.2, walk.z + 0.5));
			if (walk.x == walk.px && walk.y == walk.py && walk.z == walk.pz) {
				break;
			}
			Node parent = best.get(pack(walk.px, walk.py, walk.pz));
			if (parent == null) {
				break;
			}
			walk = parent;
		}
		java.util.Collections.reverse(out);
		if (out.isEmpty() || out.get(out.size() - 1).distanceTo(soul.center()) > 0.2) {
			out.add(soul.center());
		}
		return out;
	}

	private static int[][] steps() {
		return new int[][]{
			{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
			{1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1},
			{1, -1, 0}, {-1, -1, 0}, {0, -1, 1}, {0, -1, -1},
			{0, 1, 0}, {0, -1, 0},
			{1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1}
		};
	}

	private static boolean blocked(ClientLevel level, BlockPos pos) {
		VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
		return shape != null && !shape.isEmpty();
	}

	private static int heuristic(int x, int y, int z, BlockPos goal) {
		return Math.abs(x - goal.getX()) + Math.abs(y - goal.getY()) + Math.abs(z - goal.getZ());
	}

	private static double dist(int x, int y, int z, BlockPos goal) {
		double dx = x + 0.5 - (goal.getX() + 0.5);
		double dy = y + 0.5 - (goal.getY() + 0.5);
		double dz = z + 0.5 - (goal.getZ() + 0.5);
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private static long pack(int x, int y, int z) {
		return ((long) (x & 0x3FFFFF) << 42) | ((long) (z & 0x3FFFFF) << 20) | (y & 0xFFFFF);
	}
}
