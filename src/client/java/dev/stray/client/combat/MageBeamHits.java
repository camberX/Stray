package dev.stray.client.combat;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.location.SkyblockLocation;
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
 * Rebuilds dungeon mage beams the same way as chained firework sparks:
 * Hypixel places points ~0.5 apart on a line and often sends the same beam
 * twice. A hit is that spark line going through a mob's real hitbox. Only
 * beams that start at the local player after a left click are scored.
 */
public final class MageBeamHits {
	private static final double POINT_SPACE = 0.5;
	private static final double POINT_SPACE_SQ = POINT_SPACE * POINT_SPACE;
	private static final double OURS_SQ = 4.0 * 4.0;
	private static final double DUP_SQ = 1.0E-6;
	private static final int WINDOW_TICKS = 16;
	private static final int BEAM_TTL = 20;

	private static final List<Beam> BEAMS = new ArrayList<>();
	private static int windowUntil;

	private MageBeamHits() {
	}

	public static void reset() {
		BEAMS.clear();
		windowUntil = 0;
	}

	public static void onAttack(Minecraft client) {
		if (client == null || client.player == null || client.options == null) {
			return;
		}
		boolean down = client.options.keyAttack.isDown();
		if (down && active(client) && !client.player.isSpectator()) {
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
		if (!active(client) || Hitsound.gameTick() > windowUntil) {
			return;
		}
		LocalPlayer player = client.player;
		if (player == null || client.level == null || player.isSpectator()) {
			return;
		}
		Vec3 point = new Vec3(x, y, z);
		if (seen(point)) {
			return;
		}
		int tick = Hitsound.gameTick();
		Beam beam = BEAMS.isEmpty() ? null : BEAMS.getLast();
		if (beam != null && tick - beam.updateTick <= 1 && beam.inLine(point)) {
			beam.points.add(point);
			beam.updateTick = tick;
		} else {
			beam = new Beam(point, tick, player.distanceToSqr(point) <= OURS_SQ);
			BEAMS.add(beam);
		}
		if (beam.ours && beam.points.size() >= 2) {
			hit(client, player, beam);
		}
	}

	private static boolean seen(Vec3 point) {
		for (int i = BEAMS.size() - 1; i >= 0; i--) {
			List<Vec3> points = BEAMS.get(i).points;
			for (int p = points.size() - 1; p >= 0; p--) {
				if (points.get(p).distanceToSqr(point) <= DUP_SQ) {
					return true;
				}
			}
		}
		return false;
	}

	private static void hit(Minecraft client, LocalPlayer player, Beam beam) {
		Vec3 from = beam.points.getFirst();
		Vec3 to = beam.points.getLast();
		if (from.distanceToSqr(to) < 0.04) {
			return;
		}
		AABB search = new AABB(from, to).inflate(0.35);
		for (Entity other : client.level.getEntities(player, search, entity -> Hitsound.isAbilityTarget(entity, player))) {
			AABB hitbox = other.getBoundingBox();
			if (!hitbox.contains(from) && !hitbox.contains(to) && hitbox.clip(from, to).isEmpty()) {
				continue;
			}
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
		private final boolean ours;
		private int updateTick;

		private Beam(Vec3 first, int tick, boolean ours) {
			this.points.add(first);
			this.updateTick = tick;
			this.ours = ours;
		}

		private boolean inLine(Vec3 point) {
			if (points.size() < 2) {
				return point.distanceToSqr(points.getFirst()) <= 4.0;
			}
			Vec3 min = points.getFirst();
			Vec3 max = points.getLast();
			Vec3 along = max.subtract(min);
			Vec3 step = point.subtract(max);
			if (along.lengthSqr() < 1.0E-8 || step.lengthSqr() < 1.0E-8) {
				return false;
			}
			double colinear = Math.abs(along.normalize().dot(step.normalize()));
			return colinear > 0.99 && point.distanceToSqr(max) <= POINT_SPACE_SQ;
		}
	}
}
