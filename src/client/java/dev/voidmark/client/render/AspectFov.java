package dev.voidmark.client.render;

import dev.voidmark.client.config.VoidmarkConfig;

/**
 * Scales the horizontal projection so the world frustum matches the chosen
 * aspect. Terrain and entities outside that FOV fail the cull and are not drawn.
 */
public final class AspectFov {
	private AspectFov() {
	}

	public static float apply(float widthOrAspect) {
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!config.aspectEnabled) {
			return widthOrAspect;
		}
		return widthOrAspect * config.aspectRatio;
	}
}
