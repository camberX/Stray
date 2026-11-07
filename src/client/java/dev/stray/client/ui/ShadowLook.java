package dev.stray.client.ui;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.render.GuiDraw;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The "Shadow" style, after the profile viewer window: see-through black panels with
 * a soft shadow falling outward from their edges, a faint white hairline, and white
 * and gray text. It reuses the Click layouts; only the panels and colours change.
 */
public final class ShadowLook {
	public static final int TEXT = 0xFFEEF1F5;
	public static final int MUTED = 0xFF8A94A0;
	public static final int FAINT = 0xFF5A636E;
	public static final int LINE = 0x1CFFFFFF;
	public static final int CARD = 0x0EFFFFFF;
	public static final int CARD_HOVER = 0x1AFFFFFF;
	public static final int SELECTED = 0x22FFFFFF;
	public static final int SELECTED_LINE = 0x30FFFFFF;
	private static final int BLACK = 0x050709;

	/** Corner radii, so every Shadow surface rounds the same way: menus and screens, HUD panels, controls, slots. */
	public static final float WINDOW_R = 12f;
	public static final float HUD_R = 8f;
	public static final float CONTROL_R = 6f;
	public static final float SLOT_R = 3f;

	private ShadowLook() {
	}

	/** The one see-through black every Shadow surface uses (HUD, chat, GUIs, menu); its alpha is the opacity setting. */
	public static int FILL = 0xC8050709;

	/** Panel edge: the faint white line, or the accent when accent outlines are on. */
	public static int edge() {
		return StrayConfig.get().accentOutlines ? Theme.withAlpha(Theme.ACCENT, 0x90) : LINE;
	}

	/** Picks up the opacity setting; {@link Theme#refresh} calls it. */
	static void refresh() {
		FILL = Theme.withAlpha(BLACK, Math.round(StrayConfig.clamp(StrayConfig.get().shadowOpacity, 0.2f, 1f) * 255f));
	}

	/** Panel fill at the given opacity (0..1): black, mostly see-through. */
	public static int fill(float opacity) {
		return Theme.withAlpha(BLACK, Math.round(StrayConfig.clamp(opacity, 0f, 1f) * 0.86f * 255f));
	}

	/** A window or HUD panel: shadow outside the edge, translucent black inside, hairline edge. */
	/** A panel: frosted behind when the Blur setting is on (menus, screens, HUD and chat alike). */
	public static void panel(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, int fill) {
		panel(graphics, x, y, w, h, radius, fill, true);
	}

	/** A menu window; the same as {@link #panel}. */
	public static void window(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, int fill) {
		panel(graphics, x, y, w, h, radius, fill, true);
	}

	private static void panel(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, int fill, boolean frost) {
		if (w < 2f || h < 2f) {
			return;
		}
		float r = Math.min(Math.max(2f, radius), Math.min(w, h) * 0.5f);
		shadow(graphics, x, y, w, h, r, Math.min(w, h) < 40f ? 8 : 12, 0x60);
		if (frost) {
			dev.stray.client.render.GuiFrostBlur.blitWindow(graphics, x, y, w, h, r);
		}
		GuiDraw.roundedFine(graphics, x, y, w, h, r, fill);
		GuiDraw.hairline(graphics, x, y, w, h, r, edge());
	}

	/** Texels per GUI unit in the generated shadow textures. */
	private static final int RES = 4;
	/** Made shadow textures, most recently used last; the oldest are freed past 48. */
	private static final java.util.LinkedHashMap<Long, Soft> SOFTS = new java.util.LinkedHashMap<>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(java.util.Map.Entry<Long, Soft> eldest) {
			if (size() <= 48) {
				return false;
			}
			net.minecraft.client.Minecraft.getInstance().getTextureManager().release(eldest.getValue().id());
			return true;
		}
	};

	/** One generated shadow texture: a blurred rounded rectangle for one (reach, corner radius) pair. */
	private record Soft(net.minecraft.resources.Identifier id, int corner, int size) {
	}

	/**
	 * A soft drop shadow that follows the panel's rounded corners: a blurred rounded
	 * rectangle drawn as nine pieces, so it is one smooth gradient at any size.
	 * {@code extent} is how far it reaches past the edge, {@code strength} its darkest
	 * alpha (under the panel), {@code r} the panel's corner radius.
	 */
	public static void shadow(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float r, int extent, int strength) {
		if (strength <= 0 || w <= 0f || h <= 0f) {
			return;
		}
		soft(graphics, x, y, w, h, r, extent, Math.min(255, strength) << 24, true);
	}

	/** The same soft falloff in a colour (with its alpha), centred on the box: a glow. */
	public static void glow(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float r, int extent, int argb) {
		if ((argb >>> 24) == 0 || w <= 0f || h <= 0f) {
			return;
		}
		soft(graphics, x, y, w, h, r, extent, argb, false);
	}

	private static void soft(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, int extent, int color, boolean hollow) {
		float r = Math.max(0f, Math.min(radius, Math.min(w, h) * 0.5f));
		float e = Math.max(1f, extent);
		Soft tex = texture(e, r, hollow);
		if (tex == null) {
			return;
		}
		// Corner pieces cover the reach plus the corner radius, so the curve is in the texture.
		float c = tex.corner() / (float) RES;
		float X = x - e;
		float Y = y - e;
		float W = w + e * 2f;
		float H = h + e * 2f;
		float cs = Math.min(c, Math.min(W, H) * 0.5f);
		float mw = W - cs * 2f;
		float mh = H - cs * 2f;
		int n = tex.corner();
		int size = tex.size();
		int far = size - n;
		piece(graphics, tex, X, Y, cs, cs, 0, 0, n, n, color);
		piece(graphics, tex, X + W - cs, Y, cs, cs, far, 0, n, n, color);
		piece(graphics, tex, X, Y + H - cs, cs, cs, 0, far, n, n, color);
		piece(graphics, tex, X + W - cs, Y + H - cs, cs, cs, far, far, n, n, color);
		if (mw > 0f) {
			piece(graphics, tex, X + cs, Y, mw, cs, n, 0, 1, n, color);
			piece(graphics, tex, X + cs, Y + H - cs, mw, cs, n, far, 1, n, color);
		}
		if (mh > 0f) {
			piece(graphics, tex, X, Y + cs, cs, mh, 0, n, n, 1, color);
			piece(graphics, tex, X + W - cs, Y + cs, cs, mh, far, n, n, 1, color);
		}
		if (mw > 0f && mh > 0f) {
			piece(graphics, tex, X + cs, Y + cs, mw, mh, n, n, 1, 1, color);
		}
	}

	/** The shadow texture for this reach and radius (made once, then reused). */
	private static Soft texture(float e, float r, boolean hollow) {
		// Whole units: animated sizes (opening sheets, glows) would otherwise make a new
		// texture for every in-between radius.
		int eq = Math.round(e) * 2;
		int rq = Math.round(r) * 2;
		long key = ((long) eq << 32) | ((long) rq << 1) | (hollow ? 1L : 0L);
		Soft cached = SOFTS.get(key);
		if (cached != null) {
			return cached;
		}
		float reach = eq / 2f;
		float radius = rq / 2f;
		int n = Math.max(2, Math.min(256, (int) Math.ceil((reach + radius) * RES)));
		int size = n * 2 + 2;
		// The texture is a rounded rectangle inset by the reach, with the 2-texel middle
		// stretched to the panel's size: alpha falls off with distance from its edge.
		float half = size / (2f * RES);
		float inner = half - reach;
		com.mojang.blaze3d.platform.NativeImage image = new com.mojang.blaze3d.platform.NativeImage(size, size, false);
		for (int py = 0; py < size; py++) {
			for (int px = 0; px < size; px++) {
				float qx = Math.abs((px + 0.5f) / RES - half) - (inner - radius);
				float qy = Math.abs((py + 0.5f) / RES - half) - (inner - radius);
				float ox = Math.max(qx, 0f);
				float oy = Math.max(qy, 0f);
				float outside = (float) Math.sqrt(ox * ox + oy * oy);
				float d = outside + Math.min(Math.max(qx, qy), 0f) - radius;
				float t = clamp01((d + reach * 0.35f) / (reach * 1.35f));
				float a = 1f - t * t * (3f - 2f * t);
				a *= a;
				// Shadows leave the inside empty: the panel's own fill (and the opacity setting) decides
				// how much shows through. Glows stay filled, so a lit button has no dark rim.
				if (hollow) {
					a *= clamp01(d * RES + 0.5f);
				}
				image.setPixel(px, py, (Math.round(a * 255f) << 24) | 0xFFFFFF);
			}
		}
		net.minecraft.resources.Identifier id = dev.stray.Stray.id("dynamic/shadow_" + eq + "_" + rq + (hollow ? "_h" : ""));
		net.minecraft.client.Minecraft.getInstance().getTextureManager().register(id, new net.minecraft.client.renderer.texture.DynamicTexture(() -> "stray-shadow", image));
		Soft made = new Soft(id, n, size);
		SOFTS.put(key, made);
		return made;
	}

	private static float clamp01(float v) {
		return v < 0f ? 0f : v > 1f ? 1f : v;
	}

	private static void piece(GuiGraphicsExtractor graphics, Soft tex, float x, float y, float w, float h, int u, int v, int rw, int rh, int color) {
		GuiDraw.blit(graphics, tex.id(), x, y, w, h, u, v, rw, rh, tex.size(), tex.size(), color);
	}

	/** A row, button or slot inside a panel: no shadow, faint white fill. */
	public static void card(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, boolean hover, boolean selected) {
		if (w < 1f || h < 1f) {
			return;
		}
		float r = Math.min(Math.max(1f, radius), Math.min(w, h) * 0.5f);
		GuiDraw.roundedFine(graphics, x, y, w, h, r, selected ? SELECTED : hover ? CARD_HOVER : CARD);
		if (selected) {
			GuiDraw.hairline(graphics, x, y, w, h, r, SELECTED_LINE);
		}
	}
}
