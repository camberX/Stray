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
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/**
 * Screen-space 2D box ESP. Uses the same AABB projection as the health bar
 * so the box and bar stay the same size on screen.
 */
public final class EntityBoxEsp {
	private EntityBoxEsp() {
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || client.options.hideGui) {
			return;
		}
		StrayConfig config = StrayConfig.get();
		if (!config.boxEspEnabled) {
			return;
		}
		Camera camera = client.gameRenderer.getMainCamera();
		if (!camera.isInitialized()) {
			return;
		}
		double range = StrayConfig.clamp(config.boxEspRange, 16, 96);
		double maxSq = range * range;
		Vec3 camPos = camera.position();
		Vector3fc forward = camera.forwardVector();
		float partial = delta.getGameTimeDeltaPartialTick(true);
		boolean through = config.boxEspThroughWalls;
		boolean players = config.boxEspPlayers;
		float guiW = graphics.guiWidth();
		float guiH = graphics.guiHeight();
		int line = Theme.withAlpha(config.boxEspRgb, Math.round(config.boxEspOpacity * 255f));
		int fill = Theme.withAlpha(config.boxEspRgb, Math.round(config.boxEspFill * 255f));
		for (Entity entity : client.level.entitiesForRendering()) {
			if (!(entity instanceof LivingEntity living) || !include(client, living, players, maxSq, camPos)) {
				continue;
			}
			Vec3 feet = living.getPosition(partial);
			Vec3 mid = feet.add(0.0, living.getBbHeight() * 0.5, 0.0);
			if (!EntityScreenBoxes.facing(mid, camPos, forward)) {
				continue;
			}
			if (!through && EntityScreenBoxes.occluded(client, camPos, mid)) {
				continue;
			}
			EntityScreenBoxes.Box box = EntityScreenBoxes.project(
				client,
				EntityScreenBoxes.bounds(living, feet),
				guiW,
				guiH
			);
			if (box == null || box.h() < 4f) {
				continue;
			}
			draw(graphics, box, line, fill, config.boxEspWidth);
		}
	}

	private static boolean include(Minecraft client, LivingEntity living, boolean players, double maxSq, Vec3 camPos) {
		if (living.isRemoved() || living.isDeadOrDying() || living instanceof ArmorStand) {
			return false;
		}
		if (living.isInvisibleTo(client.player) || living == client.player) {
			return false;
		}
		if (living instanceof Player) {
			return players && NametagRenderer.realAccount(living) && living.distanceToSqr(camPos) <= maxSq;
		}
		return living.distanceToSqr(camPos) <= maxSq;
	}

	private static void draw(GuiGraphicsExtractor graphics, EntityScreenBoxes.Box box, int line, int fill, float width) {
		float t = Mth.clamp(width, 1f, 6f);
		if ((fill >>> 24) != 0 && box.w() > t * 2f && box.h() > t * 2f) {
			GuiDraw.fillSmooth(graphics, box.x() + t, box.y() + t, box.w() - t * 2f, box.h() - t * 2f, fill);
		}
		GuiDraw.fillSmooth(graphics, box.x(), box.y(), box.w(), t, line);
		GuiDraw.fillSmooth(graphics, box.x(), box.y() + box.h() - t, box.w(), t, line);
		float side = box.h() - t * 2f;
		if (side > 0f) {
			GuiDraw.fillSmooth(graphics, box.x(), box.y() + t, t, side, line);
			GuiDraw.fillSmooth(graphics, box.x() + box.w() - t, box.y() + t, t, side, line);
		}
	}
}
