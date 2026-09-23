package dev.stray.client.render;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.farming.GardenHud;
import dev.stray.client.farming.GardenHud.ContestSnap;
import dev.stray.client.farming.GardenHud.HoeSnap;
import dev.stray.client.farming.GardenHud.MilestoneSnap;
import dev.stray.client.farming.GardenHud.Need;
import dev.stray.client.farming.GardenHud.ShoppingSnap;
import dev.stray.client.farming.GardenHud.VisitorSnap;
import dev.stray.client.farming.GardenVisitors;
import dev.stray.client.farming.PestCooldown;
import dev.stray.client.farming.PestCooldown.Snap;
import dev.stray.client.item.ItemIds;
import dev.stray.client.location.SkyblockLocation;
import dev.stray.client.ui.HudEditorScreen;
import dev.stray.client.ui.Theme;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GardenHudRenderer {
	private static final float WIDTH = 158f;
	private static final float CONTEST_H = 28f;
	private static final float MILESTONE_H = 40f;
	private static final float PAD = 5f;
	private static final float LINE = 10f;
	private static final float ICON = 8f;
	private static final NumberFormat INTEGER = NumberFormat.getIntegerInstance(Locale.US);
	private static final List<Hit> ITEM_HITS = new ArrayList<>();
	private static final Map<String, ItemStack> CROP_STACKS = new HashMap<>();

	private GardenHudRenderer() {
	}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof HudEditorScreen) {
				return;
			}
			ScreenMouseEvents.allowMouseClick(screen).register((opened, event) -> !mouseClicked(event));
			ScreenEvents.afterExtract(screen).register((opened, graphics, mouseX, mouseY, tick) -> {
				if (opened instanceof AbstractContainerScreen<?>) {
					HudChrome.beginHud();
					try {
						paint(graphics);
					} finally {
						HudChrome.endHud();
					}
				}
				hoverRecipe(graphics, client.font, mouseX, mouseY);
			});
		});
	}

	public static boolean mouseClicked(MouseButtonEvent event) {
		if (event.button() != 0 || !StrayConfig.get().gardenShoppingHudEnabled) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.screen instanceof HudEditorScreen) {
			return false;
		}
		if (client.screen == null) {
			return false;
		}
		for (Hit hit : ITEM_HITS) {
			if (hit.contains(event.x(), event.y())) {
				hit.click.run();
				return true;
			}
		}
		return false;
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
		VisitorSnap snap = GardenHud.visitors().present() ? GardenHud.visitors() : sampleVisitor();
		return visitorHeightOf(snap);
	}

	public static float hoeWidth() {
		return WIDTH;
	}

	public static float hoeHeight() {
		HoeSnap snap = GardenHud.hoe().present() ? GardenHud.hoe() : sampleHoe();
		return hoeHeightOf(snap);
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

	public static float pestCooldownWidth(Font font) {
		return timeWidth(font, cooldownLabel());
	}

	public static float pestCooldownHeight() {
		return PAD * 2f + LINE;
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.screen instanceof AbstractContainerScreen<?>) {
			return;
		}
		paint(graphics);
	}

	private static void paint(GuiGraphicsExtractor graphics) {
		ITEM_HITS.clear();
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
				drawVisitor(graphics, client.font, client.player, box.x(), box.y(), HudLayout.scale(HudLayout.Id.VISITOR), snap);
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
		if (config.pestCooldownHudEnabled) {
			Snap snap = PestCooldown.snap();
			if (showPestCooldown(snap)) {
				HudLayout.Box box = HudLayout.box(HudLayout.Id.PEST_COOLDOWN, client.font, graphics.guiWidth(), graphics.guiHeight());
				drawPestCooldown(graphics, client.font, box.x(), box.y(), HudLayout.scale(HudLayout.Id.PEST_COOLDOWN), snap);
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
		GuiDraw.small(graphics, font, GuiDraw.ellipsize(font, crops, WIDTH - PAD * 2, true), PAD + 1, PAD + LINE, Theme.TEXT);
		graphics.pose().popMatrix();
	}

	private static boolean showPestCooldown(Snap snap) {
		if (HudLayout.editorOpen()) {
			return true;
		}
		return snap.present() && snap.kind() != PestCooldown.Kind.READY;
	}

	private static String cooldownLabel() {
		Snap snap = PestCooldown.snap();
		if (snap.present() && snap.kind() != PestCooldown.Kind.READY) {
			return snap.label();
		}
		return Snap.sample().label();
	}

	private static float timeWidth(Font font, String text) {
		return GuiDraw.smallWidth(font, text) + PAD * 2f + 2f;
	}

	private static void drawPestCooldown(
		GuiGraphicsExtractor graphics,
		Font font,
		float x,
		float y,
		float scale,
		Snap value
	) {
		String text = value.present() && value.kind() != PestCooldown.Kind.READY ? value.label() : Snap.sample().label();
		int color = value.present() && value.kind() == PestCooldown.Kind.MAX ? 0xFF5A4A : Theme.TEXT;
		float width = timeWidth(font, text);
		begin(graphics, x, y, scale, width, pestCooldownHeight());
		GuiDraw.small(graphics, font, text, PAD + 1, PAD, color);
		graphics.pose().popMatrix();
	}

	private static void drawVisitor(
		GuiGraphicsExtractor graphics,
		Font font,
		LocalPlayer player,
		float x,
		float y,
		float scale,
		VisitorSnap value
	) {
		VisitorSnap snap = value.present() ? value : sampleVisitor();
		float height = visitorHeightOf(snap);
		begin(graphics, x, y, scale, WIDTH, height);
		String title = snap.count() == 1 ? "1 VISITOR" : snap.count() + " VISITORS";
		GuiDraw.small(graphics, font, title, PAD + 1, PAD, Theme.ACCENT);
		String next = snap.locked()
			? "Not unlocked"
			: snap.queueFull() ? "Queue Full!" : snap.next();
		int nextColor = snap.queueFull() || snap.locked() ? 0xFFF87171 : Theme.MUTED;
		right(graphics, font, GuiDraw.ellipsize(font, next, 88, true), PAD, nextColor);
		float cursor = PAD + LINE;
		List<String> names = snap.names();
		if (names.isEmpty()) {
			GuiDraw.small(graphics, font, "None waiting", PAD + 1, cursor, Theme.MUTED);
		} else {
			for (String name : names) {
				drawVisitorRow(graphics, font, player, name, cursor);
				cursor += LINE;
			}
		}
		graphics.pose().popMatrix();
	}

	private static void drawVisitorRow(
		GuiGraphicsExtractor graphics,
		Font font,
		LocalPlayer player,
		String name,
		float y
	) {
		List<String> crops = GardenHud.cropsFor(name);
		boolean icons = player != null && canDrawIcons(crops);
		float cropsWidth = icons ? cropWidth(font, crops) : textCropWidth(font, crops);
		float nameMax = Math.max(40f, WIDTH - PAD * 2 - cropsWidth - 4f);
		String label = GuiDraw.ellipsize(font, name, nameMax, true);
		GuiDraw.small(graphics, font, label, PAD + 1, y, Theme.TEXT);
		drawCrops(graphics, font, player, crops, WIDTH - PAD, y, icons);
	}

	private static void drawCrops(
		GuiGraphicsExtractor graphics,
		Font font,
		LocalPlayer player,
		List<String> crops,
		float right,
		float y,
		boolean icons
	) {
		if (crops.isEmpty()) {
			return;
		}
		if (!icons) {
			String text = GuiDraw.ellipsize(font, String.join(" · ", crops), 72, true);
			GuiDraw.small(graphics, font, text, right - GuiDraw.smallWidth(font, text), y, Theme.MUTED);
			return;
		}
		float x = right - cropWidth(font, crops);
		float iconY = y - 1f;
		for (int i = 0; i < crops.size(); i++) {
			if (i > 0) {
				x += 1f;
			}
			String crop = crops.get(i);
			ItemStack stack = GardenVisitors.ANY.equals(crop) || GardenVisitors.UNKNOWN.equals(crop)
				? ItemStack.EMPTY
				: cropStack(crop);
			if (stack.isEmpty()) {
				String shortName = shortCrop(crop);
				GuiDraw.small(graphics, font, shortName, x, y, Theme.MUTED);
				x += GuiDraw.smallWidth(font, shortName);
				continue;
			}
			graphics.pose().pushMatrix();
			graphics.pose().translate(x, iconY);
			graphics.pose().scale(ICON / 16f, ICON / 16f);
			graphics.item(player, stack, 0, 0, 41 + crop.hashCode());
			graphics.pose().popMatrix();
			x += ICON;
		}
	}

	private static float textCropWidth(Font font, List<String> crops) {
		return GuiDraw.smallWidth(font, GuiDraw.ellipsize(font, String.join(" · ", crops), 72, true));
	}

	private static boolean canDrawIcons(List<String> crops) {
		for (String crop : crops) {
			if (GardenVisitors.ANY.equals(crop) || GardenVisitors.UNKNOWN.equals(crop)) {
				continue;
			}
			if (!cropStack(crop).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private static float cropWidth(Font font, List<String> crops) {
		if (crops.isEmpty()) {
			return 0f;
		}
		float width = 0f;
		for (int i = 0; i < crops.size(); i++) {
			if (i > 0) {
				width += 1f;
			}
			String crop = crops.get(i);
			if (GardenVisitors.ANY.equals(crop) || GardenVisitors.UNKNOWN.equals(crop) || cropStack(crop).isEmpty()) {
				width += GuiDraw.smallWidth(font, shortCrop(crop));
			} else {
				width += ICON;
			}
		}
		return width;
	}

	private static String shortCrop(String crop) {
		if (crop == null || crop.isBlank()) {
			return "";
		}
		if (GardenVisitors.ANY.equals(crop) || GardenVisitors.UNKNOWN.equals(crop)) {
			return crop;
		}
		int space = crop.lastIndexOf(' ');
		return space > 0 && crop.length() > 12 ? crop.substring(space + 1) : crop;
	}

	private static ItemStack cropStack(String name) {
		if (name == null || name.isBlank()) {
			return ItemStack.EMPTY;
		}
		ItemStack cached = CROP_STACKS.get(name);
		if (cached != null) {
			return cached;
		}
		ItemStack stack = vanillaCrop(name);
		if (stack.isEmpty()) {
			ItemIds.Preview preview = ItemIds.resolve(name);
			if (preview.kind() == ItemIds.Kind.VANILLA || preview.kind() == ItemIds.Kind.SKYBLOCK) {
				stack = preview.stack();
			}
		}
		if (stack == null) {
			stack = ItemStack.EMPTY;
		}
		CROP_STACKS.put(name, stack);
		return stack;
	}

	private static ItemStack vanillaCrop(String name) {
		return switch (name.toLowerCase(Locale.ROOT)) {
			case "wheat" -> new ItemStack(Items.WHEAT);
			case "carrot" -> new ItemStack(Items.CARROT);
			case "potato" -> new ItemStack(Items.POTATO);
			case "sugar cane", "cane" -> new ItemStack(Items.SUGAR_CANE);
			case "melon slice", "melon" -> new ItemStack(Items.MELON_SLICE);
			case "cocoa beans", "cocoa" -> new ItemStack(Items.COCOA_BEANS);
			case "red mushroom block", "mushroom" -> new ItemStack(Items.RED_MUSHROOM_BLOCK);
			case "pumpkin" -> new ItemStack(Items.PUMPKIN);
			case "nether wart", "wart" -> new ItemStack(Items.NETHER_WART);
			case "cactus" -> new ItemStack(Items.CACTUS);
			case "sunflower" -> new ItemStack(Items.SUNFLOWER);
			case "bread" -> new ItemStack(Items.BREAD);
			case "cake" -> new ItemStack(Items.CAKE);
			case "jack o' lantern" -> new ItemStack(Items.JACK_O_LANTERN);
			case "golden carrot" -> new ItemStack(Items.GOLDEN_CARROT);
			case "raw mutton" -> new ItemStack(Items.MUTTON);
			case "raw porkchop" -> new ItemStack(Items.PORKCHOP);
			case "raw rabbit" -> new ItemStack(Items.RABBIT);
			default -> ItemStack.EMPTY;
		};
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
		float height = hoeHeightOf(snap);
		begin(graphics, x, y, scale, WIDTH, height);
		GuiDraw.small(graphics, font, "HOE " + snap.level() + "➜" + snap.next(), PAD + 1, PAD, Theme.ACCENT);
		String xp = amount(snap.exp()) + "/" + amount(snap.need());
		right(graphics, font, xp, PAD, snap.upgrade() ? 0xFFF87171 : Theme.TEXT);
		String warn = hoeWarn(snap);
		if (!warn.isEmpty()) {
			GuiDraw.small(graphics, font, warn, PAD + 1, PAD + LINE, 0xFFF87171);
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
		String crop = snap.maxed()
			? snap.crop() + " MAXED"
			: snap.crop() + "  " + snap.tier() + "➜" + snap.next();
		GuiDraw.small(graphics, font, GuiDraw.ellipsize(font, crop, 96, true), PAD + 1, PAD, Theme.ACCENT);
		double percent = snap.need() <= 0 ? 100d : 100d * snap.have() / (double) snap.need();
		right(graphics, font, String.format(Locale.ROOT, "%.1f%%", Math.min(100d, percent)), PAD, Theme.MUTED);
		String progress = amount(snap.have()) + "/" + amount(snap.need());
		GuiDraw.small(graphics, font, progress, PAD + 1, PAD + LINE, Theme.TEXT);
		String rate = snap.perSecond() > 0.05d
			? amount(Math.round(snap.perSecond())) + "/s"
			: "0/s";
		right(graphics, font, rate, PAD + LINE, Theme.ACCENT);
		String eta = snap.eta().isEmpty() ? "—" : "In " + snap.eta();
		GuiDraw.small(graphics, font, eta, PAD + 1, PAD + LINE * 2, Theme.MUTED);
		right(graphics, font, "Counter  " + amount(snap.counter()), PAD + LINE * 2, Theme.MUTED);
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
		ITEM_HITS.clear();
		begin(graphics, x, y, scale, WIDTH, height);
		GuiDraw.small(graphics, font, "SHOPPING LIST", PAD + 1, PAD, Theme.ACCENT);
		float cursor = PAD + LINE;
		List<Need> items = snap.items();
		if (items.isEmpty()) {
			GuiDraw.small(graphics, font, "Open a visitor", PAD + 1, cursor, Theme.MUTED);
		} else {
			int shown = Math.min(8, items.size());
			for (int i = 0; i < shown; i++) {
				Need need = items.get(i);
				boolean ready = need.having() >= need.required();
				boolean craftable = !ready && need.craftable();
				String count = amount(need.having()) + "/" + amount(need.required());
				float countW = GuiDraw.smallWidth(font, count);
				float markW = craftable ? GuiDraw.smallWidth(font, " C") : 0f;
				String label = GuiDraw.ellipsize(font, need.name(), WIDTH - PAD * 2 - countW - markW - 6, true);
				GuiDraw.small(graphics, font, label, PAD + 1, cursor, Theme.TEXT);
				if (craftable) {
					GuiDraw.small(
						graphics,
						font,
						"C",
						PAD + 1 + GuiDraw.smallWidth(font, label) + 3,
						cursor,
						0xFF75D69C
					);
				}
				GuiDraw.small(
					graphics,
					font,
					count,
					WIDTH - PAD - countW,
					cursor,
					ready ? 0xFF75D69C : Theme.MUTED
				);
				if (value.present() && !HudLayout.editorOpen()) {
					ITEM_HITS.add(new Hit(
						x + PAD * scale,
						y + cursor * scale,
						(WIDTH - PAD * 2) * scale,
						LINE * scale,
						need.name(),
						craftable,
						() -> openShopping(need.name())
					));
				}
				cursor += LINE;
			}
		}
		graphics.pose().popMatrix();
	}

	private static void hoverRecipe(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		if (!StrayConfig.get().gardenShoppingHudEnabled || HudLayout.editorOpen()) {
			return;
		}
		for (Hit hit : ITEM_HITS) {
			if (hit.contains(mouseX, mouseY)) {
				String command = StrayConfig.get().gardenShoppingClickLabel();
				String tip = hit.craftable
					? "Craftable · Click for " + command + " " + hit.query
					: "Click for " + command + " " + hit.query;
				graphics.setTooltipForNextFrame(font, Component.literal(tip), mouseX, mouseY);
				return;
			}
		}
	}

	private static float hoeHeightOf(HoeSnap snap) {
		return hoeWarn(snap).isEmpty() ? PAD * 2 + LINE : PAD * 2 + LINE * 2;
	}

	private static String hoeWarn(HoeSnap snap) {
		if (snap.overclock()) {
			return "Overclock required";
		}
		if (snap.upgrade()) {
			return "Upgrade required";
		}
		if (snap.overflow()) {
			return "Overflow";
		}
		return "";
	}

	private static float visitorHeightOf(VisitorSnap snap) {
		int rows = 1 + Math.max(1, snap.names().size());
		return PAD * 2 + rows * LINE;
	}

	private static float shoppingHeightOf(ShoppingSnap snap) {
		int rows = 1 + Math.max(1, Math.min(8, snap.items().size()));
		return PAD * 2 + rows * LINE;
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
		return new VisitorSnap(true, 2, List.of("Emissary Carlton", "Spaceman"), "11m", false, false);
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
			List.of(new Need("Enchanted Bread", "ENCHANTED_BREAD", 64, 12, true), new Need("Enchanted Sugar", "ENCHANTED_SUGAR", 32, 32, false)),
			List.of(),
			List.of()
		);
	}

	private static void openShopping(String name) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.player.connection == null || name == null || name.isBlank()) {
			return;
		}
		String command = StrayConfig.get().gardenShoppingBz ? "bz" : "recipe";
		client.player.connection.sendCommand(command + " " + name.trim());
	}

	private record Hit(float x, float y, float w, float h, String query, boolean craftable, Runnable click) {
		private boolean contains(double mx, double my) {
			return w > 0 && h > 0 && mx >= x && mx <= x + w && my >= y && my <= y + h;
		}
	}
}
