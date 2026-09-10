package dev.stray.client.fairy;

import dev.stray.client.config.StrayConfig;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class FairySoulRenderer {
	private FairySoulRenderer() {
	}

	public static void init() {
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> emit());
	}

	private static void emit() {
		if (!FairySoulTracker.active()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		List<FairySouls.Soul> souls = FairySoulTracker.visible();
		if (souls.isEmpty()) {
			return;
		}
		StrayConfig config = StrayConfig.get();
		int rgb = config.fairySoulRgb & 0xFFFFFF;
		int fill = (Math.round(0.34f * 255f) << 24) | rgb;
		int line = (Math.round(0.92f * 255f) << 24) | rgb;
		GizmoStyle box = GizmoStyle.strokeAndFill(line, 2.0f, fill);
		boolean through = config.fairySoulThroughWalls;
		for (FairySouls.Soul soul : souls) {
			GizmoProperties cuboid = Gizmos.cuboid(new AABB(soul.x(), soul.y(), soul.z(), soul.x() + 1, soul.y() + 1, soul.z() + 1), box);
			if (through) {
				cuboid.setAlwaysOnTop();
			}
		}
		List<Vec3> path = FairySoulTracker.path();
		if (!path.isEmpty()) {
			Vec3 feet = client.player.position().add(0, 0.12, 0);
			GizmoProperties first = Gizmos.line(feet, path.getFirst(), line, 2.6f);
			first.setAlwaysOnTop();
			for (int i = 1; i < path.size(); i++) {
				GizmoProperties segment = Gizmos.line(path.get(i - 1), path.get(i), line, 2.6f);
				segment.setAlwaysOnTop();
			}
		}
	}
}
