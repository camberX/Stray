package dev.stray.client.combat;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.location.SkyblockLocation;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Hypixel mage beams are firework sparks ~0.5 apart on a line, often sent
 * twice. One left-click is one volley: extra sparks and the duplicate packet
 * must not stack sounds, but a later click on the same mob is a new volley.
 */
public final class MageBeamHits {
	private static final double POINT_SPACE_SQ = 1.6 * 1.6;
	private static final double GAP_SPACE_SQ = 2.6 * 2.6;
	private static final double OURS_SQ = 6.0 * 6.0;
	private static final double DUP_SQ = 0.04 * 0.04;
	private static final double HIT_INFLATE = 1.0;
	private static final double COLINEAR = 0.90;
	private static final double LINE_PAD = 0.55;
	private static final int WINDOW_TICKS = 20;
	private static final int BEAM_GAP = 5;
	private static final int BEAM_TTL = 24;
	private static final int DUP_TICKS = 2;

	private static final List<Beam> BEAMS = new ArrayList<>();
	private static final IntOpenHashSet SHOT_HITS = new IntOpenHashSet();
	private static int windowUntil;
	private static int clickId;
	private static boolean attackHeld;

	private MageBeamHits() {
	}

	public static void reset() {
		BEAMS.clear();
		SHOT_HITS.clear();
		windowUntil = 0;
		clickId = 0;
		attackHeld = false;
	}

	public static void onAttack(Minecraft client) {
		if (client == null || client.player == null || client.options == null) {
			return;
		}
		if (client.player.isSpectator() || !active(client)) {
			attackHeld = false;
			return;
		}
		boolean key = client.options.keyAttack.isDown();
		if (key && !attackHeld) {
			clickId++;
		}
		attackHeld = key;
		if (key || client.player.swinging) {
			windowUntil = Hitsound.gameTick() + WINDOW_TICKS;
		}
	}

	public static void tick(Minecraft client) {
		onAttack(client);
		int tick = Hitsound.gameTick();
		Iterator<Beam> it = BEAMS.iterator();
		while (it.hasNext()) {
			if (tick - it.next().updateTick > BEAM_TTL) {
				it.remove();
			}
		}
	}

	public static void onParticle(double x, double y, double z, ParticleType<?> type) {
		if (type != ParticleTypes.FIREWORK) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (!active(client)) {
			return;
		}
		LocalPlayer player = client.player;
		if (player == null || client.level == null || player.isSpectator()) {
			return;
		}
		int tick = Hitsound.gameTick();
		Vec3 point = new Vec3(x, y, z);
		Beam beam = match(point, tick);
		if (beam != null) {
			if (beam.has(point)) {
				return;
			}
			beam.points.add(point);
			beam.updateTick = tick;
		} else if (tick <= windowUntil && fromStaff(player, point)) {
			Beam live = liveOurs(tick);
			if (live != null && live.clickId == clickId) {
				if (live.has(point)) {
					return;
				}
				return;
			}
			SHOT_HITS.clear();
			beam = new Beam(point, tick, clickId);
			BEAMS.add(beam);
		} else {
			return;
		}
		if (beam != null && beam.ours) {
			hit(client, player, beam);
		}
	}

	private static boolean fromStaff(LocalPlayer player, Vec3 point) {
		if (player.distanceToSqr(point) > OURS_SQ) {
			return false;
		}
		return Math.abs(point.y - player.getEyeY()) <= 1.6;
	}

	private static Beam liveOurs(int tick) {
		Beam best = null;
		for (int i = 0; i < BEAMS.size(); i++) {
			Beam beam = BEAMS.get(i);
			if (!beam.ours || tick - beam.updateTick > DUP_TICKS) {
				continue;
			}
			if (best == null || beam.points.size() > best.points.size()) {
				best = beam;
			}
		}
		return best;
	}

	private static Beam match(Vec3 point, int tick) {
		Beam best = null;
		double bestScore = Double.MAX_VALUE;
		for (int i = BEAMS.size() - 1; i >= 0; i--) {
			Beam beam = BEAMS.get(i);
			if (tick - beam.updateTick > BEAM_GAP || beam.clickId != clickId) {
				continue;
			}
			double score = beam.matchScore(point);
			if (score < 0.0) {
				continue;
			}
			if (best == null
					|| beam.ours && !best.ours
					|| beam.ours == best.ours && score < bestScore) {
				best = beam;
				bestScore = score;
			}
		}
		return best;
	}

	private static void hit(Minecraft client, LocalPlayer player, Beam beam) {
		Vec3 first = beam.points.getFirst();
		Vec3 last = beam.points.getLast();
		Vec3 along = last.subtract(first);
		Vec3 from = first;
		Vec3 to = last;
		if (along.lengthSqr() >= 0.04) {
			to = last.add(along.normalize().scale(1.2));
		}
		AABB search = new AABB(from, to).inflate(HIT_INFLATE + 0.35);
		for (int i = 0; i < beam.points.size(); i++) {
			search = search.minmax(new AABB(beam.points.get(i), beam.points.get(i)).inflate(HIT_INFLATE));
		}
		for (Entity other : client.level.getEntities(player, search, entity -> Hitsound.isAbilityTarget(entity, player))) {
			int id = other.getId();
			if (SHOT_HITS.contains(id) || beam.hitIds.contains(id)) {
				continue;
			}
			AABB hitbox = other.getBoundingBox().inflate(HIT_INFLATE);
			if (!clips(beam, hitbox, from, to)) {
				continue;
			}
			beam.hitIds.add(id);
			SHOT_HITS.add(id);
			Hitsound.onAbilityHit(other);
		}
	}

	private static boolean clips(Beam beam, AABB hitbox, Vec3 from, Vec3 to) {
		if (hitbox.contains(from) || hitbox.contains(to) || hitbox.clip(from, to).isPresent()) {
			return true;
		}
		List<Vec3> points = beam.points;
		for (int i = 0; i < points.size(); i++) {
			Vec3 point = points.get(i);
			if (hitbox.contains(point)) {
				return true;
			}
			if (i > 0 && hitbox.clip(points.get(i - 1), point).isPresent()) {
				return true;
			}
		}
		return false;
	}

	private static boolean active(Minecraft client) {
		if (client == null || client.player == null || client.level == null || client.isPaused()) {
			return false;
		}
		if (!SkyblockLocation.inDungeon) {
			return false;
		}
		StrayConfig config = StrayConfig.get();
		if (!config.hitsoundMage) {
			return false;
		}
		return config.hitmarkerEnabled || config.hitsoundEnabled;
	}

	private static final class Beam {
		private final List<Vec3> points = new ArrayList<>();
		private final IntOpenHashSet hitIds = new IntOpenHashSet();
		private final int clickId;
		private boolean ours = true;
		private int updateTick;

		private Beam(Vec3 first, int tick, int clickId) {
			this.points.add(first);
			this.updateTick = tick;
			this.clickId = clickId;
		}

		private boolean has(Vec3 point) {
			for (int i = 0; i < points.size(); i++) {
				if (points.get(i).distanceToSqr(point) <= DUP_SQ) {
					return true;
				}
			}
			return false;
		}

		private double matchScore(Vec3 point) {
			if (has(point)) {
				return 0.0;
			}
			Vec3 last = points.getLast();
			double lastDist = point.distanceToSqr(last);
			if (lastDist <= POINT_SPACE_SQ && colinear(point)) {
				return lastDist;
			}
			if (lastDist <= GAP_SPACE_SQ && colinear(point)) {
				return lastDist + 1.0;
			}
			double line = distToLineSq(point);
			if (line >= 0.0 && line <= LINE_PAD * LINE_PAD && alongBeam(point)) {
				return line + 2.0;
			}
			return -1.0;
		}

		private boolean colinear(Vec3 point) {
			if (points.size() < 2) {
				return true;
			}
			Vec3 last = points.getLast();
			Vec3 along = last.subtract(points.getFirst());
			Vec3 step = point.subtract(last);
			if (along.lengthSqr() < 1.0E-8 || step.lengthSqr() < 1.0E-8) {
				return true;
			}
			return along.normalize().dot(step.normalize()) >= COLINEAR;
		}

		private boolean alongBeam(Vec3 point) {
			Vec3 first = points.getFirst();
			Vec3 last = points.getLast();
			Vec3 along = last.subtract(first);
			if (along.lengthSqr() < 0.04) {
				return point.distanceToSqr(last) <= GAP_SPACE_SQ;
			}
			double t = point.subtract(first).dot(along) / along.lengthSqr();
			return t >= 0.75 && t <= 1.45;
		}

		private double distToLineSq(Vec3 point) {
			Vec3 first = points.getFirst();
			Vec3 last = points.getLast();
			Vec3 along = last.subtract(first);
			if (along.lengthSqr() < 0.04) {
				return point.distanceToSqr(last);
			}
			double t = Math.max(0.0, Math.min(1.0, point.subtract(first).dot(along) / along.lengthSqr()));
			return point.distanceToSqr(first.add(along.scale(t)));
		}
	}
}
