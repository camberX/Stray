package dev.stray.client.ui;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.config.UnloadState;
import dev.stray.client.render.GuiDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The "uid?" click GUI: a 660 x 560 window in screen pixels (times the DPI scale) with a
 * layered frame, a colour strip, a rail of eight 75 x 64 tabs and, per tab, the modules as
 * group boxes in two columns. Each group starts with an "Enabled" checkbox and then the
 * module's own settings, drawn by the same rows every other style uses, in this look.
 */
public final class UidMenu {
	public static final float W = 660f;
	public static final float H = 560f;
	private static final float TAB_W = 75f;
	private static final float TAB_H = 64f;
	/** Tabs start 20 px down the window (MenuWindow::update_dpi). */
	private static final float TAB_TOP = 20f;
	public static final String[] DPI_LABELS = {"Auto", "100%", "125%", "150%", "175%", "200%"};

	/** A rail tab: its icon and the click GUI categories it holds. */
	private record Tab(String name, String glyph, String[] categories) {
	}

	/** Eight full-size tabs, each holding one or more click GUI categories. */
	private static final Tab[] TABS = {
		new Tab("Dungeons", "\uEAB1", new String[] {"Dungeons"}),
		new Tab("M4", "\uE799", new String[] {"M4"}),
		new Tab("Skills", "\uEAD5", new String[] {"Mining", "Farming", "Foraging"}),
		new Tab("Slayer", "\uEBE9", new String[] {"Slayer"}),
		new Tab("World", "\uE80B", new String[] {"World"}),
		new Tab("Visuals", "\uE8F4", new String[] {"Entities", "Mobs"}),
		new Tab("HUD", "\uE871", new String[] {"HUD", "Chat"}),
		new Tab("Misc", "\uE8B8", new String[] {"Menus", "Player", "Keys", "Client"}),
	};



	private static boolean drawing;
	private static int active;
	private static float winX = Float.NaN;
	private static float winY;
	/** One menu unit in GUI units: DPI scale over the GUI scale. */
	private static float unit = 0.5f;
	private static final float[] scroll = new float[TABS.length];
	private static float contentH;
	private static float areaX;
	private static float areaY;
	private static float areaW;
	private static float areaH;
	/** Group heights measured last frame, by key. */
	private static final Map<String, Float> heights = new HashMap<>();
	/** The lowest point a row drew to in the group being drawn. */
	private static float extent;
	/** Popups (dropdown lists) drawn after everything else, so they cover the rows under them. */
	private static final List<Runnable> deferred = new ArrayList<>();
	private static boolean dragging;
	private static float dragDX;
	private static float dragDY;
	private static float mobListScroll;
	private static float popupScroll;
	/** The rail's highlight, gliding to the active tab. */
	private static float railY = Float.NaN;
	private static long lastFrame;
	private static long switchedAt;
	private static final float PAGE_MS = 190f;

	private UidMenu() {
	}

	public static boolean active() {
		return "uid".equals(StrayConfig.get().clickGuiStyle);
	}

	public static boolean drawing() {
		return drawing;
	}

	/** The accent, from "Menu color" (green by default). */
	public static int accent() {
		return 0xFF000000 | (StrayConfig.get().uidAccent & 0xFFFFFF);
	}

	/** Menu units per GUI unit, for the screen's view transform. */
	static float unit() {
		Minecraft client = Minecraft.getInstance();
		double gui = client == null || client.getWindow() == null ? 2.0 : client.getWindow().getGuiScale();
		return (float) (dpiScale() / Math.max(1.0, gui));
	}

	/**
	 * The DPI scale: as picked, or "Auto": the menu is sized for a 1080p screen, so scale with the
	 * window's height in 25 % steps (1440p 125 %, 4K 200 %).
	 */
	static double dpiScale() {
		Minecraft client = Minecraft.getInstance();
		int height = client == null || client.getWindow() == null ? 1080 : client.getWindow().getHeight();
		int dpi = Mth.clamp(StrayConfig.get().uidDpi, 0, DPI_LABELS.length - 1);
		double picked = dpi > 0 ? 1.0 + (dpi - 1) * 0.25 : Mth.clamp(Math.round(height / 1080.0 * 4.0) / 4.0, 1.0, 3.0);
		// Never taller than the window: on short screens the whole menu shrinks to fit.
		return Math.min(picked, Math.max(0.5, (height - 16) / H));
	}


	/** easeOutCubic. */
	private static float ease(float t) {
		float u = 1f - t;
		return 1f - u * u * u;
	}

	/** A row reports how far down it drew, so the group knows its height. */
	static void extent(float bottom) {
		if (drawing && bottom > extent) {
			extent = bottom;
		}
	}

	static void defer(Runnable draw) {
		deferred.add(draw);
	}

	static float popupScroll() {
		return popupScroll;
	}

	static void popupScroll(float value) {
		popupScroll = value;
	}

	// ------------------------------------------------------------------ drawing

	static void draw(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		unit = unit();
		float sw = screen.width / unit;
		float sh = screen.height / unit;
		if (Float.isNaN(winX)) {
			winX = Math.round((sw - W) / 2f);
			winY = Math.round((sh - H) / 2f);
		}
		winX = Math.round(Mth.clamp(winX, 0, Math.max(0, sw - W)));
		winY = Math.round(Mth.clamp(winY, 0, Math.max(0, sh - H)));
		int mx = Math.round(mouseX / unit);
		int my = Math.round(mouseY / unit);
		ClickGui.pointer(mx, my);
		int[] saved = swapTheme();
		drawing = true;
		deferred.clear();
		graphics.pose().pushMatrix();
		graphics.pose().scale(unit, unit);
		// Open / close: the window grows from 96 %, rises a little and fades in; closing plays it back.
		float open = ease(Mth.clamp(screen.clickAppear(), 0f, 1f));
		long now = System.nanoTime();
		float dt = lastFrame == 0L ? 0f : Math.min(0.1f, (now - lastFrame) / 1.0e9f);
		lastFrame = now;
		float targetY = TAB_TOP + active * TAB_H;
		railY = Float.isNaN(railY) || open < 0.05f ? targetY : railY + (targetY - railY) * Math.min(1f, dt * 22f);
		float page = ease(Mth.clamp((now - switchedAt) / (PAGE_MS * 1.0e6f), 0f, 1f));
		try {
			GuiDraw.alphaScale = open;
			GuiDraw.fill(graphics, 0, 0, sw, sh, 0x30000000);
			float cx = winX + W / 2f;
			float cy = winY + H / 2f;
			float grow = 0.96f + 0.04f * open;
			graphics.pose().translate(cx, cy + (1f - open) * 10f);
			graphics.pose().scale(grow, grow);
			graphics.pose().translate(-cx, -cy);
			UidLook.window(graphics, winX, winY, W, H);
			UidLook.rail(graphics, winX, winY, H, TAB_W, railY, TAB_H);
			tabs(screen, graphics, font, mx, my);
			// Switching tabs: the new page slides in from the right as it fades in.
			GuiDraw.alphaScale = open * page;
			graphics.pose().pushMatrix();
			graphics.pose().translate((1f - page) * 14f, 0f);
			content(screen, graphics, font, mx, my);
			graphics.pose().popMatrix();
			GuiDraw.alphaScale = open;
			UidLook.strip(graphics, winX, winY, W);
			for (Runnable r : new ArrayList<>(deferred)) {
				r.run();
			}
			screen.clickPicker(graphics, font);
		} finally {
			GuiDraw.alphaScale = 1f;
			graphics.pose().popMatrix();
			drawing = false;
			restoreTheme(saved);
		}
	}

	private static void tabs(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mx, int my) {
		for (int i = 0; i < TABS.length; i++) {
			float tx = winX + 6;
			float ty = winY + TAB_TOP + i * TAB_H;
			if (ty + TAB_H > winY + H - 6) {
				break;
			}
			boolean hovered = GuiDraw.hovered(mx, my, tx, ty, TAB_W, TAB_H);
			int tint = i == active ? 0xFFFFFFFF : hovered ? 0xC8FFFFFF : 0x64FFFFFF;
			float scale = 3.25f;
			float gw = GuiDraw.iconWidth(font, TABS[i].glyph(), scale);
			GuiDraw.icon(graphics, font, TABS[i].glyph(), tx + (TAB_W - gw) / 2f, ty + (TAB_H - 9f * scale) / 2f + 2f, scale, tint);
			int index = i;
			screen.clickHit(tx, ty, TAB_W, TAB_H, () -> {
				if (active != index) {
					active = index;
					switchedAt = System.nanoTime();
					ClickGui.blurMobSearch();
				}
			});
		}
	}

	/** One group: a module, or a category's loose rows (key binds, the mob list). */
	private record Group(String key, String title, ClickGui.Mod mod, String category, List<MenuItems.Item> loose) {
	}

	private static List<Group> groups(StrayScreen screen) {
		List<Group> out = new ArrayList<>();
		for (String category : TABS[active].categories()) {
			List<MenuItems.Item> loose = new ArrayList<>();
			for (MenuItems.Item item : MenuItems.items(screen, category)) {
				ClickGui.Mod mod = item.mod();
				if (mod != null) {
					out.add(new Group("mod:" + mod.name(), ClickGui.display(mod.name()), mod, category, null));
				} else if (item.key().startsWith("mod:")) {
					// A plain module without settings.
					String name = item.key().substring(4);
					ClickGui.Mod plain = find(name);
					out.add(new Group(item.key(), item.label(), plain, category, null));
				} else {
					loose.add(item);
				}
			}
			if (!loose.isEmpty() || "Mobs".equals(category)) {
				out.add(new Group("cat:" + category, "Mobs".equals(category) ? "Mob glow" : ClickGui.display(category), null, category, loose));
			}
		}
		return out;
	}

	private static ClickGui.Mod find(String name) {
		for (ClickGui.Mod mod : ClickGui.modules()) {
			if (mod.name().equals(name)) {
				return mod;
			}
		}
		return null;
	}

	private static void content(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mx, int my) {
		// The 24-column grid's area: right of the rail, inside the frame (layout_category_grid).
		areaX = winX + 6 + TAB_W + 10;
		areaY = winY + 10 + 10;
		areaW = W - 6 - TAB_W - 10 - 7 - 10;
		areaH = H - 10 - 10 - 6 - 10;
		float colW = (float) Math.floor(areaW / 2f);
		float groupW = colW - 20;
		List<Group> list = groups(screen);
		float[] colY = {0f, 0f};
		float viewH = areaH;
		float maxScroll = Math.max(0f, contentH - viewH);
		scroll[active] = Mth.clamp(scroll[active], 0f, maxScroll);
		float offset = scroll[active];
		int mark = screen.clickHitMark();
		boolean clip = GuiDraw.scissor(graphics, areaX, areaY, areaW, areaH);
		try {
			for (Group group : list) {
				int col = colY[0] <= colY[1] ? 0 : 1;
				float gx = areaX + col * colW + 10;
				float gy = areaY + colY[col] + 10 - offset;
				float measured = heights.getOrDefault(group.key(), estimate(group));
				float gh = Math.max(24f, measured);
				// Only draw what can be seen; heights of the rest stay from when they were.
				if (gy < areaY + areaH && gy + gh > areaY) {
					UidLook.group(graphics, font, gx, gy, groupW, gh, group.title());
					extent = gy;
					float top = gy + 12;
					int rows = screen.clickHitMark();
					drawGroup(screen, graphics, font, mx, my, group, gx + 10, top, groupW - 20);
					// Rows not drawn in this look don't report how far they go; their click areas do.
					float bottom = screen.clickHitsBottom(rows, extent);
					float h = bottom - gy + 9;
					heights.put(group.key(), Math.max(24f, h));
				}
				colY[col] += gh + 20;
			}
		} finally {
			if (clip) {
				GuiDraw.disableScissor(graphics);
			}
			screen.clickClipHits(mark, areaX, areaY, areaW, areaH);
		}
		contentH = Math.max(colY[0], colY[1]);
		if (contentH > viewH + 1) {
			UidLook.scrollbar(graphics, areaX + areaW - 7, areaY + 1, areaH - 2, viewH, contentH, offset);
			if (offset > 0) {
				UidLook.arrow(graphics, areaX + areaW - 18, areaY + 9, 0xC8FFFFFF, false);
			}
			if (offset < maxScroll - 0.5f) {
				UidLook.arrow(graphics, areaX + areaW - 18, areaY + areaH - 9, 0xC8FFFFFF, true);
			}
		}
	}

	private static float estimate(Group group) {
		if (group.mod() != null && group.mod().feature() != null) {
			return 18f + group.mod().feature().rows() * 20f + 20f;
		}
		return 40f;
	}

	private static void drawGroup(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mx, int my, Group group, float x, float y, float w) {
		ClickGui.Mod mod = group.mod();
		if (mod != null) {
			if (!mod.hold()) {
				y = screen.clickToggle(graphics, font, x, y, w, "Enabled", mod.on().getAsBoolean(), () -> ClickGui.toggle(mod));
			}
			// A switched-off module only shows its switch; its settings come back when it's on.
			boolean on = mod.hold() || mod.on().getAsBoolean();
			if (on && ClickGui.moduleHasSettings(mod)) {
				int mark = screen.clickHitMark();
				ClickGui.drawModuleSettings(screen, graphics, font, mx, my, mod, x, y, w);
				y = Math.max(y, Math.max(extent, screen.clickHitsBottom(mark, y)));
			}
			y = sharedRows(screen, graphics, font, mod, on, x, y, w);
			extent(y);
			return;
		}
		if ("Mobs".equals(group.category())) {
			mobList(screen, graphics, font, mx, my, group.loose(), x, y, w);
			return;
		}
		for (MenuItems.Item item : group.loose()) {
			if (item.on() != null) {
				Runnable left = item.left();
				y = screen.clickToggle(graphics, font, x, y, w, item.label(), item.on(), () -> {
					if (left != null) {
						left.run();
					}
				});
			} else if (item.key().startsWith("bind:")) {
				y = keyRow(screen, graphics, font, mx, my, x, y, w, item);
			} else {
				y = screen.clickAction(graphics, font, x, y, w, item.label(), item.left());
			}
		}
		extent(y);
	}

	/**
	 * Settings two modules share, shown under whichever of them is on: which star mobs count
	 * (bats, fels) live under Star mobs, but the star mob box uses them too.
	 */
	private static float sharedRows(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, ClickGui.Mod mod, boolean on, float x, float y, float w) {
		StrayConfig config = StrayConfig.get();
		if ("Star mob box".equals(mod.name()) && on && !config.starVisuals.glowEnabled) {
			y = screen.clickToggle(graphics, font, x, y, w, "Highlight bats", config.starMobBats, () -> config.starMobBats = !config.starMobBats);
			y = screen.clickToggle(graphics, font, x, y, w, "Highlight fels", config.starMobFels, () -> config.starMobFels = !config.starMobFels);
		}
		return y;
	}

	/** A key's name as the menu shows it: upper case, "-" when there is none. */
	static String keyText(String label) {
		if (label == null || label.isBlank() || "none".equalsIgnoreCase(label.trim())) {
			return "-";
		}
		return label.toUpperCase(java.util.Locale.ROOT);
	}

	/** A key bind as a label with the key in brackets at the right, like the menu's hotkeys. */
	private static float keyRow(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mx, int my, float x, float y, float w, MenuItems.Item item) {
		UidLook.text(graphics, font, item.label(), x + 20, y, UidLook.LABEL);
		boolean listening = "Press a key".equals(item.chip());
		String value = "[" + (listening ? "..." : keyText(item.chip())) + "]";
		float vw = UidLook.smallWidth(font, value);
		boolean hovered = GuiDraw.hovered(mx, my, x + w - vw - 2, y, vw + 4, 12);
		UidLook.small(graphics, font, value, x + w - vw, y + 1, listening ? UidLook.KEY_LISTEN : hovered ? UidLook.KEY_HOVER : UidLook.KEY);
		screen.clickHit(x, y, w, 12, item.left());
		extent(y + 12);
		return y + 17;
	}

	/** Mob glow: a search box and a list box; picked mobs show in the accent. */
	private static void mobList(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mx, int my, List<MenuItems.Item> items, float x, float y, float w) {
		float lx = x + 20;
		float lw = w - 20;
		float rowH = 18f;
		float listH = 300f;
		UidLook.textbox(graphics, lx, y, lw, 20);
		boolean focused = ClickGui.mobSearchFocused();
		String query = ClickGui.mobQuery();
		String shown = query.isEmpty() && !focused ? "Search" : query + (focused && (System.currentTimeMillis() / 500L) % 2L == 0L ? "|" : "");
		UidLook.text(graphics, font, shown, lx + 5, y + 5, query.isEmpty() && !focused ? UidLook.KEY : focused ? accent() : UidLook.LABEL);
		ClickGui.mobSearchBox(lx, y, lw, 20);
		screen.clickHit(lx, y, lw, 20, ClickGui::focusMobSearch);
		float ly = y + 25;
		UidLook.rect(graphics, lx, ly, lw, listH, 0xFF232323);
		UidLook.outline(graphics, lx, ly, lw, listH, UidLook.EDGE);
		float content = items.size() * rowH;
		mobListScroll = Mth.clamp(mobListScroll, 0f, Math.max(0f, content - listH + 2));
		int mark = screen.clickHitMark();
		boolean clip = GuiDraw.scissor(graphics, lx + 1, ly + 1, lw - 2, listH - 2);
		try {
			for (int i = 0; i < items.size(); i++) {
				float ry = ly + 1 + i * rowH - mobListScroll;
				if (ry + rowH < ly || ry > ly + listH) {
					continue;
				}
				MenuItems.Item item = items.get(i);
				boolean hovered = GuiDraw.hovered(mx, my, lx, ry, lw, rowH);
				if (hovered) {
					UidLook.rect(graphics, lx + 1, ry + 1, lw - 2, rowH - 2, 0xFF1A1A1A);
				}
				boolean on = Boolean.TRUE.equals(item.on());
				UidLook.text(graphics, font, UidLook.fit(font, item.label(), lw - 16), lx + 8, ry + 4, on ? accent() : UidLook.LABEL);
				screen.clickHit(lx, ry, lw, rowH, item.left());
			}
			if (items.isEmpty()) {
				UidLook.text(graphics, font, "Nothing matches", lx + 8, ly + 6, UidLook.LABEL_OFF);
			}
		} finally {
			if (clip) {
				GuiDraw.disableScissor(graphics);
			}
			screen.clickClipHits(mark, lx, ly, lw, listH);
		}
		if (content > listH) {
			UidLook.scrollbar(graphics, lx + lw - 8, ly + 1, listH - 2, listH, content, mobListScroll);
		}
		mobBox[0] = lx;
		mobBox[1] = ly;
		mobBox[2] = lw;
		mobBox[3] = listH;
		extent(ly + listH);
	}

	private static final float[] mobBox = new float[4];

	// ------------------------------------------------------------------ input

	static boolean scroll(double x, double y, double wheel) {
		if (wheel == 0) {
			return false;
		}
		if (popupOpen && contains(x, y, popupBox[0], popupBox[1], popupBox[2], popupBox[3])) {
			popupScroll = Math.max(0f, popupScroll - (float) wheel * 20f);
			return true;
		}
		if (TABS[active].name().equals("Visuals") && contains(x, y, mobBox[0], mobBox[1], mobBox[2], mobBox[3])) {
			mobListScroll = Math.max(0f, mobListScroll - (float) wheel * 36f);
			return true;
		}
		if (contains(x, y, areaX, areaY, areaW, areaH)) {
			scroll[active] = Math.max(0f, scroll[active] - (float) wheel * 40f);
			return true;
		}
		return false;
	}

	private static boolean popupOpen;
	private static final float[] popupBox = new float[4];

	static void popup(float x, float y, float w, float h) {
		popupOpen = true;
		popupBox[0] = x;
		popupBox[1] = y;
		popupBox[2] = w;
		popupBox[3] = h;
	}

	static void noPopup() {
		popupOpen = false;
	}

	/** A press nothing else took: on the window it starts a drag. */
	static boolean press(double x, double y) {
		if (contains(x, y, winX, winY, W, H)) {
			dragging = true;
			dragDX = (float) x - winX;
			dragDY = (float) y - winY;
			return true;
		}
		return false;
	}

	static boolean drag(double x, double y) {
		if (!dragging) {
			return false;
		}
		winX = (float) x - dragDX;
		winY = (float) y - dragDY;
		return true;
	}

	static void release() {
		dragging = false;
	}

	/** "Reset menu layout": back to the middle. */
	static void resetPosition() {
		winX = Float.NaN;
	}

	private static boolean contains(double x, double y, float rx, float ry, float rw, float rh) {
		return x >= rx && x <= rx + rw && y >= ry && y <= ry + rh;
	}

	// ------------------------------------------------------------------ theme

	/** Other rows read the Theme colours: give them this look's while it draws. */
	private static int[] swapTheme() {
		int[] old = {Theme.ACCENT, Theme.ACCENT_DIM, Theme.CARD, Theme.CARD_HOVER, Theme.LINE, Theme.TRACK, Theme.PANEL, Theme.TEXT, Theme.MUTED, Theme.OFF, Theme.WINDOW, Theme.WINDOW_SOLID, Theme.SHEET};
		int accent = accent();
		Theme.ACCENT = accent;
		Theme.ACCENT_DIM = accent & 0x00FFFFFF | 0x66000000;
		Theme.CARD = 0xFF1F1F1F;
		Theme.CARD_HOVER = 0xFF292929;
		Theme.LINE = UidLook.EDGE;
		Theme.TRACK = 0xFF323232;
		Theme.PANEL = 0xFF232323;
		Theme.TEXT = UidLook.LABEL;
		Theme.MUTED = UidLook.VALUE;
		Theme.OFF = 0xFF646464;
		Theme.WINDOW = 0xFF171717;
		Theme.WINDOW_SOLID = 0xFF171717;
		Theme.SHEET = 0xFF282828;
		return old;
	}

	private static void restoreTheme(int[] old) {
		Theme.ACCENT = old[0];
		Theme.ACCENT_DIM = old[1];
		Theme.CARD = old[2];
		Theme.CARD_HOVER = old[3];
		Theme.LINE = old[4];
		Theme.TRACK = old[5];
		Theme.PANEL = old[6];
		Theme.TEXT = old[7];
		Theme.MUTED = old[8];
		Theme.OFF = old[9];
		Theme.WINDOW = old[10];
		Theme.WINDOW_SOLID = old[11];
		Theme.SHEET = old[12];
	}

	static void markDirty() {
		UnloadState.markDirty();
	}
}
