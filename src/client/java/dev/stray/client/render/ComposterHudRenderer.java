package dev.stray.client.render;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.farming.ComposterTracker;
import dev.stray.client.ui.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.text.NumberFormat;
import java.util.Locale;

public final class ComposterHudRenderer {
	public static final float WIDTH = 166f;
	public static final float HEIGHT = 64f;
	private static final float PAD = 5f;
	private static final float BAR_W = WIDTH - PAD * 2f;
	private static final float BAR_H = 2.4f;
	private static final NumberFormat INTEGER = NumberFormat.getIntegerInstance(Locale.US);
	private static ComposterTracker.Snapshot textSource;
	private static Text textCache;

	private ComposterHudRenderer() {
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
		if (client.player == null || client.options.hideGui || !StrayConfig.get().composterHudEnabled) {
			return;
		}
		ComposterTracker.Snapshot snapshot = ComposterTracker.snapshot();
		if (!snapshot.present() && !HudLayout.editorOpen()) {
			return;
		}
		HudLayout.Box box = HudLayout.box(HudLayout.Id.COMPOSTER, client.font, graphics.guiWidth(), graphics.guiHeight());
		draw(graphics, client.font, box.x(), box.y(), HudLayout.scale(HudLayout.Id.COMPOSTER), snapshot);
	}

	public static void draw(
		GuiGraphicsExtractor graphics,
		Font font,
		float x,
		float y,
		float scale,
		ComposterTracker.Snapshot value
	) {
		ComposterTracker.Snapshot snapshot = value.present() ? value : sample();
		Text text = text(font, snapshot);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		if (scale != 1f) {
			graphics.pose().scale(scale, scale);
		}

		HudChrome.panel(graphics, 0, 0, WIDTH, HEIGHT, 5, Theme.HUD_WINDOW, Theme.HUD_LINE);
		GuiDraw.small(graphics, font, "COMPOSTER", PAD + 1, PAD, Theme.ACCENT);
		right(graphics, font, text.next, PAD, snapshot.active() ? Theme.TEXT : 0xFFF87171);

		resource(
			graphics,
			font,
			"Organic Matter",
			text.organic,
			snapshot.organicMatter(),
			snapshot.maxOrganicMatter(),
			15,
			0xFFF5C16C
		);
		resource(graphics, font, "Fuel", text.fuel, snapshot.fuel(), snapshot.maxFuel(), 29, 0xFF75D69C);

		GuiDraw.small(graphics, font, text.stored, PAD + 1, 44, Theme.TEXT);
		if (text.predicted != null) {
			right(graphics, font, text.predicted, 44, Theme.ACCENT);
		}

		GuiDraw.small(graphics, font, text.busy, PAD + 1, 54, Theme.MUTED);
		if (text.perHour != null) {
			right(graphics, font, text.perHour, 54, Theme.ACCENT);
		}
		graphics.pose().popMatrix();
	}

	/** Formats once per snapshot. NumberFormat lookups and String.format each frame showed in Spark. */
	private static Text text(Font font, ComposterTracker.Snapshot snapshot) {
		if (snapshot == textSource && textCache != null) {
			return textCache;
		}
		String next = snapshot.active() ? snapshot.nextCompost() : "INACTIVE";
		String organic = ratio(snapshot.organicMatter(), snapshot.maxOrganicMatter());
		String fuel = ratio(snapshot.fuel(), snapshot.maxFuel());
		String stored = "Stored  " + amount(snapshot.storedCompost());
		String predicted = snapshot.predictedCompost() >= 0 ? amount(snapshot.predictedCompost()) + " compost" : null;
		String busyRaw = snapshot.active() ? "Busy  " + snapshot.emptyIn() : snapshot.emptyIn();
		String busy = GuiDraw.ellipsize(font, busyRaw, 110, true);
		String perHour = snapshot.compostPerHour() > 0d
			? String.format(Locale.ROOT, "%.1f/h", snapshot.compostPerHour())
			: null;
		textSource = snapshot;
		textCache = new Text(next, organic, fuel, stored, predicted, busy, perHour);
		return textCache;
	}

	private static String ratio(long current, long maximum) {
		return maximum > 0 ? shortAmount(current) + "/" + shortAmount(maximum) : shortAmount(current) + "/?";
	}

	private static void resource(
		GuiGraphicsExtractor graphics,
		Font font,
		String label,
		String value,
		long current,
		long maximum,
		float y,
		int color
	) {
		GuiDraw.small(graphics, font, label, PAD + 1, y, Theme.TEXT);
		right(graphics, font, value, y, Theme.MUTED);
		float barY = y + 9;
		GuiDraw.rounded(graphics, PAD, barY, BAR_W, BAR_H, BAR_H * 0.5f, Theme.HUD_TRACK);
		if (maximum > 0 && current > 0) {
			float fraction = Math.max(0f, Math.min(1f, current / (float) maximum));
			GuiDraw.rounded(graphics, PAD, barY, Math.max(BAR_H, BAR_W * fraction), BAR_H, BAR_H * 0.5f, color);
		}
	}

	private static void right(GuiGraphicsExtractor graphics, Font font, String text, float y, int color) {
		GuiDraw.small(graphics, font, text, WIDTH - PAD - GuiDraw.smallWidth(font, text), y, color);
	}

	private static String shortAmount(long value) {
		long amount = Math.max(0L, value);
		if (amount >= 1_000_000_000L) {
			return compact(amount / 1_000_000_000d) + "b";
		}
		if (amount >= 1_000_000L) {
			return compact(amount / 1_000_000d) + "m";
		}
		if (amount >= 1_000L) {
			return compact(amount / 1_000d) + "k";
		}
		return Long.toString(amount);
	}

	private static String compact(double value) {
		return value >= 100d || Math.abs(value - Math.rint(value)) < 0.05d
			? String.format(Locale.ROOT, "%.0f", value)
			: String.format(Locale.ROOT, "%.1f", value);
	}

	private static String amount(long value) {
		return INTEGER.format(Math.max(0L, value));
	}

	private record Text(
		String next,
		String organic,
		String fuel,
		String stored,
		String predicted,
		String busy,
		String perHour
	) {
	}

	private static ComposterTracker.Snapshot sampleCache;

	private static ComposterTracker.Snapshot sample() {
		if (sampleCache == null) {
			sampleCache = makeSample();
		}
		return sampleCache;
	}

	private static ComposterTracker.Snapshot makeSample() {
		return new ComposterTracker.Snapshot(
			true,
			true,
			"7m 42s",
			82_500,
			100_000,
			76_200,
			160_000,
			18,
			31,
			"5h 14m",
			8.6d,
			true
		);
	}
}
