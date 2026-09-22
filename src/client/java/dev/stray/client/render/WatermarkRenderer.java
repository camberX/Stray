package dev.stray.client.render;

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
	private static final float CLIENT_HEIGHT = 22;
	private static final float S_SCALE = 2.55f;
	private static final float TRAY_SCALE = 1.6f;
	private static final float VERSION_SCALE = 0.62f;
	/** Vanilla bitmap cells are 8px. S ink ends on the baseline; y fills the last row. */
	private static final float CAP_BOTTOM = 7f;
	private static final float DESCENDER_BOTTOM = 8f;
	/** Bitmap advance includes one empty pixel past the S ink. */
	private static final float SIDE_GAP = 1f;
	private static final float PART_GAP = 3f;
	private static final String DEV_TAG = "DEV";
	private static final float DEV_GAP = 3f;
	private static final int WHITE = 0xFFFFFFFF;

	private WatermarkRenderer() {
	}

	public static void init() {
	}

	public static float height() {
		return StrayConfig.get().watermarkClient() ? CLIENT_HEIGHT : HEIGHT;
	}

	public static float occupiedHeight() {
		StrayConfig config = StrayConfig.get();
		return config.watermarkEnabled ? height() * HudLayout.scale(HudLayout.Id.WATERMARK) + 8 : 0;
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) {
			return;
		}
		StrayConfig config = StrayConfig.get();
		if (!config.watermarkEnabled) {
			return;
		}
		HudLayout.Box box = HudLayout.box(HudLayout.Id.WATERMARK, client.font, graphics.guiWidth(), graphics.guiHeight());
		draw(graphics, client.font, box.x(), box.y(), HudLayout.scale(HudLayout.Id.WATERMARK));
	}

	public static void draw(GuiGraphicsExtractor graphics, Font font, float x, float y, float scale) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		if (scale != 1.0f) {
			graphics.pose().scale(scale, scale);
		}
		if (StrayConfig.get().watermarkClient()) {
			drawClient(graphics, font);
		} else {
			drawPanel(graphics, font);
		}
		graphics.pose().popMatrix();
	}

	private static void drawPanel(GuiGraphicsExtractor graphics, Font font) {
		List<String> parts = new ArrayList<>();
		parts.add("STRAY");
		StrayConfig config = StrayConfig.get();
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

		float pad = 7;
		float gap = 8;
		float w = width(font);

		HudChrome.panel(graphics, 0, 0, w, HEIGHT, 5, Theme.WINDOW, Theme.LINE, Theme.ACCENT);

		float cx = pad + 2;
		float textY = GuiDraw.middle(0, HEIGHT);
		for (int i = 0; i < parts.size(); i++) {
			int color = i == 0 ? Theme.ACCENT : Theme.TEXT;
			if (i == 0) {
				GuiDraw.brand(graphics, font, parts.get(i), cx, textY, color);
			} else {
				GuiDraw.menu(graphics, font, parts.get(i), cx, textY, color);
			}
			cx += partWidth(font, parts.get(i), i == 0);
			if (i == 0) {
				cx += DEV_GAP;
				GuiDraw.brandSmall(graphics, font, DEV_TAG, cx, textY, Theme.WARN);
				cx += GuiDraw.brandSmallWidth(font, DEV_TAG);
			}
			if (i + 1 < parts.size()) {
				cx += gap / 2f;
				GuiDraw.fill(graphics, cx, 4, 1, HEIGHT - 8, Theme.HUD_LINE);
				cx += gap / 2f + 1;
			}
		}
	}

	private static void drawClient(GuiGraphicsExtractor graphics, Font font) {
		Component mark = MenuFont.title("S");
		Component rest = MenuFont.title("tray");
		String version = versionLabel();
		float textX = besideS(font, mark);
		float trayY = trayTop();
		drawScaled(graphics, font, mark, 0, 0, S_SCALE, Theme.ACCENT);
		if (!version.isEmpty()) {
			Component minor = MenuFont.brand(version);
			drawScaled(graphics, font, minor, textX, 0, VERSION_SCALE, WHITE);
			float extraX = textX + font.width(minor) * VERSION_SCALE + PART_GAP;
			String extra = clientExtras(StrayConfig.get());
			if (!extra.isEmpty()) {
				drawScaled(graphics, font, MenuFont.brand(extra), extraX, 0, VERSION_SCALE, WHITE);
			}
		}
		drawScaled(graphics, font, rest, textX, trayY, TRAY_SCALE, Theme.ACCENT);
	}

	/** One screen pixel of shadow, so the big S and the smaller tray share a bottom edge. */
	private static void drawScaled(GuiGraphicsExtractor graphics, Font font, Component text, float x, float y, float scale, int color) {
		GuiDraw.text(graphics, font, text, x + 1f, y + 1f, scale, shadow(color), false);
		GuiDraw.text(graphics, font, text, x, y, scale, color, false);
	}

	private static int shadow(int color) {
		int alpha = color >>> 24;
		int red = Math.round(((color >> 16) & 0xFF) * 0.25f);
		int green = Math.round(((color >> 8) & 0xFF) * 0.25f);
		int blue = Math.round((color & 0xFF) * 0.25f);
		return (alpha << 24) | (red << 16) | (green << 8) | blue;
	}

	private static float besideS(Font font, Component mark) {
		return Math.max(0f, font.width(mark) * S_SCALE - S_SCALE + SIDE_GAP);
	}

	/** Puts the bottom of the y in tray on the bottom of S. */
	private static float trayTop() {
		return CAP_BOTTOM * S_SCALE - DESCENDER_BOTTOM * TRAY_SCALE;
	}

	private static int widthTick = Integer.MIN_VALUE;
	private static boolean widthClient;
	private static float widthCache;

	public static float width(Font font) {
		Minecraft client = Minecraft.getInstance();
		StrayConfig config = StrayConfig.get();
		int tick = client.player == null ? -1 : client.player.tickCount;
		boolean clientStyle = config.watermarkClient();
		if (tick == widthTick && widthCache > 0f && widthClient == clientStyle) {
			return widthCache;
		}
		float w = clientStyle ? clientWidth(font, config) : panelWidth(font, config);
		widthTick = tick;
		widthClient = clientStyle;
		widthCache = w;
		return w;
	}

	private static float panelWidth(Font font, StrayConfig config) {
		float w = 16 + brandExtra(font);
		w += GuiDraw.brandWidth(font, "STRAY");
		if (config.watermarkFps) {
			w += 9 + GuiDraw.menuWidth(font, "000 fps");
		}
		if (config.watermarkPing) {
			w += 9 + GuiDraw.menuWidth(font, "000 ms");
		}
		if (config.watermarkTime) {
			w += 9 + GuiDraw.menuWidth(font, "00:00");
		}
		if (config.watermarkName) {
			w += 9 + GuiDraw.menuWidth(font, HudStats.playerName());
		}
		return w;
	}

	private static float clientWidth(Font font, StrayConfig config) {
		float markW = besideS(font, MenuFont.title("S"));
		float top = 0f;
		String version = versionLabel();
		if (!version.isEmpty()) {
			top += font.width(MenuFont.brand(version)) * VERSION_SCALE;
		}
		String extra = clientExtras(config);
		if (!extra.isEmpty()) {
			if (top > 0f) {
				top += PART_GAP;
			}
			top += font.width(MenuFont.brand(extra)) * VERSION_SCALE;
		}
		float trayW = font.width(MenuFont.title("tray")) * TRAY_SCALE;
		float right = Math.max(top, trayW);
		return markW + right + 1f;
	}

	private static String clientExtras(StrayConfig config) {
		StringBuilder extra = new StringBuilder();
		if (config.watermarkFps) {
			extra.append(HudStats.fps()).append(" fps");
		}
		if (config.watermarkPing) {
			if (extra.length() > 0) {
				extra.append(' ');
			}
			extra.append(HudStats.pingLabel());
		}
		if (config.watermarkTime) {
			if (extra.length() > 0) {
				extra.append(' ');
			}
			extra.append(HudStats.time());
		}
		if (config.watermarkName) {
			if (extra.length() > 0) {
				extra.append(' ');
			}
			extra.append(HudStats.playerName());
		}
		return extra.toString();
	}

	private static String versionLabel() {
		return FabricLoader.getInstance()
			.getModContainer("stray")
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("");
	}

	private static float brandExtra(Font font) {
		return DEV_GAP + GuiDraw.brandSmallWidth(font, DEV_TAG);
	}

	private static int partWidth(Font font, String part, boolean logo) {
		return logo ? GuiDraw.brandWidth(font, part) : GuiDraw.menuWidth(font, part);
	}
}
