package dev.stray.client.editor;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Outline of a set of blocks drawn as one shape: only the outside faces get a fill (merged into
 * big rectangles where they lie flat next to each other) and only real edges get a line, so
 * neighbouring selected blocks join up instead of each getting its own box.
 */
final class SelectionMesh {
	record Quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d) {
	}

	record Line(Vec3 from, Vec3 to) {
	}

	final List<Quad> quads = new ArrayList<>();
	final List<Line> lines = new ArrayList<>();

	static SelectionMesh build(LongOpenHashSet blocks) {
		SelectionMesh mesh = new SelectionMesh();
		if (blocks == null || blocks.isEmpty()) {
			return mesh;
		}
		// Outside faces, grouped by direction and plane for merging.
		Long2ObjectOpenHashMap<LongOpenHashSet>[] planes = newPlanes();
		// Unit edges (doubled coordinates of the lower end, plus axis), deduplicated.
		LongOpenHashSet[] edges = {new LongOpenHashSet(), new LongOpenHashSet(), new LongOpenHashSet()};
		for (long pos : blocks) {
			int x = BlockPos.getX(pos);
			int y = BlockPos.getY(pos);
			int z = BlockPos.getZ(pos);
			for (Direction d : Direction.values()) {
				if (blocks.contains(BlockPos.offset(pos, d))) {
					continue;
				}
				int plane = switch (d.getAxis()) {
					case X -> x + (d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0);
					case Y -> y + (d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0);
					case Z -> z + (d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0);
				};
				int u = switch (d.getAxis()) {
					case X -> z;
					case Y, Z -> x;
				};
				int v = switch (d.getAxis()) {
					case X, Z -> y;
					case Y -> z;
				};
				planes[d.ordinal()].computeIfAbsent(plane, k -> new LongOpenHashSet()).add(pack(u, v));
				for (Direction t : Direction.values()) {
					if (t.getAxis() == d.getAxis()) {
						continue;
					}
					long side = BlockPos.offset(pos, t);
					boolean flatContinues = blocks.contains(side) && !blocks.contains(BlockPos.offset(side, d));
					if (flatContinues) {
						continue;
					}
					addEdge(edges, x, y, z, d, t);
				}
			}
		}
		for (Direction d : Direction.values()) {
			for (var entry : planes[d.ordinal()].long2ObjectEntrySet()) {
				greedy(mesh, d, (int) entry.getLongKey(), entry.getValue());
			}
		}
		mergeEdges(mesh, edges);
		return mesh;
	}

	@SuppressWarnings("unchecked")
	private static Long2ObjectOpenHashMap<LongOpenHashSet>[] newPlanes() {
		Long2ObjectOpenHashMap<LongOpenHashSet>[] planes = new Long2ObjectOpenHashMap[6];
		for (int i = 0; i < 6; i++) {
			planes[i] = new Long2ObjectOpenHashMap<>();
		}
		return planes;
	}

	private static long pack(int u, int v) {
		return ((long) u << 32) | (v & 0xFFFFFFFFL);
	}

	/** Merges a plane's unit faces into as few rectangles as it easily can. */
	private static void greedy(SelectionMesh mesh, Direction d, int plane, LongOpenHashSet cells) {
		LongArrayList sorted = new LongArrayList(cells);
		sorted.sort((a, b) -> {
			int va = (int) a;
			int vb = (int) b;
			if (va != vb) {
				return Integer.compare(va, vb);
			}
			return Integer.compare((int) (a >> 32), (int) (b >> 32));
		});
		LongOpenHashSet used = new LongOpenHashSet();
		for (int i = 0; i < sorted.size(); i++) {
			long cell = sorted.getLong(i);
			if (used.contains(cell)) {
				continue;
			}
			int u0 = (int) (cell >> 32);
			int v0 = (int) cell;
			int u1 = u0;
			while (cells.contains(pack(u1 + 1, v0)) && !used.contains(pack(u1 + 1, v0))) {
				u1++;
			}
			int v1 = v0;
			grow:
			while (true) {
				for (int u = u0; u <= u1; u++) {
					long next = pack(u, v1 + 1);
					if (!cells.contains(next) || used.contains(next)) {
						break grow;
					}
				}
				v1++;
			}
			for (int v = v0; v <= v1; v++) {
				for (int u = u0; u <= u1; u++) {
					used.add(pack(u, v));
				}
			}
			mesh.quads.add(quad(d, plane, u0, v0, u1 + 1, v1 + 1));
		}
	}

	private static Quad quad(Direction d, int plane, int u0, int v0, int u1, int v1) {
		// Nudged a hair outward so the fill never fights with the block's own face.
		double p = plane + (d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0.004 : -0.004);
		return switch (d.getAxis()) {
			case X -> new Quad(new Vec3(p, v0, u0), new Vec3(p, v0, u1), new Vec3(p, v1, u1), new Vec3(p, v1, u0));
			case Y -> new Quad(new Vec3(u0, p, v0), new Vec3(u1, p, v0), new Vec3(u1, p, v1), new Vec3(u0, p, v1));
			case Z -> new Quad(new Vec3(u0, v0, p), new Vec3(u1, v0, p), new Vec3(u1, v1, p), new Vec3(u0, v1, p));
		};
	}

	/** The unit edge of face {@code d} of block (x,y,z) on side {@code t}. */
	private static void addEdge(LongOpenHashSet[] edges, int x, int y, int z, Direction d, Direction t) {
		// Doubled coordinates keep the half-block positions whole.
		int cx = 2 * x + 1 + d.getStepX() + t.getStepX();
		int cy = 2 * y + 1 + d.getStepY() + t.getStepY();
		int cz = 2 * z + 1 + d.getStepZ() + t.getStepZ();
		Direction.Axis along = third(d.getAxis(), t.getAxis());
		switch (along) {
			case X -> cx -= 1;
			case Y -> cy -= 1;
			case Z -> cz -= 1;
		}
		edges[along.ordinal()].add(BlockPos.asLong(cx / 2, cy / 2, cz / 2));
	}

	private static Direction.Axis third(Direction.Axis a, Direction.Axis b) {
		for (Direction.Axis axis : Direction.Axis.values()) {
			if (axis != a && axis != b) {
				return axis;
			}
		}
		return Direction.Axis.Y;
	}

	/** Joins unit edges that continue in a straight line into one long line. */
	private static void mergeEdges(SelectionMesh mesh, LongOpenHashSet[] edges) {
		for (Direction.Axis axis : Direction.Axis.values()) {
			LongOpenHashSet set = edges[axis.ordinal()];
			LongOpenHashSet done = new LongOpenHashSet();
			for (long start : set) {
				if (done.contains(start)) {
					continue;
				}
				// Walk back to the first segment of this run, then forward to its end.
				long first = start;
				while (set.contains(step(first, axis, -1))) {
					first = step(first, axis, -1);
				}
				long last = first;
				done.add(first);
				while (set.contains(step(last, axis, 1))) {
					last = step(last, axis, 1);
					done.add(last);
				}
				Vec3 from = new Vec3(BlockPos.getX(first), BlockPos.getY(first), BlockPos.getZ(first));
				long end = step(last, axis, 1);
				Vec3 to = new Vec3(BlockPos.getX(end), BlockPos.getY(end), BlockPos.getZ(end));
				mesh.lines.add(new Line(from, to));
			}
		}
	}

	private static long step(long pos, Direction.Axis axis, int amount) {
		return switch (axis) {
			case X -> BlockPos.offset(pos, amount, 0, 0);
			case Y -> BlockPos.offset(pos, 0, amount, 0);
			case Z -> BlockPos.offset(pos, 0, 0, amount);
		};
	}
}
