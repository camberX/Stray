package dev.stray.client.render;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.farming.JacobContestTracker;
import dev.stray.client.farming.JacobContestTracker.Medal;
import dev.stray.client.ui.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.text.NumberFormat;
import java.util.Locale;

public final class JacobContestHudRenderer {
	public static final float WIDTH = 158f;
	private static final float HEIGHT = 62f;
	private static final float PAD = 6f;

	private JacobContestHudRenderer() {
	}

	public static void init() {
	}

	public static float drawWidth() {
		return WIDTH;
	}

	public static float drawHeight() {
		return HEIGHT;
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui || !StrayConfig.get().jacobContestHudEnabled) {
			return;
		}
		JacobContestTracker.Snapshot snap = JacobContestTracker.snapshot();
		if (!snap.present() && !HudLayout.editorOpen()) {
			return;
		}
		HudLayout.Box box = HudLayout.box(HudLayout.Id.JACOB, client.font, graphics.guiWidth(), graphics.guiHeight());
		draw(graphics, client.font, box.x(), box.y(), HudLayout.scale(HudLayout.Id.JACOB), snap);
	}

	public static void draw(
		GuiGraphicsExtractor graphics,
		Font font,
		float x,
		float y,
		float scale,
		JacobContestTracker.Snapshot value
	) {
		JacobContestTracker.Snapshot snap = value.present() ? value : sample();
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		if (scale != 1f) {
			graphics.pose().scale(scale, scale);
		}

		HudChrome.panel(graphics, 0, 0, WIDTH, HEIGHT, 5, Theme.HUD_WINDOW, Theme.HUD_LINE);
		GuiDraw.small(graphics, font, "JACOB'S CONTEST", PAD + 1, PAD, Theme.ACCENT);
		right(graphics, font, snap.remaining(), PAD, Theme.MUTED);

		String score = amount(snap.score());
		Component crop = hudClip(font, snap.crop(), 92);
		GuiDraw.hud(graphics, font, crop, PAD + 1, 18, Theme.TEXT);
		right(graphics, font, score, 18, Theme.TEXT);

		String rank = label(snap.currentRank());
		GuiDraw.small(graphics, font, "Current", PAD + 1, 32, Theme.MUTED);
		GuiDraw.small(graphics, font, rank, 46, 32, medalColor(snap.currentRank()));

		String projected = label(snap.projectedRank()) + " · " + amount(snap.projectedScore());
		GuiDraw.small(graphics, font, "Projected", PAD + 1, 43, Theme.MUTED);
		GuiDraw.small(graphics, font, GuiDraw.ellipsize(font, projected, WIDTH - 46 - PAD, true), 46, 43, medalColor(snap.projectedRank()));

		String update = snap.updates() == 0 ? "learning" : amount(Math.round(snap.perUpdate())) + "/update";
		String rate = amount(Math.round(snap.perSecond())) + "/s · " + update;
		GuiDraw.small(graphics, font, GuiDraw.ellipsize(font, rate, WIDTH - PAD * 2, true), PAD + 1, 53, Theme.MUTED);
		graphics.pose().popMatrix();
	}

	private static void right(GuiGraphicsExtractor graphics, Font font, String text, float y, int color) {
		GuiDraw.small(graphics, font, text, WIDTH - PAD - GuiDraw.smallWidth(font, text), y, color);
	}

	private static int medalColor(Medal medal) {
		return switch (medal) {
			case BRONZE -> 0xFFCD7F32;
			case SILVER -> 0xFFD5D8DC;
			case GOLD -> 0xFFFFD54F;
			case PLATINUM -> 0xFF80DEEA;
			case DIAMOND -> 0xFF81D4FA;
			case NONE -> Theme.MUTED;
		};
	}

	private static String label(Medal medal) {
		if (medal == null || medal == Medal.NONE) {
			return "Unranked";
		}
		String text = medal.name().toLowerCase(Locale.ROOT);
		return Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

	private static String amount(long value) {
		return NumberFormat.getIntegerInstance(Locale.US).format(Math.max(0L, value));
	}

	private static Component hudClip(Font font, String value, float width) {
		String text = value == null ? "" : value;
		if (GuiDraw.hudWidth(font, Component.literal(text)) <= width) {
			return Component.literal(text);
		}
		while (text.length() > 1 && GuiDraw.hudWidth(font, Component.literal(text + "..")) > width) {
			text = text.substring(0, text.length() - 1);
		}
		return Component.literal(text + "..");
	}

	private static JacobContestTracker.Snapshot sample() {
		return new JacobContestTracker.Snapshot(
			true,
			"Nether Wart",
			"5:59",
			139_874,
			Medal.SILVER,
			Medal.GOLD,
			242_600,
			285.5d,
			571d,
			4
		);
	}
}
