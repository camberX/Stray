package dev.stray.client.dungeon.m4;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.stray.Stray;
import dev.stray.client.dungeon.DungeonParty.DungeonClass;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * BirdAddon's custom M4 waypoints: per class, shown between two moments of the boss
 * ({@code b2spawn}, {@code 30s}, {@code bossstart}, ...). The JSON and the export strings
 * are the same as BirdAddon's, so they can be shared both ways.
 */
public final class M4Waypoints {
	public static final Pattern START_END = Pattern.compile("^b\\d(spawnstart|spawn|kill)$|^(\\d+(\\.\\d+)?|\\.\\d+)s$|^bossstart$|^bossend$");
	public static final String USAGE = "Usage: /m4wp add <class> <start> <end>\n"
		+ "Options for start/end are:\n"
		+ "b<bear number>spawnstart (for when the timer starts counting down)\n"
		+ "b<bear number>spawn\n"
		+ "b<bear number>kill\n"
		+ "<number of seconds in to boss>s\n"
		+ "bossstart\n"
		+ "bossend";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Same shape as BirdAddon's: {@code {"pos":{"x":..,"y":..,"z":..},"clazz":"MAGE","start":..,"end":..}}. */
	static final class Pos {
		int x;
		int y;
		int z;

		Pos(int x, int y, int z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}

	static final class Waypoint {
		Pos pos;
		String clazz;
		String start;
		String end;
	}

	static final List<Waypoint> waypoints = new ArrayList<>();
	private static boolean loaded;

	private M4Waypoints() {
	}

	private static Path file() {
		return M4Stats.file("waypoints.json");
	}

	private static Path birdAddonFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("odin").resolve("addons").resolve("m4waypoints.json");
	}

	public static DungeonClass classFromName(String name) {
		if (name == null) {
			return null;
		}
		return switch (name.trim().toLowerCase(Locale.ROOT)) {
			case "berserk", "bers", "b" -> DungeonClass.BERSERK;
			case "healer", "heal", "h" -> DungeonClass.HEALER;
			case "archer", "arch", "a" -> DungeonClass.ARCHER;
			case "mage", "m" -> DungeonClass.MAGE;
			case "tank", "t" -> DungeonClass.TANK;
			default -> null;
		};
	}

	// ---------------------------------------------------------------- storage

	static void ensureLoaded() {
		if (!loaded) {
			load(false);
		}
	}

	/** Loads the saved waypoints; with {@code fromBirdAddon}, BirdAddon's file instead. */
	public static boolean load(boolean fromBirdAddon) {
		loaded = true;
		Path path = fromBirdAddon ? birdAddonFile() : file();
		if (!fromBirdAddon && !Files.isRegularFile(path)) {
			path = birdAddonFile();
		}
		if (!Files.isRegularFile(path)) {
			return false;
		}
		try {
			List<Waypoint> read = GSON.fromJson(Files.readString(path), new TypeToken<List<Waypoint>>() { }.getType());
			waypoints.clear();
			if (read != null) {
				for (Waypoint waypoint : read) {
					if (waypoint != null && waypoint.pos != null) {
						waypoints.add(waypoint);
					}
				}
			}
			return true;
		} catch (Exception exception) {
			Stray.LOGGER.warn("Could not read M4 waypoints", exception);
			return false;
		}
	}

	private static void save() {
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(waypoints));
		} catch (IOException exception) {
			Stray.LOGGER.warn("Could not save M4 waypoints", exception);
		}
	}

	// ---------------------------------------------------------------- commands

	private static BlockPos lookedAt() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return null;
		}
		HitResult hit = client.player.pick(100.0, 0f, false);
		return hit.getType() == HitResult.Type.BLOCK ? ((BlockHitResult) hit).getBlockPos() : null;
	}

	/** cgm4's island is offset from the real M4 arena. */
	private static BlockPos toArena(BlockPos pos) {
		if (M4.onCgm4) {
			return new BlockPos(pos.getX() - 2, pos.getY() - 41, pos.getZ() - 2);
		}
		if (!M4.inBoss() && !M4.onM4Miku) {
			M4Stats.message("need to be in f4/m4 or on catgirlm4's/m4miku's is");
		}
		return pos;
	}

	public static void add(String clazz, String start, String end) {
		ensureLoaded();
		if (classFromName(clazz) == null) {
			M4Stats.message("invalid class");
			return;
		}
		if (!START_END.matcher(start).matches() || !START_END.matcher(end).matches()) {
			M4Stats.message(USAGE);
			return;
		}
		BlockPos looked = lookedAt();
		if (looked == null) {
			return;
		}
		BlockPos pos = toArena(looked);
		Waypoint waypoint = new Waypoint();
		waypoint.pos = new Pos(pos.getX(), pos.getY(), pos.getZ());
		waypoint.clazz = classFromName(clazz).name();
		waypoint.start = start;
		waypoint.end = end;
		waypoints.add(waypoint);
		save();
		M4Stats.message("Waypoint added at " + looked.getX() + ", " + looked.getY() + ", " + looked.getZ());
	}

	public static void remove() {
		ensureLoaded();
		BlockPos looked = lookedAt();
		if (looked == null) {
			return;
		}
		BlockPos pos = toArena(looked);
		if (waypoints.removeIf(w -> w.pos.x == pos.getX() && w.pos.y == pos.getY() && w.pos.z == pos.getZ())) {
			M4Stats.message("removed waypoint");
			save();
		} else {
			M4Stats.message("no waypoint to remove");
		}
	}

	public static void export() {
		ensureLoaded();
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
				gzip.write(GSON.toJson(waypoints).getBytes(StandardCharsets.UTF_8));
			}
			Minecraft.getInstance().keyboardHandler.setClipboard(Base64.getEncoder().encodeToString(bytes.toByteArray()));
			M4Stats.message("Waypoints exported to clipboard");
		} catch (IOException exception) {
			M4Stats.message("Waypoints failed to export");
		}
	}

	public static void importFromClipboard() {
		String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard().trim();
		try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(clipboard)))) {
			List<Waypoint> read = GSON.fromJson(new String(gzip.readAllBytes(), StandardCharsets.UTF_8), new TypeToken<List<Waypoint>>() { }.getType());
			if (read == null) {
				throw new IOException("empty");
			}
			loaded = true;
			waypoints.clear();
			for (Waypoint waypoint : read) {
				if (waypoint != null && waypoint.pos != null) {
					waypoints.add(waypoint);
				}
			}
			save();
			M4Stats.message("Waypoints imported");
		} catch (Exception exception) {
			M4Stats.message("Waypoints failed to import");
		}
	}

	// ---------------------------------------------------------------- timing

	static boolean shouldRenderNow(String start, String end) {
		return eventPassed(start.toLowerCase(Locale.ROOT)) && !eventPassed(end.toLowerCase(Locale.ROOT));
	}

	private static boolean eventPassed(String event) {
		if (event.startsWith("b")) {
			if (event.startsWith("boss")) {
				return event.endsWith("start");
			}
			if (event.length() < 2 || !Character.isDigit(event.charAt(1))) {
				return false;
			}
			int bear = event.charAt(1) - '0';
			if (event.endsWith("spawnstart")) {
				return M4.spawnStartTimes.size() >= bear;
			}
			if (event.endsWith("kill")) {
				return M4.killTimes.size() >= bear;
			}
			if (event.endsWith("spawn")) {
				return M4.spawnTimes.size() >= bear;
			}
			return false;
		}
		if (event.endsWith("s")) {
			try {
				return M4.timer / 20.0 > Double.parseDouble(event.substring(0, event.length() - 1));
			} catch (NumberFormatException exception) {
				return false;
			}
		}
		return false;
	}
}
