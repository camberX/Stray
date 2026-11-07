package dev.stray.client.dungeon.m4;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * BirdAddon's ParticleTrails: the dark dust trail that flies into the middle of the arena
 * before a Spirit Bear spawns. Once three particles line up on a straight line towards
 * (5.5, 69, 5.5) the rest of that line is predicted, which gives the experimental bear timer
 * (particles left) and the predicted spawn spot (last particle snapped to a 1/32 grid).
 */
public final class M4Particles {
	private static final Vec3 TARGET = new Vec3(5.5, 69.0, 5.5);
	private static final double EPSILON_SQ = 0.0001 * 0.0001;
	private static final double LOWER_BOUND = 69.6969;

	static final class Timed {
		final Vec3 pos;
		final int createdTick;
		boolean linear;

		Timed(Vec3 pos, int createdTick) {
			this.pos = pos;
			this.createdTick = createdTick;
		}
	}

	public static int predictionsRemaining = -1;
	public static boolean particleAddedThisTick;
	public static Vec3 spawnPrediction = Vec3.ZERO;
	public static List<Vec3> predictions = List.of();

	private static final List<Timed> particles = new ArrayList<>();
	private static final List<Timed[]> candidatePairs = new ArrayList<>();

	private M4Particles() {
	}

	static void reset() {
		predictions = List.of();
		predictionsRemaining = -1;
		spawnPrediction = Vec3.ZERO;
		particles.clear();
		candidatePairs.clear();
	}

	static void serverTick() {
		if (!M4.inBoss()) {
			return;
		}
		int cutoff = M4.timer - 8;
		particles.removeIf(p -> p.createdTick < cutoff);
		candidatePairs.removeIf(pair -> pair[0].createdTick < cutoff || pair[1].createdTick < cutoff);
		if (!M4.spawnTimes.isEmpty()
			&& M4.timer >= M4.spawnTimes.getLast() + 3
			&& M4.spawnStartTimes.size() == M4.spawnTimes.size()) {
			predictions = List.of();
			predictionsRemaining = -1;
			spawnPrediction = Vec3.ZERO;
		}
	}

	static void onBearParticle(Vec3 pos) {
		Timed added = new Timed(pos, M4.timer);
		for (Timed[] pair : candidatePairs) {
			if (linearToMid(pair[0].pos, added.pos) && linearToMid(pair[1].pos, added.pos)) {
				pair[0].linear = true;
				pair[1].linear = true;
				added.linear = true;
				particleAddedThisTick = true;
				Timed lowest = pair[0];
				if (pair[1].pos.y < lowest.pos.y) {
					lowest = pair[1];
				}
				if (added.pos.y < lowest.pos.y) {
					lowest = added;
				}
				int remaining = remaining(lowest.pos);
				// The bear timer never goes up once predictions are found.
				if (predictionsRemaining < 0 || predictionsRemaining > remaining) {
					predictionsRemaining = remaining;
				}
				break;
			}
		}
		for (Timed existing : particles) {
			if (linearToMid(existing.pos, added.pos)) {
				candidatePairs.add(new Timed[] {existing, added});
			}
		}
		particles.add(added);
		if (predictions.isEmpty() && added.linear) {
			predictions = generate();
		}
	}

	private static boolean linearToMid(Vec3 a, Vec3 b) {
		Vec3 line = b.subtract(a);
		double lengthSq = line.lengthSqr();
		if (lengthSq == 0.0) {
			return false;
		}
		Vec3 toTarget = TARGET.subtract(a);
		return line.cross(toTarget).lengthSqr() <= EPSILON_SQ * lengthSq;
	}

	/** Scales the step for particles that never arrived (particle cap, distance). */
	private static Vec3 step(Vec3 delta) {
		double spacing = 0.38;
		int steps = Math.max(1, (int) Math.round(Math.abs(delta.y) / spacing));
		double perSegment = Math.abs(delta.y) / steps;
		return perSegment >= 0.36 && perSegment <= 0.4 ? delta.scale(1.0 / steps) : null;
	}

	private static List<Vec3> generate() {
		List<Timed> linear = new ArrayList<>();
		for (Timed p : particles) {
			if (p.linear) {
				linear.add(p);
			}
		}
		if (linear.size() < 2) {
			return List.of();
		}
		Vec3 first = linear.get(0).pos;
		Vec3 second = linear.get(1).pos;
		Vec3 lower = first.y < second.y ? first : second;
		Vec3 upper = first.y < second.y ? second : first;
		Vec3 step = step(lower.subtract(upper));
		if (step == null) {
			return List.of();
		}
		List<Vec3> out = new ArrayList<>();
		Vec3 next = lower.add(step);
		while (next.y > LOWER_BOUND && out.size() < 512) {
			out.add(next);
			next = next.add(step);
		}
		if (out.isEmpty()) {
			return List.of();
		}
		Vec3 last = out.getLast();
		spawnPrediction = new Vec3(Math.floor(last.x * 32) / 32, Math.floor(last.y * 32) / 32, Math.floor(last.z * 32) / 32);
		return List.copyOf(out);
	}

	private static int remaining(Vec3 pos) {
		if (predictions.isEmpty()) {
			return -1;
		}
		int closest = 0;
		double best = Double.MAX_VALUE;
		for (int i = 0; i < predictions.size(); i++) {
			double d = predictions.get(i).distanceToSqr(pos);
			if (d < best) {
				best = d;
				closest = i;
			}
		}
		return predictions.size() - closest;
	}
}
