package dev.stray.client.render;

import dev.stray.client.mixin.BossHealthOverlayAccessor;
import dev.stray.client.ui.MenuFont;
import dev.stray.client.ui.Theme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

public final class BossBarHudRenderer {
	private static final float BAR_W = 184;
	private static final float BAR_H = 16;
	private static final float GAP = 4;
	private static final int MAX = 5;
	private static int eventsTick = Integer.MIN_VALUE;
	private static List<LerpingBossEvent> eventsCache = List.of();
	private static final IdentityHashMap<LerpingBossEvent, Label> LABELS = new IdentityHashMap<>();

	private BossBarHudRenderer() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		List<LerpingBossEvent> events = events();
		if (events.isEmpty() && !HudLayout.editorOpen()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		HudLayout.apply(graphics, font, HudLayout.Id.BOSS, () -> {
			if (events.isEmpty()) {
				drawEmpty(graphics, font, 0, 0);
				return;
			}
			float y = 0;
			int drawn = 0;
			for (LerpingBossEvent event : events) {
				draw(graphics, font, 0, y, event);
				y += BAR_H + GAP;
				drawn++;
				if (drawn >= MAX) {
					break;
				}
			}
		});
	}

	public static float drawWidth() {
		return BAR_W;
	}

	public static float drawHeight() {
		int n = Math.max(1, Math.min(MAX, events().size()));
		return n * BAR_H + (n - 1) * GAP;
	}

	private static List<LerpingBossEvent> events() {
		Minecraft client = Minecraft.getInstance();
		int tick = client.player == null ? -1 : client.player.tickCount;
		if (tick == eventsTick) {
			return eventsCache;
		}
		eventsTick = tick;
		if (client.gui == null) {
			eventsCache = List.of();
			return eventsCache;
		}
		var map = ((BossHealthOverlayAccessor) client.gui.getBossOverlay()).stray$events();
		if (map == null || map.isEmpty()) {
			eventsCache = List.of();
			return eventsCache;
		}
		List<LerpingBossEvent> out = new ArrayList<>(Math.min(MAX, map.size()));
		int n = 0;
		for (LerpingBossEvent event : map.values()) {
			out.add(event);
			n++;
			if (n >= MAX) {
				break;
			}
		}
		eventsCache = out;
		LABELS.keySet().retainAll(out);
		return eventsCache;
	}

	/** Boss names are styled Components; rebuilding and measuring them each frame showed in Spark. */
	private static Label label(Font font, LerpingBossEvent event) {
		Label cached = LABELS.get(event);
		Component raw = event.getName();
		if (cached != null && cached.source == raw) {
			return cached;
		}
		Component name = MenuFont.applyBody(raw);
		Label made = new Label(raw, name, GuiDraw.hudWidth(font, name));
		LABELS.put(event, made);
		return made;
	}

	private static void drawEmpty(GuiGraphicsExtractor graphics, Font font, float x, float y) {
		HudChrome.panel(graphics, x, y, BAR_W, BAR_H, 5, Theme.WINDOW, Theme.LINE);
		GuiDraw.small(graphics, font, "BOSS", x + 8, y + 4, Theme.MUTED);
	}

	private static void draw(GuiGraphicsExtractor graphics, Font font, float x, float y, LerpingBossEvent event) {
		float t = Mth.clamp(event.getProgress(), 0f, 1f);
		int fill = barColor(event);
		HudChrome.panel(graphics, x, y, BAR_W, BAR_H, 5, Theme.HUD_WINDOW, Theme.HUD_LINE);
		if (t > 0.01f) {
			GuiDraw.rounded(graphics, x + 8, y + BAR_H - 4, Math.max(2f, (BAR_W - 16f) * t), 2.5f, 1.2f, fill);
		}
		Label label = label(font, event);
		float nx = x + (BAR_W - label.width) * 0.5f;
		GuiDraw.hud(graphics, font, label.name, nx, y + 2, 0xFFFFFFFF);
	}

	private record Label(Component source, Component name, int width) {
	}

	private static int barColor(LerpingBossEvent event) {
		ChatFormatting formatting = event.getColor().getFormatting();
		Integer rgb = formatting == null ? null : formatting.getColor();
		if (rgb == null) {
			return Theme.ACCENT;
		}
		return 0xFF000000 | rgb;
	}
}
