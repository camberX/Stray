package dev.stray.client.render;

import dev.stray.client.config.EntityKind;
import dev.stray.client.config.EntityVisuals;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.location.SkyblockLocation;
import dev.stray.client.render.overlay.Canvas;
import dev.stray.client.ui.Theme;
import dev.stray.client.util.Sight;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/**
 * Star mob 2D boxes for the streamproof overlay: same boxes and settings as the 2D box ESP,
 * projected with the game's own camera but drawn in the overlay window, so they line up
 * with the world without ever landing in the game's frame.
 */
public final class StarMobOverlay {
	private StarMobOverlay() {
	}

	public static boolean wants() {
		if (dev.stray.Edition.LEGIT || !SkyblockLocation.inDungeon || SkyblockLocation.inBoss) {
			return false;
		}
		EntityVisuals visuals = StrayConfig.get().visuals(EntityKind.STAR);
		return visuals.boxEnabled && visuals.boxStreamproof;
	}

	/** A box outline of thickness {@code t}; with {@code corners}, only a quarter of each side from each corner. */
	private static void frame(Canvas canvas, float x, float y, float w, float h, float t, boolean corners, int color) {
		if (w <= 0f || h <= 0f) {
			return;
		}
		if (!corners) {
			canvas.fill(x, y, w, t, color);
			canvas.fill(x, y + h - t, w, t, color);
			float side = h - t * 2f;
			if (side > 0f) {
				canvas.fill(x, y + t, t, side, color);
				canvas.fill(x + w - t, y + t, t, side, color);
			}
			return;
		}
		float cw = Math.max(t, w / 4f);
		float ch = Math.max(t, h / 4f);
		canvas.fill(x, y, cw, t, color);
		canvas.fill(x + w - cw, y, cw, t, color);
		canvas.fill(x, y + h - t, cw, t, color);
		canvas.fill(x + w - cw, y + h - t, cw, t, color);
		canvas.fill(x, y + t, t, ch - t, color);
		canvas.fill(x + w - t, y + t, t, ch - t, color);
		canvas.fill(x, y + h - ch, t, ch - t, color);
		canvas.fill(x + w - t, y + h - ch, t, ch - t, color);
	}

	public static void draw(Canvas canvas, int guiW, int guiH, float partial) {
		Minecraft client = Minecraft.getInstance();
		if (!wants() || client.level == null || client.player == null) {
			return;
		}
		Camera camera = client.gameRenderer.mainCamera();
		if (!camera.isInitialized()) {
			return;
		}
		EntityVisuals visuals = StrayConfig.get().visuals(EntityKind.STAR);
		Vec3 camPos = camera.position();
		Vector3fc forward = camera.forwardVector();
		float guiScale = (float) client.getWindow().getGuiScale();
		int line = Theme.withAlpha(visuals.boxRgb, Math.round(visuals.boxOpacity * 255f));
		int fill = Theme.withAlpha(visuals.boxRgb, Math.round(visuals.boxFill * 255f));
		float t = Mth.clamp(visuals.boxWidth, 1f, 6f) / Math.max(1f, guiScale);
		for (Entity entity : client.level.entitiesForRendering()) {
			if (!(entity instanceof LivingEntity living) || EntityKind.of(living) != EntityKind.STAR) {
				continue;
			}
			if (!EntityBoxEsp.include(client, living, visuals, camPos)) {
				continue;
			}
			Vec3 feet = living.getPosition(partial);
			Vec3 mid = feet.add(0.0, living.getBbHeight() * 0.5, 0.0);
			if (!EntityScreenBoxes.facing(mid, camPos, forward)) {
				continue;
			}
			if (!visuals.boxThroughWalls && Sight.occluded(client, camPos, mid)) {
				continue;
			}
			EntityScreenBoxes.Box box = EntityScreenBoxes.project(client, EntityScreenBoxes.boxBounds(living, feet), guiW, guiH);
			if (box == null || box.h() < 4f) {
				continue;
			}
			if (visuals.boxTracer) {
				int tracer = Theme.withAlpha(visuals.boxRgb, Math.round(visuals.boxOpacity * 0.7f * 255f));
				canvas.line(guiW / 2f, guiH, box.x() + box.w() / 2f, box.y() + box.h(), t, tracer);
			}
			if ((fill >>> 24) != 0 && box.w() > t * 2f && box.h() > t * 2f) {
				canvas.fill(box.x() + t, box.y() + t, box.w() - t * 2f, box.h() - t * 2f, fill);
			}
			if (visuals.boxOutline) {
				// Dark edges a pixel outside and inside the line.
				float px = 1f / Math.max(1f, guiScale);
				int dark = Math.round(visuals.boxOpacity * 0xB0) << 24;
				frame(canvas, box.x() - px, box.y() - px, box.w() + px * 2f, box.h() + px * 2f, px, visuals.boxStyle == 1, dark);
				frame(canvas, box.x() + t, box.y() + t, box.w() - t * 2f, box.h() - t * 2f, px, visuals.boxStyle == 1, dark);
			}
			frame(canvas, box.x(), box.y(), box.w(), box.h(), t, visuals.boxStyle == 1, line);
			int label = Theme.withAlpha(0xFFFFFF, Math.round(visuals.boxOpacity * 255f));
			if (visuals.boxName) {
				String plate = StarMobEsp.plate(living);
				if (plate != null && !plate.isEmpty()) {
					canvas.centeredText(plate, box.x() + box.w() / 2f, box.y() - 10f, label, 1f);
				}
			}
			if (visuals.boxDistance) {
				String d = Math.round(Math.sqrt(living.distanceToSqr(camPos))) + "m";
				canvas.centeredText(d, box.x() + box.w() / 2f, box.y() + box.h() + 2f, label, 1f);
			}
		}
	}
}
