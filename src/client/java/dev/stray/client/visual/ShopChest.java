package dev.stray.client.visual;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Players the shop desk gave the chest cosmetic (Female Gender Mod style); keyed by UUID. */
public final class ShopChest {
	private static final Set<UUID> ON = ConcurrentHashMap.newKeySet();

	private ShopChest() {
	}

	public static void set(UUID uuid, boolean on) {
		if (uuid == null) {
			return;
		}
		if (on) {
			ON.add(uuid);
		} else {
			ON.remove(uuid);
		}
	}

	public static boolean has(UUID uuid) {
		return uuid != null && ON.contains(uuid);
	}
}
