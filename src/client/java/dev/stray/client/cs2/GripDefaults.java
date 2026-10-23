package dev.stray.client.cs2;

import java.util.Map;

/**
 * Default first-person knife positions in the hand, tuned in-game per knife:
 * edge/spine, sideways, along handle, pitch, yaw, roll, size. Knives not listed use no offset.
 */
public final class GripDefaults {
	private static final Map<String, float[]> HAND = Map.ofEntries(
		Map.entry("weapon_knife_butterfly", new float[] {0.55f, 2.85f, 0f, 0f, 0f, 0f, 1f}),
		Map.entry("weapon_bayonet", new float[] {0f, 1.34f, 1.15f, 0f, 0f, 0f, 1f}),
		Map.entry("weapon_knife_survival_bowie", new float[] {0.68f, 2.30f, 0.22f, 0f, 0f, 0f, 1f}),
		Map.entry("weapon_knife_css", new float[] {0.63f, 1.04f, -0.19f, 0f, 0f, 0f, 1f}),
		Map.entry("weapon_knife_falchion", new float[] {-0.93f, 1.62f, 1.81f, 0f, 0f, 0f, 1f}),
		Map.entry("weapon_knife_flip", new float[] {0.03f, 1.42f, 1.92f, 0f, 0f, 0f, 1f}),
		Map.entry("weapon_knife_gut", new float[] {0f, 1.20f, 1.81f, 0f, 0f, 0f, 1f}),
		Map.entry("weapon_knife_tactical", new float[] {0.27f, 1.26f, 1.56f, 0f, 0f, 0f, 1f}),
		Map.entry("weapon_knife_karambit", new float[] {0.33f, -0.39f, 0.41f, 0f, 0f, 0f, 1.13f}),
		Map.entry("weapon_knife_kukri", new float[] {0f, 1.92f, 0f, 0f, 0f, 0f, 1f}),
		Map.entry("weapon_knife_m9_bayonet", new float[] {1.07f, 1.64f, 1.40f, 0f, 0f, 0f, 1f}),
		Map.entry("weapon_knife_gypsy_jackknife", new float[] {0f, 1.70f, 1.51f, 0f, 0f, 0f, 1f}),
		Map.entry("weapon_knife_outdoor", new float[] {1.45f, 2.25f, 0.05f, 0f, 0f, 0f, 1f}),
		Map.entry("weapon_knife_cord", new float[] {-0.33f, 1.53f, 1.26f, 0f, 0f, 0f, 1f}),
		Map.entry("weapon_knife_skeleton", new float[] {0f, 1.37f, 0.96f, 0f, 0f, 0f, 1f}),
		Map.entry("weapon_knife_stiletto", new float[] {0f, 1.42f, 0.93f, 0f, 0f, 0f, 1f}),
		Map.entry("weapon_knife_canis", new float[] {-0.36f, 1.18f, 1.42f, 0f, 0f, 0f, 1f}),
		Map.entry("weapon_knife_widowmaker", new float[] {0f, -0.33f, 1.42f, -7f, 0f, 0f, 1.24f}),
		Map.entry("weapon_knife_ursus", new float[] {0f, 1.29f, 1.20f, 0f, 0f, 0f, 1f})
	);

	private GripDefaults() {
	}

	/** The default in-hand position for a knife id, as a fresh array. */
	public static float[] hand(String knifeId) {
		float[] g = HAND.get(knifeId);
		return g == null ? new float[] {0f, 0f, 0f, 0f, 0f, 0f, 1f} : g.clone();
	}
}
