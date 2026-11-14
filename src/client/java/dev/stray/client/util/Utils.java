package dev.stray.client.util;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;

import java.util.Locale;

/** Small helpers needed in more than one place that fit no narrower class in this package. */
public final class Utils {
	private Utils() {
	}

	/** The installed Stray version. */
	public static String modVersion() {
		return FabricLoader.getInstance()
			.getModContainer("stray")
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("1.3.33");
	}

	/** Ease-out cubic over 0..1: fast at first, settling at the end. */
	public static float easeOutCubic(float t) {
		float u = 1f - Mth.clamp(t, 0f, 1f);
		return 1f - u * u * u;
	}

	/** Running totals of the steps, starting with 0 (one entry longer than the input). */
	public static long[] cumulative(long... steps) {
		long[] out = new long[steps.length + 1];
		long total = 0L;
		for (int i = 0; i < steps.length; i++) {
			total += steps[i];
			out[i + 1] = total;
		}
		return out;
	}

	/** The legacy colour code of a pet rarity name ("epic" gives the purple code), white for common or unknown. */
	public static String petTierCode(String tier) {
		return switch (tier == null ? "" : tier.toLowerCase(Locale.ROOT)) {
			case "uncommon" -> "§a";
			case "rare" -> "§9";
			case "epic" -> "§5";
			case "legendary" -> "§6";
			case "mythic" -> "§d";
			default -> "§f";
		};
	}

	/** A pet rarity name as a number, common or unknown 0 up to mythic 5. */
	public static int petTierIndex(String tier) {
		return switch (tier == null ? "" : tier.toLowerCase(Locale.ROOT)) {
			case "uncommon" -> 1;
			case "rare" -> 2;
			case "epic" -> 3;
			case "legendary" -> 4;
			case "mythic" -> 5;
			default -> 0;
		};
	}
}
