package dev.stray.client.net;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.ui.MenuFont;
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
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Pings from the Stray live websocket. Same room: Hypixel lobby, multiplayer server, or singleplayer world. */
public final class LobbyPings {
	private static final long LIFE_MS = 30_000L;
	private static final int MAX = 32;
	private static final double LOOK_RANGE = 96.0;
	private static final double AIM_NDC = 0.05;
	private static final int YELLOW = 0xF5C400;
	private static final List<Ping> PINGS = new ArrayList<>();

	private LobbyPings() {
	}

	public static void init() {
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> emitMarkers());
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

	public static void accept(String name, double x, double y, double z, String label) {
		if (!StrayConfig.get().strayPingEnabled || name == null || name.isBlank()) {
			return;
		}
		Vec3 pos = new Vec3(x, y, z);
		long now = System.currentTimeMillis();
		synchronized (PINGS) {
			PINGS.removeIf(ping -> ping.name.equalsIgnoreCase(name));
			if (PINGS.size() >= MAX) {
				PINGS.removeFirst();
			}
			PINGS.add(new Ping(name, pos, label == null ? "" : label, now));
		}
	}

	public static boolean remove(String name) {
		if (name == null || name.isBlank()) {
			return false;
		}
		synchronized (PINGS) {
			return PINGS.removeIf(ping -> ping.name.equalsIgnoreCase(name));
		}
	}

	/** True when the crosshair is on this player's ping marker. */
	public static boolean aimingAtOwn(String name) {
		if (name == null || name.isBlank()) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		Ping match;
		synchronized (PINGS) {
			match = null;
			for (Ping ping : PINGS) {
				if (ping.name.equalsIgnoreCase(name)) {
					match = ping;
					break;
				}
			}
		}
		return match != null && aimsAt(client, match);
	}

	/** Exact look-at point on a block face, entity, or far clip — never the player's feet. */
	public static Vec3 lookHit() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			return null;
		}
		Vec3 eye = client.player.getEyePosition();
		Vec3 look = client.player.getLookAngle();
		Vec3 end = eye.add(look.scale(LOOK_RANGE));
		BlockHitResult block = client.level.clip(new ClipContext(
			eye,
			end,
			ClipContext.Block.OUTLINE,
			ClipContext.Fluid.NONE,
			client.player
		));
		double blockDist = block.getLocation().distanceToSqr(eye);
		HitResult vanilla = client.hitResult;
		if (vanilla instanceof EntityHitResult entity && entity.getEntity() != null) {
			Vec3 at = entity.getLocation();
			double entityDist = at.distanceToSqr(eye);
			if (entityDist <= LOOK_RANGE * LOOK_RANGE && entityDist < blockDist) {
				return at;
			}
		}
		Vec3 at = block.getLocation();
		Vec3 toEye = eye.subtract(at);
		double len = toEye.length();
		if (len > 1.0E-4) {
			at = at.add(toEye.scale(0.04 / len));
		}
		return at;
	}

	private static boolean aimsAt(Minecraft client, Ping ping) {
		if (client.player == null || client.level == null) {
			return false;
		}
		Camera camera = client.gameRenderer.getMainCamera();
		if (!camera.isInitialized()) {
			return false;
		}
		Vec3 origin = camera.position();
		Vector3fc forward = camera.forwardVector();
		Vec3 end = origin.add(forward.x() * LOOK_RANGE, forward.y() * LOOK_RANGE, forward.z() * LOOK_RANGE);
		double dist = Math.max(1.0, ping.pos.distanceTo(origin));
		double pad = Math.max(0.35, dist * 0.018);
		AABB box = new AABB(ping.pos, ping.pos).inflate(pad);
		if (hits(box, origin, end)) {
			return true;
		}
		return nearCrosshair(client, origin, forward, ping.pos);
	}

	private static boolean hits(AABB box, Vec3 origin, Vec3 end) {
		if (box.contains(origin)) {
			return true;
		}
		Optional<Vec3> clip = box.clip(origin, end);
		return clip.isPresent();
	}

	private static boolean nearCrosshair(Minecraft client, Vec3 origin, Vector3fc forward, Vec3 point) {
		Vec3 rel = point.subtract(origin);
		double facing = rel.x * forward.x() + rel.y * forward.y() + rel.z * forward.z();
		if (facing <= 0.12) {
			return false;
		}
		Vec3 ndc = client.gameRenderer.projectPointToScreen(point);
		return Math.abs(ndc.x) <= AIM_NDC && Math.abs(ndc.y) <= AIM_NDC;
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
		String self = client.player.getGameProfile().name();
		List<Ping> snapshot;
		synchronized (PINGS) {
			snapshot = List.copyOf(PINGS);
		}
		for (Ping ping : snapshot) {
			Vec3 rel = ping.pos.subtract(camPos);
			double facing = rel.x * forward.x() + rel.y * forward.y() + rel.z * forward.z();
			if (facing <= 0.08) {
				continue;
			}
			Vec3 ndc = client.gameRenderer.projectPointToScreen(ping.pos);
			if (ndc.x < -1.25 || ndc.x > 1.25 || ndc.y < -1.25 || ndc.y > 1.25) {
				continue;
			}
			float x = (float) ((ndc.x * 0.5 + 0.5) * graphics.guiWidth());
			float y = (float) ((-ndc.y * 0.5 + 0.5) * graphics.guiHeight());
			boolean hot = aimsAt(client, ping);
			int rgb = hot ? 0xFFFFFF : YELLOW;
			int fill = 0xFF000000 | rgb;
			int ink = 0xFF140E04;
			drawDiamond(graphics, x, y, hot ? 8.4f : 7.2f, ink);
			drawDiamond(graphics, x, y, hot ? 6.2f : 5.2f, fill);
			GuiDraw.stroke(graphics, x - 9f, y + 7f, x, y + 17f, hot ? 2.6f : 2.2f, fill);
			GuiDraw.stroke(graphics, x + 9f, y + 7f, x, y + 17f, hot ? 2.6f : 2.2f, fill);
			double dist = ping.pos.distanceTo(camPos);
			Component meters = MenuFont.vanilla(GuiDraw.meters(dist));
			GuiDraw.text(graphics, font, meters, x + 12f, y - 5f, fill, true);
			String title = ping.label.isEmpty() ? ping.name : ping.name + " · " + ping.label;
			if (self != null && ping.name.equalsIgnoreCase(self) && ping.label.isEmpty()) {
				title = hot ? "Remove" : ping.name;
			}
			Component name = MenuFont.vanilla(title);
			float nameW = font.width(name);
			GuiDraw.text(graphics, font, name, x - nameW * 0.5f, y - 20f, fill, true);
		}
	}

	private static void drawDiamond(GuiGraphicsExtractor graphics, float x, float y, float radius, int color) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().rotate((float) (Math.PI * 0.25));
		GuiDraw.fillSmooth(graphics, -radius, -radius, radius * 2f, radius * 2f, color);
		graphics.pose().popMatrix();
	}

	private static void emitMarkers() {
		if (!StrayConfig.get().strayPingEnabled) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		Camera camera = client.gameRenderer.getMainCamera();
		if (!camera.isInitialized()) {
			return;
		}
		Vec3 origin = camera.position();
		Vector3fc fwd = camera.forwardVector();
		Vector3fc upv = camera.upVector();
		Vector3f right = new Vector3f(upv).cross(fwd.x(), fwd.y(), fwd.z());
		if (right.lengthSquared() < 1.0E-6f) {
			right.set(1f, 0f, 0f);
		} else {
			right.normalize();
		}
		Vec3 r = new Vec3(right.x, right.y, right.z);
		Vec3 u = new Vec3(upv.x(), upv.y(), upv.z());
		List<Ping> snapshot;
		synchronized (PINGS) {
			snapshot = List.copyOf(PINGS);
		}
		for (Ping ping : snapshot) {
			boolean hot = aimsAt(client, ping);
			int rgb = hot ? 0xFFFFFF : YELLOW;
			int line = 0xF2000000 | rgb;
			int fill = 0x99000000 | rgb;
			double dist = Math.max(1.0, ping.pos.distanceTo(origin));
			double s = Mth.clamp(dist * 0.02, 0.10, 0.38);
			Vec3 p = ping.pos;
			Vec3 top = p.add(u.scale(s));
			Vec3 bot = p.add(u.scale(-s));
			Vec3 left = p.add(r.scale(-s));
			Vec3 rightP = p.add(r.scale(s));
			GizmoProperties diamond = Gizmos.rect(top, rightP, bot, left, GizmoStyle.fill(fill));
			diamond.setAlwaysOnTop();
			worldLine(top, rightP, line, 2.6f);
			worldLine(rightP, bot, line, 2.6f);
			worldLine(bot, left, line, 2.6f);
			worldLine(left, top, line, 2.6f);
			Vec3 vL = p.add(u.scale(-s * 0.4)).add(r.scale(-s * 0.9));
			Vec3 vR = p.add(u.scale(-s * 0.4)).add(r.scale(s * 0.9));
			Vec3 vTip = p.add(u.scale(-s * 1.9));
			worldLine(vL, vTip, line, 2.6f);
			worldLine(vR, vTip, line, 2.6f);
		}
	}

	private static void worldLine(Vec3 a, Vec3 b, int color, float width) {
		GizmoProperties line = Gizmos.line(a, b, color, width);
		line.setAlwaysOnTop();
	}

	private record Ping(String name, Vec3 pos, String label, long at) {
	}
}
