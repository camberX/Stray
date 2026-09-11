package dev.stray.client.render;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.farming.PestEsp;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

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
}
