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

/**
 * Dungeon Mage Staff beams are firework sparks after a left click. Aim is
 * frozen on the click (not every held tick). A hit is that ray going through
 * the mob's real hitbox.
 */
public final class MageBeamHits {
	private static final double MAX_RANGE = 32.0;
	private static final double RAY_RADIUS_SQ = 1.6 * 1.6;
	private static final double END_PAD = 0.6;
	private static final int WINDOW_TICKS = 14;
	private static final int PULSE_GAP = 3;

	private static int windowUntil;
	private static int lastNearTick = Integer.MIN_VALUE;
	private static boolean wasAttack;
	private static double beamEnd;
	private static Vec3 origin = Vec3.ZERO;
	private static Vec3 look = new Vec3(0.0, 0.0, 1.0);

	private MageBeamHits() {
	}

	public static void reset() {
		windowUntil = 0;
		lastNearTick = Integer.MIN_VALUE;
		wasAttack = false;
		beamEnd = 0.0;
	}

	public static void onAttack(Minecraft client) {
		if (client == null || client.player == null || client.options == null) {
			return;
		}
		boolean down = client.options.keyAttack.isDown();
		if (down && !wasAttack && active(client) && !client.player.isSpectator()) {
			aim(client.player);
			windowUntil = Hitsound.gameTick() + WINDOW_TICKS;
			beamEnd = 0.0;
		}
		wasAttack = down;
		if (down && active(client)) {
			windowUntil = Math.max(windowUntil, Hitsound.gameTick() + WINDOW_TICKS);
		}
	}

	public static void tick(Minecraft client) {
		onAttack(client);
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
		if (look.lengthSqr() < 1.0E-6) {
			return;
		}
		Vec3 point = new Vec3(x, y, z);
		Vec3 rel = point.subtract(origin);
		double along = rel.dot(look);
		if (along < 0.15 || along > MAX_RANGE) {
			return;
		}
		if (rel.lengthSqr() - along * along > RAY_RADIUS_SQ) {
			return;
		}
		int tick = Hitsound.gameTick();
		if (along < 3.0 && tick - lastNearTick >= PULSE_GAP) {
			aim(player);
			rel = point.subtract(origin);
			along = rel.dot(look);
			beamEnd = 0.0;
		}
		if (along < 3.0) {
			lastNearTick = tick;
		}
		if (along > beamEnd) {
			beamEnd = along;
		}
		scan(client, player, origin, look, beamEnd + END_PAD);
	}

	private static void aim(LocalPlayer player) {
		origin = player.getEyePosition();
		Vec3 dir = player.getLookAngle();
		if (dir.lengthSqr() > 1.0E-6) {
			look = dir.normalize();
		}
	}

	private static void scan(Minecraft client, LocalPlayer player, Vec3 from, Vec3 dir, double range) {
		Vec3 to = from.add(dir.scale(Math.min(MAX_RANGE, range)));
		AABB search = new AABB(from, to).inflate(0.4);
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
}
