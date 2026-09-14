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
 * Dungeon Mage Staff beams are long-distance firework sparks in a line from
 * the player after a left click. Hits use that spark line against the mob's
 * real box, not a fat look-ray and not a spark sitting inside the box
 * (Hypixel spaces sparks, so a direct hit often has none on the AABB).
 */
public final class MageBeamHits {
	private static final double MAX_RANGE = 32.0;
	private static final double ARM_ALONG = 8.0;
	private static final double RAY_RADIUS_SQ = 1.25 * 1.25;
	private static final double HIT_INFLATE = 0.18;
	private static final double MIN_BEAM = 2.5;
	private static final int WINDOW_TICKS = 12;

	private static int windowUntil;
	private static boolean armed;
	private static double beamEnd;
	private static Vec3 origin = Vec3.ZERO;
	private static Vec3 look = new Vec3(0.0, 0.0, 1.0);

	private MageBeamHits() {
	}

	public static void reset() {
		windowUntil = 0;
		armed = false;
		beamEnd = 0.0;
	}

	public static void onAttack(Minecraft client) {
		if (client == null || client.player == null || client.options == null) {
			return;
		}
		if (!client.options.keyAttack.isDown()) {
			return;
		}
		if (!active(client)) {
			return;
		}
		LocalPlayer player = client.player;
		if (player.isSpectator()) {
			return;
		}
		origin = player.getEyePosition();
		look = player.getLookAngle();
		if (look.lengthSqr() < 1.0E-6) {
			return;
		}
		look = look.normalize();
		windowUntil = Math.max(windowUntil, Hitsound.gameTick() + WINDOW_TICKS);
	}

	public static void tick(Minecraft client) {
		if (Hitsound.gameTick() > windowUntil) {
			armed = false;
			beamEnd = 0.0;
		}
		onAttack(client);
	}

	public static void onParticle(
		double x,
		double y,
		double z,
		ParticleType<?> type,
		int count,
		float speed,
		float dx,
		float dy,
		float dz,
		boolean far
	) {
		if (type != ParticleTypes.FIREWORK) {
			return;
		}
		if (count > 1 || speed > 0.02f) {
			return;
		}
		if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > 0.04f) {
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
		Vec3 from = origin;
		Vec3 dir = look;
		if (dir.lengthSqr() < 1.0E-6) {
			return;
		}
		Vec3 rel = point.subtract(from);
		double along = rel.dot(dir);
		if (along < 0.2 || along > MAX_RANGE) {
			return;
		}
		if (rel.lengthSqr() - along * along > RAY_RADIUS_SQ) {
			return;
		}
		if (along <= ARM_ALONG) {
			armed = true;
		}
		if (!armed) {
			return;
		}
		if (along > beamEnd) {
			beamEnd = along;
		}
		if (beamEnd < MIN_BEAM) {
			return;
		}
		scan(client, player, from, dir, beamEnd);
	}

	private static void scan(Minecraft client, LocalPlayer player, Vec3 from, Vec3 dir, double range) {
		Vec3 to = from.add(dir.scale(range));
		AABB search = new AABB(from, to).inflate(HIT_INFLATE + 0.35);
		for (Entity other : client.level.getEntities(player, search, entity -> Hitsound.isAbilityTarget(entity, player))) {
			AABB hitbox = other.getBoundingBox().inflate(HIT_INFLATE);
			if (hitbox.clip(from, to).isEmpty()) {
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
