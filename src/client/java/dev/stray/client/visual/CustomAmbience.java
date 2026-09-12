package dev.stray.client.visual;

import dev.stray.client.config.StrayConfig;
import net.minecraft.world.level.biome.Biome;

/**
 * Client-side weather and clock override, matching LiquidBounce CustomAmbience.
 */
public final class CustomAmbience {
	private CustomAmbience() {
	}

	public static boolean active() {
		return StrayConfig.get().ambienceEnabled;
	}

	public static boolean overridesWeather() {
		return active() && StrayConfig.get().weather() != StrayConfig.Weather.NO_CHANGE;
	}

	public static boolean overridesTime() {
		return active() && StrayConfig.get().clock() != StrayConfig.Clock.NO_CHANGE;
	}

	public static float rainLevel(float original) {
		if (!active()) {
			return original;
		}
		return switch (StrayConfig.get().weather()) {
			case SUNNY -> 0f;
			case RAINY, THUNDER -> 1f;
			case SNOWY -> 0.9f;
			case NO_CHANGE -> original;
		};
	}

	public static float thunderLevel(float original) {
		if (!active()) {
			return original;
		}
		return switch (StrayConfig.get().weather()) {
			case THUNDER -> 1f;
			case SUNNY, RAINY, SNOWY -> 0f;
			case NO_CHANGE -> original;
		};
	}

	public static long dayTime(long original) {
		if (!active()) {
			return original;
		}
		return switch (StrayConfig.get().clock()) {
			case DAWN -> 23041L;
			case DAY -> 1000L;
			case NOON -> 6000L;
			case DUSK -> 12610L;
			case NIGHT -> 13000L;
			case MIDNIGHT -> 18000L;
			case NO_CHANGE -> original;
		};
	}

	public static boolean snowy() {
		return active() && StrayConfig.get().weather() == StrayConfig.Weather.SNOWY;
	}

	public static Biome.Precipitation precipitation(Biome.Precipitation original) {
		return snowy() ? Biome.Precipitation.SNOW : original;
	}

	public static float precipitationGradient(float original) {
		StrayConfig config = StrayConfig.get();
		if (active() && config.ambiencePrecipitation && original != 0f) {
			return StrayConfig.clamp(config.ambiencePrecipitationGradient, 0.1f, 1f);
		}
		return original;
	}
}
