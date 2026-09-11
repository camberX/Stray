package dev.stray.client.render;

import dev.stray.client.config.StrayConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Side-of-entity health bars. Height matches the mob on screen. Fill eases
 * toward real health and shifts green to red as it drops.
 */
public final class EntityHealthBars {
	private static final float WIDTH_RATIO = 0.09f;
	private static final float GAP_RATIO = 0.11f;
	private static final int TRACK = 0xCC0B0E14;
	private static final int LINE = 0x661C2430;
	private static final Map<UUID, Bar> BARS = new HashMap<>();
	private static long lastNs = System.nanoTime();

	private EntityHealthBars() {
	}

	public static void init() {
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || client.options.hideGui) {
			return;
		}
		StrayConfig config = StrayConfig.get();
		if (!config.healthBarEnabled) {
			BARS.clear();
			return;
		}
		long now = System.nanoTime();
		float dt = Math.min(0.05f, (now - lastNs) / 1_000_000_000f);
		lastNs = now;
		Camera camera = client.gameRenderer.getMainCamera();
		if (!camera.isInitialized()) {
			return;
		}
		double range = StrayConfig.clamp(config.healthBarRange, 16, 96);
		double maxSq = range * range;
		Vec3 camPos = camera.position();
		Vector3fc forward = camera.forwardVector();
		float partial = delta.getGameTimeDeltaPartialTick(true);
		boolean through = config.healthBarThroughWalls;
		boolean players = config.healthBarPlayers;
		boolean right = config.healthBarRight();
		float guiW = graphics.guiWidth();
		float guiH = graphics.guiHeight();
		Set<UUID> seen = new HashSet<>();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (!(entity instanceof LivingEntity living) || !include(client, living, players, maxSq, camPos)) {
				continue;
			}
			Vec3 feet = living.getPosition(partial);
			Vec3 mid = feet.add(0.0, living.getBbHeight() * 0.5, 0.0);
			Vec3 rel = mid.subtract(camPos);
			double facing = rel.x * forward.x() + rel.y * forward.y() + rel.z * forward.z();
			if (facing <= 0.12) {
				continue;
			}
			if (!through && occluded(client, camPos, mid)) {
				continue;
			}
			AABB box = bounds(living, feet);
			ScreenBox screen = project(client, box, guiW, guiH);
			if (screen == null || screen.h() < 4f) {
				continue;
			}
			float max = Math.max(0.001f, living.getMaxHealth());
			float target = Mth.clamp(living.getHealth() / max, 0f, 1f);
			UUID id = living.getUUID();
			Bar bar = BARS.computeIfAbsent(id, ignored -> new Bar(target));
			bar.tick(target, dt);
			seen.add(id);
			draw(graphics, screen, bar.shown, right);
		}
		Iterator<Map.Entry<UUID, Bar>> it = BARS.entrySet().iterator();
		while (it.hasNext()) {
			if (!seen.contains(it.next().getKey())) {
				it.remove();
			}
		}
	}

	private static AABB bounds(LivingEntity living, Vec3 feet) {
		double hw = living.getBbWidth() * 0.5;
		double h = living.getBbHeight();
		return new AABB(feet.x - hw, feet.y, feet.z - hw, feet.x + hw, feet.y + h, feet.z + hw);
	}

	private static ScreenBox project(Minecraft client, AABB box, float guiW, float guiH) {
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
		return new ScreenBox(minX, minY, maxX - minX, maxY - minY);
	}

	private static boolean include(
		Minecraft client,
		LivingEntity living,
		boolean players,
		double maxSq,
		Vec3 camPos
	) {
		if (living.isRemoved() || living.isDeadOrDying() || living.getMaxHealth() <= 0f) {
			return false;
		}
		if (living instanceof ArmorStand) {
			return false;
		}
		if (living.isInvisibleTo(client.player)) {
			return false;
		}
		if (living == client.player) {
			return false;
		}
		if (living instanceof Player) {
			if (!players || !NametagRenderer.realAccount(living)) {
				return false;
			}
		}
		return living.distanceToSqr(camPos) <= maxSq;
	}

	private static boolean occluded(Minecraft client, Vec3 from, Vec3 to) {
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

	private static void draw(GuiGraphicsExtractor graphics, ScreenBox box, float shown, boolean right) {
		float h = box.h();
		float w = Mth.clamp(h * WIDTH_RATIO, 1.1f, 4.5f);
		float gap = Mth.clamp(h * GAP_RATIO, 1.5f, 5f);
		float pad = Mth.clamp(h * 0.03f, 0.4f, 1f);
		float x = right ? box.x + box.w + gap : box.x - gap - w;
		float y = box.y;
		float fillH = h * Mth.clamp(shown, 0f, 1f);
		float radius = Math.min(w * 0.45f, h * 0.12f);
		int color = 0xFF000000 | healthColor(shown);
		GuiDraw.rounded(graphics, x - pad, y - pad, w + pad * 2f, h + pad * 2f, radius + pad * 0.4f, LINE);
		GuiDraw.rounded(graphics, x, y, w, h, radius, TRACK);
		if (fillH >= 0.6f) {
			GuiDraw.rounded(graphics, x, y + h - fillH, w, fillH, radius, color);
		}
	}

	private static int healthColor(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return hsv(t * 120f, 0.90f, 0.92f);
	}

	private static int hsv(float hue, float sat, float val) {
		float chroma = val * sat;
		float x = chroma * (1f - Math.abs((hue / 60f) % 2f - 1f));
		float m = val - chroma;
		float r;
		float g;
		float b;
		if (hue < 60f) {
			r = chroma;
			g = x;
			b = 0f;
		} else {
			r = x;
			g = chroma;
			b = 0f;
		}
		int ri = Math.round((r + m) * 255f);
		int gi = Math.round((g + m) * 255f);
		int bi = Math.round((b + m) * 255f);
		return (ri << 16) | (gi << 8) | bi;
	}

	private record ScreenBox(float x, float y, float w, float h) {
	}

	private static final class Bar {
		float shown;

		Bar(float start) {
			this.shown = start;
		}

		void tick(float target, float dt) {
			float k = 1f - (float) Math.exp(-14f * dt);
			shown += (target - shown) * k;
			if (Math.abs(target - shown) < 0.002f) {
				shown = target;
			}
		}
	}
}
