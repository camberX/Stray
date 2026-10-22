package dev.stray.client.movement;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stray.Stray;
import dev.stray.client.StrayClient;
import dev.stray.client.combat.OdinClicks;
import dev.stray.client.config.IslandSaves;
import dev.stray.client.config.StrayConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Records where you walk as a polyline and draws it in the world. Recordings
 * are saved per island (Skyblock area) so Crystal Hollows routes never show
 * up in the Hub. Toggle recording with the Record key or /stray path.
 */
public final class PathRecorder {
	private static final Gson GSON = new GsonBuilder().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("stray").resolve("paths.json");
	private static final double MIN_STEP_SQ = 0.35 * 0.35;
	private static final int MAX_POINTS = 24_000;
	private static final double DRAW_RANGE_SQ = 160.0 * 160.0;

	/** island key -> recordings in save order. */
	private static final Map<String, List<Recording>> SAVED = new LinkedHashMap<>();
	private static Recording live;
	private static boolean recordWasDown;
	private static boolean loaded;
	private static boolean dirty;

	private PathRecorder() {
	}

	public static void init() {
		load();
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> emit());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			if (live != null) {
				stop(false);
			}
			save();
		});
	}

	public static boolean recording() {
		return live != null;
	}

	public static Recording liveRecording() {
		return live;
	}

	public static String island() {
		return IslandSaves.key();
	}

	public static String islandLabel() {
		return IslandSaves.label();
	}

	public static List<Recording> here() {
		return SAVED.getOrDefault(island(), List.of());
	}

	public static int savedCount() {
		int n = 0;
		for (List<Recording> list : SAVED.values()) {
			n += list.size();
		}
		return n;
	}

	public static void tick(Minecraft client) {
		StrayConfig config = StrayConfig.get();
		if (client == null || client.player == null || client.level == null) {
			recordWasDown = false;
			return;
		}
		boolean down = StrayClient.strayHotkeys(client) && OdinClicks.isPressed(OdinClicks.parseKey(config.pathRecordKey));
		if (down && !recordWasDown) {
			toggle();
		}
		recordWasDown = down;
		if (live == null) {
			if (dirty) {
				save();
			}
			return;
		}
		if (!live.island.equals(island())) {
			stop(true);
			return;
		}
		Vec3 feet = client.player.position();
		if (live.points.isEmpty() || live.points.getLast().distanceToSqr(feet) >= MIN_STEP_SQ) {
			live.points.add(feet);
			if (live.points.size() > MAX_POINTS) {
				stop(true);
			}
		}
	}

	public static void toggle() {
		if (live != null) {
			stop(true);
		} else {
			start();
		}
	}

	public static void start() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		String key = island();
		int n = SAVED.getOrDefault(key, List.of()).size() + 1;
		live = new Recording(nextName(key, "Path " + n), key, new ArrayList<>(), true);
		live.points.add(client.player.position());
		client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.3f, 0.7f));
		chat("Recording " + live.name + " on " + islandLabel() + ".", ChatFormatting.GREEN);
	}

	public static void stop(boolean announce) {
		Recording done = live;
		live = null;
		if (done == null) {
			return;
		}
		if (done.points.size() < 2) {
			if (announce) {
				chat("Nothing recorded.", ChatFormatting.GRAY);
			}
			return;
		}
		SAVED.computeIfAbsent(done.island, ignored -> new ArrayList<>()).add(done);
		dirty = true;
		save();
		if (announce) {
			Minecraft client = Minecraft.getInstance();
			client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 0.8f, 0.7f));
			chat("Saved " + done.name + " (" + done.points.size() + " points, " + Math.round(length(done)) + "m).", ChatFormatting.YELLOW);
		}
	}

	public static boolean rename(String from, String to) {
		Recording target = find(from);
		if (target == null || to == null || to.isBlank()) {
			return false;
		}
		target.name = nextName(target.island, to.trim());
		dirty = true;
		save();
		return true;
	}

	public static boolean delete(String name) {
		Recording target = find(name);
		if (target == null) {
			return false;
		}
		List<Recording> list = SAVED.get(target.island);
		if (list != null) {
			list.remove(target);
			if (list.isEmpty()) {
				SAVED.remove(target.island);
			}
		}
		dirty = true;
		save();
		return true;
	}

	public static void delete(Recording recording) {
		if (recording != null) {
			delete(recording.name);
		}
	}

	public static void setVisible(Recording recording, boolean visible) {
		if (recording == null) {
			return;
		}
		recording.visible = visible;
		dirty = true;
	}

	public static boolean toggleVisible(String name) {
		Recording target = find(name);
		if (target == null) {
			return false;
		}
		target.visible = !target.visible;
		dirty = true;
		save();
		return true;
	}

	public static void showAll(boolean visible) {
		for (Recording recording : here()) {
			recording.visible = visible;
		}
		dirty = true;
		save();
	}

	public static Recording find(String name) {
		if (name == null) {
			return null;
		}
		String needle = name.trim().toLowerCase(Locale.ROOT);
		for (Recording recording : here()) {
			if (recording.name.toLowerCase(Locale.ROOT).equals(needle)) {
				return recording;
			}
		}
		for (List<Recording> list : SAVED.values()) {
			for (Recording recording : list) {
				if (recording.name.toLowerCase(Locale.ROOT).equals(needle)) {
					return recording;
				}
			}
		}
		return null;
	}

	public static double length(Recording recording) {
		double total = 0;
		List<Vec3> pts = recording.points;
		for (int i = 1; i < pts.size(); i++) {
			total += pts.get(i - 1).distanceTo(pts.get(i));
		}
		return total;
	}

	private static String nextName(String island, String wanted) {
		String base = wanted.isBlank() ? "Path" : wanted.trim();
		List<Recording> list = SAVED.getOrDefault(island, List.of());
		String candidate = base;
		int n = 2;
		outer:
		while (true) {
			for (Recording recording : list) {
				if (recording.name.equalsIgnoreCase(candidate)) {
					candidate = base + " " + n++;
					continue outer;
				}
			}
			return candidate;
		}
	}

	private static void emit() {
		StrayConfig config = StrayConfig.get();
		if (!config.pathsEnabled) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			return;
		}
		Camera camera = client.gameRenderer.getMainCamera();
		Vec3 cam = camera.isInitialized() ? camera.position() : client.player.position();
		int rgb = config.pathRgb & 0xFFFFFF;
		int savedColor = 0xE0000000 | rgb;
		int liveColor = 0xF0000000 | (0xFFFFFF & brighten(rgb));
		for (Recording recording : here()) {
			if (recording.visible) {
				draw(recording, savedColor, config.pathWidth, config.pathThroughWalls, cam);
			}
		}
		if (live != null) {
			draw(live, liveColor, config.pathWidth + 0.6f, config.pathThroughWalls, cam);
		}
	}

	private static void draw(Recording recording, int color, float width, boolean through, Vec3 cam) {
		List<Vec3> pts = recording.points;
		Vec3 prev = null;
		for (Vec3 raw : pts) {
			Vec3 point = raw.add(0, 0.08, 0);
			if (prev != null) {
				if (prev.distanceToSqr(cam) <= DRAW_RANGE_SQ || point.distanceToSqr(cam) <= DRAW_RANGE_SQ) {
					GizmoProperties line = Gizmos.line(prev, point, color, width);
					if (through) {
						line.setAlwaysOnTop();
					}
				}
			}
			prev = point;
		}
	}

	private static int brighten(int rgb) {
		int r = Math.min(255, ((rgb >> 16) & 0xFF) + 70);
		int g = Math.min(255, ((rgb >> 8) & 0xFF) + 70);
		int b = Math.min(255, (rgb & 0xFF) + 70);
		return (r << 16) | (g << 8) | b;
	}

	private static void chat(String text, ChatFormatting color) {
		Minecraft client = Minecraft.getInstance();
		if (client.gui == null) {
			return;
		}
		client.gui.getChat().addClientSystemMessage(
			Component.literal("Stray path ").withStyle(ChatFormatting.AQUA)
				.append(Component.literal(text).withStyle(color))
		);
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		SAVED.clear();
		if (!Files.isRegularFile(FILE)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(FILE)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			JsonObject islands = root.getAsJsonObject("islands");
			if (islands == null) {
				return;
			}
			for (String island : islands.keySet()) {
				JsonArray list = islands.getAsJsonArray(island);
				if (list == null) {
					continue;
				}
				List<Recording> out = new ArrayList<>();
				for (JsonElement element : list) {
					Recording recording = read(island, element);
					if (recording != null) {
						out.add(recording);
					}
				}
				if (!out.isEmpty()) {
					SAVED.put(island, out);
				}
			}
		} catch (Exception exception) {
			Stray.LOGGER.warn("Could not read paths.json", exception);
		}
	}

	private static Recording read(String island, JsonElement element) {
		if (element == null || !element.isJsonObject()) {
			return null;
		}
		JsonObject object = element.getAsJsonObject();
		String name = object.has("name") ? object.get("name").getAsString() : "Path";
		boolean visible = !object.has("visible") || object.get("visible").getAsBoolean();
		List<Vec3> points = new ArrayList<>();
		JsonArray raw = object.getAsJsonArray("points");
		if (raw != null) {
			for (JsonElement p : raw) {
				if (p.isJsonArray() && p.getAsJsonArray().size() >= 3) {
					JsonArray a = p.getAsJsonArray();
					points.add(new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()));
				}
			}
		}
		if (points.size() < 2) {
			return null;
		}
		return new Recording(name, island, points, visible);
	}

	private static void save() {
		JsonObject root = new JsonObject();
		JsonObject islands = new JsonObject();
		for (Map.Entry<String, List<Recording>> entry : SAVED.entrySet()) {
			JsonArray list = new JsonArray();
			for (Recording recording : entry.getValue()) {
				JsonObject object = new JsonObject();
				object.addProperty("name", recording.name);
				object.addProperty("visible", recording.visible);
				JsonArray points = new JsonArray();
				for (Vec3 p : recording.points) {
					JsonArray a = new JsonArray();
					a.add(round(p.x));
					a.add(round(p.y));
					a.add(round(p.z));
					points.add(a);
				}
				object.add("points", points);
				list.add(object);
			}
			islands.add(entry.getKey(), list);
		}
		root.add("islands", islands);
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(root, writer);
			}
			dirty = false;
		} catch (IOException exception) {
			Stray.LOGGER.warn("Could not write paths.json", exception);
		}
	}

	private static double round(double v) {
		return Math.round(v * 100.0) / 100.0;
	}

	public static final class Recording {
		public String name;
		public final String island;
		public final List<Vec3> points;
		public boolean visible;

		Recording(String name, String island, List<Vec3> points, boolean visible) {
			this.name = name;
			this.island = island;
			this.points = points;
			this.visible = visible;
		}

		public int size() {
			return points.size();
		}
	}
}
