package dev.stray.client.render;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.item.ItemStorage;
import dev.stray.client.item.RawmatsTracker;
import dev.stray.client.item.SkyblockProfileApi;
import dev.stray.client.ui.HudEditorScreen;
import dev.stray.client.ui.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RawmatsHudRenderer {
	public static final float WIDTH = 236;
	private static final float PAD = 7;
	private static final float HEAD = 28;
	private static final float ROW = 18;
	private static final float ROW_NOTE = 26;
	private static final float ICON = 16;
	private static final int MAX_ROWS = 10;
	private static Rect modeHit = Rect.EMPTY;
	private static Rect displayHit = Rect.EMPTY;

	private RawmatsHudRenderer() {
	}

	public static void init() {
	}

	public static float drawWidth() {
		return WIDTH;
	}

	public static float drawHeight() {
		RawmatsTracker.Snapshot snap = RawmatsTracker.snapshot();
		if (!snap.present() && !HudLayout.editorOpen()) {
			return 0;
		}
		return heightOf(snap);
	}

	public static boolean mouseClicked(MouseButtonEvent event) {
		if (event.button() != 0 || !StrayConfig.get().rawmatsHudEnabled) {
			return false;
		}
		if (Minecraft.getInstance().screen instanceof HudEditorScreen) {
			return false;
		}
		if (!(Minecraft.getInstance().screen instanceof ChatScreen)) {
			return false;
		}
		if (displayHit.contains(event.x(), event.y())) {
			StrayConfig config = StrayConfig.get();
			config.cycleRawmatsDisplay();
			config.save();
			return true;
		}
		if (modeHit.contains(event.x(), event.y())) {
			StrayConfig config = StrayConfig.get();
			config.cycleRawmatsMode();
			config.save();
			return true;
		}
		return false;
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.options.hideGui) {
			modeHit = Rect.EMPTY;
			displayHit = Rect.EMPTY;
			return;
		}
		StrayConfig config = StrayConfig.get();
		if (!config.rawmatsHudEnabled) {
			modeHit = Rect.EMPTY;
			displayHit = Rect.EMPTY;
			return;
		}
		RawmatsTracker.Snapshot snap = RawmatsTracker.snapshot();
		if (!snap.present() && !HudLayout.editorOpen()) {
			modeHit = Rect.EMPTY;
			displayHit = Rect.EMPTY;
			return;
		}
		HudLayout.Box box = HudLayout.box(HudLayout.Id.RAWMATS, client.font, graphics.guiWidth(), graphics.guiHeight());
		draw(graphics, client, box.x(), box.y(), HudLayout.scale(HudLayout.Id.RAWMATS), snap);
	}

	public static void draw(GuiGraphicsExtractor graphics, Minecraft client, float x, float y, float scale, RawmatsTracker.Snapshot snap) {
		Font font = client.font;
		float h = heightOf(snap);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		if (scale != 1.0f) {
			graphics.pose().scale(scale, scale);
		}

		HudChrome.panel(graphics, 0, 0, WIDTH, h, 6, Theme.WINDOW, Theme.LINE);
		GuiDraw.small(graphics, font, "RAW MATS", PAD + 4, PAD + 1, Theme.ACCENT);
		boolean chat = client.screen instanceof ChatScreen;
		StrayConfig config = StrayConfig.get();
		String mode = config.rawmatsModeLabel();
		String display = config.rawmatsDisplayLabel();
		float modeW = GuiDraw.smallWidth(font, mode);
		float displayW = GuiDraw.smallWidth(font, display);
		int switchColor = chat ? Theme.ACCENT : Theme.MUTED;
		float modeX = WIDTH - PAD - modeW;
		float displayX = modeX - 8 - displayW;
		GuiDraw.small(graphics, font, display, displayX, PAD + 1, switchColor);
		GuiDraw.small(graphics, font, mode, modeX, PAD + 1, switchColor);
		displayHit = new Rect(x + displayX * scale, y + PAD * scale, (displayW + 6) * scale, 12 * scale);
		modeHit = new Rect(x + modeX * scale, y + PAD * scale, (modeW + 8) * scale, 12 * scale);

		if (!snap.present()) {
			GuiDraw.menu(graphics, font, "No item tracked", PAD + 4, PAD + 12, Theme.TEXT);
			GuiDraw.small(graphics, font, "/st rawmats <id> [count]", PAD + 4, PAD + 24, Theme.MUTED);
			graphics.pose().popMatrix();
			return;
		}

		boolean remaining = config.rawmatsRemaining;
		String title = ellipsize(font, titleOf(snap), WIDTH - PAD * 2 - 88, false);
		GuiDraw.menu(graphics, font, title, PAD + 4, PAD + 12, Theme.TEXT);
		String tally = snap.complete() + "/" + snap.total();
		String extra = remaining
			? format(remainingTotal(snap)) + " left"
			: Math.round(snap.progress() * 100f) + "%";
		String right = tally + "  " + extra;
		GuiDraw.small(graphics, font, right, WIDTH - PAD - GuiDraw.smallWidth(font, right), PAD + 12, Theme.MUTED);

		List<RawmatsTracker.Line> lines = visible(snap);
		if (snap.lines().isEmpty()) {
			GuiDraw.small(graphics, font, snap.recipe() ? "No ingredients" : "No craft recipe", PAD + 4, HEAD + 2, Theme.MUTED);
			graphics.pose().popMatrix();
			return;
		}
		if (lines.isEmpty()) {
			GuiDraw.small(graphics, font, "None remaining", PAD + 4, HEAD + 2, Theme.ACCENT);
			graphics.pose().popMatrix();
			return;
		}

		LocalPlayer player = client.player;
		int shown = Math.min(MAX_ROWS, lines.size());
		float rowY = HEAD;
		for (int i = 0; i < shown; i++) {
			RawmatsTracker.Line line = lines.get(i);
			row(graphics, font, player, line, PAD, rowY, i, remaining);
			rowY += rowHeight(line);
		}
		if (lines.size() > MAX_ROWS) {
			GuiDraw.small(graphics, font, "+" + (lines.size() - MAX_ROWS) + " more", PAD + 4, rowY + 1, Theme.MUTED);
			rowY += 12;
		}
		String hint = storageHint(snap);
		if (hint != null) {
			GuiDraw.small(graphics, font, hint, PAD + 4, rowY + 1, Theme.MUTED);
		}
		graphics.pose().popMatrix();
	}

	private static String titleOf(RawmatsTracker.Snapshot snap) {
		if (snap.crafts() > 1L) {
			return snap.name() + " ×" + snap.crafts();
		}
		return snap.name();
	}

	private static void row(
		GuiGraphicsExtractor graphics,
		Font font,
		LocalPlayer player,
		RawmatsTracker.Line line,
		float x,
		float y,
		int seed,
		boolean remaining
	) {
		ItemStack stack = line.icon();
		boolean note = line.hasNote();
		float iconY = note ? y + 5 : y + 1;
		HudChrome.rounded(graphics, x, iconY, ICON, ICON, 3, Theme.HUD_TRACK);
		if (stack != null && !stack.isEmpty() && player != null) {
			graphics.item(player, stack, Math.round(x), Math.round(iconY), 200 + seed);
		}
		float textX = x + ICON + 4;
		String amount = remaining
			? format(line.remaining()) + " left"
			: format(line.have()) + "/" + format(line.need());
		float amountW = GuiDraw.smallWidth(font, amount);
		float nameW = WIDTH - textX - amountW - PAD - 8;
		String name = ellipsize(font, line.name(), nameW, true);
		int nameColor = line.done() ? Theme.ACCENT : Theme.TEXT;
		GuiDraw.small(graphics, font, name, textX, y + 1, nameColor);
		GuiDraw.small(graphics, font, amount, WIDTH - PAD - amountW, y + 1, line.done() ? Theme.ACCENT : Theme.MUTED);
		if (note) {
			String used = ellipsize(font, line.note(), WIDTH - textX - PAD - 4, true);
			GuiDraw.small(graphics, font, used, textX, y + 10, Theme.MUTED);
		}
		float barX = textX;
		float barW = WIDTH - textX - PAD;
		float barY = note ? y + 20 : y + 12;
		HudChrome.rounded(graphics, barX, barY, barW, 2.5f, 1.2f, Theme.HUD_TRACK);
		float filled = Math.max(line.have() > 0L ? 2f : 0f, barW * line.progress());
		HudChrome.rounded(graphics, barX, barY, filled, 2.5f, 1.2f, line.done() ? Theme.ACCENT : Theme.ACCENT_DIM);
	}

	private static float heightOf(RawmatsTracker.Snapshot snap) {
		if (!snap.present()) {
			return 44;
		}
		List<RawmatsTracker.Line> lines = visible(snap);
		int shown = Math.min(MAX_ROWS, Math.max(1, lines.size()));
		float rows = 0f;
		if (lines.isEmpty()) {
			rows = ROW;
		} else {
			for (int i = 0; i < shown; i++) {
				rows += rowHeight(lines.get(i));
			}
		}
		boolean extra = lines.size() > MAX_ROWS;
		boolean hint = storageHint(snap) != null;
		return HEAD + PAD + rows + (extra ? 12 : 0) + (hint ? 12 : 0) + PAD;
	}

	private static float rowHeight(RawmatsTracker.Line line) {
		return line.hasNote() ? ROW_NOTE : ROW;
	}

	private static String storageHint(RawmatsTracker.Snapshot snap) {
		if (ItemStorage.hasApiStorage()) {
			return null;
		}
		SkyblockProfileApi.Status status = SkyblockProfileApi.status();
		if (status == SkyblockProfileApi.Status.LOADING || status == SkyblockProfileApi.Status.IDLE) {
			return "Loading Ender Chest, backpacks, and sacks";
		}
		if (status == SkyblockProfileApi.Status.ERROR) {
			return "Couldn't load Ender Chest / backpacks / sacks";
		}
		if (!snap.sawEnder() || !snap.sawBackpack() || !snap.sawSacks()) {
			return "Open Ender Chest, backpacks, and sacks to count them";
		}
		return null;
	}

	private static List<RawmatsTracker.Line> visible(RawmatsTracker.Snapshot snap) {
		List<RawmatsTracker.Line> lines = snap.lines();
		if (!StrayConfig.get().rawmatsRemaining) {
			return lines;
		}
		List<RawmatsTracker.Line> left = new ArrayList<>();
		for (RawmatsTracker.Line line : lines) {
			if (!line.done()) {
				left.add(line);
			}
		}
		return left;
	}

	private static long remainingTotal(RawmatsTracker.Snapshot snap) {
		long total = 0L;
		for (RawmatsTracker.Line line : snap.lines()) {
			total += line.remaining();
		}
		return total;
	}

	static String format(long value) {
		if (value < 1_000L) {
			return Long.toString(value);
		}
		if (value < 10_000L) {
			return String.format(Locale.ROOT, "%.1fk", value / 1000d);
		}
		if (value < 1_000_000L) {
			return (value / 1000L) + "k";
		}
		if (value < 10_000_000L) {
			return String.format(Locale.ROOT, "%.1fm", value / 1_000_000d);
		}
		return (value / 1_000_000L) + "m";
	}

	private static String ellipsize(Font font, String value, float max, boolean small) {
		return GuiDraw.ellipsize(font, value, max, small);
	}

	private record Rect(float x, float y, float w, float h) {
		private static final Rect EMPTY = new Rect(0, 0, 0, 0);

		boolean contains(double mx, double my) {
			return w > 0 && h > 0 && mx >= x && mx <= x + w && my >= y && my <= y + h;
		}
	}
}
