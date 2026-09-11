package dev.stray.client.render;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.ui.Theme;
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
 * Side-of-entity health bars. Fill eases toward the real health, and the color
 * shifts from green to red as the bar drops.
 */
public final class EntityHealthBars {
	private static final float BAR_W = 4.5f;
	private static final float BAR_H = 36f;
	private static final float GAP = 8f;
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
		float userScale = StrayConfig.clampHudScale(config.nametagScale);
		Set<UUID> seen = new HashSet<>();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (!(entity instanceof LivingEntity living) || !include(client, living, players, maxSq, camPos)) {
				continue;
			}
			Vec3 mid = living.getPosition(partial).add(0.0, living.getBbHeight() * 0.55, 0.0);
			Vec3 rel = mid.subtract(camPos);
			double facing = rel.x * forward.x() + rel.y * forward.y() + rel.z * forward.z();
			if (facing <= 0.12) {
				continue;
			}
			if (!through && occluded(client, camPos, mid)) {
				continue;
			}
			Vec3 ndc = client.gameRenderer.projectPointToScreen(mid);
			if (ndc.x < -1.2 || ndc.x > 1.2 || ndc.y < -1.2 || ndc.y > 1.2) {
				continue;
			}
			float max = Math.max(0.001f, living.getMaxHealth());
			float target = Mth.clamp(living.getHealth() / max, 0f, 1f);
			UUID id = living.getUUID();
			Bar bar = BARS.computeIfAbsent(id, ignored -> new Bar(target));
			bar.tick(target, dt);
			seen.add(id);
			float sx = (float) ((ndc.x * 0.5 + 0.5) * graphics.guiWidth());
			float sy = (float) ((-ndc.y * 0.5 + 0.5) * graphics.guiHeight());
			double dist = Math.sqrt(living.distanceToSqr(camPos));
			draw(graphics, sx, sy, bar.shown, NametagRenderer.distanceScale(dist) * userScale, right);
		}
		Iterator<Map.Entry<UUID, Bar>> it = BARS.entrySet().iterator();
		while (it.hasNext()) {
			if (!seen.contains(it.next().getKey())) {
				it.remove();
			}
		}
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

	private static void draw(
		GuiGraphicsExtractor graphics,
		float sx,
		float sy,
		float shown,
		float scale,
		boolean right
	) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(sx, sy);
		if (scale != 1.0f) {
			graphics.pose().scale(scale, scale);
		}
		float x = right ? GAP : -GAP - BAR_W;
		float y = -BAR_H * 0.5f;
		float fillH = BAR_H * Mth.clamp(shown, 0f, 1f);
		int color = 0xFF000000 | healthColor(shown);
		GuiDraw.rounded(graphics, x - 1f, y - 1f, BAR_W + 2f, BAR_H + 2f, 2.2f, LINE);
		GuiDraw.rounded(graphics, x, y, BAR_W, BAR_H, 1.8f, TRACK);
		if (fillH >= 0.6f) {
			GuiDraw.rounded(graphics, x, y + BAR_H - fillH, BAR_W, fillH, 1.8f, color);
		}
		graphics.pose().popMatrix();
	}

	private static int healthColor(float t) {
		t = Mth.clamp(t, 0f, 1f);
		if (t >= 0.5f) {
			return Theme.mix(0xF5C16C, 0x34D399, (t - 0.5f) * 2f);
		}
		return Theme.mix(0xFB7185, 0xF5C16C, t * 2f);
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
