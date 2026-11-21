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
	public static int TEXT = 0xFFEEF1F5;
	public static int MUTED = 0xFF8A94A0;
	public static int FAINT = 0xFF5A636E;
	public static int LINE = 0x1CFFFFFF;
	public static int CARD = 0x0EFFFFFF;
	public static int CARD_HOVER = 0x1AFFFFFF;
	public static int SELECTED = 0x22FFFFFF;
	public static int SELECTED_LINE = 0x30FFFFFF;
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

	/** Text and card colours for the look in use: the screens that read them follow it. */
	static void look() {
		if (Looks.uid()) {
			TEXT = UidLook.LABEL;
			MUTED = UidLook.VALUE;
			FAINT = 0xFF646464;
			LINE = UidLook.EDGE;
			CARD = 0xFF1F1F1F;
			CARD_HOVER = 0xFF292929;
			SELECTED = 0xFF2E2E2E;
			SELECTED_LINE = 0xFF3C3C3C;
			FILL = 0xF0171717;
		} else if (Looks.minecraft()) {
			TEXT = MinecraftLook.WHITE;
			MUTED = MinecraftLook.GREY;
			FAINT = MinecraftLook.DGREY;
			LINE = MinecraftLook.LINE;
			CARD = 0x18FFFFFF;
			CARD_HOVER = 0x2CFFFFFF;
			SELECTED = 0x40FFFFFF;
			SELECTED_LINE = MinecraftLook.WHITE;
			FILL = MinecraftLook.BG;
		} else if (Looks.panels()) {
			TEXT = PanelLook.WHITE;
			MUTED = PanelLook.GREY;
			FAINT = PanelLook.DGREY;
			LINE = PanelLook.EDGE;
			CARD = PanelLook.G2;
			CARD_HOVER = PanelLook.G3;
			SELECTED = PanelLook.G3;
			SELECTED_LINE = PanelLook.accent();
			FILL = Theme.withAlpha(0x1C1C1C, Math.round(StrayConfig.clamp(StrayConfig.get().hudOpacity, 0.2f, 1f) * 255f));
		} else if (Looks.ex()) {
			TEXT = ExColors.onSurface;
			MUTED = ExColors.onSurfaceVariant;
			FAINT = ExColors.outline;
			LINE = ExColors.outlineVariant;
			CARD = ExColors.surfaceHigh;
			CARD_HOVER = ExColors.surfaceHighest;
			SELECTED = ExColors.secondaryContainer;
			SELECTED_LINE = ExColors.secondary;
			FILL = ExColors.alpha(ExColors.surfaceMid, ExColors.opacity());
		} else {
			TEXT = 0xFFEEF1F5;
			MUTED = 0xFF8A94A0;
			FAINT = 0xFF5A636E;
			LINE = 0x1CFFFFFF;
			CARD = 0x0EFFFFFF;
			CARD_HOVER = 0x1AFFFFFF;
			SELECTED = 0x22FFFFFF;
			SELECTED_LINE = 0x30FFFFFF;
		}
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
		if (Looks.uid() && !UidMenu.drawing()) {
			UidLook.frame(graphics, x, y, w, h, (fill >>> 24) & 0xFF);
			return;
		}
		if (Looks.ex()) {
			ExLook.surface(graphics, x, y, w, h, exRadius(radius, w, h), exFill(fill, ExColors.surfaceMid), frost);
			return;
		}
		if (Looks.panels()) {
			PanelLook.panel(graphics, x, y, w, h, fill);
			return;
		}
		if (Looks.minecraft()) {
			MinecraftLook.panel(graphics, x, y, w, h, fill);
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

	/** Expressive rounds about twice as much as Shadow: 12 becomes 22, the HUD's 8 becomes 14. */
	static float exRadius(float radius, float w, float h) {
		float r = radius >= WINDOW_R ? 22f : radius >= HUD_R ? 14f : Math.max(radius * 2f, 8f);
		return Math.min(r, Math.min(w, h) * 0.5f);
	}

	/**
	 * A fill meant as Shadow's see-through black becomes the look's own surface at the same
	 * alpha (so fades still work); a real colour passes through.
	 */
	static int exFill(int fill, int surface) {
		int r = (fill >> 16) & 0xFF;
		int g = (fill >> 8) & 0xFF;
		int b = fill & 0xFF;
		if (r < 0x1C && g < 0x1C && b < 0x22) {
			int a = (fill >>> 24) & 0xFF;
			return Theme.withAlpha(surface, Math.min(255, Math.round(a * ExColors.opacity() / 0.78f)));
		}
		return fill;
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
		// All nine pieces as one GUI element: each separate blit was its own overlap search.
		float[] q = new float[9 * 8];
		int[] at = {0};
		piece(q, at, tex, X, Y, cs, cs, 0, 0, n, n);
		piece(q, at, tex, X + W - cs, Y, cs, cs, far, 0, n, n);
		piece(q, at, tex, X, Y + H - cs, cs, cs, 0, far, n, n);
		piece(q, at, tex, X + W - cs, Y + H - cs, cs, cs, far, far, n, n);
		if (mw > 0f) {
			piece(q, at, tex, X + cs, Y, mw, cs, n, 0, 1, n);
			piece(q, at, tex, X + cs, Y + H - cs, mw, cs, n, far, 1, n);
		}
		if (mh > 0f) {
			piece(q, at, tex, X, Y + cs, cs, mh, 0, n, n, 1);
			piece(q, at, tex, X + W - cs, Y + cs, cs, mh, far, n, n, 1);
		}
		if (mw > 0f && mh > 0f) {
			piece(q, at, tex, X + cs, Y + cs, mw, mh, n, n, 1, 1);
		}
		GuiDraw.quads(graphics, tex.id(), new org.joml.Matrix3x2f(graphics.pose()), q, at[0] / 8, color);
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

	private static void piece(float[] q, int[] at, Soft tex, float x, float y, float w, float h, int u, int v, int rw, int rh) {
		if (w <= 0f || h <= 0f) {
			return;
		}
		float s = tex.size();
		int i = at[0];
		q[i] = x;
		q[i + 1] = y;
		q[i + 2] = x + w;
		q[i + 3] = y + h;
		q[i + 4] = u / s;
		q[i + 5] = v / s;
		q[i + 6] = (u + rw) / s;
		q[i + 7] = (v + rh) / s;
		at[0] = i + 8;
	}

	/** A row, button or slot inside a panel: no shadow, faint white fill. */
	public static void card(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, boolean hover, boolean selected) {
		if (w < 1f || h < 1f) {
			return;
		}
		if (Looks.uid() && !UidMenu.drawing()) {
			UidLook.control(graphics, x, y, w, h, hover, selected);
			return;
		}
		if (Looks.ex()) {
			// Wide small controls are pills; square ones (slots) are rounded squares.
			float r = h <= 24f ? (w > h * 1.3f ? h * 0.5f : Math.max(3f, h * 0.28f)) : exRadius(radius, w, h);
			ExLook.rounded(graphics, x, y, w, h, r, selected ? ExColors.secondaryContainer : hover ? ExColors.surfaceHighest : ExColors.surfaceHigh);
			return;
		}
		if (Looks.panels()) {
			PanelLook.control(graphics, x, y, w, h, hover, selected);
			return;
		}
		if (Looks.minecraft()) {
			MinecraftLook.control(graphics, x, y, w, h, hover, selected);
			return;
		}
		float r = Math.min(Math.max(1f, radius), Math.min(w, h) * 0.5f);
		GuiDraw.roundedFine(graphics, x, y, w, h, r, selected ? SELECTED : hover ? CARD_HOVER : CARD);
		if (selected) {
			GuiDraw.hairline(graphics, x, y, w, h, r, SELECTED_LINE);
		}
	}
}
