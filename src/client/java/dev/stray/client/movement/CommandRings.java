package dev.stray.client.movement;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.stray.client.config.IslandSaves;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.NametagRenderer;
import dev.stray.client.ui.Anim;
import dev.stray.client.ui.MenuFont;
import dev.stray.client.ui.Theme;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Place a ring at your feet with {@code /stray cmd "warp hub" 2}. Walking
 * into it sends that command once; leaving arms it again. Saved per
 * Skyblock island.
 */
public final class CommandRings {
	private static final Path FILE = IslandSaves.DIR.resolve("command-rings.json");
	private static final int MAX = 32;
	private static final int SEGMENTS = 48;
	private static final int SEGMENTS_FAR = 24;
	private static final double DRAW_RANGE = 96.0;
	private static final double DRAW_RANGE_SQ = DRAW_RANGE * DRAW_RANGE;
	/** Feet must be on the ring's own floor. A block above or below does not count. */
	private static final double SAME_HEIGHT = 0.5;
	private static final double FAR_SQ = 24.0 * 24.0;
	private static final double[] COS = new double[SEGMENTS + 1];
	private static final double[] SIN = new double[SEGMENTS + 1];

	static {
		for (int i = 0; i <= SEGMENTS; i++) {
			double angle = (i % SEGMENTS) * (Math.PI * 2.0 / SEGMENTS);
			COS[i] = Math.cos(angle);
			SIN[i] = Math.sin(angle);
		}
	}
	private static final float TAG_H = 14f;
	private static final float PAD_X = 8f;
	private static final Map<String, List<Ring>> SAVED = new LinkedHashMap<>();
	private static boolean loaded;
	private static boolean dirty;
	private static String lastIsland = "";

	private CommandRings() {
	}

	public static void init() {
		load();
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> emit());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> save());
	}

	public static boolean enabled() {
		return StrayConfig.get().commandRingsEnabled;
	}

	public static boolean visible() {
		StrayConfig config = StrayConfig.get();
		return config.commandRingsEnabled && config.commandRingsRender;
	}

	public static int count() {
		return here().size();
	}

	public static List<Ring> rings() {
		return here();
	}

	public static void clear() {
		String island = IslandSaves.key();
		List<Ring> rings = SAVED.get(island);
		if (rings == null || rings.isEmpty()) {
			return;
		}
		rings.clear();
		SAVED.remove(island);
		touch();
	}

	public static void onWorldChange() {
		lastIsland = "";
	}

	public static String place(String rawCommand, float radius) {
		if (rawCommand == null) {
			return "Need a command.";
		}
		String command = rawCommand.trim();
		if (command.startsWith("/")) {
			command = command.substring(1).trim();
		}
		if (command.isEmpty()) {
			return "Need a command.";
		}
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null) {
			return "Join a world first.";
		}
		StrayConfig config = StrayConfig.get();
		if (!config.commandRingsEnabled) {
			config.commandRingsEnabled = true;
			config.save();
		}
		float size = StrayConfig.clamp(radius, 0.5f, 16f);
		List<Ring> rings = mutableHere();
		if (rings.size() >= MAX) {
			rings.removeFirst();
		}
		Vec3 pos = player.position();
		rings.add(new Ring(pos.x, pos.y, pos.z, size, command, true));
		touch();
		return "Ring " + rings.size() + " on " + IslandSaves.label() + ": /" + command + "  " + format(size) + "m";
	}

	public static boolean remove(int index) {
		List<Ring> rings = mutableHere();
		if (index < 1 || index > rings.size()) {
			return false;
		}
		rings.remove(index - 1);
		dropIslandIfEmpty(rings);
		touch();
		return true;
	}

	public static String removeNearest() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null) {
			return "Join a world first.";
		}
		List<Ring> rings = mutableHere();
		if (rings.isEmpty()) {
			return "No rings on " + IslandSaves.label() + ".";
		}
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		int best = 0;
		double bestDist = Double.MAX_VALUE;
		for (int i = 0; i < rings.size(); i++) {
			Ring ring = rings.get(i);
			double dx = x - ring.x;
			double dy = y - ring.y;
			double dz = z - ring.z;
			double dist = dx * dx + dy * dy + dz * dz;
			if (dist < bestDist) {
				bestDist = dist;
				best = i;
			}
		}
		Ring removed = rings.remove(best);
		dropIslandIfEmpty(rings);
		touch();
		return "Removed nearest ring: /" + removed.command;
	}

	public static void tick(Minecraft client) {
		syncIsland();
		if (dirty) {
			save();
		}
		List<Ring> rings = here();
		if (client.player == null || rings.isEmpty()) {
			return;
		}
		LocalPlayer player = client.player;
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		boolean allow = enabled() && client.level != null && client.screen == null;
		for (Ring ring : rings) {
			double dx = x - ring.x;
			double dz = z - ring.z;
			boolean inColumn = dx * dx + dz * dz <= ring.radius * ring.radius;
			boolean sameHeight = Math.abs(y - ring.y) <= SAME_HEIGHT;
			if (!inColumn) {
				// Left the circle: arm for the next walk-in. Height alone does not re-arm.
				ring.armed = true;
				ring.inside = false;
				continue;
			}
			ring.inside = sameHeight;
			if (!sameHeight) {
				continue;
			}
			if (allow && ring.armed) {
				ring.armed = false;
				run(client, ring);
			} else {
				// Already standing in it (placed here, menu open, or feature off): do not fire.
				ring.armed = false;
			}
		}
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (!visible() || here().isEmpty()) {
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
		int rgb = StrayConfig.get().commandRingsRgb & 0xFFFFFF;
		for (Ring ring : here()) {
			double relX = ring.x - camPos.x;
			double relY = ring.y + 1.15 - camPos.y;
			double relZ = ring.z - camPos.z;
			double distSq = relX * relX + relY * relY + relZ * relZ;
			if (distSq > DRAW_RANGE_SQ) {
				continue;
			}
			double facing = relX * forward.x() + relY * forward.y() + relZ * forward.z();
			if (facing <= 0.12) {
				continue;
			}
			Vec3 head = new Vec3(ring.x, ring.y + 1.15, ring.z);
			Vec3 ndc = client.gameRenderer.projectPointToScreen(head);
			if (ndc.x < -1.2 || ndc.x > 1.2 || ndc.y < -1.2 || ndc.y > 1.2) {
				continue;
			}
			float sx = (float) ((ndc.x * 0.5 + 0.5) * graphics.guiWidth());
			float sy = (float) ((-ndc.y * 0.5 + 0.5) * graphics.guiHeight());
			float scale = NametagRenderer.distanceScale(Math.sqrt(distSq));
			Component name = ring.nameLabel();
			Component meters = ring.metersLabel();
			float nameW = font.width(name);
			float distW = font.width(meters);
			float w = nameW + 5f + distW + PAD_X * 2f;
			graphics.pose().pushMatrix();
			graphics.pose().translate(sx, sy);
			if (scale != 1.0f) {
				graphics.pose().scale(scale, scale);
			}
			float left = -w * 0.5f;
			float top = -2f - TAG_H;
			GuiDraw.panel(graphics, left, top, w, TAG_H, 5, Theme.WINDOW, ring.inside ? Theme.ACCENT : Theme.LINE);
			GuiDraw.text(graphics, font, name, left + PAD_X, GuiDraw.middle(top, TAG_H), 0xFF000000 | rgb, false);
			GuiDraw.text(graphics, font, meters, left + PAD_X + nameW + 5f, GuiDraw.middle(top, TAG_H), Anim.fade(Theme.MUTED, 1f), false);
			graphics.pose().popMatrix();
		}
	}

	private static void emit() {
		if (!visible() || here().isEmpty()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		Camera camera = client.gameRenderer.getMainCamera();
		Vec3 camPos = camera.isInitialized() ? camera.position() : null;
		int rgb = StrayConfig.get().commandRingsRgb & 0xFFFFFF;
		int line = 0xEB000000 | rgb;
		for (Ring ring : here()) {
			int segments = SEGMENTS;
			if (camPos != null) {
				double dx = ring.x - camPos.x;
				double dy = ring.y - camPos.y;
				double dz = ring.z - camPos.z;
				double distSq = dx * dx + dy * dy + dz * dz;
				if (distSq > DRAW_RANGE_SQ) {
					continue;
				}
				if (distSq > FAR_SQ) {
					segments = SEGMENTS_FAR;
				}
			}
			double y = ring.y + 0.04;
			int fill = (Math.round((ring.inside ? 0.38f : 0.22f) * 255f) << 24) | rgb;
			drawDisk(ring, y, fill, segments);
			drawCircle(ring, y, ring.radius, line, 2.6f, segments);
			drawCircle(ring, y, Math.max(0.2, ring.radius * 0.92), 0x66000000 | rgb, 1.4f, segments);
		}
	}

	private static void drawDisk(Ring ring, double y, int fill, int segments) {
		GizmoStyle style = GizmoStyle.fill(fill);
		Vec3 center = new Vec3(ring.x, y, ring.z);
		Vec3 prev = null;
		int step = SEGMENTS / segments;
		for (int i = 0; i <= SEGMENTS; i += step) {
			Vec3 point = new Vec3(ring.x + COS[i] * ring.radius, y, ring.z + SIN[i] * ring.radius);
			if (prev != null) {
				GizmoProperties gizmo = Gizmos.rect(center, prev, point, center, style);
				gizmo.setAlwaysOnTop();
			}
			prev = point;
		}
	}

	private static void drawCircle(Ring ring, double y, double radius, int color, float width, int segments) {
		Vec3 prev = null;
		int step = SEGMENTS / segments;
		for (int i = 0; i <= SEGMENTS; i += step) {
			Vec3 point = new Vec3(ring.x + COS[i] * radius, y, ring.z + SIN[i] * radius);
			if (prev != null) {
				GizmoProperties gizmo = Gizmos.line(prev, point, color, width);
				gizmo.setAlwaysOnTop();
			}
			prev = point;
		}
	}

	private static void run(Minecraft client, Ring ring) {
		if (client.player == null || client.player.connection == null) {
			return;
		}
		try {
			client.player.connection.sendCommand(ring.command);
		} catch (RuntimeException ignored) {
			return;
		}
		client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.4f, 0.6f));
	}

	private static String format(float value) {
		if (Math.abs(value - Math.round(value)) < 0.05f) {
			return Integer.toString(Math.round(value));
		}
		return String.format(Locale.ROOT, "%.1f", value);
	}

	public static final class Ring {
		public final double x;
		public final double y;
		public final double z;
		public final float radius;
		public final String command;
		boolean inside;
		boolean armed;
		private Component nameLabel;
		private Component metersLabel;

		Ring(double x, double y, double z, float radius, String command, boolean placedNow) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.radius = radius;
			this.command = command;
			this.inside = placedNow;
			this.armed = false;
		}

		Component nameLabel() {
			if (nameLabel == null) {
				nameLabel = MenuFont.vanilla("/" + command);
			}
			return nameLabel;
		}

		Component metersLabel() {
			if (metersLabel == null) {
				metersLabel = MenuFont.vanilla(format(radius) + "m");
			}
			return metersLabel;
		}
	}

	private static void dropIslandIfEmpty(List<Ring> rings) {
		if (rings.isEmpty()) {
			SAVED.remove(IslandSaves.key());
		}
	}

	private static List<Ring> here() {
		List<Ring> rings = SAVED.get(IslandSaves.key());
		return rings == null ? List.of() : rings;
	}

	private static List<Ring> mutableHere() {
		return SAVED.computeIfAbsent(IslandSaves.key(), ignored -> new ArrayList<>());
	}

	private static void syncIsland() {
		String island = IslandSaves.key();
		if (island.equals(lastIsland)) {
			return;
		}
		lastIsland = island;
		for (Ring ring : here()) {
			ring.inside = false;
			ring.armed = false;
		}
	}

	private static void touch() {
		dirty = true;
		save();
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		SAVED.clear();
		JsonObject islands = IslandSaves.readIslands(FILE);
		for (String island : islands.keySet()) {
			JsonArray list = islands.getAsJsonArray(island);
			if (list == null) {
				continue;
			}
			List<Ring> rings = new ArrayList<>();
			for (JsonElement element : list) {
				Ring ring = read(element);
				if (ring != null) {
					rings.add(ring);
				}
			}
			if (!rings.isEmpty()) {
				SAVED.put(island, rings);
			}
		}
	}

	private static Ring read(JsonElement element) {
		if (element == null || !element.isJsonObject()) {
			return null;
		}
		JsonObject object = element.getAsJsonObject();
		if (!object.has("x") || !object.has("y") || !object.has("z") || !object.has("command")) {
			return null;
		}
		String command = object.get("command").getAsString().trim();
		if (command.startsWith("/")) {
			command = command.substring(1).trim();
		}
		if (command.isEmpty()) {
			return null;
		}
		float radius = object.has("radius") ? object.get("radius").getAsFloat() : 2f;
		radius = StrayConfig.clamp(radius, 0.5f, 16f);
		return new Ring(
			object.get("x").getAsDouble(),
			object.get("y").getAsDouble(),
			object.get("z").getAsDouble(),
			radius,
			command,
			false
		);
	}

	private static void save() {
		JsonObject islands = new JsonObject();
		for (Map.Entry<String, List<Ring>> entry : SAVED.entrySet()) {
			if (entry.getValue().isEmpty()) {
				continue;
			}
			JsonArray list = new JsonArray();
			for (Ring ring : entry.getValue()) {
				JsonObject object = new JsonObject();
				object.addProperty("x", IslandSaves.coord(ring.x));
				object.addProperty("y", IslandSaves.coord(ring.y));
				object.addProperty("z", IslandSaves.coord(ring.z));
				object.addProperty("radius", IslandSaves.coord(ring.radius));
				object.addProperty("command", ring.command);
				list.add(object);
			}
			islands.add(entry.getKey(), list);
		}
		if (IslandSaves.writeIslands(FILE, islands)) {
			dirty = false;
		}
	}
}
