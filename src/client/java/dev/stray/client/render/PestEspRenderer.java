package dev.stray.client.render;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.farming.PestEsp;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.List;

public final class PestEspRenderer {
	private PestEspRenderer() {
	}

	public static void init() {
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> emit());
	}

	private static void emit() {
		if (!PestEsp.active()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		List<PestEsp.Mark> pests = PestEsp.snapshot();
		if (pests.isEmpty()) {
			return;
		}
		StrayConfig config = StrayConfig.get();
		int rgb = config.pestEspRgb & 0xFFFFFF;
		float opacity = StrayConfig.clamp(config.pestEspOpacity, 0.08f, 0.85f);
		int fill = (Math.round(opacity * 255f) << 24) | rgb;
		int line = (Math.round(Math.min(1f, opacity + 0.45f) * 255f) << 24) | rgb;
		GizmoStyle style = GizmoStyle.strokeAndFill(line, 2.0f, fill);
		boolean through = config.pestEspThroughWalls;
		for (PestEsp.Mark pest : pests) {
			GizmoProperties box = Gizmos.cuboid(pest.box(), style);
			if (through) {
				box.setAlwaysOnTop();
			}
		}
		if (!PestEsp.holdingVacuum(client.player)) {
			return;
		}
		Vec3 camera = client.gameRenderer.getMainCamera().position();
		Vec3 look = client.player.getViewVector(1.0f);
		Vec3 from = camera.add(look.scale(0.4));
		for (PestEsp.Mark pest : pests) {
			GizmoProperties tracer = Gizmos.line(from, pest.center(), line, 2.0f);
			if (through) {
				tracer.setAlwaysOnTop();
			}
		}
	}

	static void extract(GuiGraphicsExtractor graphics) {
		StrayConfig config = StrayConfig.get();
		if (!config.pestEspNametag || !PestEsp.active()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || client.font == null || client.options.hideGui) {
			return;
		}
		if (!client.gameRenderer.getMainCamera().isInitialized()) {
			return;
		}
		List<PestEsp.Mark> pests = PestEsp.snapshot();
		if (pests.isEmpty()) {
			return;
		}
		Vec3 camera = client.gameRenderer.getMainCamera().position();
		Vector3fc forward = client.gameRenderer.getMainCamera().forwardVector();
		float userScale = StrayConfig.clampHudScale(config.playerVisuals.nametagScale);
		boolean through = config.pestEspThroughWalls;
		Font font = client.font;
		for (PestEsp.Mark pest : pests) {
			if (pest.name() == null || pest.name().isEmpty()) {
				continue;
			}
			Vec3 at = new Vec3(pest.center().x, pest.box().maxY + 0.35, pest.center().z);
			Vec3 rel = at.subtract(camera);
			double facing = rel.x * forward.x() + rel.y * forward.y() + rel.z * forward.z();
			if (facing <= 0.12) {
				continue;
			}
			if (!through && occluded(client, camera, at)) {
				continue;
			}
			Vec3 ndc = client.gameRenderer.projectPointToScreen(at);
			if (ndc.x < -1.2 || ndc.x > 1.2 || ndc.y < -1.2 || ndc.y > 1.2) {
				continue;
			}
			float x = (float) ((ndc.x * 0.5 + 0.5) * graphics.guiWidth());
			float y = (float) ((-ndc.y * 0.5 + 0.5) * graphics.guiHeight());
			float scale = NametagRenderer.distanceScale(rel.length()) * userScale;
			Component text = Component.literal(pest.name());
			float width = font.width(text);
			GuiDraw.text(graphics, font, text, x - width * scale * 0.5f, y - font.lineHeight * scale, scale, 0xFFFFFFFF, true);
		}
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
}
