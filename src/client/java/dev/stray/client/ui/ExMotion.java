package dev.stray.client.ui;

import dev.stray.client.config.StrayConfig;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Material 3 Expressive motion: springs advanced by real time (not per frame, so they look
 * the same at 60 and 1000 FPS), the motion scheme's spring tokens, and the pointer's press
 * for ripples and pressed shapes. Display only: it reads the local mouse button, nothing else.
 */
public final class ExMotion {
	/** A spring: stiffness and damping ratio, as Material's motion tokens give them. */
	public record Spec(float stiffness, float damping) {
	}

	// Material 3 Expressive motion scheme ("expressive"): spatial springs overshoot, effects don't.
	public static final Spec SPATIAL_FAST = new Spec(800f, 0.6f);
	public static final Spec SPATIAL = new Spec(380f, 0.8f);
	public static final Spec SPATIAL_SLOW = new Spec(200f, 0.8f);
	public static final Spec EFFECTS_FAST = new Spec(3800f, 1f);
	public static final Spec EFFECTS = new Spec(1600f, 1f);
	public static final Spec EFFECTS_SLOW = new Spec(800f, 1f);

	private static final class State {
		float pos;
		float vel;
		long last;
		long seen;
	}

	private static final Map<String, State> STATES = new HashMap<>();
	private static long lastNanos;
	private static float dt;
	private static long lastSweep;

	private static boolean down;
	private static float pressX = Float.NaN;
	private static float pressY = Float.NaN;
	private static long pressAt;
	private static long releaseAt;

	private ExMotion() {
	}

	public static boolean reduced() {
		StrayConfig config = StrayConfig.get();
		return config == null || config.exReduceMotion;
	}

	/**
	 * Tracks the pointer's press; {@code mx, my} is the pointer in the caller's units. Safe to call
	 * more than once a frame (the HUD and the menu both do): springs keep their own clocks.
	 */
	public static void frame(float mx, float my) {
		long now = System.nanoTime();
		dt = lastNanos == 0L ? 0f : Math.min(1f / 20f, Math.max(0f, (now - lastNanos) / 1.0e9f));
		lastNanos = now;
		boolean pressed = dev.stray.client.input.MouseButtons.down(0);
		if (pressed && !down) {
			pressX = mx;
			pressY = my;
			pressAt = now;
			releaseAt = 0L;
		} else if (!pressed && down) {
			releaseAt = now;
		}
		down = pressed;
		if (now - lastSweep > 5_000_000_000L) {
			lastSweep = now;
			Iterator<State> it = STATES.values().iterator();
			while (it.hasNext()) {
				if (now - it.next().seen > 10_000_000_000L) {
					it.remove();
				}
			}
		}
	}

	/** Seconds since the last frame. */
	public static float dt() {
		return dt;
	}

	/**
	 * The value of the spring {@code key} heading for {@code target}: it starts at the target the
	 * first time it is asked for, then follows every change with the spring's motion.
	 */
	public static float spring(String key, float target, Spec spec) {
		State s = STATES.get(key);
		long now = System.nanoTime();
		if (s == null) {
			s = new State();
			s.pos = target;
			s.last = now;
			s.seen = now;
			STATES.put(key, s);
			return target;
		}
		return advance(s, target, spec, now);
	}

	private static float advance(State s, float target, Spec spec, long now) {
		s.seen = now;
		if (reduced()) {
			s.pos = target;
			s.vel = 0f;
			s.last = now;
			return target;
		}
		float elapsed = Math.min(0.25f, Math.max(0f, (now - s.last) / 1.0e9f));
		s.last = now;
		if (elapsed > 0f) {
			step(s, target, spec, elapsed);
		}
		return s.pos;
	}

	/**
	 * 0 to 1 for something that just appeared: it springs in from 0 when it was not drawn a
	 * moment ago (a popover opening, a list dropping down) and stays at 1 while it keeps showing.
	 */
	public static float enter(String key, Spec spec) {
		long now = System.nanoTime();
		State s = STATES.get(key);
		if (s == null || now - s.seen > 150_000_000L) {
			if (s == null) {
				s = new State();
				STATES.put(key, s);
			}
			s.pos = reduced() ? 1f : 0f;
			s.vel = 0f;
			s.last = now;
			s.seen = now;
			return s.pos;
		}
		return advance(s, 1f, spec, now);
	}

	/** Sets a spring's position (and stops it), so the next change animates from there. */
	public static void snap(String key, float value) {
		State s = STATES.computeIfAbsent(key, k -> new State());
		s.pos = value;
		s.vel = 0f;
		s.last = System.nanoTime();
		s.seen = s.last;
	}

	private static void step(State s, float target, Spec spec, float dt) {
		float k = spec.stiffness();
		float c = 2f * spec.damping() * (float) Math.sqrt(k);
		// Small steps keep the stiff springs stable at low frame rates.
		int n = Math.max(1, (int) Math.ceil(dt / 0.004f));
		float h = dt / n;
		for (int i = 0; i < n; i++) {
			float a = -k * (s.pos - target) - c * s.vel;
			s.vel += a * h;
			s.pos += s.vel * h;
		}
		if (Math.abs(s.pos - target) < 0.0005f && Math.abs(s.vel) < 0.01f) {
			s.pos = target;
			s.vel = 0f;
		}
	}

	/**
	 * A spring released from 0 towards 1 {@code seconds} ago, worked out directly (no state):
	 * for things that start at a known moment, like a card entering.
	 */
	public static float since(float seconds, Spec spec) {
		if (reduced() || seconds <= 0f) {
			return seconds <= 0f && !reduced() ? 0f : 1f;
		}
		float w = (float) Math.sqrt(spec.stiffness());
		float z = spec.damping();
		if (z >= 1f) {
			float e = (float) Math.exp(-w * seconds);
			return 1f - e * (1f + w * seconds);
		}
		float wd = w * (float) Math.sqrt(1f - z * z);
		float e = (float) Math.exp(-z * w * seconds);
		return 1f - e * ((float) Math.cos(wd * seconds) + z * w / wd * (float) Math.sin(wd * seconds));
	}

	public static boolean down() {
		return down;
	}

	/** True while the pointer is held down after pressing inside this rectangle. */
	public static boolean held(float x, float y, float w, float h) {
		return down && pressX >= x && pressX < x + w && pressY >= y && pressY < y + h;
	}

	/**
	 * The ripple for a press inside this rectangle: {x, y, radius, alpha} in the caller's units,
	 * or null when there is none to draw.
	 */
	public static float[] ripple(float x, float y, float w, float h) {
		if (reduced() || pressAt == 0L || !(pressX >= x && pressX < x + w && pressY >= y && pressY < y + h)) {
			return null;
		}
		long now = System.nanoTime();
		float age = (now - pressAt) / 1.0e9f;
		float out = releaseAt == 0L ? 0f : (now - releaseAt) / 1.0e9f;
		if (out > 0.35f || age > 3f) {
			return null;
		}
		float far = 0f;
		for (float cx : new float[]{x, x + w}) {
			for (float cy : new float[]{y, y + h}) {
				far = Math.max(far, (float) Math.hypot(cx - pressX, cy - pressY));
			}
		}
		float grow = 1f - (float) Math.exp(-age / 0.11f);
		float alpha = 0.10f * (1f - Math.min(1f, out / 0.35f));
		return new float[]{pressX, pressY, far * grow, alpha};
	}
}
