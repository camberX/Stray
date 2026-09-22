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
	private static final float CLIENT_HEIGHT = 20;
	private static final float NAME_SCALE = 2f;
	private static final float VERSION_DROP = 1f;
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
		Component name = MenuFont.title("Stray");
		String version = versionLabel();
		float nameW = font.width(name) * NAME_SCALE;
		GuiDraw.text(graphics, font, name, 0, 0, NAME_SCALE, Theme.ACCENT, true);
		float x = nameW + PART_GAP;
		if (!version.isEmpty()) {
			Component minor = MenuFont.brand(version);
			GuiDraw.text(graphics, font, minor, x, VERSION_DROP, WHITE, true);
			x += font.width(minor) + PART_GAP;
		}
		String extra = clientExtras(StrayConfig.get());
		if (!extra.isEmpty()) {
			GuiDraw.text(graphics, font, MenuFont.brand(extra), x, VERSION_DROP, WHITE, true);
		}
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
		float w = font.width(MenuFont.title("Stray")) * NAME_SCALE;
		String version = versionLabel();
		if (!version.isEmpty()) {
			w += PART_GAP + font.width(MenuFont.brand(version));
		}
		String extra = clientExtras(config);
		if (!extra.isEmpty()) {
			w += PART_GAP + font.width(MenuFont.brand(extra));
		}
		return w;
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
