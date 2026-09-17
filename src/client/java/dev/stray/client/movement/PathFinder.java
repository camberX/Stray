package dev.stray.client.movement;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Grid A* for a two-block-tall walker: cardinal and diagonal steps, one-block
 * jumps, drops up to three blocks. Nodes are the block the feet stand in.
 */
final class PathFinder {
	private static final int MAX_DROP = 3;

	record Result(List<BlockPos> nodes, boolean partial) {
	}

	private static final class Node {
		final BlockPos pos;
		final long key;
		Node parent;
		double g;
		double f;
		boolean closed;

		Node(BlockPos pos, long key) {
			this.pos = pos;
			this.key = key;
		}
	}

	private PathFinder() {
	}

	static Result find(Level level, BlockPos start, BlockPos goal, int budget) {
		Long2ObjectOpenHashMap<Node> all = new Long2ObjectOpenHashMap<>();
		PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
		Node first = node(all, start);
		first.g = 0;
		first.f = heuristic(start, goal);
		open.add(first);
		Node best = first;
		double bestH = first.f;
		int expanded = 0;
		while (!open.isEmpty() && expanded < budget) {
			Node current = open.poll();
			if (current.closed) {
				continue;
			}
			current.closed = true;
			expanded++;
			if (current.pos.equals(goal)) {
				return new Result(trace(current), false);
			}
			double h = heuristic(current.pos, goal);
			if (h < bestH) {
				bestH = h;
				best = current;
			}
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dz == 0) {
						continue;
					}
					boolean diagonal = dx != 0 && dz != 0;
					BlockPos flat = current.pos.offset(dx, 0, dz);
					if (diagonal) {
						BlockPos a = current.pos.offset(dx, 0, 0);
						BlockPos b = current.pos.offset(0, 0, dz);
						if (!clear(level, a) || !clear(level, b)) {
							continue;
						}
					}
					double stepCost = diagonal ? 1.4142 : 1.0;
					if (standable(level, flat)) {
						relax(level, all, open, current, flat, stepCost, goal);
						continue;
					}
					if (clear(level, flat)) {
						for (int drop = 1; drop <= MAX_DROP; drop++) {
							BlockPos down = flat.below(drop);
							if (!clear(level, down)) {
								break;
							}
							if (standable(level, down)) {
								relax(level, all, open, current, down, stepCost + drop * 0.6, goal);
								break;
							}
						}
						continue;
					}
					if (diagonal) {
						continue;
					}
					BlockPos up = flat.above();
					if (standable(level, up) && passable(level, current.pos.above(2))) {
						relax(level, all, open, current, up, stepCost + 1.4, goal);
					}
				}
			}
		}
		if (best == first) {
			return new Result(List.of(), true);
		}
		return new Result(trace(best), true);
	}

	static boolean standable(Level level, BlockPos feet) {
		if (!clear(level, feet)) {
			return false;
		}
		BlockPos below = feet.below();
		BlockState floor = level.getBlockState(below);
		if (!floor.getFluidState().isEmpty()) {
			return false;
		}
		return !floor.getCollisionShape(level, below).isEmpty();
	}

	static boolean clear(Level level, BlockPos feet) {
		return passable(level, feet) && passable(level, feet.above());
	}

	static boolean passable(Level level, BlockPos pos) {
		return PathWalker.passable(level, pos);
	}

	/** Drops intermediate nodes when a straight walk between neighbours stays on walkable ground. */
	static List<Vec3> straighten(Level level, List<BlockPos> nodes) {
		List<Vec3> out = new ArrayList<>();
		if (nodes.isEmpty()) {
			return out;
		}
		int anchor = 0;
		out.add(center(nodes.get(0)));
		while (anchor < nodes.size() - 1) {
			int far = anchor + 1;
			for (int probe = nodes.size() - 1; probe > anchor + 1; probe--) {
				if (probe - anchor > 12) {
					continue;
				}
				if (walkable(level, nodes.get(anchor), nodes.get(probe))) {
					far = probe;
					break;
				}
			}
			out.add(center(nodes.get(far)));
			anchor = far;
		}
		return out;
	}

	private static boolean walkable(Level level, BlockPos a, BlockPos b) {
		if (a.getY() != b.getY()) {
			return false;
		}
		Vec3 from = center(a);
		Vec3 to = center(b);
		double dist = from.distanceTo(to);
		int steps = Math.max(2, Mth.ceil(dist * 3));
		for (int i = 1; i < steps; i++) {
			double t = i / (double) steps;
			Vec3 p = from.lerp(to, t);
			for (double ox = -0.3; ox <= 0.3; ox += 0.3) {
				for (double oz = -0.3; oz <= 0.3; oz += 0.3) {
					BlockPos feet = BlockPos.containing(p.x + ox, a.getY(), p.z + oz);
					if (!standable(level, feet)) {
						return false;
					}
				}
			}
		}
		return true;
	}

	private static Vec3 center(BlockPos pos) {
		return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
	}

	private static void relax(
		Level level,
		Long2ObjectOpenHashMap<Node> all,
		PriorityQueue<Node> open,
		Node current,
		BlockPos next,
		double cost,
		BlockPos goal
	) {
		Node node = node(all, next);
		if (node.closed) {
			return;
		}
		double g = current.g + cost;
		if (node.parent != null && g >= node.g) {
			return;
		}
		node.parent = current;
		node.g = g;
		node.f = g + heuristic(next, goal);
		open.add(node);
	}

	private static Node node(Long2ObjectOpenHashMap<Node> all, BlockPos pos) {
		long key = pos.asLong();
		Node node = all.get(key);
		if (node == null) {
			node = new Node(pos.immutable(), key);
			node.g = Double.MAX_VALUE;
			all.put(key, node);
		}
		return node;
	}

	private static double heuristic(BlockPos a, BlockPos b) {
		int dx = Math.abs(a.getX() - b.getX());
		int dz = Math.abs(a.getZ() - b.getZ());
		int dy = Math.abs(a.getY() - b.getY());
		int lo = Math.min(dx, dz);
		int hi = Math.max(dx, dz);
		return hi - lo + lo * 1.4142 + dy * 0.8;
	}

	private static List<BlockPos> trace(Node end) {
		List<BlockPos> out = new ArrayList<>();
		for (Node n = end; n != null; n = n.parent) {
			out.add(n.pos);
		}
		Collections.reverse(out);
		return out;
	}
}
