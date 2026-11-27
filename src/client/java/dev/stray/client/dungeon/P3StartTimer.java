package dev.stray.client.dungeon;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.location.SkyblockLocation;
import dev.stray.client.render.HudLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;

/**
 * F7/M7 P3 start timer, after Blade Addons' Term Start Timer (CC0): Storm dying (the wither
 * hurt sound at volume 15, pitch 1 during P2) starts a 3 second countdown in server ticks
 * (60 ticks, counted like NoammAddons), until P3 begins.
 */
public final class P3StartTimer {
	private static final int TOTAL_TICKS = 60;
	private static final String P2_START = "[BOSS] Storm: Pathetic Maxor, just like expected.";
	private static final String P3_START = "[BOSS] Goldor: Who dares trespass into my domain?";

	private static boolean inP2;
	private static boolean stormDead;
	private static int ticks = TOTAL_TICKS;

	private P3StartTimer() {
	}

	public static void onChat(String plain) {
		if (P2_START.equals(plain)) {
			reset();
			inP2 = true;
		} else if (P3_START.equals(plain)) {
			inP2 = false;
		}
	}

	public static void onSound(SoundEvent sound, float volume, float pitch) {
		if (inP2 && !stormDead && sound == SoundEvents.WITHER_HURT && volume == 15.0f && pitch == 1.0f) {
			stormDead = true;
			ticks = TOTAL_TICKS;
		}
	}

	public static void serverTick() {
		if (stormDead && ticks > 0) {
			ticks--;
		}
	}

	public static void reset() {
		inP2 = false;
		stormDead = false;
		ticks = TOTAL_TICKS;
	}

	private static boolean running() {
		return inP2 && stormDead && ticks > 0 && SkyblockLocation.inDungeon;
	}

	private static String text(boolean example) {
		int left = example ? 37 : ticks;
		return "§eP3 Start: §f" + String.format(Locale.ROOT, "%.2fs", left * 0.05);
	}

	public static float width(Font font) {
		return font.width(text(true));
	}

	public static float height() {
		return 9f;
	}

	public static void extract(GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		if (!StrayConfig.get().p3StartTimer || client.player == null || client.gui.hud.isHidden()) {
			return;
		}
		boolean example = HudLayout.editorOpen();
		if (!example && !running()) {
			return;
		}
		Font font = client.font;
		String text = text(example);
		HudLayout.apply(graphics, font, HudLayout.Id.P3_START, () -> graphics.text(font, text, 0, 0, 0xFFFFFFFF, true));
	}
}
