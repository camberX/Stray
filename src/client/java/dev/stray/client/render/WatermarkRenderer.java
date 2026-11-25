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
		return uid() ? uidHeight(uidScale() * HudLayout.scale(HudLayout.Id.WATERMARK)) * (float) (uidScale() / guiScale()) : HEIGHT;
	}

	private static boolean uid() {
		return dev.stray.client.ui.Looks.uid();
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
		} else if (HudChrome.win98()) {
			drawWin98(graphics, font);
		} else {
			drawPanels(graphics, font);
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

	/** The watermark's stats, in order: fps, ping, time, name (each when switched on). */
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

	private static final String[] PART_KEYS = {"shadow:0", "shadow:1", "shadow:2", "shadow:3"};

	// ---- Panels: a flat grey box, "stray" in the accent, the stats in white with
	// one-pixel rules between them.

	private static final float PN_PAD = 7f;
	private static final float PN_GAP = 13f;

	/** Modern writes the name in lower case; Minecraft as the game writes names. */
	private static String brandText() {
		if (dev.stray.client.ui.Looks.signature()) {
			return "stray.";
		}
		return dev.stray.client.ui.Looks.minecraft() ? "Stray" : "stray";
	}

	private static float panelsWidth(Font font) {
		float w = PN_PAD + font.width(MenuFont.shadow(brandText()));
		List<String> parts = shadowParts();
		for (int i = 0; i < parts.size(); i++) {
			w += PN_GAP + stableWidth(font, MenuFont.shadow(parts.get(i)), i < PART_KEYS.length ? PART_KEYS[i] : "shadow:" + i);
		}
		return w + PN_PAD;
	}

	private static void drawPanels(GuiGraphicsExtractor graphics, Font font) {
		List<String> parts = shadowParts();
		boolean mc = dev.stray.client.ui.Looks.minecraft();
		boolean sig = dev.stray.client.ui.Looks.signature();
		Component brand = MenuFont.shadow(brandText());
		float w = panelsWidth(font);
		if (HudChrome.background()) {
			if (mc) {
				// Minecraft: the chat box's black, square corners.
				GuiDraw.fill(graphics, 0, 0, w, HEIGHT, dev.stray.client.ui.Theme.HUD_WINDOW);
			} else if (sig) {
				dev.stray.client.ui.SignatureLook.panel(graphics, 0, 0, w, HEIGHT, dev.stray.client.ui.Theme.HUD_WINDOW);
			} else {
				dev.stray.client.ui.PanelLook.panel(graphics, 0, 0, w, HEIGHT, 0xF01C1C1C);
			}
		}
		float textY = Math.round(GuiDraw.middle(0, HEIGHT)) + (mc ? 1f : 0f);
		if (sig) {
			// "stray" in the text colour, the dot in the accent.
			Component word = MenuFont.shadow("stray");
			GuiDraw.text(graphics, font, word, PN_PAD, textY, dev.stray.client.ui.SignatureLook.TEXT, false);
			GuiDraw.text(graphics, font, MenuFont.shadow("."), PN_PAD + font.width(word), textY, dev.stray.client.ui.SignatureLook.accent(), false);
		} else {
			GuiDraw.text(graphics, font, brand, PN_PAD, textY, mc ? dev.stray.client.ui.MinecraftLook.YELLOW : dev.stray.client.ui.PanelLook.accent(), false);
		}
		float x = PN_PAD + font.width(brand);
		for (int i = 0; i < parts.size(); i++) {
			Component text = MenuFont.shadow(parts.get(i));
			GuiDraw.fillSmooth(graphics, x + PN_GAP * 0.5f - 0.5f, 5f, 1f, HEIGHT - 10f, mc ? dev.stray.client.ui.MinecraftLook.LINE : sig ? dev.stray.client.ui.SignatureLook.LINE : dev.stray.client.ui.PanelLook.EDGE);
			x += PN_GAP;
			GuiDraw.text(graphics, font, text, x, textY, sig ? dev.stray.client.ui.SignatureLook.SOFT : 0xFFFFFFFF, false);
			x += stableWidth(font, text, i < PART_KEYS.length ? PART_KEYS[i] : "shadow:" + i);
		}
	}

	private static int parseInt(String value) {
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return Integer.MAX_VALUE;
		}
	}

	// ---- uid?: the classic cheat-menu watermark: a 30 px box of seven 1 px bands, then
	// "stray" with ".gay" in the menu colour and the stats, " | " between them, in sharp DejaVu.

	/** Inside the bands: 16 px tall, text 4 px from the sides (the original's 30 / 11 minus its 7 bands). */
	private static final float UID_INNER_H = 16f;
	private static final float UID_INNER_PAD = 4f;

	/** One grey band in screen pixels: half the scale, at least 1, so the outline stays thin. */
	/** How many screen pixels each half of the uid? colour strip is (the watermark's, for every strip). */
	public static int uidBarPx() {
		return Math.max(1, (int) Math.round(uidScale()));
	}

	/** How many screen pixels one band of the uid? frame is: every uid? frame uses the watermark's. */
	public static int uidBandPx() {
		return band(uidScale());
	}

	private static int band(double s) {
		return Math.max(1, (int) Math.round(s * 0.5));
	}

	/** The bands' total thickness on one side, in watermark pixels. */
	private static float frame(double s) {
		return UID_BANDS.length * band(s) / (float) s;
	}

	/** Height in watermark pixels: the bands hug the inside, so thinner bands mean a shorter box. */
	private static float uidHeight(double s) {
		return UID_INNER_H + frame(s) * 2f;
	}
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
		float w = frame(s) + UID_INNER_PAD + dev.stray.client.ui.UidText.width(7, "stray", s) + dev.stray.client.ui.UidText.width(7, ".gay", s);
		for (String part : shadowParts()) {
			w += dev.stray.client.ui.UidText.width(7, " | ", s) + dev.stray.client.ui.UidText.width(7, Text.zeroDigits(part), s);
		}
		return w + UID_INNER_PAD + frame(s);
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
		int ph = (int) Math.round(uidHeight(s) * s);
		// The grey bands at half the scale (thinner outline); the bar keeps the full scale.
		int band = band(s);
		int barPx = Math.max(1, (int) Math.round(s));
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
			dev.stray.client.ui.UidLook.band(graphics, bx, by, bw, barPx);
		}
		graphics.pose().popMatrix();
		// Text, measured and placed in watermark pixels.
		int accent = 0xE6000000 | (StrayConfig.get().uidAccent & 0xFFFFFF);
		float x = frame(s) + UID_INNER_PAD;
		// Capitals centred in the space inside the bands (under the bar when it's on), worked out
		// from the real cap height at this size; text y is 7 units above its baseline.
		float top = UID_BANDS.length * band / (float) s + (StrayConfig.get().watermarkUidBar ? 2f * barPx / (float) s : 0f);
		float bottom = uidHeight(s) - UID_BANDS.length * band / (float) s;
		float cap = dev.stray.client.ui.UidText.capHeight(7, s) / (float) s;
		float y = (top + bottom) / 2f + cap / 2f - 7f;
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

	// ---- Windows 98: a little taskbar, Start button and a tray with the stats

	private static List<String> win98Parts() {
		StrayConfig config = StrayConfig.get();
		List<String> parts = new ArrayList<>();
		if (config.watermarkFps) {
			parts.add(HudStats.fps() + " fps");
		}
		if (config.watermarkPing) {
			parts.add(HudStats.pingLabel());
		}
		if (config.watermarkName) {
			parts.add(HudStats.playerName());
		}
		if (config.watermarkTime) {
			parts.add(HudStats.time());
		}
		return parts;
	}

	private static float win98StartW(Font font) {
		return dev.stray.client.ui.Win98.boldWidth(font, "Stray") + 21f;
	}

	private static float win98TrayW(Font font, List<String> parts) {
		float w = 6f;
		for (int i = 0; i < parts.size(); i++) {
			w += dev.stray.client.ui.Win98.width(font, Text.zeroDigits(parts.get(i)));
			if (i + 1 < parts.size()) {
				w += 9f;
			}
		}
		return parts.isEmpty() ? 0f : w + 2f;
	}

	private static float win98Width(Font font) {
		List<String> parts = win98Parts();
		float w = 3f + win98StartW(font) + 3f;
		if (!parts.isEmpty()) {
			w += win98TrayW(font, parts) + 3f;
		}
		return w;
	}

	private static void drawWin98(GuiGraphicsExtractor graphics, Font font) {
		float w = win98Width(font);
		if (HudChrome.background()) {
			dev.stray.client.ui.Win98.raised(graphics, 0, 0, w, HEIGHT);
		}
		float by = 2f;
		float bh = HEIGHT - 4f;
		float startW = win98StartW(font);
		dev.stray.client.ui.Win98.raised(graphics, 3f, by, startW, bh);
		float lx = 7f;
		float ly = by + (bh - 9f) / 2f;
		GuiDraw.fill(graphics, lx, ly, 4, 4, 0xFFE0402A);
		GuiDraw.fill(graphics, lx + 5, ly, 4, 4, 0xFF34A853);
		GuiDraw.fill(graphics, lx + 5, ly + 5, 4, 4, 0xFFF9BB2D);
		GuiDraw.fill(graphics, lx, ly + 5, 4, 4, 0xFF2D7CF6);
		dev.stray.client.ui.Win98.bold(graphics, font, "Stray", 20f, by + (bh - 8f) / 2f, dev.stray.client.ui.Win98.TEXT);
		List<String> parts = win98Parts();
		if (parts.isEmpty()) {
			return;
		}
		float tx = 3f + startW + 3f;
		float tw = win98TrayW(font, parts);
		dev.stray.client.ui.Win98.shallow(graphics, tx, by, tw, bh, dev.stray.client.ui.Win98.FACE);
		float cx = tx + 4f;
		for (int i = 0; i < parts.size(); i++) {
			String part = parts.get(i);
			dev.stray.client.ui.Win98.text(graphics, font, part, cx, by + (bh - 8f) / 2f, dev.stray.client.ui.Win98.TEXT);
			cx += dev.stray.client.ui.Win98.width(font, Text.zeroDigits(part));
			if (i + 1 < parts.size()) {
				cx += 4f;
				GuiDraw.fill(graphics, cx, by + 3, 1, bh - 6, dev.stray.client.ui.Win98.SHADOW);
				GuiDraw.fill(graphics, cx + 1, by + 3, 1, bh - 6, dev.stray.client.ui.Win98.WHITE);
				cx += 5f;
			}
		}
	}

	private static int widthTick = Integer.MIN_VALUE;
	private static float widthCache;
	private static String widthLook = "";

	public static float width(Font font) {
		Minecraft client = Minecraft.getInstance();
		StrayConfig config = StrayConfig.get();
		int tick = client.player == null ? -1 : client.player.tickCount;
		String look = dev.stray.client.ui.Looks.current();
		if (tick == widthTick && widthCache > 0f && look.equals(widthLook)) {
			return widthCache;
		}
		float w = uid() ? uidWidth(uidScale() * HudLayout.scale(HudLayout.Id.WATERMARK)) * (float) (uidScale() / guiScale()) : HudChrome.win98() ? win98Width(font) : panelsWidth(font);
		widthTick = tick;
		widthCache = w;
		widthLook = look;
		return w;
	}

}
