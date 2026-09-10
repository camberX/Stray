package dev.stray.client.mining;

import dev.stray.client.config.StrayConfig;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CrystalHollowsRenderer {
	private CrystalHollowsRenderer() {
	}

	public static void init() {
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> emit());
	}

	private static void emit() {
		if (!CrystalHollows.active()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		boolean through = StrayConfig.get().crystalHollowsThroughWalls;
		Vec3 camera = client.gameRenderer.getMainCamera().position();
		for (CrystalHollows.Mark mark : CrystalHollows.marks()) {
			int rgb = mark.rgb() & 0xFFFFFF;
			int line = 0xEB000000 | rgb;
			int fill = 0x55000000 | rgb;
			Vec3 center = Vec3.atCenterOf(mark.pos());
			if (!mark.nucleus()) {
				GizmoStyle box = GizmoStyle.strokeAndFill(line, 2.0f, fill);
				AABB aabb = new AABB(mark.pos()).inflate(0.12);
				GizmoProperties cuboid = Gizmos.cuboid(aabb, box);
				if (through) {
					cuboid.setAlwaysOnTop();
				}
			}
			int meters = (int) Math.round(center.distanceTo(camera));
			String label = mark.label() + "  " + meters + "m";
			GizmoProperties text = Gizmos.billboardText(
				label,
				center.add(0, mark.nucleus() ? 5 : 1.4, 0),
				TextGizmo.Style.forColorAndCentered(0xFF000000 | rgb).withScale(mark.nucleus() ? 0.34f : 0.24f)
			);
			text.setAlwaysOnTop();
		}
	}
}
