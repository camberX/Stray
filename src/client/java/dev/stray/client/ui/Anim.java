package dev.stray.client.ui;

import dev.stray.client.config.StrayConfig;

public final class Anim {
	private Anim() {
	}

	public static float exp(float current, float target, float speed, float dt) {
		if (!StrayConfig.get().uiAnimations) {
			return target;
		}
		if (Math.abs(target - current) < 0.003f) {
			return target;
		}
		return current + (target - current) * (1f - (float) Math.exp(-speed * dt));
	}

	public static int mix(int from, int to, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int a = Math.round(((from >>> 24) & 0xFF) * (1f - t) + ((to >>> 24) & 0xFF) * t);
		int r = Math.round(((from >> 16) & 0xFF) * (1f - t) + ((to >> 16) & 0xFF) * t);
		int g = Math.round(((from >> 8) & 0xFF) * (1f - t) + ((to >> 8) & 0xFF) * t);
		int b = Math.round((from & 0xFF) * (1f - t) + (to & 0xFF) * t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	public static int fade(int color, float t) {
		int a = (color >>> 24) & 0xFF;
		int na = Math.max(0, Math.min(255, Math.round(a * t)));
		return (na << 24) | (color & 0x00FFFFFF);
	}
}
