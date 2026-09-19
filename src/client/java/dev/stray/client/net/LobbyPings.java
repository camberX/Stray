package dev.stray.client.net;

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
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;

/** Pings from the Stray live websocket. Same room: Hypixel lobby, multiplayer server, or singleplayer world. */
public final class LobbyPings {
	private static final long LIFE_MS = 45_000L;
	private static final int MAX = 32;
	private static final float TAG_H = 14f;
	private static final float PAD_X = 8f;
	private static final List<Ping> PINGS = new ArrayList<>();

	private LobbyPings() {
	}

	public static void init() {
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> emitBoxes());
	}

	public static void tick() {
		long now = System.currentTimeMillis();
		synchronized (PINGS) {
			PINGS.removeIf(ping -> now - ping.at > LIFE_MS);
		}
	}

	public static void clear() {
		synchronized (PINGS) {
			PINGS.clear();
		}
	}

	public static void accept(String name, int x, int y, int z, String label) {
		if (!StrayConfig.get().strayPingEnabled || name == null || name.isBlank()) {
			return;
		}
		BlockPos pos = new BlockPos(x, y, z);
		long now = System.currentTimeMillis();
		synchronized (PINGS) {
			PINGS.removeIf(ping -> ping.name.equalsIgnoreCase(name));
			if (PINGS.size() >= MAX) {
				PINGS.removeFirst();
			}
			PINGS.add(new Ping(name, pos, label == null ? "" : label, now));
		}
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (!StrayConfig.get().strayPingEnabled) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) {
			return;
		}
		Camera camera = client.gameRenderer.getMainCamera();
		if (!camera.isInitialized()) {
			return;
		}
		Vec3 camPos = camera.position();
		Vector3fc forward = camera.forwardVector();
		Font font = client.font;
		int rgb = Theme.ACCENT & 0xFFFFFF;
		List<Ping> snapshot;
		synchronized (PINGS) {
			snapshot = List.copyOf(PINGS);
		}
		for (Ping ping : snapshot) {
			Vec3 head = Vec3.atCenterOf(ping.pos).add(0, 1.6, 0);
			Vec3 rel = head.subtract(camPos);
			double facing = rel.x * forward.x() + rel.y * forward.y() + rel.z * forward.z();
			if (facing <= 0.12) {
				continue;
			}
			Vec3 ndc = client.gameRenderer.projectPointToScreen(head);
			if (ndc.x < -1.2 || ndc.x > 1.2 || ndc.y < -1.2 || ndc.y > 1.2) {
				continue;
			}
			float x = (float) ((ndc.x * 0.5 + 0.5) * graphics.guiWidth());
			float y = (float) ((-ndc.y * 0.5 + 0.5) * graphics.guiHeight());
			double dist = head.distanceTo(camPos);
			float scale = NametagRenderer.distanceScale(dist);
			String title = ping.label.isEmpty() ? ping.name : ping.name + " · " + ping.label;
			Component name = MenuFont.vanilla(title);
			Component meters = MenuFont.vanilla(GuiDraw.meters(dist));
			float nameW = font.width(name);
			float distW = font.width(meters);
			float w = nameW + 5f + distW + PAD_X * 2f;
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
	}

	private static void emitBoxes() {
		if (!StrayConfig.get().strayPingEnabled) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		int rgb = Theme.ACCENT & 0xFFFFFF;
		int line = 0xEB000000 | rgb;
		int fill = 0x55000000 | rgb;
		Camera camera = client.gameRenderer.getMainCamera();
		Vec3 start = null;
		if (camera.isInitialized()) {
			Vector3fc f = camera.forwardVector();
			Vector3f up = new Vector3f(camera.upVector());
			Vec3 cam = camera.position();
			start = cam.add(f.x() * 0.9, f.y() * 0.9, f.z() * 0.9).subtract(up.x * 0.28, up.y * 0.28, up.z * 0.28);
		}
		List<Ping> snapshot;
		synchronized (PINGS) {
			snapshot = List.copyOf(PINGS);
		}
		for (Ping ping : snapshot) {
			GizmoProperties cuboid = Gizmos.cuboid(new AABB(ping.pos).inflate(0.08), GizmoStyle.strokeAndFill(line, 2.6f, fill));
			cuboid.setAlwaysOnTop();
			Vec3 bottom = Vec3.atCenterOf(ping.pos);
			GizmoProperties beam = Gizmos.line(bottom, bottom.add(0, 8, 0), line, 2.4f);
			beam.setAlwaysOnTop();
			if (start != null) {
				GizmoProperties tracer = Gizmos.line(start, bottom, 0xB0000000 | rgb, 1.8f);
				tracer.setAlwaysOnTop();
			}
		}
	}

	private record Ping(String name, BlockPos pos, String label, long at) {
	}
}
