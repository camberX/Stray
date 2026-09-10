package dev.stray.client.visual.motionblur;

public final class BlurStrengthCalculator {
	public record Result(float strength, int sampleAmount) {
	}

	public Result calculate(float baseStrength, float fps, int refreshRate, boolean scalingEnabled) {
		if (!scalingEnabled) {
			return new Result(baseStrength, 100);
		}
		float fpsOverRefresh = refreshRate > 0 ? fps / refreshRate : 1.0f;
		if (fpsOverRefresh < 1.0f) {
			fpsOverRefresh = 1.0f;
		}
		float scaledStrength = baseStrength * fpsOverRefresh;
		int sampleAmount = fpsOverRefresh > 1.0f ? (int) (100 * fpsOverRefresh) : 100;
		return new Result(scaledStrength, sampleAmount);
	}
}
