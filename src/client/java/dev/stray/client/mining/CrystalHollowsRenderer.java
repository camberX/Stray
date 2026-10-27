package dev.stray.client.mining;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.NametagRenderer;
import dev.stray.client.ui.Anim;
import dev.stray.client.ui.MenuFont;
import dev.stray.client.ui.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.List;

public final class CrystalHollowsRenderer {
	private static final float TAG_H = 14f;
	private static final float PAD_X = 8f;

	private CrystalHollowsRenderer() {
	}

	public static void init() {
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> emitBoxes());
		CrystalHollowsScanner.init();
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		boolean hollows = CrystalHollows.active();
		List<CrystalHollows.Mark> route = RouteMiner.marks();
		if (!hollows && route.isEmpty()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.gui.hud.isHidden()) {
			return;
		}
		Camera camera = client.gameRenderer.mainCamera();
		if (!camera.isInitialized()) {
			return;
		}
		Vec3 camPos = camera.position();
		Vector3fc forward = camera.forwardVector();
		Font font = client.font;
		if (hollows) {
			for (CrystalHollows.Mark mark : CrystalHollows.marks()) {
				drawTag(graphics, client, font, camPos, forward, mark);
			}
		}
		for (CrystalHollows.Mark mark : route) {
			drawTag(graphics, client, font, camPos, forward, mark);
		}
	}

	private static void drawTag(
		GuiGraphicsExtractor graphics,
		Minecraft client,
		Font font,
		Vec3 camPos,
		Vector3fc forward,
		CrystalHollows.Mark mark
	) {
		Vec3 head = Vec3.atCenterOf(mark.pos()).add(0, mark.nucleus() ? 5 : 1.6, 0);
		Vec3 rel = head.subtract(camPos);
		double facing = rel.x * forward.x() + rel.y * forward.y() + rel.z * forward.z();
		if (facing <= 0.12) {
			return;
		}
		Vec3 ndc = client.gameRenderer.projectPointToScreen(head);
		if (ndc.x < -1.2 || ndc.x > 1.2 || ndc.y < -1.2 || ndc.y > 1.2) {
			return;
		}
		float x = (float) ((ndc.x * 0.5 + 0.5) * graphics.guiWidth());
		float y = (float) ((-ndc.y * 0.5 + 0.5) * graphics.guiHeight());
		double dist = head.distanceTo(camPos);
		float scale = NametagRenderer.distanceScale(dist);
		Component name = MenuFont.vanilla(mark.label());
		Component meters = MenuFont.vanilla(GuiDraw.meters(dist));
		float nameW = font.width(name);
		float distW = font.width(meters);
		float inner = nameW + 5f + distW;
		float w = inner + PAD_X * 2f;
		int rgb = mark.rgb() & 0xFFFFFF;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		if (scale != 1.0f) {
			graphics.pose().scale(scale, scale);
		}
		float left = -w * 0.5f;
		float top = -2f - TAG_H;
		GuiDraw.panel(graphics, left, top, w, TAG_H, 5, Theme.WINDOW, Theme.LINE);
		GuiDraw.text(graphics, font, name, left + PAD_X, GuiDraw.middle(top, TAG_H), 0xFF000000 | rgb, false);
		GuiDraw.text(graphics, font, meters, left + PAD_X + nameW + 5f, GuiDraw.middle(top, TAG_H), Anim.fade(Theme.MUTED, 1f), false);
		graphics.pose().popMatrix();
	}

	private static void emitBoxes() {
		MetalDetector.emitBoxes();
		boolean through = StrayConfig.get().crystalHollowsThroughWalls;
		if (CrystalHollows.active()) {
			for (CrystalHollows.Mark mark : CrystalHollows.marks()) {
				if (mark.nucleus()) {
					continue;
				}
				emitBox(mark, through);
			}
		}
		for (CrystalHollows.Mark mark : RouteMiner.marks()) {
			emitBox(mark, through);
		}
	}

	private static void emitBox(CrystalHollows.Mark mark, boolean through) {
		int rgb = mark.rgb() & 0xFFFFFF;
		int line = 0xEB000000 | rgb;
		int fill = 0x55000000 | rgb;
		GizmoProperties cuboid = Gizmos.cuboid(new AABB(mark.pos()).inflate(0.2), GizmoStyle.strokeAndFill(line, 2.4f, fill));
		if (through) {
			cuboid.setAlwaysOnTop();
		}
	}
}
