package dev.stray.client.visual;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Wing cosmetics granted from the shop desk; keyed by player UUID. */
public final class ShopWings {
	public enum Style {
		ANGEL(7, 1.0f, 0.95f, false, 0xF4F6FF),
		DEMON(5, 1.15f, 1.0f, false, 0x2B1D2E),
		FAIRY(6, 0.9f, 0.62f, true, 0x9AE6FF),
		PHOENIX(8, 1.1f, 0.9f, true, 0xFF7A1A);

		public final int feathers;
		public final float span;
		public final float alpha;
		public final boolean glow;
		public final int defaultRgb;

		Style(int feathers, float span, float alpha, boolean glow, int defaultRgb) {
			this.feathers = feathers;
			this.span = span;
			this.alpha = alpha;
			this.glow = glow;
			this.defaultRgb = defaultRgb;
		}

		static Style parse(String raw) {
			if (raw == null || raw.isBlank()) {
				return null;
			}
			try {
				return Style.valueOf(raw.trim().toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException ignored) {
				return null;
			}
		}
	}

	public record Wings(Style style, int rgb) {
	}

	private static final Map<UUID, Wings> WINGS = new ConcurrentHashMap<>();

	private ShopWings() {
	}

	public static void set(UUID uuid, String style, String rgbHex) {
		if (uuid == null) {
			return;
		}
		Style parsed = Style.parse(style);
		if (parsed == null) {
			WINGS.remove(uuid);
			return;
		}
		int rgb = parsed.defaultRgb;
		if (rgbHex != null && rgbHex.matches("(?i)#?[0-9a-f]{6}")) {
			rgb = Integer.parseInt(rgbHex.replace("#", ""), 16);
		}
		WINGS.put(uuid, new Wings(parsed, rgb & 0xFFFFFF));
	}

	public static Wings get(UUID uuid) {
		return uuid == null ? null : WINGS.get(uuid);
	}

	public static void clear() {
		WINGS.clear();
	}
}
