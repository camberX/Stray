package dev.stray.client.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Material You colors for the Hyprland click GUI. On a machine running the
 * illogical-impulse dotfiles this is the wallpaper palette quickshell writes to
 * {@code ~/.local/state/quickshell/user/generated/colors.json}, re-read when it
 * changes. Anywhere else the roles are built from the Stray accent.
 */
final class MaterialPalette {
	private static final long CHECK_MS = 2000L;
	private static final Path FILE = Paths.get(System.getProperty("user.home", ""), ".local", "state", "quickshell", "user", "generated", "colors.json");

	static int surface;
	static int surfaceLow;
	static int surfaceContainer;
	static int surfaceHigh;
	static int surfaceHighest;
	static int onSurface;
	static int onSurfaceVariant;
	static int outline;
	static int outlineVariant;
	static int primary;
	static int onPrimary;
	static int secondaryContainer;
	static int onSecondaryContainer;
	/** True while the colors come from the wallpaper file, not the accent. */
	static boolean wallpaper;

	private static long checkedAt;
	private static long fileStamp = Long.MIN_VALUE;
	private static int accentSeed = -1;

	private MaterialPalette() {
	}

	static void refresh() {
		long now = System.currentTimeMillis();
		if (now - checkedAt < CHECK_MS && (wallpaper || accentSeed == Theme.ACCENT)) {
			return;
		}
		checkedAt = now;
		long stamp = stamp();
		if (stamp != Long.MIN_VALUE) {
			if (stamp != fileStamp || !wallpaper) {
				fileStamp = stamp;
				wallpaper = load();
			}
			if (wallpaper) {
				return;
			}
		} else {
			wallpaper = false;
		}
		if (accentSeed != Theme.ACCENT) {
			derive(Theme.ACCENT);
		}
	}

	private static long stamp() {
		try {
			return Files.isRegularFile(FILE) ? Files.getLastModifiedTime(FILE).toMillis() : Long.MIN_VALUE;
		} catch (Exception ignored) {
			return Long.MIN_VALUE;
		}
	}

	private static boolean load() {
		try {
			JsonObject json = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
			surface = color(json, "surface");
			surfaceLow = color(json, "surface_container_low");
			surfaceContainer = color(json, "surface_container");
			surfaceHigh = color(json, "surface_container_high");
			surfaceHighest = color(json, "surface_container_highest");
			onSurface = color(json, "on_surface");
			onSurfaceVariant = color(json, "on_surface_variant");
			outline = color(json, "outline");
			outlineVariant = color(json, "outline_variant");
			primary = color(json, "primary");
			onPrimary = color(json, "on_primary");
			secondaryContainer = color(json, "secondary_container");
			onSecondaryContainer = color(json, "on_secondary_container");
			accentSeed = -1;
			return true;
		} catch (Exception ignored) {
			return false;
		}
	}

	private static int color(JsonObject json, String key) {
		JsonElement value = json.get(key);
		if (value == null) {
			throw new IllegalStateException(key);
		}
		String hex = value.getAsString().trim();
		if (hex.startsWith("#")) {
			hex = hex.substring(1);
		}
		return 0xFF000000 | (Integer.parseInt(hex.substring(0, 6), 16) & 0xFFFFFF);
	}

	/** Dark Material roles tinted toward the accent, close to what matugen gives. */
	private static void derive(int accent) {
		accentSeed = accent;
		int rgb = accent & 0xFFFFFF;
		surface = tint(0x131313, rgb, 0.04f);
		surfaceLow = tint(0x1B1B1B, rgb, 0.05f);
		surfaceContainer = tint(0x1F1F1F, rgb, 0.06f);
		surfaceHigh = tint(0x2A2A2A, rgb, 0.07f);
		surfaceHighest = tint(0x353535, rgb, 0.08f);
		onSurface = tint(0xE5E5E5, rgb, 0.04f);
		onSurfaceVariant = tint(0xC6C6C6, rgb, 0.08f);
		outline = tint(0x919191, rgb, 0.08f);
		outlineVariant = tint(0x474747, rgb, 0.08f);
		primary = 0xFF000000 | rgb;
		onPrimary = tint(0x101010, rgb, 0.22f);
		secondaryContainer = tint(0x2C2C2C, rgb, 0.28f);
		onSecondaryContainer = tint(0xE8E8E8, rgb, 0.10f);
	}

	private static int tint(int base, int rgb, float t) {
		return 0xFF000000 | (Theme.mix(base, rgb, t) & 0xFFFFFF);
	}
}
