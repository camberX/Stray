package dev.stray.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.stray.client.config.IslandSaves;
import dev.stray.client.config.StrayConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Waypoints: boxes you place where you stand. The bottom of the box is at your feet, centred
 * on you; width, height, line thickness and both colours (with opacity) come from the settings
 * when you place it, and "Restyle nearest" applies new settings to one you placed before.
 * Saved per SkyBlock island. Visual only.
 */
public final class BoxWaypoints {
	private static final Path FILE = IslandSaves.DIR.resolve("waypoints.json");
	private static final int MAX = 128;
	private static final Map<String, List<Waypoint>> SAVED = new LinkedHashMap<>();
	private static boolean loaded;

	private BoxWaypoints() {
	}

	public static void init() {
		load();
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> draw());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> save());
	}

	// ------------------------------------------------------------------ actions

	/** Places a waypoint at your feet with the current settings. Returns what happened, for chat. */
	public static String addHere() {
		Player player = Minecraft.getInstance().player;
		if (player == null) {
			return "Not in a world.";
		}
		List<Waypoint> here = mutableHere();
		if (here.size() >= MAX) {
			return "This island has " + MAX + " waypoints already. Remove some first.";
		}
		Vec3 feet = feet(player);
		here.add(styled(feet.x, feet.y, feet.z));
		save();
		return "Waypoint placed at " + coords(feet) + ".";
	}

	/** Gives the nearest waypoint the current size and colours. */
	public static String restyleNearest() {
		int index = nearest();
		if (index < 0) {
			return "No waypoints on this island.";
		}
		List<Waypoint> here = mutableHere();
		Waypoint old = here.get(index);
		here.set(index, styled(old.x, old.y, old.z));
		save();
		return "Restyled the waypoint at " + coords(new Vec3(old.x, old.y, old.z)) + ".";
	}

	public static String removeNearest() {
		int index = nearest();
		if (index < 0) {
			return "No waypoints on this island.";
		}
		Waypoint old = mutableHere().remove(index);
		save();
		return "Removed the waypoint at " + coords(new Vec3(old.x, old.y, old.z)) + ".";
	}

	public static String clear() {
		List<Waypoint> here = SAVED.remove(IslandSaves.key());
		if (here == null || here.isEmpty()) {
			return "No waypoints on this island.";
		}
		save();
		return "Cleared " + here.size() + (here.size() == 1 ? " waypoint." : " waypoints.");
	}

	public static int count() {
		List<Waypoint> here = SAVED.get(IslandSaves.key());
		return here == null ? 0 : here.size();
	}

	// ------------------------------------------------------------------ drawing

	private static void draw() {
		StrayConfig config = StrayConfig.get();
		if (!config.waypoints) {
			return;
		}
		List<Waypoint> here = SAVED.get(IslandSaves.key());
		if (here == null || here.isEmpty()) {
			return;
		}
		// Through walls is full edition only: Stray Legit draws what you could see anyway.
		boolean onTop = config.waypointsThroughWalls && !dev.stray.Edition.LEGIT;
		for (Waypoint point : here) {
			AABB box = new AABB(point.x - point.width / 2.0, point.y, point.z - point.width / 2.0,
				point.x + point.width / 2.0, point.y + point.height, point.z + point.width / 2.0);
			boolean fill = (point.fill >>> 24) != 0;
			boolean line = (point.outline >>> 24) != 0 && point.thickness > 0f;
			if (!fill && !line) {
				continue;
			}
			GizmoStyle style = fill && line ? GizmoStyle.strokeAndFill(point.outline, point.thickness, point.fill)
				: fill ? GizmoStyle.fill(point.fill) : GizmoStyle.stroke(point.outline, point.thickness);
			GizmoProperties cuboid = Gizmos.cuboid(box, style);
			if (onTop) {
				cuboid.setAlwaysOnTop();
			}
		}
	}

	// ------------------------------------------------------------------ helpers

	/** Where your feet are; with "Centre on block", moved sideways to the middle of the block (the height stays). */
	private static Vec3 feet(Player player) {
		Vec3 at = player.position();
		if (!StrayConfig.get().waypointsSnap) {
			return at;
		}
		return new Vec3(Math.floor(at.x) + 0.5, at.y, Math.floor(at.z) + 0.5);
	}

	private static Waypoint styled(double x, double y, double z) {
		StrayConfig config = StrayConfig.get();
		return new Waypoint(x, y, z,
			StrayConfig.clamp(config.waypointWidth, 0.1f, 16f),
			StrayConfig.clamp(config.waypointHeight, 0.1f, 64f),
			StrayConfig.clamp(config.waypointThickness, 0f, 10f),
			config.waypointOutline, config.waypointFill);
	}

	/** The waypoint closest to you on this island, or -1. */
	private static int nearest() {
		Player player = Minecraft.getInstance().player;
		List<Waypoint> here = SAVED.get(IslandSaves.key());
		if (player == null || here == null || here.isEmpty()) {
			return -1;
		}
		int best = -1;
		double bestDist = Double.MAX_VALUE;
		for (int i = 0; i < here.size(); i++) {
			Waypoint point = here.get(i);
			double dist = player.position().distanceToSqr(point.x, point.y, point.z);
			if (dist < bestDist) {
				bestDist = dist;
				best = i;
			}
		}
		return best;
	}

	private static String coords(Vec3 at) {
		return String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", at.x, at.y, at.z);
	}

	private static List<Waypoint> mutableHere() {
		return SAVED.computeIfAbsent(IslandSaves.key(), ignored -> new ArrayList<>());
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		JsonObject islands = IslandSaves.readIslands(FILE);
		for (String island : islands.keySet()) {
			JsonArray list = islands.getAsJsonArray(island);
			if (list == null) {
				continue;
			}
			List<Waypoint> points = new ArrayList<>();
			for (JsonElement element : list) {
				if (element.isJsonObject()) {
					JsonObject o = element.getAsJsonObject();
					if (o.has("x") && o.has("y") && o.has("z")) {
						points.add(new Waypoint(o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble(),
							number(o, "width", 1f), number(o, "height", 2f), number(o, "thickness", 2f),
							o.has("outline") ? o.get("outline").getAsInt() : 0xFFFFFFFF, o.has("fill") ? o.get("fill").getAsInt() : 0x40FFFFFF));
					}
				}
			}
			if (!points.isEmpty()) {
				SAVED.put(island, points);
			}
		}
	}

	private static float number(JsonObject o, String key, float fallback) {
		return o.has(key) ? o.get(key).getAsFloat() : fallback;
	}

	private static void save() {
		JsonObject islands = new JsonObject();
		for (Map.Entry<String, List<Waypoint>> entry : SAVED.entrySet()) {
			if (entry.getValue().isEmpty()) {
				continue;
			}
			JsonArray list = new JsonArray();
			for (Waypoint point : entry.getValue()) {
				JsonObject o = new JsonObject();
				o.addProperty("x", point.x);
				o.addProperty("y", point.y);
				o.addProperty("z", point.z);
				o.addProperty("width", point.width);
				o.addProperty("height", point.height);
				o.addProperty("thickness", point.thickness);
				o.addProperty("outline", point.outline);
				o.addProperty("fill", point.fill);
				list.add(o);
			}
			islands.add(entry.getKey(), list);
		}
		IslandSaves.writeIslands(FILE, islands);
	}

	private record Waypoint(double x, double y, double z, float width, float height, float thickness, int outline, int fill) {
	}
}
