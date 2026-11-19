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
		ShadowLook.shadow(graphics, x, y + Math.min(3f, h * 0.05f), w, h, rr, Math.min(w, h) < 40f ? 6 : 12, Math.round(0x50 * a / 255f));
		if (frost) {
			GuiFrostBlur.blitWindow(graphics, x, y, w, h, rr);
		}
		rounded(graphics, x, y, w, h, rr, fill);
		GuiDraw.hairline(graphics, x, y, w, h, rr, ExColors.alpha(ExColors.outlineVariant, 0.55f * a / 255f));
	}

	/** The hover / press tint Material lays over anything you can click. */
	public static void state(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float r, float hover, boolean pressed, int on) {
		float a = pressed ? 0.12f : 0.08f * hover;
		if (a > 0.004f) {
			rounded(graphics, x, y, w, h, r, ExColors.alpha(on, a));
		}
	}

	// ------------------------------------------------------------------ controls

	/**
	 * A switch, {@code 36 x 22} at scale 1, moving with {@code t} (0 off, 1 on): the track fills
	 * with primary and the thumb grows and carries a check.
	 */
	public static void toggle(GuiGraphicsExtractor graphics, float x, float y, float s, float t, boolean hover) {
		float w = 36f * s;
		float h = 22f * s;
		int track = lerp(ExColors.surfaceHighest, ExColors.primary, t);
		rounded(graphics, x, y, w, h, h * 0.5f, track);
		if (t < 0.999f) {
			float ring = 2f * s;
			int edge = ExColors.alpha(ExColors.outline, 1f - t);
			outlineRound(graphics, x, y, w, h, h * 0.5f, ring, edge, track);
		}
		float off = (hover ? 12f : 10f) * s;
		float on = 16f * s;
		float size = off + (on - off) * t;
		float pad = (h - size) * 0.5f;
		float left = x + 6f * s - (off - 10f * s) * 0.5f;
		float right = x + w - pad - size;
		float tx = left + (right - left) * t;
		int thumb = lerp(ExColors.outline, ExColors.onPrimary, t);
		if (hover) {
			GuiDraw.circle(graphics, tx + size * 0.5f, y + h * 0.5f, 14f * s, ExColors.alpha(t > 0.5f ? ExColors.primary : ExColors.onSurface, 0.10f));
		}
		rounded(graphics, tx, y + pad, size, size, size * 0.5f, thumb);
		if (t > 0.35f) {
			int check = ExColors.alpha(ExColors.primary, Math.min(1f, (t - 0.35f) / 0.5f));
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
		float hh = (hover ? 22f : 20f) * s;
		float hw = (hover ? 4f : 3f) * s;
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
		float left = picked || first ? big : small;
		float right = picked || last ? big : small;
		int fill = picked ? ExColors.primary : ExColors.surfaceHighest;
		sides(graphics, x, y, w, h, left, right, fill);
		if (hover > 0.01f && !picked) {
			sidesState(graphics, x, y, w, h, left, right, hover);
		}
	}

	private static void sidesState(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float left, float right, float hover) {
		sides(graphics, x, y, w, h, left, right, ExColors.alpha(ExColors.onSurface, 0.08f * hover));
	}

	/** A filter / input chip: picked ones are secondary container with a check, the rest outlined. */
	public static void chip(GuiGraphicsExtractor graphics, float x, float y, float w, float h, boolean picked, float hover) {
		float r = Math.min(8f, h * 0.5f);
		if (picked) {
			rounded(graphics, x, y, w, h, r, ExColors.secondaryContainer);
		} else {
			outlineRound(graphics, x, y, w, h, r, Math.max(0.5f, h / 32f), ExColors.outlineVariant, ExColors.surfaceMid);
		}
		state(graphics, x, y, w, h, r, hover, false, picked ? ExColors.onSecondaryContainer : ExColors.onSurface);
	}

	/** A filled or tonal button (pill). */
	public static void button(GuiGraphicsExtractor graphics, float x, float y, float w, float h, boolean filled, float hover, boolean pressed) {
		float r = h * 0.5f;
		if (pressed) {
			r = Math.min(r, Math.max(6f, h * 0.3f));
		}
		rounded(graphics, x, y, w, h, r, filled ? ExColors.primary : ExColors.secondaryContainer);
		state(graphics, x, y, w, h, r, hover, pressed, filled ? ExColors.onPrimary : ExColors.onSecondaryContainer);
	}

	/** A text field: surface-highest with a primary underline while typing. */
	public static void field(GuiGraphicsExtractor graphics, float x, float y, float w, float h, boolean focused, float hover) {
		float r = Math.min(10f, h * 0.35f);
		rounded(graphics, x, y, w, h, r, ExColors.surfaceHighest);
		state(graphics, x, y, w, h, r, hover, false, ExColors.onSurface);
		if (focused) {
			float line = Math.max(1.5f, h / 14f);
			if (GuiDraw.scissor(graphics, x, y + h - line, w, line + 1)) {
				rounded(graphics, x, y, w, h, r, ExColors.primary);
				GuiDraw.disableScissor(graphics);
			}
		}
	}

	/** A colour swatch: a rounded square, checkered under see-through colours, ringed when picked. */
	public static void swatch(GuiGraphicsExtractor graphics, float x, float y, float size, int argb, boolean picked) {
		float r = Math.max(3f, size / 3f);
		if (picked) {
			float g = Math.max(2f, size / 10f);
			rounded(graphics, x - g * 2f, y - g * 2f, size + g * 4f, size + g * 4f, r + g * 2f, ExColors.primary);
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
		color = GuiDraw.fade(color);
		if (UidText.ready()) {
			UidText.draw(graphics, family, value, x, y, color, textScale);
			return;
		}
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
