package dev.stray.client.visual;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Players with the chest cosmetic (Female Gender Mod style) and their own size and position;
 * keyed by UUID. Granted from the shop desk, adjusted by the player in Cosmetics.
 */
public final class ShopChest {
	/** Size 0.5..1.6 (1 = default); y moves it down (+) or up (-) in model pixels. */
	public record Chest(float size, float y) {
	}

	private static final Map<UUID, Chest> ON = new ConcurrentHashMap<>();

	private ShopChest() {
	}

	public static void set(UUID uuid, boolean on) {
		set(uuid, on, 1f, 0f);
	}

	public static void set(UUID uuid, boolean on, float size, float y) {
		if (uuid == null) {
			return;
		}
		if (on) {
			ON.put(uuid, new Chest(clamp(size, 0.5f, 1.6f, 1f), clamp(y, -1.5f, 2f, 0f)));
		} else {
			ON.remove(uuid);
		}
	}

	public static boolean has(UUID uuid) {
		return uuid != null && ON.containsKey(uuid);
	}

	public static Chest get(UUID uuid) {
		return uuid == null ? null : ON.get(uuid);
	}

	private static float clamp(float value, float min, float max, float fallback) {
		if (Float.isNaN(value)) {
			return fallback;
		}
		return Math.max(min, Math.min(max, value));
	}
}
