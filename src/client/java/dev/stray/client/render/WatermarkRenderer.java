package dev.stray.client.render;

import dev.stray.client.util.Text;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.ui.MenuFont;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class WatermarkRenderer {
	public static final float HEIGHT = 18;

	private WatermarkRenderer() {
	}

	public static void init() {
	}

	public static float height() {
		return uid() ? UID_H * (float) (uidScale() / guiScale()) : HEIGHT;
	}

	private static boolean uid() {
		return "uid".equals(StrayConfig.get().watermarkStyle);
	}

	public static float occupiedHeight() {
		StrayConfig config = StrayConfig.get();
		return config.watermarkEnabled ? height() * HudLayout.scale(HudLayout.Id.WATERMARK) + 8 : 0;
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.gui.hud.isHidden()) {
			return;
		}
		StrayConfig config = StrayConfig.get();
		if (!config.watermarkEnabled) {
			return;
		}
		HudLayout.Box box = HudLayout.box(HudLayout.Id.WATERMARK, client.font, graphics.guiWidth(), graphics.guiHeight());
		HudChrome.element(HudLayout.Id.WATERMARK);
		draw(graphics, client.font, box.x(), box.y(), HudLayout.scale(HudLayout.Id.WATERMARK));
	}

	public static void draw(GuiGraphicsExtractor graphics, Font font, float x, float y, float scale) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		if (scale != 1.0f) {
			graphics.pose().scale(scale, scale);
		}
		if (uid()) {
			drawUid(graphics, scale);
		} else {
			drawShadow(graphics, font);
		}
		graphics.pose().popMatrix();
	}

	/**
	 * Width for a live stat (fps, ping, time) that doesn't jump around: every digit is measured
	 * as a 0, so 87 and 91 fps take the same room, and when the digit count does change
	 * (99 to 100 fps) the width eases there instead of snapping.
	 */
	private static final java.util.Map<String, double[]> STABLE = new java.util.HashMap<>();

	/**
	 * Measured every call on purpose. A cache here froze widths taken before the TTF glyphs
	 * were ready, so pills stayed the wrong size and never eased.
	 */
	private static float measuredWidth(Font font, Component text) {
		return font.width(Component.literal(Text.zeroDigits(text.getString())).withStyle(text.getStyle()));
	}

	static float stableWidth(Font font, Component text, String key) {
		float target = measuredWidth(font, text);
		long now = System.nanoTime();
		double[] state = STABLE.get(key);
		if (state == null) {
			STABLE.put(key, new double[] {target, now});
			return target;
		}
		double dt = Math.min(0.25, (now - state[1]) / 1_000_000_000.0);
		if (dt > 0) {
			state[0] += (target - state[0]) * (1.0 - Math.exp(-dt * 8.0));
			if (Math.abs(target - state[0]) < 0.02) {
				state[0] = target;
			}
			state[1] = now;
		}
		return (float) state[0];
	}

	// ---- Shadow: a see-through black pill, "Stray" in a white pill, the stats in Google Sans Flex

	private static final float SH_PAD = 3f;
	private static final float SH_PILL = HEIGHT - SH_PAD * 2f;
	private static final float SH_GAP = 9f;

	private static List<String> shadowParts() {
		StrayConfig config = StrayConfig.get();
		List<String> parts = new ArrayList<>();
		if (config.watermarkFps) {
			parts.add(HudStats.fps() + " fps");
		}
		if (config.watermarkPing) {
			parts.add(HudStats.pingLabel());
		}
		if (config.watermarkTime) {
			parts.add(HudStats.time());
		}
		if (config.watermarkName) {
			parts.add(HudStats.playerName());
		}
		return parts;
	}

	private static float shadowBrandW(Font font) {
		return 7f + font.width(MenuFont.shadow("Stray")) + 7f;
	}

	private static float shadowWidth(Font font) {
		float w = SH_PAD + shadowBrandW(font);
		List<String> parts = shadowParts();
		for (String part : parts) {
			w += SH_GAP + stableWidth(font, MenuFont.shadow(part), "shadow:" + parts.indexOf(part));
		}
		return w + (parts.isEmpty() ? SH_PAD : 8f);
	}

	private static final String[] PART_KEYS = {"shadow:0", "shadow:1", "shadow:2", "shadow:3"};

	/** One pass per frame: the parts are built and measured once, then laid out and drawn. */
	private static void drawShadow(GuiGraphicsExtractor graphics, Font font) {
		List<String> parts = shadowParts();
		int count = parts.size();
		Component brand = MenuFont.shadow("Stray");
		float bw = 7f + font.width(brand) + 7f;
		Component[] texts = new Component[count];
		float[] widths = new float[count];
		float w = SH_PAD + bw;
		for (int i = 0; i < count; i++) {
			texts[i] = MenuFont.shadow(parts.get(i));
			widths[i] = stableWidth(font, texts[i], i < PART_KEYS.length ? PART_KEYS[i] : "shadow:" + i);
			w += SH_GAP + widths[i];
		}
		w += count == 0 ? SH_PAD : 8f;
		float r = HEIGHT * 0.5f;
		if (HudChrome.background()) {
			dev.stray.client.ui.ShadowLook.panel(graphics, 0, 0, w, HEIGHT, r, dev.stray.client.ui.ShadowLook.FILL);
		}
		GuiDraw.roundedFine(graphics, SH_PAD, SH_PAD, bw, SH_PILL, SH_PILL * 0.5f, 0xFFEEF1F5);
		// Same font, size and baseline as the stats beside it, so it sits centred in its pill.
		float textY = Math.round(GuiDraw.middle(0, HEIGHT));
		GuiDraw.text(graphics, font, brand, SH_PAD + 7f, textY, 0xFF0B0E13, false);
		float x = SH_PAD + bw;
		for (int i = 0; i < count; i++) {
			if (i > 0) {
				GuiDraw.circle(graphics, x + SH_GAP * 0.5f, HEIGHT * 0.5f, 1f, 0x40FFFFFF);
			}
			x += SH_GAP;
			GuiDraw.text(graphics, font, texts[i], x, textY, i == 0 ? 0xFFEEF1F5 : 0xFF9AA4AF, false);
			x += widths[i];
		}
	}


	// ---- uid?: the classic cheat-menu watermark: a 30 px box of seven 1 px bands, then
	// "stray" with ".gay" in the menu colour and the stats, " | " between them, in sharp DejaVu.

	private static final float UID_H = 30f;
	private static final float UID_PAD = 11f;
	private static final int[] UID_BANDS = {10, 60, 40, 40, 40, 60, 20};
	private static final int UID_TEXT = 0xE6FFFFFF;

	private static double guiScale() {
		Minecraft client = Minecraft.getInstance();
		return client.getWindow() == null ? 2.0 : client.getWindow().getGuiScale();
	}

	/** Screen pixels per watermark pixel: sized for 1080p like the menu (125 % at 1440p). */
	private static double uidScale() {
		Minecraft client = Minecraft.getInstance();
		int h = client.getWindow() == null ? 1080 : client.getWindow().getHeight();
		return net.minecraft.util.Mth.clamp(Math.round(h / 1080.0 * 4.0) / 4.0, 1.0, 3.0);
	}

	/** Width in watermark pixels. */
	private static float uidWidth(double s) {
		float w = UID_PAD + dev.stray.client.ui.UidText.width(7, "stray", s) + dev.stray.client.ui.UidText.width(7, ".gay", s);
		for (String part : shadowParts()) {
			w += dev.stray.client.ui.UidText.width(7, " | ", s) + dev.stray.client.ui.UidText.width(7, Text.zeroDigits(part), s);
		}
		return w + UID_PAD;
	}

	private static void drawUid(GuiGraphicsExtractor graphics, float hudScale) {
		if (!dev.stray.client.ui.UidText.ready()) {
			return;
		}
		double gui = guiScale();
		double s = uidScale() * hudScale;
		float w = uidWidth(s);
		// Work in watermark pixels: one unit is s screen pixels.
		float unit = (float) (uidScale() / gui);
		graphics.pose().pushMatrix();
		graphics.pose().scale(unit, unit);
		// The frame on whole screen pixels: each band is round(s) pixels wide.
		org.joml.Vector2f o = graphics.pose().transformPosition(0f, 0f, new org.joml.Vector2f());
		int px = (int) Math.round(o.x * gui);
		int py = (int) Math.round(o.y * gui);
		int pw = (int) Math.round(w * s);
		int ph = (int) Math.round(UID_H * s);
		int band = Math.max(1, (int) Math.round(s));
		graphics.pose().pushMatrix();
		graphics.pose().identity();
		float inv = (float) (1.0 / gui);
		graphics.pose().scale(inv, inv);
		for (int i = 0; i < UID_BANDS.length; i++) {
			int c = UID_BANDS[i];
			int inset = i * band;
			GuiDraw.fill(graphics, px + inset, py + inset, pw - inset * 2, ph - inset * 2, 0xFF000000 | (c << 16) | (c << 8) | c);
		}
		if (StrayConfig.get().watermarkUidBar) {
			// The menu's bar just inside the bands: blue to pink to yellow, its lower half darker.
			int bx = px + band * UID_BANDS.length;
			int by = py + band * UID_BANDS.length;
			int bw = pw - band * UID_BANDS.length * 2;
			int half = bw / 2;
			dev.stray.client.ui.UidLook.horizontal(graphics, bx, by, half, band * 2, 0xFF37B1DA, 0xFFCC46CD);
			dev.stray.client.ui.UidLook.horizontal(graphics, bx + half, by, bw - half, band * 2, 0xFFCC46CD, 0xFFCCE335);
			GuiDraw.fill(graphics, bx, by + band, bw, band, 0x77000000);
		}
		graphics.pose().popMatrix();
		// Text, measured and placed in watermark pixels.
		int accent = 0xE6000000 | (StrayConfig.get().uidAccent & 0xFFFFFF);
		float x = UID_PAD;
		// Baseline 7 under y: centres the capitals in the space inside the bands (measured on screen);
		// with the bar on, in the space under the bar.
		float y = StrayConfig.get().watermarkUidBar ? 12f : 11f;
		x = uidText(graphics, "stray", x, y, UID_TEXT, s);
		x = uidText(graphics, ".gay", x, y, accent, s);
		List<String> parts = shadowParts();
		for (int i = 0; i < parts.size(); i++) {
			String part = parts.get(i);
			x = uidText(graphics, " | ", x, y, UID_TEXT, s);
			// Numbers take the room of zeros and sit right-aligned in it, so they don't jitter.
			float room = dev.stray.client.ui.UidText.width(7, Text.zeroDigits(part), s);
			float real = dev.stray.client.ui.UidText.width(7, part, s);
			int color = UID_TEXT;
			if (i == 0 && StrayConfig.get().watermarkFps && HudStats.fps() < 20) {
				color = 0xE6FF0000;
			}
			uidText(graphics, part, x + room - real, y, color, s);
			x += room;
		}
		graphics.pose().popMatrix();
	}

	private static float uidText(GuiGraphicsExtractor graphics, String text, float x, float y, int color, double s) {
		dev.stray.client.ui.UidText.draw(graphics, 7, text, x, y, color, s);
		return x + dev.stray.client.ui.UidText.width(7, text, s);
	}

	private static int widthTick = Integer.MIN_VALUE;
	private static float widthCache;

	public static float width(Font font) {
		Minecraft client = Minecraft.getInstance();
		StrayConfig config = StrayConfig.get();
		int tick = client.player == null ? -1 : client.player.tickCount;
		if (tick == widthTick && widthCache > 0f) {
			return widthCache;
		}
		float w = uid() ? uidWidth(uidScale()) * (float) (uidScale() / guiScale()) : shadowWidth(font);
		widthTick = tick;
		widthCache = w;
		return w;
	}

}
