package dev.stray.client.render;

import dev.stray.client.ui.MenuFont;
import dev.stray.client.ui.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.List;

public final class EffectsHudRenderer {
	private static final float CHIP_H = 16;
	private static final float ICON = 12;
	private static final float MIN_W = 72;
	private static final int MAX = 8;

	private static int cacheTick = Integer.MIN_VALUE;
	private static List<Chip> cache = List.of();
	private static float cacheWidth = MIN_W;

	private EffectsHudRenderer() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Font font = Minecraft.getInstance().font;
		List<Chip> chips = chips(font);
		if (chips.isEmpty() && !HudLayout.editorOpen()) {
			return;
		}
		float boxW = cacheWidth;
		HudLayout.apply(graphics, font, HudLayout.Id.EFFECTS, () -> {
			if (chips.isEmpty()) {
				HudChrome.panel(graphics, 0, 0, boxW, CHIP_H, 5, Theme.WINDOW, Theme.LINE);
				GuiDraw.small(graphics, font, "EFFECTS", 8, 4, Theme.MUTED);
				return;
			}
			float y = 0;
			for (Chip chip : chips) {
				drawChip(graphics, font, chip, boxW - chip.width, y);
				y += CHIP_H + 3;
			}
		});
	}

	public static float drawWidth(Font font) {
		chips(font);
		return cacheWidth;
	}

	public static float drawHeight() {
		int n = Math.max(1, chips(Minecraft.getInstance().font).size());
		return n * CHIP_H + (n - 1) * 3;
	}

	public static float stackHeight() {
		int n = chips(Minecraft.getInstance().font).size();
		if (n == 0) {
			return 0;
		}
		return n * CHIP_H + (n - 1) * 3;
	}

	/** Names, suffixes, and widths change at most once a tick, so they are built once a tick. */
	private static List<Chip> chips(Font font) {
		Minecraft client = Minecraft.getInstance();
		int tick = client.player != null ? client.player.tickCount : -1;
		if (tick == cacheTick) {
			return cache;
		}
		LocalPlayer player = client.player;
		cacheTick = tick;
		if (player == null) {
			cache = List.of();
			cacheWidth = MIN_W;
			return cache;
		}
		List<Chip> out = new ArrayList<>();
		float max = MIN_W;
		for (MobEffectInstance instance : player.getActiveEffects()) {
			if (!instance.showIcon()) {
				continue;
			}
			MobEffect effect = instance.getEffect().value();
			Component name = MenuFont.applyBody(effect.getDisplayName());
			String extra = extra(instance);
			int nameW = GuiDraw.hudWidth(font, name);
			float width = ICON + 14 + nameW + GuiDraw.smallWidth(font, extra);
			max = Math.max(max, width);
			out.add(new Chip(instance, name, extra, nameW, width));
			if (out.size() >= MAX) {
				break;
			}
		}
		cache = out;
		cacheWidth = max;
		return out;
	}

	private static void drawChip(GuiGraphicsExtractor graphics, Font font, Chip chip, float x, float y) {
		HudChrome.panel(graphics, x, y, chip.width, CHIP_H, 5, Theme.WINDOW, Theme.LINE);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + 6, y + 2);
		graphics.pose().scale(ICON / 18f, ICON / 18f);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Gui.getMobEffectSprite(chip.instance.getEffect()), 0, 0, 18, 18);
		graphics.pose().popMatrix();
		float tx = x + 6 + ICON + 3;
		GuiDraw.hud(graphics, font, chip.name, tx, y + 3, 0xFFFFFFFF);
		if (!chip.extra.isEmpty()) {
			GuiDraw.small(graphics, font, chip.extra, tx + chip.nameWidth, y + 4, Theme.MUTED);
		}
	}

	private static String extra(MobEffectInstance instance) {
		String amp = instance.getAmplifier() > 0 ? " " + roman(instance.getAmplifier() + 1) : "";
		String time = duration(instance);
		return amp + (time.isEmpty() ? "" : "  " + time);
	}

	private static String duration(MobEffectInstance instance) {
		if (instance.isInfiniteDuration()) {
			return "∞";
		}
		int sec = Mth.floor(instance.getDuration() / 20f);
		if (sec < 0) {
			return "";
		}
		return String.format(java.util.Locale.ROOT, "%d:%02d", sec / 60, sec % 60);
	}

	private static String roman(int value) {
		return switch (value) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			case 6 -> "VI";
			case 7 -> "VII";
			case 8 -> "VIII";
			case 9 -> "IX";
			case 10 -> "X";
			default -> Integer.toString(value);
		};
	}

	private record Chip(MobEffectInstance instance, Component name, String extra, int nameWidth, float width) {
	}
}
