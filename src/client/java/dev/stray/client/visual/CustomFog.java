package dev.stray.client.visual;

import dev.stray.client.render.StrayUniforms;
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

	public static void bind() {
		StrayUniforms.fog(applied ? 1f : 0f);
	}

	public static Sample sample() {
		return sample;
	}

	/** How much Space Roof's haze is in (0..1), eased in and out. */
	private static float haze;
	private static long hazeNs;

	public static void apply(FogData data, Camera camera, int renderDistanceChunks) {
		applied = false;
		sample = Sample.OFF;
		StrayConfig config = StrayConfig.get();
		if (data == null || camera == null || data.color == null) {
			return;
		}
		applyHaze(data, camera, config);
		if (!config.fogEnabled) {
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
		if (src == null || src.contains("strayMix")) {
			return src;
		}
		String needle = "return vec4(mix(fragColor.rgb, fogColor.rgb, fogValue * fogColor.a), fragColor.a);";
		if (!src.contains(needle)) {
			return src;
		}
		String curved = """
			float strayLin = clamp(fogValue, 0.0, 1.0);
			float strayMix = fogValue * fogColor.a;
			if (u_StrayMisc.y > 0.5) {
				float strayDensity = clamp(fogColor.a, 0.0, 1.0);
				strayMix = strayDensity <= 0.001 ? 0.0 : pow(strayLin, mix(3.2, 0.22, strayDensity));
			}
			return vec4(mix(fragColor.rgb, fogColor.rgb, strayMix), fragColor.a);""";
		return StrayUniforms.insertBlock(src).replace(needle, curved);
	}

	/**
	 * Space Roof's cosmic haze: inside a boss part with sky, the air fills with a thin haze in a
	 * soft shade of the sky's colour, so the room fades into it with distance (never fully).
	 * Through vanilla's fog, so blocks, players and mobs all get it evenly. Eases in and out.
	 */
	private static void applyHaze(FogData data, Camera camera, StrayConfig config) {
		long now = System.nanoTime();
		float dt = hazeNs == 0L ? 0f : Math.min(0.25f, (now - hazeNs) / 1.0e9f);
		hazeNs = now;
		FogType type = camera.getFluidInCamera();
		boolean want = config.spaceRoofMist && type != FogType.WATER && type != FogType.LAVA && type != FogType.POWDER_SNOW
			&& SpaceRoof.underSky(camera.position());
		haze += ((want ? 1f : 0f) - haze) * Math.min(1f, dt * 1.5f);
		if (haze < 0.002f) {
			haze = want ? haze : 0f;
			return;
		}
		int rgb = config.spaceRoofRgb & 0xFFFFFF;
		// Lighter than the stone, so it reads as glowing haze rather than just darkness.
		float red = ((rgb >> 16) & 0xFF) / 255f * 0.55f + 0.07f;
		float green = ((rgb >> 8) & 0xFF) / 255f * 0.55f + 0.06f;
		float blue = (rgb & 0xFF) / 255f * 0.55f + 0.09f;
		float start = Mth.lerp(haze, data.environmentalStart, 2f);
		float end = Mth.lerp(haze, data.environmentalEnd, 42f);
		data.environmentalStart = start;
		data.environmentalEnd = end;
		data.renderDistanceStart = Math.min(data.renderDistanceStart, end);
		data.renderDistanceEnd = Math.min(data.renderDistanceEnd, Math.max(end, data.renderDistanceStart + 1f));
		float alpha = Mth.lerp(haze, data.color.w, 0.65f);
		data.color.set(Mth.lerp(haze, data.color.x, red), Mth.lerp(haze, data.color.y, green), Mth.lerp(haze, data.color.z, blue), alpha);
	}

	public static int fogRgb(StrayConfig config) {
		return config.matchFogToWorld ? config.worldTintRgb : config.fogRgb;
	}
}
