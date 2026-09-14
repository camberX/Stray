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
 * twice. Sparks can arrive a few ticks apart, so the chain is matched by
 * colinearity and spacing, not "same tick + last beam only".
 */
public final class MageBeamHits {
	private static final double POINT_SPACE_SQ = 1.15 * 1.15;
	private static final double OURS_SQ = 3.5 * 3.5;
	private static final double DUP_SQ = 1.0E-4;
	private static final double HIT_INFLATE = 0.75;
	private static final double COLINEAR = 0.94;
	private static final double FALL_Y = 0.22;
	private static final int WINDOW_TICKS = 20;
	private static final int BEAM_GAP = 5;
	private static final int BEAM_TTL = 24;

	private static final List<Beam> BEAMS = new ArrayList<>();
	private static final IntOpenHashSet SHOT_HITS = new IntOpenHashSet();
	private static int windowUntil;

	private MageBeamHits() {
	}

	public static void reset() {
		BEAMS.clear();
		SHOT_HITS.clear();
		windowUntil = 0;
	}

	public static void onAttack(Minecraft client) {
		if (client == null || client.player == null || client.options == null) {
			return;
		}
		if (client.player.isSpectator() || !active(client)) {
			return;
		}
		if (client.options.keyAttack.isDown() || client.player.swinging) {
			windowUntil = Hitsound.gameTick() + WINDOW_TICKS;
		}
	}

	public static void tick(Minecraft client) {
		onAttack(client);
		int tick = Hitsound.gameTick();
		if (tick > windowUntil) {
			SHOT_HITS.clear();
		}
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
		if (beam == null) {
			if (tick > windowUntil || !fromStaff(player, point)) {
				return;
			}
			if (liveOurs(tick)) {
				return;
			}
			beam = new Beam(point, tick, true);
			BEAMS.add(beam);
		} else if (beam.points.getLast().distanceToSqr(point) <= DUP_SQ) {
			return;
		} else {
			beam.points.add(point);
			beam.updateTick = tick;
		}
		if (beam.ours && beam.points.size() >= 2) {
			hit(client, player, beam);
		}
	}

	private static boolean fromStaff(LocalPlayer player, Vec3 point) {
		if (player.distanceToSqr(point) > OURS_SQ) {
			return false;
		}
		return Math.abs(point.y - player.getEyeY()) <= 1.15;
	}

	private static boolean liveOurs(int tick) {
		for (int i = 0; i < BEAMS.size(); i++) {
			Beam beam = BEAMS.get(i);
			if (beam.ours && tick - beam.updateTick <= BEAM_GAP) {
				return true;
			}
		}
		return false;
	}

	private static Beam match(Vec3 point, int tick) {
		Beam best = null;
		for (int i = BEAMS.size() - 1; i >= 0; i--) {
			Beam beam = BEAMS.get(i);
			if (tick - beam.updateTick > BEAM_GAP || !beam.inLine(point)) {
				continue;
			}
			if (best == null || beam.ours && !best.ours || beam.points.size() > best.points.size()) {
				best = beam;
			}
		}
		return best;
	}

	private static void hit(Minecraft client, LocalPlayer player, Beam beam) {
		int n = beam.points.size();
		Vec3 from = beam.points.get(n - 2);
		Vec3 last = beam.points.get(n - 1);
		Vec3 step = last.subtract(from);
		Vec3 to = step.lengthSqr() < 1.0E-8 ? last : last.add(step.normalize().scale(0.35));
		AABB search = new AABB(from, to).inflate(HIT_INFLATE);
		for (Entity other : client.level.getEntities(player, search, entity -> Hitsound.isAbilityTarget(entity, player))) {
			int id = other.getId();
			if (SHOT_HITS.contains(id) || beam.hitIds.contains(id)) {
				continue;
			}
			AABB hitbox = other.getBoundingBox().inflate(HIT_INFLATE);
			if (!hitbox.contains(from) && !hitbox.contains(to) && !hitbox.contains(last)
					&& hitbox.clip(from, to).isEmpty()) {
				continue;
			}
			beam.hitIds.add(id);
			SHOT_HITS.add(id);
			Hitsound.onAbilityHit(other);
		}
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
		private boolean ours;
		private int updateTick;

		private Beam(Vec3 first, int tick, boolean ours) {
			this.points.add(first);
			this.updateTick = tick;
			this.ours = ours;
		}

		private boolean inLine(Vec3 point) {
			Vec3 last = points.getLast();
			if (point.y < last.y - FALL_Y) {
				return false;
			}
			if (point.distanceToSqr(last) > POINT_SPACE_SQ) {
				return false;
			}
			if (points.size() < 2) {
				return true;
			}
			Vec3 along = last.subtract(points.getFirst());
			Vec3 step = point.subtract(last);
			if (along.lengthSqr() < 1.0E-8 || step.lengthSqr() < 1.0E-8) {
				return true;
			}
			return along.normalize().dot(step.normalize()) >= COLINEAR;
		}
	}
}
