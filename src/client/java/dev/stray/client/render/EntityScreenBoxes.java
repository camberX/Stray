package dev.stray.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/**
 * Shared screen-space AABB used by the health bar and 2D box ESP so both
 * scale to the same on-screen height.
 */
final class EntityScreenBoxes {
	private EntityScreenBoxes() {
	}

	static Box project(Minecraft client, AABB box, float guiW, float guiH) {
		float minX = Float.POSITIVE_INFINITY;
		float minY = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY;
		float maxY = Float.NEGATIVE_INFINITY;
		int hits = 0;
		for (int i = 0; i < 8; i++) {
			double x = (i & 1) == 0 ? box.minX : box.maxX;
			double y = (i & 2) == 0 ? box.minY : box.maxY;
			double z = (i & 4) == 0 ? box.minZ : box.maxZ;
			Vec3 ndc = client.gameRenderer.projectPointToScreen(new Vec3(x, y, z));
			if (ndc.x < -2.0 || ndc.x > 2.0 || ndc.y < -2.0 || ndc.y > 2.0) {
				continue;
			}
			float sx = (float) ((ndc.x * 0.5 + 0.5) * guiW);
			float sy = (float) ((-ndc.y * 0.5 + 0.5) * guiH);
			minX = Math.min(minX, sx);
			maxX = Math.max(maxX, sx);
			minY = Math.min(minY, sy);
			maxY = Math.max(maxY, sy);
			hits++;
		}
		if (hits < 2 || maxX <= minX || maxY <= minY) {
			return null;
		}
		return new Box(minX, minY, maxX - minX, maxY - minY);
	}

	static AABB bounds(LivingEntity living, Vec3 feet) {
		double hw = living.getBbWidth() * 0.5;
		double h = living.getBbHeight();
		return new AABB(feet.x - hw, feet.y, feet.z - hw, feet.x + hw, feet.y + h, feet.z + hw);
	}

	static boolean facing(Vec3 mid, Vec3 camPos, Vector3fc forward) {
		Vec3 rel = mid.subtract(camPos);
		return rel.x * forward.x() + rel.y * forward.y() + rel.z * forward.z() > 0.12;
	}

	static boolean occluded(Minecraft client, Vec3 from, Vec3 to) {
		HitResult hit = client.level.clip(new ClipContext(
			from,
			to,
			ClipContext.Block.VISUAL,
			ClipContext.Fluid.NONE,
			client.player
		));
		if (hit.getType() == HitResult.Type.MISS) {
			return false;
		}
		return hit.getLocation().distanceToSqr(from) + 0.36 < to.distanceToSqr(from);
	}

	record Box(float x, float y, float w, float h) {
	}
}
