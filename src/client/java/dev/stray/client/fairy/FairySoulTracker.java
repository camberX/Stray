package dev.stray.client.fairy;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.location.SkyblockLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
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
	private static final double LOCK_RANGE = 10.0;
	private static final int SEARCH = 28;
	private static final int ASTAR_NODES = 5000;
	private static final Map<Long, Boolean> SOLID = new HashMap<>();
	private static FairySouls.Soul locked;
	private static List<Vec3> path = List.of();
	private static BlockPos lastStart;
	private static int idle;

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
		if (client.player == null || client.level == null) {
			clearPath();
			return;
		}
		if (!StrayConfig.get().fairySoulEsp) {
			clearPath();
			return;
		}
		if (locked != null && FairySoulProgress.found(locked)) {
			clearPath();
		}
		Vec3 at = client.player.position();
		FairySouls.Soul target = locked;
		if (target == null || FairySoulProgress.found(target)) {
			target = nearestUnfound(at.x, at.z, LOCK_RANGE);
		}
		if (target == null) {
			clearPath();
			return;
		}
		locked = target;
		BlockPos start = BlockPos.containing(at.x, at.y, at.z);
		if (start.equals(lastStart) && !path.isEmpty()) {
			return;
		}
		if (idle > 0) {
			idle--;
			return;
		}
		List<Vec3> next = route(client.level, start, target);
		lastStart = start;
		if (!next.isEmpty()) {
			path = next;
			idle = 3;
		} else {
			path = List.of();
			idle = 10;
		}
	}

	public static void onUseBlock(BlockHitResult hit) {
		if (hit == null) {
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
		markFound(soul);
	}

	public static void onChat(Component message) {
		if (message == null) {
			return;
		}
		String text = message.getString().replaceAll("§.", "").toLowerCase(Locale.ROOT);
		boolean found = text.contains("you found a fairy soul");
		boolean already = text.contains("already found that fairy soul");
		if (!found && !already) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		FairySouls.Soul soul = locked;
		if (soul == null || FairySoulProgress.found(soul)) {
			soul = nearestUnfound(client.player.getX(), client.player.getZ(), 16.0);
		}
		if (soul != null) {
			markFound(soul);
		}
	}

	private static void markFound(FairySouls.Soul soul) {
		FairySoulProgress.mark(soul);
		if (locked != null && locked.key().equals(soul.key())) {
			clearPath();
		}
	}

	private static void clearPath() {
		locked = null;
		path = List.of();
		lastStart = null;
		idle = 0;
		SOLID.clear();
	}

	private static FairySouls.Soul nearestUnfound(double x, double z, double range) {
		FairySouls.Soul best = null;
		double bestD = range;
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
		for (FairySouls.Soul soul : FairySouls.forArea(SkyblockLocation.area)) {
			if (FairySoulProgress.found(soul)) {
				continue;
			}
			double d = at.distanceTo(soul.center());
			if (d <= bestD) {
				bestD = d;
				best = soul;
			}
		}
		return best;
	}

	private static List<Vec3> route(ClientLevel level, BlockPos start, FairySouls.Soul soul) {
		SOLID.clear();
		BlockPos goal = soul.pos();
		List<BlockPos> cells = astar(level, start, goal);
		if (cells.isEmpty()) {
			return List.of();
		}
		List<Vec3> points = new ArrayList<>();
		for (BlockPos cell : cells) {
			boolean dest = cell.equals(goal);
			points.add(new Vec3(cell.getX() + 0.5, cell.getY() + (dest ? 0.45 : 0.12), cell.getZ() + 0.5));
		}
		if (points.get(points.size() - 1).distanceTo(soul.center()) > 0.2) {
			points.add(soul.center());
		}
		points = pull(level, points, goal);
		return roundCorners(level, points, goal);
	}

	private static List<BlockPos> astar(ClientLevel level, BlockPos start, BlockPos goal) {
		record Node(int x, int y, int z, int g, int px, int py, int pz) {
		}
		PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingInt(n -> n.g + heuristic(n.x, n.y, n.z, goal)));
		Map<Long, Node> best = new HashMap<>();
		Node origin = new Node(start.getX(), start.getY(), start.getZ(), 0, start.getX(), start.getY(), start.getZ());
		open.add(origin);
		best.put(pack(origin.x, origin.y, origin.z), origin);
		Node found = null;
		int minY = Math.min(start.getY(), goal.getY()) - 8;
		int maxY = Math.max(start.getY(), goal.getY()) + 10;
		int guard = 0;
		while (!open.isEmpty() && guard++ < ASTAR_NODES) {
			Node cur = open.poll();
			if (reached(cur.x, cur.y, cur.z, goal)) {
				found = cur;
				break;
			}
			BlockPos from = new BlockPos(cur.x, cur.y, cur.z);
			for (int[] step : STEPS) {
				int nx = cur.x + step[0];
				int ny = cur.y + step[1];
				int nz = cur.z + step[2];
				if (ny < minY || ny > maxY) {
					continue;
				}
				if (xz(nx, nz, start) > SEARCH && xz(nx, nz, goal) > SEARCH) {
					continue;
				}
				BlockPos to = new BlockPos(nx, ny, nz);
				if (!canStep(level, from, to, goal)) {
					continue;
				}
				int g = cur.g + (step[0] != 0 && step[2] != 0 ? 14 : 10) + Math.abs(step[1]) * 4;
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
			return List.of();
		}
		List<BlockPos> out = new ArrayList<>();
		Node walk = found;
		while (true) {
			out.add(new BlockPos(walk.x, walk.y, walk.z));
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
		return out;
	}

	private static final int[][] STEPS = {
		{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
		{1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1},
		{1, -1, 0}, {-1, -1, 0}, {0, -1, 1}, {0, -1, -1},
		{0, 1, 0}, {0, -1, 0},
		{0, -2, 0}, {1, -2, 0}, {-1, -2, 0}, {0, -2, 1}, {0, -2, -1}
	};

	private static boolean canStep(ClientLevel level, BlockPos from, BlockPos to, BlockPos goal) {
		int dx = to.getX() - from.getX();
		int dy = to.getY() - from.getY();
		int dz = to.getZ() - from.getZ();
		if (Math.abs(dx) > 1 || Math.abs(dz) > 1 || dy > 1 || dy < -2) {
			return false;
		}
		if (dx == 0 && dy == 0 && dz == 0) {
			return false;
		}
		if (dx != 0 && dz != 0) {
			return false;
		}
		boolean dest = to.equals(goal);
		if (!dest && !passable(level, to)) {
			return false;
		}
		if (dx != 0 || dz != 0) {
			BlockPos side = from.offset(dx, 0, dz);
			if (dy != 0 && !passable(level, side) && !side.equals(goal)) {
				return false;
			}
			if (dy == 1 && blocked(level, from.above(2))) {
				return false;
			}
			if (dy < 0) {
				for (int drop = -1; drop > dy; drop--) {
					BlockPos mid = from.offset(dx, drop, dz);
					if (!passable(level, mid) && !mid.equals(goal)) {
						return false;
					}
				}
			}
		}
		if (dx == 0 && dz == 0) {
			if (dy > 0 && blocked(level, from.above(2)) && !dest) {
				return false;
			}
			return dest || passable(level, to);
		}
		return dest || standable(level, to) || (dy < 0 && passable(level, to));
	}

	private static boolean reached(int x, int y, int z, BlockPos goal) {
		if (x != goal.getX() || z != goal.getZ()) {
			return false;
		}
		return y == goal.getY() || y == goal.getY() - 1;
	}

	private static boolean standable(ClientLevel level, BlockPos feet) {
		return passable(level, feet) && floor(level, feet.below());
	}

	private static boolean passable(ClientLevel level, BlockPos feet) {
		if (blocked(level, feet)) {
			return false;
		}
		return !blocked(level, feet.above());
	}

	private static boolean floor(ClientLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.SLABS) || state.is(BlockTags.STAIRS)) {
			return true;
		}
		return blocked(level, pos);
	}

	private static boolean walkThrough(BlockState state) {
		return state.is(BlockTags.DOORS) || state.is(BlockTags.FENCE_GATES) || state.is(BlockTags.TRAPDOORS)
			|| state.is(BlockTags.CLIMBABLE);
	}

	private static boolean blocked(ClientLevel level, BlockPos pos) {
		long key = pack(pos.getX(), pos.getY(), pos.getZ());
		Boolean cached = SOLID.get(key);
		if (cached != null) {
			return cached;
		}
		BlockState state = level.getBlockState(pos);
		boolean hit;
		if (walkThrough(state)) {
			hit = false;
		} else {
			VoxelShape shape = state.getCollisionShape(level, pos);
			hit = shape != null && !shape.isEmpty() && shape.max(Direction.Axis.Y) > 0.2;
		}
		SOLID.put(key, hit);
		return hit;
	}

	private static List<Vec3> pull(ClientLevel level, List<Vec3> raw, BlockPos goal) {
		if (raw.size() < 3) {
			return raw;
		}
		List<Vec3> out = new ArrayList<>();
		out.add(raw.getFirst());
		int last = 0;
		for (int i = 2; i < raw.size(); i++) {
			if (!clear(level, raw.get(last), raw.get(i), goal)) {
				out.add(raw.get(i - 1));
				last = i - 1;
			}
		}
		out.add(raw.getLast());
		return out;
	}

	private static List<Vec3> roundCorners(ClientLevel level, List<Vec3> raw, BlockPos goal) {
		if (raw.size() < 3) {
			return raw;
		}
		List<Vec3> out = new ArrayList<>();
		out.add(raw.getFirst());
		for (int i = 1; i < raw.size() - 1; i++) {
			Vec3 prev = raw.get(i - 1);
			Vec3 cur = raw.get(i);
			Vec3 next = raw.get(i + 1);
			Vec3 in = cur.subtract(prev);
			Vec3 outv = next.subtract(cur);
			boolean turn = Math.abs(in.x) > 0.05 && Math.abs(outv.z) > 0.05
				|| Math.abs(in.z) > 0.05 && Math.abs(outv.x) > 0.05
				|| Math.abs(in.y) > 0.05 && (Math.abs(outv.x) > 0.05 || Math.abs(outv.z) > 0.05)
				|| (Math.abs(in.x) > 0.05 || Math.abs(in.z) > 0.05) && Math.abs(outv.y) > 0.05;
			if (!turn) {
				out.add(cur);
				continue;
			}
			Vec3 a = prev.add(cur.subtract(prev).normalize().scale(Math.min(0.55, prev.distanceTo(cur) * 0.45)));
			Vec3 b = cur.add(next.subtract(cur).normalize().scale(Math.min(0.55, cur.distanceTo(next) * 0.45)));
			if (clear(level, a, b, goal)) {
				out.add(a);
				out.add(a.lerp(b, 0.5).lerp(cur, 0.22));
				out.add(b);
			} else {
				out.add(cur);
			}
		}
		out.add(raw.getLast());
		return out;
	}

	private static boolean clear(ClientLevel level, Vec3 from, Vec3 to, BlockPos goal) {
		double dist = from.distanceTo(to);
		if (dist < 0.05) {
			return true;
		}
		int steps = Math.max(2, (int) Math.ceil(dist * 4.0));
		for (int i = 0; i <= steps; i++) {
			Vec3 p = from.lerp(to, i / (double) steps);
			BlockPos cell = BlockPos.containing(p.x, p.y, p.z);
			if (cell.equals(goal) || looksLikeSoul(level, cell)) {
				continue;
			}
			if (blocked(level, cell)) {
				return false;
			}
		}
		return true;
	}

	private static int heuristic(int x, int y, int z, BlockPos goal) {
		return (Math.abs(x - goal.getX()) + Math.abs(z - goal.getZ())) * 10 + Math.abs(y - goal.getY()) * 6;
	}

	private static double xz(int x, int z, BlockPos at) {
		double dx = x + 0.5 - (at.getX() + 0.5);
		double dz = z + 0.5 - (at.getZ() + 0.5);
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static long pack(int x, int y, int z) {
		return ((long) (x & 0x3FFFFF) << 42) | ((long) (z & 0x3FFFFF) << 20) | (y & 0xFFFFF);
	}
}
