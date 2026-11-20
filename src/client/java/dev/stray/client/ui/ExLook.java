package dev.stray.client.ui;

import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.GuiFrostBlur;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Material 3 Expressive pieces, in whatever units the pose is in: tonal surfaces with large
 * rounded corners, switches with a check in the thumb, sliders with a bar handle and a gap,
 * connected button groups, chips, swatches and text fields. Colours come from {@link ExColors}.
 */
public final class ExLook {
	/** Roboto Flex families for {@link #text}: body, body bold, small, title, headline. */
	public static final int BODY = 20;
	public static final int BOLD = 21;
	public static final int SMALL = 22;
	public static final int TITLE = 23;
	public static final int HEADLINE = 24;

	/** Screen pixels per unit for FreeType text, set by whoever draws (the menu: its DPI). */
	public static double textScale = 1.0;

	private ExLook() {
	}

	// ------------------------------------------------------------------ surfaces

	public static void rounded(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float r, int color) {
		GuiDraw.roundedFine(graphics, x, y, w, h, Math.min(r, Math.min(w, h) * 0.5f), color);
	}

	/**
	 * Rounded with a different radius on the left and right ends (connected button groups,
	 * split sliders): each half is drawn clipped with its own corners.
	 */
	public static void sides(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float left, float right, int color) {
		if (Math.abs(left - right) < 0.5f) {
			rounded(graphics, x, y, w, h, left, color);
			return;
		}
		// Each half is clipped and drawn with its own corners. Clips land on whole GUI units, so
		// the split is worked out there once and both halves share it: no seam between them.
		org.joml.Vector2f a = graphics.pose().transformPosition(x - 2f, y - 2f, new org.joml.Vector2f());
		org.joml.Vector2f b = graphics.pose().transformPosition(x + w + 2f, y + h + 2f, new org.joml.Vector2f());
		org.joml.Vector2f m = graphics.pose().transformPosition(x + w * 0.5f, y, new org.joml.Vector2f());
		int x0 = (int) Math.floor(Math.min(a.x, b.x));
		int x1 = (int) Math.ceil(Math.max(a.x, b.x));
		int y0 = (int) Math.floor(Math.min(a.y, b.y));
		int y1 = (int) Math.ceil(Math.max(a.y, b.y));
		int split = Math.round(m.x);
		if (split <= x0 || split >= x1) {
			rounded(graphics, x, y, w, h, Math.min(left, right), color);
			return;
		}
		clip(graphics, x0, y0, split, y1);
		rounded(graphics, x, y, w, h, left, color);
		graphics.disableScissor();
		clip(graphics, split, y0, x1, y1);
		rounded(graphics, x, y, w, h, right, color);
		graphics.disableScissor();
	}

	/** A clip in GUI units, whatever the pose. */
	private static void clip(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
		graphics.pose().pushMatrix();
		graphics.pose().identity();
		graphics.enableScissor(x0, y0, x1, y1);
		graphics.pose().popMatrix();
	}

	/**
	 * A raised surface: a soft shadow, an optional frost behind, the fill and a faint edge.
	 * Windows, sheets, popovers and HUD panels.
	 */
	public static void surface(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float r, int fill, boolean frost) {
		if (w < 2f || h < 2f) {
			return;
		}
		float rr = Math.min(r, Math.min(w, h) * 0.5f);
		int a = (fill >>> 24) & 0xFF;
		// The shadow is cut out under the box, so it sits exactly on it: shifted, its cut-out
		// would show as a lip under see-through surfaces.
		ShadowLook.shadow(graphics, x, y, w, h, rr, Math.min(w, h) < 40f ? 6 : 10, Math.round(0x48 * a / 255f));
		if (frost) {
			GuiFrostBlur.blitWindow(graphics, x, y, w, h, rr);
		}
		rounded(graphics, x, y, w, h, rr, fill);
		GuiDraw.hairline(graphics, x, y, w, h, rr, ExColors.alpha(ExColors.outlineVariant, 0.55f * a / 255f));
	}

	/** The hover / press tint Material lays over anything you can click. */
	public static void state(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float r, float hover, boolean pressed, int on) {
		// Material's state layer: hover 8 %, pressed a little more, both easing in and out, and a
		// ripple spreading from where the pointer went down.
		String k = key(x, y, w, h);
		float hv = ExMotion.spring("st" + k, hover, ExMotion.EFFECTS);
		float pr = ExMotion.spring("sp" + k, pressed || ExMotion.held(x, y, w, h) ? 1f : 0f, ExMotion.EFFECTS_FAST);
		float a = 0.08f * Math.max(0f, hv) + 0.04f * Math.max(0f, pr);
		if (a > 0.004f) {
			rounded(graphics, x, y, w, h, r, ExColors.alpha(on, Math.min(0.16f, a)));
		}
		ripple(graphics, x, y, w, h, r, on);
	}

	/** A spring key for a control by where it sits. */
	static String key(float x, float y, float w, float h) {
		return Math.round(x) + "," + Math.round(y) + "," + Math.round(w) + "," + Math.round(h);
	}

	/**
	 * The press ripple, kept inside the shape: the part of its circle's square that lies in the
	 * control, rounded like a circle until it reaches the edges and like the control once it does.
	 */
	public static void ripple(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float r, int on) {
		float[] rp = ExMotion.ripple(x, y, w, h);
		if (rp == null || rp[2] < 0.5f || rp[3] < 0.004f) {
			return;
		}
		float x0 = Math.max(x, rp[0] - rp[2]);
		float y0 = Math.max(y, rp[1] - rp[2]);
		float x1 = Math.min(x + w, rp[0] + rp[2]);
		float y1 = Math.min(y + h, rp[1] + rp[2]);
		float iw = x1 - x0;
		float ih = y1 - y0;
		if (iw <= 0f || ih <= 0f) {
			return;
		}
		boolean edge = x0 <= x + 0.01f || y0 <= y + 0.01f || x1 >= x + w - 0.01f || y1 >= y + h - 0.01f;
		float half = Math.min(iw, ih) * 0.5f;
		// A circle while it is inside the control; the control's own corners once it meets an edge.
		float rr = edge ? Math.min(half, Math.max(r, half * 0.6f)) : half;
		rounded(graphics, x0, y0, iw, ih, rr, ExColors.alpha(on, rp[3]));
	}

	// ------------------------------------------------------------------ controls

	/**
	 * A switch, {@code 36 x 22} at scale 1, moving with {@code t} (0 off, 1 on): the track fills
	 * with primary and the thumb grows and carries a check.
	 */
	public static void toggle(GuiGraphicsExtractor graphics, float x, float y, float s, float t, boolean hover) {
		float w = 36f * s;
		float h = 22f * s;
		// t may overshoot a little (the spatial spring): the thumb bounces, colours stay in range.
		float tc = Math.max(0f, Math.min(1f, t));
		String k = "tg" + key(x, y, w, h);
		float hv = ExMotion.spring(k + "h", hover ? 1f : 0f, ExMotion.EFFECTS);
		float pr = ExMotion.spring(k + "p", ExMotion.held(x - 4f * s, y - 4f * s, w + 8f * s, h + 8f * s) ? 1f : 0f, ExMotion.SPATIAL_FAST);
		int track = lerp(ExColors.surfaceHighest, ExColors.primary, tc);
		rounded(graphics, x, y, w, h, h * 0.5f, track);
		if (tc < 0.999f) {
			float ring = 2f * s;
			int edge = ExColors.alpha(ExColors.outline, 1f - tc);
			outlineRound(graphics, x, y, w, h, h * 0.5f, ring, edge, track);
		}
		// The thumb: small when off, bigger when on, bigger still while hovered and held (Material's 28 dp press).
		float off = (10f + 2f * hv) * s;
		float on = (16f + 1f * hv) * s;
		float size = off + (on - off) * tc + (19f * s - (off + (on - off) * tc)) * Math.max(0f, pr);
		float pad = (h - size) * 0.5f;
		float left = x + 6f * s - (off - 10f * s) * 0.5f;
		float right = x + w - pad - size;
		float tx = left + (right - left) * t;
		int thumb = lerp(ExColors.outline, ExColors.onPrimary, tc);
		float halo = Math.max(hv, Math.max(0f, pr));
		if (halo > 0.01f) {
			GuiDraw.circle(graphics, tx + size * 0.5f, y + h * 0.5f, (12f + 2f * halo) * s, ExColors.alpha(tc > 0.5f ? ExColors.primary : ExColors.onSurface, (0.08f + 0.04f * Math.max(0f, pr)) * halo));
		}
		rounded(graphics, tx, y + pad, size, size, size * 0.5f, thumb);
		if (tc > 0.35f) {
			int check = ExColors.alpha(ExColors.primary, Math.min(1f, (tc - 0.35f) / 0.5f));
			float cx = tx + size * 0.5f;
			float cy = y + h * 0.5f;
			float u = size / 16f;
			GuiDraw.stroke(graphics, cx - 4.2f * u, cy + 0.2f * u, cx - 1.3f * u, cy + 3.0f * u, 1.9f * u, check);
			GuiDraw.stroke(graphics, cx - 1.3f * u, cy + 3.0f * u, cx + 4.3f * u, cy - 3.0f * u, 1.9f * u, check);
		}
	}

	/** A ring {@code ring} units wide inside a rounded shape, by drawing the shape and the inside over it. */
	public static void outlineRound(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float r, float ring, int edge, int inside) {
		if ((edge >>> 24) == 0) {
			return;
		}
		rounded(graphics, x, y, w, h, r, edge);
		rounded(graphics, x + ring, y + ring, w - ring * 2f, h - ring * 2f, Math.max(0f, r - ring), inside);
	}

	/**
	 * The Expressive slider: a 12-tall track, the active part in primary, a gap, a 4-wide bar
	 * handle 20 tall, the rest in secondary container. {@code y} is the top of the handle.
	 */
	public static void slider(GuiGraphicsExtractor graphics, float x, float y, float w, float t, boolean hover, float s) {
		t = Math.max(0f, Math.min(1f, t));
		float th = 12f * s;
		String k = "sl" + key(x, y, w, 20f * s);
		float hv = ExMotion.spring(k + "h", hover ? 1f : 0f, ExMotion.EFFECTS);
		float pr = ExMotion.spring(k + "p", ExMotion.held(x - 6f * s, y - 4f * s, w + 12f * s, 28f * s) ? 1f : 0f, ExMotion.SPATIAL_FAST);
		// The handle: taller on hover, narrower while dragged, as Material's does.
		float hh = (20f + 2f * hv + 2f * Math.max(0f, pr)) * s;
		float hw = Math.max(1.5f * s, (3f + 1f * hv - 1.5f * pr) * s);
		float gap = 3f * s;
		float ty = y + (20f * s - th) * 0.5f;
		float hx = x + (w - hw) * t;
		float leftW = hx - gap - x;
		float rightX = hx + hw + gap;
		float rightW = x + w - rightX;
		if (leftW > 0.5f) {
			sides(graphics, x, ty, leftW, th, th * 0.5f, 2f * s, ExColors.primary);
		}
		if (rightW > 0.5f) {
			sides(graphics, rightX, ty, rightW, th, 2f * s, th * 0.5f, ExColors.secondaryContainer);
			float dot = 4f * s;
			rounded(graphics, x + w - dot - 4f * s, ty + (th - dot) * 0.5f, dot, dot, dot * 0.5f, ExColors.primary);
		}
		rounded(graphics, hx, y + (20f * s - hh) * 0.5f, hw, hh, hw * 0.5f, ExColors.primary);
	}

	/**
	 * One button of a connected group: picked ones are full pills in primary, the rest are
	 * tonal with small inner corners and full outer ones.
	 */
	public static void segment(GuiGraphicsExtractor graphics, float x, float y, float w, float h, boolean first, boolean last, boolean picked, float hover) {
		float big = h * 0.5f;
		float small = Math.min(6f, h * 0.25f);
		// Picking morphs the corners to a pill and fills it (the spatial spring rounds a bit past).
		float pk = ExMotion.spring("sg" + key(x, y, w, h), picked ? 1f : 0f, ExMotion.SPATIAL_FAST);
		float pc = Math.max(0f, Math.min(1f, pk));
		float left = first ? big : Math.min(big, small + (big - small) * Math.max(0f, pk));
		float right = last ? big : Math.min(big, small + (big - small) * Math.max(0f, pk));
		int fill = lerp(ExColors.surfaceHighest, ExColors.primary, pc);
		sides(graphics, x, y, w, h, left, right, fill);
		float hv = ExMotion.spring("sgh" + key(x, y, w, h), hover, ExMotion.EFFECTS) * (1f - pc);
		if (hv > 0.01f) {
			sidesState(graphics, x, y, w, h, left, right, hv);
		}
		ripple(graphics, x, y, w, h, Math.min(left, right), picked ? ExColors.onPrimary : ExColors.onSurface);
	}

	private static void sidesState(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float left, float right, float hover) {
		sides(graphics, x, y, w, h, left, right, ExColors.alpha(ExColors.onSurface, 0.08f * hover));
	}

	/** A filter / input chip: picked ones are secondary container with a check, the rest outlined. */
	public static void chip(GuiGraphicsExtractor graphics, float x, float y, float w, float h, boolean picked, float hover) {
		float r = Math.min(8f, h * 0.5f);
		float pk = Math.max(0f, Math.min(1f, ExMotion.spring("ch" + Math.round(y) + "," + Math.round(h) + "," + Math.round(x / 4f), picked ? 1f : 0f, ExMotion.EFFECTS)));
		if (pk < 0.999f) {
			outlineRound(graphics, x, y, w, h, r, Math.max(0.5f, h / 32f), ExColors.alpha(ExColors.outlineVariant, 1f - pk), ExColors.surfaceMid);
		}
		if (pk > 0.001f) {
			rounded(graphics, x, y, w, h, r, ExColors.alpha(ExColors.secondaryContainer, pk));
		}
		state(graphics, x, y, w, h, r, hover, false, picked ? ExColors.onSecondaryContainer : ExColors.onSurface);
	}

	/** A filled or tonal button (pill). */
	public static void button(GuiGraphicsExtractor graphics, float x, float y, float w, float h, boolean filled, float hover, boolean pressed) {
		// Expressive buttons squash their corners while pressed and spring back.
		float pr = ExMotion.spring("bt" + key(x, y, w, h), pressed || ExMotion.held(x, y, w, h) ? 1f : 0f, ExMotion.SPATIAL_FAST);
		float full = h * 0.5f;
		float squashed = Math.min(full, Math.max(6f, h * 0.3f));
		float r = Math.max(2f, Math.min(full, full + (squashed - full) * pr));
		rounded(graphics, x, y, w, h, r, filled ? ExColors.primary : ExColors.secondaryContainer);
		state(graphics, x, y, w, h, r, hover, pressed, filled ? ExColors.onPrimary : ExColors.onSecondaryContainer);
	}

	/** A text field: surface-highest with a primary underline while typing. */
	public static void field(GuiGraphicsExtractor graphics, float x, float y, float w, float h, boolean focused, float hover) {
		float r = Math.min(10f, h * 0.35f);
		rounded(graphics, x, y, w, h, r, ExColors.surfaceHighest);
		state(graphics, x, y, w, h, r, hover, false, ExColors.onSurface);
		// The focus line grows out from the middle.
		float f = Math.max(0f, Math.min(1f, ExMotion.spring("fd" + key(x, y, w, h), focused ? 1f : 0f, ExMotion.SPATIAL)));
		if (f > 0.01f) {
			float line = Math.max(1.5f, h / 14f);
			float lw = w * f;
			if (GuiDraw.scissor(graphics, x + (w - lw) * 0.5f, y + h - line, lw, line + 1)) {
				rounded(graphics, x, y, w, h, r, ExColors.primary);
				GuiDraw.disableScissor(graphics);
			}
		}
	}

	/** A colour swatch: a rounded square, checkered under see-through colours, ringed when picked. */
	public static void swatch(GuiGraphicsExtractor graphics, float x, float y, float size, int argb, boolean picked) {
		float r = Math.max(3f, size / 3f);
		float pk = ExMotion.spring("sw" + key(x, y, size, size), picked ? 1f : 0f, ExMotion.SPATIAL_FAST);
		if (pk > 0.01f) {
			float g = Math.max(2f, size / 10f) * Math.max(0f, pk);
			rounded(graphics, x - g * 2f, y - g * 2f, size + g * 4f, size + g * 4f, r + g * 2f, ExColors.alpha(ExColors.primary, Math.min(1f, pk)));
			rounded(graphics, x - g, y - g, size + g * 2f, size + g * 2f, r + g, ExColors.surfaceHigh);
		}
		if ((argb >>> 24) < 250) {
			rounded(graphics, x, y, size, size, r, 0xFF8E8E8E);
			float half = size * 0.5f;
			float in = r * 0.3f;
			GuiDraw.fill(graphics, x + in, y + in, half - in, half - in, 0xFFC8C8C8);
			GuiDraw.fill(graphics, x + half, y + half, half - in, half - in, 0xFFC8C8C8);
		}
		rounded(graphics, x, y, size, size, r, argb);
		GuiDraw.hairline(graphics, x, y, size, size, r, 0x26FFFFFF);
	}

	/** A thin rounded scrollbar thumb on the right of a list. */
	public static void scrollbar(GuiGraphicsExtractor graphics, float x, float y, float h, float view, float content, float offset, float s) {
		if (content <= view + 0.5f) {
			return;
		}
		float w = 4f * s;
		float th = Math.max(24f * s, h * view / content);
		float ty = y + (h - th) * (offset / Math.max(1f, content - view));
		rounded(graphics, x, ty, w, th, w * 0.5f, ExColors.alpha(ExColors.onSurfaceVariant, 0.55f));
	}

	// ------------------------------------------------------------------ text

	/** Roboto Flex through FreeType, onto whole screen pixels; the game font when FreeType is missing. */
	public static void text(GuiGraphicsExtractor graphics, Font font, int family, String value, float x, float y, int color) {
		if (value == null || value.isEmpty() || (color >>> 24) < 4) {
			return;
		}
		if (UidText.ready()) {
			// UidText fades by the screen's alpha itself (fading here too made text lag its card).
			UidText.draw(graphics, family, value, x, y, color, textScale);
			return;
		}
		color = GuiDraw.fade(color);
		float em = UidText.em(family);
		var comp = family == TITLE || family == HEADLINE || family == BOLD ? MenuFont.shadowTitle(value) : MenuFont.shadow(value);
		GuiDraw.text(graphics, font, comp, x, y, em / 9f, color, false);
	}

	public static float width(Font font, int family, String value) {
		if (value == null || value.isEmpty()) {
			return 0f;
		}
		if (UidText.ready()) {
			return UidText.width(family, value, textScale);
		}
		return font.width(MenuFont.shadow(value)) * UidText.em(family) / 9f;
	}

	/** Cuts a label down with "…" to fit. */
	public static String fit(Font font, int family, String value, float max) {
		if (value == null) {
			return "";
		}
		if (width(font, family, value) <= max) {
			return value;
		}
		String dots = "…";
		int end = value.length();
		while (end > 0 && width(font, family, value.substring(0, end) + dots) > max) {
			end--;
		}
		return value.substring(0, end) + dots;
	}

	public static int lerp(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16) | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
	}
}
