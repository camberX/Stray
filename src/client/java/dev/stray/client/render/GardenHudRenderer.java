package dev.stray.client.render;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.farming.GardenHud;
import dev.stray.client.farming.GardenHud.ContestSnap;
import dev.stray.client.farming.GardenHud.HoeSnap;
import dev.stray.client.farming.GardenHud.MilestoneSnap;
import dev.stray.client.farming.GardenHud.Need;
import dev.stray.client.farming.GardenHud.ShoppingSnap;
import dev.stray.client.farming.GardenHud.VisitorSnap;
import dev.stray.client.location.SkyblockLocation;
import dev.stray.client.ui.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public final class GardenHudRenderer {
	private static final float WIDTH = 158f;
	private static final float CONTEST_H = 40f;
	private static final float VISITOR_H = 48f;
	private static final float HOE_H = 48f;
	private static final float MILESTONE_H = 72f;
	private static final float PAD = 6f;
	private static final float LINE = 11f;
	private static final NumberFormat INTEGER = NumberFormat.getIntegerInstance(Locale.US);

	private GardenHudRenderer() {
	}

	public static void init() {
	}

	public static float contestWidth() {
		return WIDTH;
	}

	public static float contestHeight() {
		return CONTEST_H;
	}

	public static float visitorWidth() {
		return WIDTH;
	}

	public static float visitorHeight() {
		return VISITOR_H;
	}

	public static float hoeWidth() {
		return WIDTH;
	}

	public static float hoeHeight() {
		return HOE_H;
	}

	public static float milestoneWidth() {
		return WIDTH;
	}

	public static float milestoneHeight() {
		return MILESTONE_H;
	}

	public static float shoppingWidth() {
		return WIDTH;
	}

	public static float shoppingHeight() {
		ShoppingSnap snap = GardenHud.shopping().present() ? GardenHud.shopping() : sampleShopping();
		return shoppingHeightOf(snap);
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) {
			return;
		}
		boolean garden = SkyblockLocation.inGarden() || HudLayout.editorOpen();
		if (!garden) {
			return;
		}
		StrayConfig config = StrayConfig.get();
		if (config.gardenContestHudEnabled) {
			ContestSnap snap = GardenHud.contest();
			if (snap.present() || HudLayout.editorOpen()) {
				HudLayout.Box box = HudLayout.box(HudLayout.Id.NEXT_CONTEST, client.font, graphics.guiWidth(), graphics.guiHeight());
				drawContest(graphics, client.font, box.x(), box.y(), HudLayout.scale(HudLayout.Id.NEXT_CONTEST), snap);
			}
		}
		if (config.gardenVisitorHudEnabled) {
			VisitorSnap snap = GardenHud.visitors();
			if (snap.present() || HudLayout.editorOpen()) {
				HudLayout.Box box = HudLayout.box(HudLayout.Id.VISITOR, client.font, graphics.guiWidth(), graphics.guiHeight());
				drawVisitor(graphics, client.font, box.x(), box.y(), HudLayout.scale(HudLayout.Id.VISITOR), snap);
			}
		}
		if (config.gardenHoeHudEnabled) {
			HoeSnap snap = GardenHud.hoe();
			if (snap.present() || HudLayout.editorOpen()) {
				HudLayout.Box box = HudLayout.box(HudLayout.Id.HOE, client.font, graphics.guiWidth(), graphics.guiHeight());
				drawHoe(graphics, client.font, box.x(), box.y(), HudLayout.scale(HudLayout.Id.HOE), snap);
			}
		}
		if (config.gardenMilestoneHudEnabled) {
			MilestoneSnap snap = GardenHud.milestone();
			if (snap.present() || HudLayout.editorOpen()) {
				HudLayout.Box box = HudLayout.box(HudLayout.Id.MILESTONE, client.font, graphics.guiWidth(), graphics.guiHeight());
				drawMilestone(graphics, client.font, box.x(), box.y(), HudLayout.scale(HudLayout.Id.MILESTONE), snap);
			}
		}
		if (config.gardenShoppingHudEnabled) {
			ShoppingSnap snap = GardenHud.shopping();
			if (snap.present() || HudLayout.editorOpen()) {
				HudLayout.Box box = HudLayout.box(HudLayout.Id.SHOPPING, client.font, graphics.guiWidth(), graphics.guiHeight());
				drawShopping(graphics, client.font, box.x(), box.y(), HudLayout.scale(HudLayout.Id.SHOPPING), snap);
			}
		}
	}

	private static void drawContest(
		GuiGraphicsExtractor graphics,
		Font font,
		float x,
		float y,
		float scale,
		ContestSnap value
	) {
		ContestSnap snap = value.present() ? value : sampleContest();
		begin(graphics, x, y, scale, WIDTH, CONTEST_H);
		GuiDraw.small(graphics, font, snap.active() ? "ACTIVE CONTEST" : "NEXT CONTEST", PAD + 1, PAD, Theme.ACCENT);
		right(graphics, font, snap.time().isEmpty() ? "?" : snap.time(), PAD, Theme.MUTED);
		String crops = snap.crops().isEmpty() ? "Open tab widget" : String.join(" · ", snap.crops());
		if (!snap.boosted().isEmpty()) {
			crops = crops.replace(snap.boosted(), snap.boosted() + "*");
		}
		GuiDraw.small(graphics, font, GuiDraw.ellipsize(font, crops, WIDTH - PAD * 2, true), PAD + 1, PAD + LINE + 4, Theme.TEXT);
		graphics.pose().popMatrix();
	}

	private static void drawVisitor(
		GuiGraphicsExtractor graphics,
		Font font,
		float x,
		float y,
		float scale,
		VisitorSnap value
	) {
		VisitorSnap snap = value.present() ? value : sampleVisitor();
		begin(graphics, x, y, scale, WIDTH, VISITOR_H);
		String title = snap.count() == 1 ? "1 VISITOR" : snap.count() + " VISITORS";
		GuiDraw.small(graphics, font, title, PAD + 1, PAD, Theme.ACCENT);
		String next = snap.locked()
			? "Not unlocked"
			: snap.queueFull() ? "Queue Full!" : "Next in " + snap.next();
		int nextColor = snap.queueFull() || snap.locked() ? 0xFFF87171 : Theme.TEXT;
		GuiDraw.small(graphics, font, GuiDraw.ellipsize(font, next, WIDTH - PAD * 2, true), PAD + 1, PAD + LINE + 4, nextColor);
		String names = snap.names().isEmpty() ? "None waiting" : String.join(" · ", snap.names());
		GuiDraw.small(graphics, font, GuiDraw.ellipsize(font, names, WIDTH - PAD * 2, true), PAD + 1, PAD + LINE * 2 + 6, Theme.MUTED);
		graphics.pose().popMatrix();
	}

	private static void drawHoe(
		GuiGraphicsExtractor graphics,
		Font font,
		float x,
		float y,
		float scale,
		HoeSnap value
	) {
		HoeSnap snap = value.present() ? value : sampleHoe();
		begin(graphics, x, y, scale, WIDTH, HOE_H);
		GuiDraw.small(graphics, font, "HOE LEVEL", PAD + 1, PAD, Theme.ACCENT);
		String level = "Level " + snap.level() + "➜" + snap.next();
		right(graphics, font, level, PAD, Theme.TEXT);
		String xp = amount(snap.exp()) + "/" + amount(snap.need());
		int xpColor = snap.upgrade() ? 0xFFF87171 : Theme.TEXT;
		GuiDraw.small(graphics, font, xp, PAD + 1, PAD + LINE + 4, xpColor);
		String warn = snap.overclock() ? "Overclock required" : snap.upgrade() ? "Upgrade required" : snap.overflow() ? "Overflow" : "";
		if (!warn.isEmpty()) {
			right(graphics, font, warn, PAD + LINE + 4, 0xFFF87171);
		}
		graphics.pose().popMatrix();
	}

	private static void drawMilestone(
		GuiGraphicsExtractor graphics,
		Font font,
		float x,
		float y,
		float scale,
		MilestoneSnap value
	) {
		MilestoneSnap snap = value.present() ? value : sampleMilestone();
		begin(graphics, x, y, scale, WIDTH, MILESTONE_H);
		GuiDraw.small(graphics, font, "CROP MILESTONE", PAD + 1, PAD, Theme.ACCENT);
		String crop = snap.maxed()
			? snap.crop() + " MAXED"
			: snap.crop() + "  " + snap.tier() + "➜" + snap.next();
		GuiDraw.small(graphics, font, GuiDraw.ellipsize(font, crop, WIDTH - PAD * 2, true), PAD + 1, 18, Theme.TEXT);
		String progress = amount(snap.have()) + "/" + amount(snap.need());
		GuiDraw.small(graphics, font, progress, PAD + 1, 30, Theme.TEXT);
		double percent = snap.need() <= 0 ? 100d : 100d * snap.have() / (double) snap.need();
		right(graphics, font, String.format(Locale.ROOT, "%.1f%%", Math.min(100d, percent)), 30, Theme.MUTED);
		String eta = snap.eta().isEmpty() ? "—" : "In " + snap.eta();
		GuiDraw.small(graphics, font, eta, PAD + 1, 42, Theme.MUTED);
		String rate = snap.perSecond() > 0.05d
			? amount(Math.round(snap.perSecond())) + "/s"
			: "0/s";
		right(graphics, font, rate, 42, Theme.ACCENT);
		GuiDraw.small(graphics, font, "Counter  " + amount(snap.counter()), PAD + 1, 54, Theme.MUTED);
		graphics.pose().popMatrix();
	}

	private static void drawShopping(
		GuiGraphicsExtractor graphics,
		Font font,
		float x,
		float y,
		float scale,
		ShoppingSnap value
	) {
		ShoppingSnap snap = value.present() ? value : sampleShopping();
		float height = shoppingHeightOf(snap);
		begin(graphics, x, y, scale, WIDTH, height);
		GuiDraw.small(graphics, font, "SHOPPING LIST", PAD + 1, PAD, Theme.ACCENT);
		float cursor = PAD + LINE + 4;
		List<Need> items = snap.items();
		if (items.isEmpty()) {
			String hint = snap.visitors().isEmpty() ? "No visitors" : "Open a visitor";
			GuiDraw.small(graphics, font, hint, PAD + 1, cursor, Theme.MUTED);
			cursor += LINE;
		} else {
			int shown = Math.min(8, items.size());
			for (int i = 0; i < shown; i++) {
				Need need = items.get(i);
				boolean ready = need.having() >= need.required();
				String label = GuiDraw.ellipsize(font, need.name(), WIDTH - 72, true);
				GuiDraw.small(graphics, font, label, PAD + 1, cursor, Theme.TEXT);
				String count = amount(need.having()) + "/" + amount(need.required());
				GuiDraw.small(
					graphics,
					font,
					count,
					WIDTH - PAD - GuiDraw.smallWidth(font, count),
					cursor,
					ready ? 0xFF75D69C : Theme.MUTED
				);
				cursor += LINE;
			}
		}
		if (!snap.visitors().isEmpty()) {
			String names = String.join(" · ", snap.visitors());
			GuiDraw.small(graphics, font, GuiDraw.ellipsize(font, names, WIDTH - PAD * 2, true), PAD + 1, cursor, Theme.MUTED);
		}
		graphics.pose().popMatrix();
	}

	private static float shoppingHeightOf(ShoppingSnap snap) {
		int rows = 1 + Math.max(1, Math.min(8, snap.items().size())) + (snap.visitors().isEmpty() ? 0 : 1);
		return Math.max(36f, PAD + 12f + rows * LINE + 4f);
	}

	private static void begin(GuiGraphicsExtractor graphics, float x, float y, float scale, float w, float h) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		if (scale != 1f) {
			graphics.pose().scale(scale, scale);
		}
		HudChrome.panel(graphics, 0, 0, w, h, 5, Theme.HUD_WINDOW, Theme.HUD_LINE);
	}

	private static void right(GuiGraphicsExtractor graphics, Font font, String text, float y, int color) {
		GuiDraw.small(graphics, font, text, WIDTH - PAD - GuiDraw.smallWidth(font, text), y, color);
	}

	private static String amount(long value) {
		return INTEGER.format(Math.max(0L, value));
	}

	private static ContestSnap sampleContest() {
		return new ContestSnap(true, false, List.of("Wheat", "Potato", "Sugar Cane"), "Potato", "26m");
	}

	private static VisitorSnap sampleVisitor() {
		return new VisitorSnap(true, 2, List.of("Carlton", "Spaceman"), "11m", false, false);
	}

	private static HoeSnap sampleHoe() {
		return new HoeSnap(true, 12, 13, 45_000, 55_000, false, false, false);
	}

	private static MilestoneSnap sampleMilestone() {
		return new MilestoneSnap(true, "Wheat", 12, 13, 45_000, 80_000, 1_245_000, false, "12m 4s", 1234d);
	}

	private static ShoppingSnap sampleShopping() {
		return new ShoppingSnap(
			true,
			List.of(new Need("Enchanted Bread", 64, 12), new Need("Enchanted Sugar", 32, 32)),
			List.of("Carlton", "Spaceman"),
			List.of()
		);
	}
}
