package dev.stray.client.ui;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.render.GuiDraw;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.HashMap;
import java.util.Map;

/**
 * The Stray look: near-black panels, one-pixel hairlines, warm off-white text and a single
 * accent colour (orange by default, your pick in Look) that only marks what is on or picked.
 * Switches move on a spring, so they overshoot a little and settle.
 */
public final class SignatureLook {
	public static final int BG = 0xFF0E0E0E;
	public static final int RAISED = 0xFF141414;
	public static final int PICKED = 0xFF1B1B1B;
	public static final int LINE = 0xFF262626;
	public static final int LINE_HOT = 0xFF363636;
	public static final int TRACK = 0xFF2C2C2C;
	public static final int TEXT = 0xFFEBE8E2;
	public static final int SOFT = 0xFFC9C6C0;
	public static final int MUTED = 0xFF8D8A84;
	public static final int FAINT = 0xFF5B5954;
	public static final int DEFAULT_ACCENT = 0xFF6A3D;
	public static final float WINDOW_R = 6f;
	public static final float CONTROL_R = 3f;

	private SignatureLook() {
	}

	/** The accent colour, opaque. */
	public static int accent() {
		StrayConfig config = StrayConfig.get();
		int rgb = config == null ? DEFAULT_ACCENT : config.signatureAccent & 0xFFFFFF;
		return 0xFF000000 | (rgb == 0 ? DEFAULT_ACCENT : rgb);
	}

	/** The knob on an accent track: the accent darkened almost to black, so it reads on any accent. */
	public static int onAccent() {
		return 0xFF000000 | Theme.mix(accent() & 0xFFFFFF, 0x000000, 0.86f);
	}

	/** A window or HUD panel: the fill (its alpha kept, its colour made this look's black), a hairline edge. */
	public static void panel(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int fill) {
		if (w < 2f || h < 2f) {
			return;
		}
		float r = Math.min(WINDOW_R, Math.min(w, h) * 0.5f);
		int a = (fill >>> 24) & 0xFF;
		GuiDraw.roundedFine(graphics, x, y, w, h, r, Theme.withAlpha(BG, a == 0 ? 0xF5 : a));
		GuiDraw.hairline(graphics, x, y, w, h, r, LINE);
	}

	/** A card, chip or slot: raised, lighter on hover; picked ones get an accent edge. */
	public static void control(GuiGraphicsExtractor graphics, float x, float y, float w, float h, boolean hover, boolean selected) {
		if (w < 1f || h < 1f) {
			return;
		}
		float r = Math.min(CONTROL_R, Math.min(w, h) * 0.5f);
		GuiDraw.roundedFine(graphics, x, y, w, h, r, selected || hover ? PICKED : RAISED);
		GuiDraw.hairline(graphics, x, y, w, h, r, selected ? accent() : hover ? LINE_HOT : LINE);
	}

	/**
	 * A pill switch. {@code pos} is the knob's place (0 off, 1 on; a spring can take it a little
	 * past either end), the colour follows it.
	 */
	public static void toggle(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float pos) {
		float t = Math.max(0f, Math.min(1f, pos));
		float r = h * 0.5f;
		GuiDraw.roundedFine(graphics, x, y, w, h, r, PanelLook.lerp(TRACK, accent(), t));
		float k = h - 4f;
		float travel = w - 4f - k;
		float kx = x + 2f + travel * Math.max(-0.12f, Math.min(1.12f, pos));
		GuiDraw.roundedFine(graphics, kx, y + 2f, k, k, k * 0.5f, PanelLook.lerp(MUTED, onAccent(), t));
	}

	/** A slider: a hairline track, the accent up to the value, a small light handle. */
	public static void slider(GuiGraphicsExtractor graphics, float x, float cy, float w, float t) {
		t = Math.max(0f, Math.min(1f, t));
		GuiDraw.fillSmooth(graphics, x, cy - 0.75f, w, 1.5f, TRACK);
		GuiDraw.fillSmooth(graphics, x, cy - 0.75f, w * t, 1.5f, accent());
		GuiDraw.roundedFine(graphics, x + (w - 3f) * t, cy - 4f, 3f, 8f, 1f, TEXT);
	}

	/** A field (search, dropdown, key): raised fill, hairline that turns accent when focused. */
	public static void field(GuiGraphicsExtractor graphics, float x, float y, float w, float h, boolean hover, boolean focused) {
		GuiDraw.roundedFine(graphics, x, y, w, h, CONTROL_R, RAISED);
		GuiDraw.hairline(graphics, x, y, w, h, CONTROL_R, focused ? accent() : hover ? LINE_HOT : LINE);
	}

	// ------------------------------------------------------------------ springs

	private static final class Spring {
		float pos;
		float vel;
		long at;
	}

	private static final Map<String, Spring> SPRINGS = new HashMap<>();
	/** Stiffness and damping: about 12 % overshoot, settled in a quarter second. */
	private static final float K = 380f;
	private static final float D = 22f;

	/**
	 * A spring toward {@code on ? 1 : 0}, stepped by real time since this key was last asked.
	 * New keys start where they belong, so nothing bounces when the menu opens.
	 */
	public static float spring(String key, boolean on) {
		float target = on ? 1f : 0f;
		long now = System.nanoTime();
		Spring s = SPRINGS.get(key);
		if (s == null) {
			if (SPRINGS.size() > 512) {
				SPRINGS.clear();
			}
			s = new Spring();
			s.pos = target;
			s.at = now;
			SPRINGS.put(key, s);
			return target;
		}
		float dt = Math.min(0.05f, (now - s.at) / 1.0e9f);
		s.at = now;
		if (ExMotion.reduced()) {
			s.pos = target;
			s.vel = 0f;
			return target;
		}
		// Small fixed steps keep the spring stable at any frame rate.
		while (dt > 0f) {
			float step = Math.min(dt, 1f / 240f);
			s.vel += (K * (target - s.pos) - D * s.vel) * step;
			s.pos += s.vel * step;
			dt -= step;
		}
		if (Math.abs(target - s.pos) < 0.001f && Math.abs(s.vel) < 0.01f) {
			s.pos = target;
			s.vel = 0f;
		}
		return s.pos;
	}

	/**
	 * Eases {@code current} toward {@code target}. Only Reduce motion stops it: the old
	 * "Animations" setting belongs to the classic menu and is off for most people.
	 */
	public static float exp(float current, float target, float speed, float dt) {
		if (ExMotion.reduced() || Math.abs(target - current) < 0.002f) {
			return target;
		}
		return current + (target - current) * (1f - (float) Math.exp(-speed * dt));
	}

	/** Ease-out cubic. */
	public static float ease(float t) {
		float u = 1f - Math.max(0f, Math.min(1f, t));
		return 1f - u * u * u;
	}
}
