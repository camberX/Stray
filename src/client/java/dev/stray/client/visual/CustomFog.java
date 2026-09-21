package dev.stray.client.visual;

import dev.stray.Stray;
import dev.stray.client.config.StrayConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;

public final class CustomFog {
	public record Sample(float start, float end, float red, float green, float blue, float alpha) {
		private static final Sample OFF = new Sample(0f, 0f, 0f, 0f, 0f, 0f);
	}

	private static boolean applied;
	private static Sample sample = Sample.OFF;

	private CustomFog() {
	}

	public static boolean applied() {
		return applied;
	}

	public static Sample sample() {
		return sample;
	}

	public static void apply(FogData data, Camera camera, int renderDistanceChunks) {
		applied = false;
		sample = Sample.OFF;
		StrayConfig config = StrayConfig.get();
		if (!config.fogEnabled || data == null || camera == null || data.color == null) {
			return;
		}

		FogType type = camera.getFluidInCamera();
		if (type == FogType.WATER || type == FogType.LAVA || type == FogType.POWDER_SNOW) {
			return;
		}

		float view = Math.max(32f, renderDistanceChunks * 16f);
		float startFrac = Mth.clamp(config.fogStart, 0f, 0.95f);
		float endFrac = Mth.clamp(Math.max(startFrac + 0.04f, config.fogEnd), 0.05f, 1f);
		float start = view * startFrac;
		float far = Math.max(start + 4f, view * endFrac);
		float density = Mth.clamp(config.fogDensity, 0f, 1f);
		float end = far;
		float alpha = density;
		if (!WorldTint.sodiumLoaded() && density > 0f) {
			end = Mth.lerp(density, far, start + (far - start) * 0.28f);
			alpha = 1f;
		}

		data.environmentalStart = start;
		data.environmentalEnd = end;
		data.renderDistanceStart = start;
		data.renderDistanceEnd = end;
		data.skyEnd = end;
		data.cloudEnd = end;

		int rgb = fogRgb(config);
		float red = ((rgb >> 16) & 0xFF) / 255f;
		float green = ((rgb >> 8) & 0xFF) / 255f;
		float blue = (rgb & 0xFF) / 255f;
		data.color.set(red, green, blue, alpha);
		sample = new Sample(start, end, red, green, blue, alpha);
		applied = true;
	}

	/**
	 * Sodium copies fog distances at the end of {@code setupFog}, then draws terrain
	 * with its own shader. That shader treats alpha as a flat multiplier, so 100%
	 * still looks like a thin ramp. The injected curve keeps start and end, and
	 * makes higher density fill the span instead of only tinting the far edge.
	 */
	public static String injectSodiumShader(String src) {
		if (src == null || src.contains("u_StrayFog")) {
			return src;
		}
		String needle = "return vec4(mix(fragColor.rgb, fogColor.rgb, fogValue * fogColor.a), fragColor.a);";
		if (!src.contains(needle)) {
			Stray.LOGGER.warn("Could not inject custom fog into Sodium fog shader");
			return src;
		}
		String curved = """
			float strayLin = clamp(fogValue, 0.0, 1.0);
			float strayMix = fogValue * fogColor.a;
			if (u_StrayFog > 0.5) {
				float strayDensity = clamp(fogColor.a, 0.0, 1.0);
				strayMix = strayDensity <= 0.001 ? 0.0 : pow(strayLin, mix(3.2, 0.22, strayDensity));
			}
			return vec4(mix(fragColor.rgb, fogColor.rgb, strayMix), fragColor.a);""";
		return "uniform float u_StrayFog;\n" + src.replace(needle, curved);
	}

	public static int fogRgb(StrayConfig config) {
		return config.matchFogToWorld ? config.worldTintRgb : config.fogRgb;
	}
}
