package dev.stray.client.render;

import dev.stray.client.util.Text;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.ui.MenuFont;
import dev.stray.client.ui.Theme;
import net.fabricmc.loader.api.FabricLoader;
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
		return HEIGHT;
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
		drawShadow(graphics, font);
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

	// ---- Shadow: a see-through black pill, "Stray" in a white pill, the stats in Inter

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

	private static void drawShadow(GuiGraphicsExtractor graphics, Font font) {
		float w = shadowWidth(font);
		float r = HEIGHT * 0.5f;
		if (HudChrome.background()) {
			dev.stray.client.ui.ShadowLook.panel(graphics, 0, 0, w, HEIGHT, r, dev.stray.client.ui.ShadowLook.FILL);
		}
		float bw = shadowBrandW(font);
		GuiDraw.roundedFine(graphics, SH_PAD, SH_PAD, bw, SH_PILL, SH_PILL * 0.5f, 0xFFEEF1F5);
		// Same font, size and baseline as the stats beside it, so it sits centred in its pill.
		GuiDraw.text(graphics, font, MenuFont.shadow("Stray"), SH_PAD + 7f, Math.round(GuiDraw.middle(0, HEIGHT)), 0xFF0B0E13, false);
		float x = SH_PAD + bw;
		List<String> parts = shadowParts();
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				GuiDraw.circle(graphics, x + SH_GAP * 0.5f, HEIGHT * 0.5f, 1f, 0x40FFFFFF);
			}
			x += SH_GAP;
			GuiDraw.text(graphics, font, MenuFont.shadow(parts.get(i)), x, Math.round(GuiDraw.middle(0, HEIGHT)), i == 0 ? 0xFFEEF1F5 : 0xFF9AA4AF, false);
			x += stableWidth(font, MenuFont.shadow(parts.get(i)), "shadow:" + i);
		}
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
		float w = shadowWidth(font);
		widthTick = tick;
		widthCache = w;
		return w;
	}

}
