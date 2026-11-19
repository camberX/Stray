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
import java.util.Locale;
import java.util.Map;

/**
 * The Expressive (Material 3 Expressive) click GUI: a large rounded window in screen pixels
 * (times its DPI scale) with a navigation rail, a big title, filter chips for a tab's
 * categories, a search field and every module as a card in masonry columns. A switched-on
 * module's card shows its settings; switched-off modules collect into one "Off" card. A
 * card's colours collapse into one chip that opens them in a popover beside the card.
 */
public final class ExpressiveMenu {
	public static final float MIN_W = 880f;
	public static final float MIN_H = 560f;
	private static final float DEF_W = 1060f;
	private static final float DEF_H = 660f;
	private static final float GRIP = 16f;
	private static final float RAIL_W = 92f;
	private static final float CARD_MIN = 236f;
	private static final float GAP = 10f;
	private static final float HEADER = 82f;

	private record Tab(String name, String glyph, String[] categories) {
	}

	private static final Tab[] TABS = {
		new Tab("Dungeons", "", new String[] {"Dungeons"}),
		new Tab("M4", "", new String[] {"M4"}),
		new Tab("Skills", "", new String[] {"Mining", "Farming", "Foraging"}),
		new Tab("Slayer", "", new String[] {"Slayer"}),
		new Tab("World", "", new String[] {"World"}),
		new Tab("Visuals", "", new String[] {"Entities", "Mobs"}),
		new Tab("HUD", "", new String[] {"HUD", "Chat"}),
		new Tab("Misc", "", new String[] {"Menus", "Player", "Keys", "Client"}),
	};

	private static boolean drawing;
	private static int active;
	/** Which of the tab's categories the chips show: -1 all of them. */
	private static final int[] filter = new int[TABS.length];

	static {
		java.util.Arrays.fill(filter, -1);
	}
	private static float winX = Float.NaN;
	private static float winY;
	private static float winW = DEF_W;
	private static float winH = DEF_H;
	private static boolean dragging;
	private static boolean resizing;
	private static float dragDX;
	private static float dragDY;
	private static float unit = 0.5f;
	private static final float[] scroll = new float[TABS.length];
	private static float contentH;
	private static float areaX;
	private static float areaY;
	private static float areaW;
	private static float areaH;
	private static final Map<String, Float> heights = new HashMap<>();
	private static float extent;
	private static final List<Runnable> deferred = new ArrayList<>();
	private static float railY = Float.NaN;
	private static long lastFrame;
	private static long switchedAt;
	private static float fade = 1f;
	private static Object fadeScreen;
	private static final Map<String, Float> anims = new HashMap<>();

	/** The card being drawn collects its colour rows here instead of drawing them. */
	private static List<Colour> colours;
	/** Colour rows draw inline (the theme page lists its roles). */
	private static boolean inline;
	/** The card whose colours popover is open, and where. */
	private static String popoverKey;
	private static final List<Colour> popover = new ArrayList<>();
	private static final float[] popoverBox = new float[4];
	private static float popoverAnchorX;
	private static float popoverAnchorY;
	private static float popoverAnchorW;
	private static float popupScroll;
	private static boolean popupOpen;
	private static final float[] popupBox = new float[4];

	/** One colour setting of a card: its name, colour, and how to open the picker for it at a spot. */
	public record Colour(String label, int rgb, PickAt open) {
	}

	@FunctionalInterface
	public interface PickAt {
		void at(float x, float y);
	}

	private ExpressiveMenu() {
	}

	public static boolean active() {
		return Looks.ex();
	}

	public static boolean drawing() {
		return drawing;
	}

	static float unit() {
		Minecraft client = Minecraft.getInstance();
		double gui = client == null || client.getWindow() == null ? 2.0 : client.getWindow().getGuiScale();
		return (float) (dpiScale() / Math.max(1.0, gui));
	}

	/** Sized for a 1080p screen, then scaled with the window's height in quarters; never taller than the window. */
	static double dpiScale() {
		Minecraft client = Minecraft.getInstance();
		int height = client == null || client.getWindow() == null ? 1080 : client.getWindow().getHeight();
		double picked = Mth.clamp(Math.round(height / 1080.0 * 4.0) / 4.0, 1.0, 3.0);
		return Math.min(picked, Math.max(0.5, (height - 16) / MIN_H));
	}

	private static float smooth(float t) {
		return t * t * (3f - 2f * t);
	}

	/** Material's emphasized decelerate, close enough: fast out, long settle. */
	private static float emphasized(float t) {
		float u = 1f - t;
		return 1f - u * u * u * u;
	}

	private static float approach(float v, float target, float step) {
		return v < target ? Math.min(target, v + step) : Math.max(target, v - step);
	}

	static boolean faded(Object screen) {
		return screen != fadeScreen || fade <= 0.001f || StrayConfig.get().exAnimMs <= 0;
	}

	public static void extent(float bottom) {
		if (drawing && bottom > extent) {
			extent = bottom;
		}
	}

	public static void defer(Runnable draw) {
		deferred.add(draw);
	}

	static float popupScroll() {
		return popupScroll;
	}

	static void popupScroll(float value) {
		popupScroll = value;
	}

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

	/** True while a card collects its colour rows (they then draw nothing themselves). */
	public static boolean collecting() {
		return drawing && colours != null && !inline;
	}

	public static void collect(Colour colour) {
		if (colours != null) {
			colours.add(colour);
		}
	}

	/** An eased value per key: switches and hovers glide instead of snapping. */
	static float anim(String key, float target) {
		Float now = anims.get(key);
		if (now == null) {
			anims.put(key, target);
			return target;
		}
		float next = now + (target - now) * 0.35f;
		if (Math.abs(next - target) < 0.01f) {
			next = target;
		}
		anims.put(key, next);
		return next;
	}

	// ------------------------------------------------------------------ drawing

	static void draw(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		unit = unit();
		ExLook.textScale = dpiScale();
		float sw = screen.width / unit;
		float sh = screen.height / unit;
		StrayConfig cfg = StrayConfig.get();
		if (!resizing) {
			winW = cfg.exWidth > 0 ? cfg.exWidth : DEF_W;
			winH = cfg.exHeight > 0 ? cfg.exHeight : DEF_H;
		}
		winW = Math.round(Mth.clamp(winW, MIN_W, Math.max(MIN_W, sw - 8)));
		winH = Math.round(Mth.clamp(winH, MIN_H, Math.max(MIN_H, sh - 8)));
		if (Float.isNaN(winX)) {
			winX = Math.round((sw - winW) / 2f);
			winY = Math.round((sh - winH) / 2f);
		}
		winX = Math.round(Mth.clamp(winX, 0, Math.max(0, sw - winW)));
		winY = Math.round(Mth.clamp(winY, 0, Math.max(0, sh - winH)));
		int mx = Math.round(mouseX / unit);
		int my = Math.round(mouseY / unit);
		ClickGui.pointer(mx, my);
		drawing = true;
		deferred.clear();
		graphics.pose().pushMatrix();
		graphics.pose().scale(unit, unit);
		long now = System.nanoTime();
		int ms = cfg.exAnimMs;
		if (screen != fadeScreen) {
			fadeScreen = screen;
			fade = ms > 0 ? 0f : 1f;
			lastFrame = now;
			popoverKey = null;
		}
		float dt = Math.min(1f / 30f, (now - lastFrame) / 1.0e9f);
		lastFrame = now;
		float target = screen.clickClosing() ? 0f : 1f;
		fade = ms <= 0 ? target : approach(fade, target, dt * 1000f / ms);
		float open = emphasized(fade);
		float targetY = railTop() + active * railStep();
		railY = Float.isNaN(railY) || ms <= 0 ? targetY : railY + (targetY - railY) * Math.min(1f, dt * 5000f / Math.max(ms, 1));
		float page = ms <= 0 ? 1f : emphasized(Mth.clamp((now - switchedAt) / (ms * 1.0e6f), 0f, 1f));
		try {
			GuiDraw.alphaScale = open;
			GuiDraw.fill(graphics, 0, 0, sw, sh, ExColors.alpha(0xFF000000, 0.32f));
			// Opening grows the window a touch from its middle while it fades in.
			float grow = 0.94f + 0.06f * open;
			graphics.pose().pushMatrix();
			graphics.pose().translate(winX + winW / 2f, winY + winH / 2f);
			graphics.pose().scale(grow, grow);
			graphics.pose().translate(-(winX + winW / 2f), -(winY + winH / 2f));
			ExLook.surface(graphics, winX, winY, winW, winH, 28f, ExColors.alpha(ExColors.surface, ExColors.opacity()), true);
			rail(screen, graphics, font, mx, my);
			header(screen, graphics, font, mx, my);
			GuiDraw.alphaScale = open * page;
			graphics.pose().pushMatrix();
			graphics.pose().translate(0f, (1f - page) * 18f);
			content(screen, graphics, font, mx, my);
			graphics.pose().popMatrix();
			GuiDraw.alphaScale = open;
			grip(graphics, mx, my);
			for (Runnable r : new ArrayList<>(deferred)) {
				r.run();
			}
			if (popoverKey != null) {
				drawPopover(screen, graphics, font, mx, my);
			}
			screen.clickPicker(graphics, font);
			graphics.pose().popMatrix();
		} finally {
			GuiDraw.alphaScale = 1f;
			graphics.pose().popMatrix();
			drawing = false;
			colours = null;
		}
	}

	private static float railTop() {
		return 74f;
	}

	private static float railStep() {
		return Math.min(64f, (winH - railTop() - 16f) / TABS.length);
	}

	private static void rail(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mx, int my) {
		float rx = winX + 8;
		float rw = RAIL_W;
		ExLook.rounded(graphics, rx, winY + 8, rw, winH - 16, 22f, ExColors.alpha(ExColors.surfaceLow, 0.9f));
		String brand = "stray";
		float bw = ExLook.width(font, ExLook.TITLE, brand);
		ExLook.text(graphics, font, ExLook.TITLE, brand, rx + (rw - bw) / 2f, winY + 26, ExColors.primary);
		float step = railStep();
		float pillW = 56f;
		float pillH = 32f;
		for (int i = 0; i < TABS.length; i++) {
			float ty = winY + railTop() + i * step;
			boolean on = i == active;
			boolean hover = GuiDraw.hovered(mx, my, rx, ty, rw, step);
			float h = anim("rail" + i, hover && !on ? 1f : 0f);
			if (h > 0.01f) {
				ExLook.rounded(graphics, rx + (rw - pillW) / 2f, ty + 4, pillW, pillH, pillH / 2f, ExColors.alpha(ExColors.onSurface, 0.08f * h));
			}
			int ink = on ? ExColors.onSecondaryContainer : ExColors.onSurfaceVariant;
			String glyph = TABS[i].glyph();
			float scale = 2.3f;
			float gw = GuiDraw.iconWidth(font, glyph, scale);
			drawIndicatorIfHere(graphics, rx, rw, pillW, pillH, ty, i);
			GuiDraw.icon(graphics, font, glyph, rx + (rw - gw) / 2f, ty + 4 + (pillH - 9f * scale) / 2f + 1f, scale, ink);
			String name = TABS[i].name();
			int family = on ? ExLook.BOLD : ExLook.SMALL;
			float nw = ExLook.width(font, family, name);
			ExLook.text(graphics, font, family, name, rx + (rw - nw) / 2f, ty + 4 + pillH + 4, on ? ExColors.onSurface : ExColors.onSurfaceVariant);
			int index = i;
			screen.clickHit(rx, ty, rw, step, () -> {
				if (active != index) {
					active = index;
					switchedAt = System.nanoTime();
					popoverKey = null;
					ClickGui.blurMobSearch();
				}
			});
		}
	}

	private static void drawIndicatorIfHere(GuiGraphicsExtractor graphics, float rx, float rw, float pillW, float pillH, float ty, int i) {
		if (i != 0) {
			return;
		}
		// Drawn once (with the first tab) so it sits under every icon; it may be between tabs.
		float py = winY + railY + 4;
		float stretch = Math.abs((winY + railTop() + active * railStep()) - (winY + railY)) > 2f ? 6f : 0f;
		ExLook.rounded(graphics, rx + (rw - pillW) / 2f - stretch / 2f, py, pillW + stretch, pillH, pillH / 2f, ExColors.secondaryContainer);
	}

	private static void header(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mx, int my) {
		float x = winX + 8 + RAIL_W + 24;
		float right = winX + winW - 24;
		Tab tab = TABS[active];
		String heading = ClickGui.filterText().isEmpty() ? tab.name() : "Results";
		ExLook.text(graphics, font, ExLook.HEADLINE, heading, x, winY + 24, ExColors.onSurface);
		float titleW = ExLook.width(font, ExLook.HEADLINE, heading);
		// Search: a pill field on the right.
		float sw = 240f;
		float sh = 40f;
		float sx = right - sw;
		float sy = winY + 22;
		boolean focused = ClickGui.searchFocused();
		boolean hover = GuiDraw.hovered(mx, my, sx, sy, sw, sh);
		ExLook.rounded(graphics, sx, sy, sw, sh, sh / 2f, ExColors.surfaceHigh);
		ExLook.state(graphics, sx, sy, sw, sh, sh / 2f, hover ? 1f : 0f, false, ExColors.onSurface);
		if (focused) {
			GuiDraw.hairline(graphics, sx, sy, sw, sh, sh / 2f, ExColors.primary);
		}
		GuiDraw.icon(graphics, font, MenuFont.SEARCH, sx + 14, sy + (sh - 13.5f) / 2f + 1f, 1.5f, ExColors.onSurfaceVariant);
		String query = ClickGui.filterText();
		boolean empty = query.isEmpty() && !focused;
		String shown = empty ? "Search " + ClickGui.modules().size() + " modules" : query + (focused && (System.currentTimeMillis() / 500L) % 2L == 0L ? "|" : "");
		ExLook.text(graphics, font, ExLook.BODY, ExLook.fit(font, ExLook.BODY, shown, sw - 56), sx + 40, sy + 13, empty ? ExColors.onSurfaceVariant : ExColors.onSurface);
		ClickGui.searchBox(sx, sy, sw, sh);
		screen.clickHit(sx, sy, sw, sh, ClickGui::focusSearch);
		// A tab with more than one category gets filter chips between the title and the search.
		if (tab.categories().length > 1 && query.isEmpty()) {
			float cx = x + titleW + 24;
			float ch = 32f;
			float cy = winY + 26;
			String[] labels = new String[tab.categories().length + 1];
			labels[0] = "All";
			for (int i = 0; i < tab.categories().length; i++) {
				labels[i + 1] = ClickGui.display(tab.categories()[i]);
			}
			for (int i = 0; i < labels.length; i++) {
				boolean picked = filter[active] == i - 1;
				float lw = ExLook.width(font, picked ? ExLook.BOLD : ExLook.BODY, labels[i]);
				float cw = lw + (picked ? 44f : 28f);
				if (cx + cw > sx - 12) {
					break;
				}
				boolean h = GuiDraw.hovered(mx, my, cx, cy, cw, ch);
				ExLook.chip(graphics, cx, cy, cw, ch, picked, anim("chip" + active + "/" + i, h ? 1f : 0f));
				float tx = cx + 14;
				if (picked) {
					checkMark(graphics, cx + 14, cy + ch / 2f, 1f, ExColors.onSecondaryContainer);
					tx += 18;
				}
				ExLook.text(graphics, font, picked ? ExLook.BOLD : ExLook.BODY, labels[i], tx, cy + 10, picked ? ExColors.onSecondaryContainer : ExColors.onSurfaceVariant);
				int pick = i - 1;
				screen.clickHit(cx, cy, cw, ch, () -> {
					filter[active] = pick;
					scroll[active] = 0f;
					switchedAt = System.nanoTime();
					popoverKey = null;
				});
				cx += cw + 8;
			}
		}
	}

	static void checkMark(GuiGraphicsExtractor graphics, float x, float cy, float s, int color) {
		GuiDraw.stroke(graphics, x, cy + 0.5f * s, x + 3.5f * s, cy + 4f * s, 2f * s, color);
		GuiDraw.stroke(graphics, x + 3.5f * s, cy + 4f * s, x + 10f * s, cy - 3.5f * s, 2f * s, color);
	}

	private static void grip(GuiGraphicsExtractor graphics, int mx, int my) {
		float gx = winX + winW - GRIP - 6;
		float gy = winY + winH - GRIP - 6;
		boolean hot = resizing || GuiDraw.hovered(mx, my, gx, gy, GRIP + 6, GRIP + 6);
		int color = ExColors.alpha(ExColors.onSurfaceVariant, hot ? 0.9f : 0.35f);
		GuiDraw.stroke(graphics, gx + GRIP, gy + 4, gx + 4, gy + GRIP, 2f, color);
		GuiDraw.stroke(graphics, gx + GRIP, gy + 10, gx + 10, gy + GRIP, 2f, color);
	}

	// ------------------------------------------------------------------ cards

	private record Group(String key, String title, ClickGui.Mod mod, String category, List<MenuItems.Item> loose) {
	}

	private static List<Group> groups(StrayScreen screen) {
		List<Group> out = new ArrayList<>();
		String query = ClickGui.filterText().trim().toLowerCase(Locale.ROOT);
		String[] cats = query.isEmpty() ? TABS[active].categories() : allCategories();
		for (int c = 0; c < cats.length; c++) {
			String category = cats[c];
			if (query.isEmpty() && filter[active] >= 0 && filter[active] != c) {
				continue;
			}
			List<MenuItems.Item> loose = new ArrayList<>();
			for (MenuItems.Item item : MenuItems.items(screen, category)) {
				ClickGui.Mod mod = item.mod();
				String title;
				Group group;
				if (mod != null) {
					title = ClickGui.display(mod.name());
					group = new Group("mod:" + mod.name(), title, mod, category, null);
				} else if (item.key().startsWith("mod:")) {
					String name = item.key().substring(4);
					title = item.label();
					group = new Group(item.key(), title, find(name), category, null);
				} else {
					if (query.isEmpty() || item.label().toLowerCase(Locale.ROOT).contains(query)) {
						loose.add(item);
					}
					continue;
				}
				boolean seen = false;
				for (Group g : out) {
					seen |= g.key().equals(group.key());
				}
				if (!seen && (query.isEmpty() || title.toLowerCase(Locale.ROOT).contains(query))) {
					out.add(group);
				}
			}
			if (!loose.isEmpty() || ("Mobs".equals(category) && query.isEmpty())) {
				out.add(new Group("cat:" + category, "Mobs".equals(category) ? "Mob glow" : ClickGui.display(category), null, category, loose));
			}
		}
		return out;
	}

	private static String[] allCategories() {
		List<String> all = new ArrayList<>();
		for (Tab tab : TABS) {
			all.addAll(List.of(tab.categories()));
		}
		return all.toArray(new String[0]);
	}

	private static ClickGui.Mod find(String name) {
		for (ClickGui.Mod mod : ClickGui.modules()) {
			if (mod.name().equals(name)) {
				return mod;
			}
		}
		return null;
	}

	private static boolean on(Group group) {
		ClickGui.Mod mod = group.mod();
		return mod == null || mod.hold() || mod.on().getAsBoolean();
	}

	private static void content(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mx, int my) {
		areaX = winX + 8 + RAIL_W + 16;
		areaY = winY + HEADER;
		areaW = winW - 8 - RAIL_W - 16 - 16;
		areaH = winH - HEADER - 12;
		int cols = Math.max(2, (int) Math.floor((areaW + GAP) / (CARD_MIN + GAP)));
		float colW = (areaW - GAP * (cols - 1) - 8) / cols;
		List<Group> list = groups(screen);
		List<Group> off = new ArrayList<>();
		float[] colY = new float[cols];
		float maxScroll = Math.max(0f, contentH - areaH);
		scroll[active] = Mth.clamp(scroll[active], 0f, maxScroll);
		float offset = scroll[active];
		int mark = screen.clickHitMark();
		boolean clip = GuiDraw.scissor(graphics, areaX - 4, areaY, areaW + 8, areaH);
		try {
			for (Group group : list) {
				if (group.mod() != null && !on(group)) {
					off.add(group);
					continue;
				}
				int col = shortest(colY);
				float gx = areaX + col * (colW + GAP);
				float gy = areaY + colY[col] - offset;
				float gh = Math.max(52f, heights.getOrDefault(group.key(), estimate(group)));
				if (gy < areaY + areaH && gy + gh > areaY) {
					float h = card(screen, graphics, font, mx, my, group, gx, gy, colW, gh);
					heights.put(group.key(), Math.max(52f, h));
				}
				colY[col] += gh + GAP;
			}
			if (!off.isEmpty()) {
				int col = shortest(colY);
				float gx = areaX + col * (colW + GAP);
				float gy = areaY + colY[col] - offset;
				float gh = 44f + off.size() * 36f + 8f;
				if (gy < areaY + areaH && gy + gh > areaY) {
					offCard(screen, graphics, font, mx, my, off, gx, gy, colW, gh);
				}
				colY[col] += gh + GAP;
			}
			if (list.isEmpty()) {
				ExLook.text(graphics, font, ExLook.BODY, "Nothing matches", areaX + 8, areaY + 12, ExColors.onSurfaceVariant);
			}
		} finally {
			if (clip) {
				GuiDraw.disableScissor(graphics);
			}
			screen.clickClipHits(mark, areaX - 4, areaY, areaW + 8, areaH);
		}
		float tallest = 0f;
		for (float c : colY) {
			tallest = Math.max(tallest, c);
		}
		contentH = tallest;
		ExLook.scrollbar(graphics, areaX + areaW - 2, areaY + 4, areaH - 8, areaH, contentH, offset, 1f);
	}

	private static int shortest(float[] colY) {
		int best = 0;
		for (int i = 1; i < colY.length; i++) {
			if (colY[i] < colY[best] - 0.5f) {
				best = i;
			}
		}
		return best;
	}

	private static float estimate(Group group) {
		if (group.mod() != null && group.mod().feature() != null) {
			return 56f + group.mod().feature().rows() * 34f;
		}
		return 56f;
	}

	/** One card; returns its height as drawn. */
	private static float card(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mx, int my, Group group, float x, float y, float w, float h) {
		ClickGui.Mod mod = group.mod();
		boolean hasSettings = mod == null || ClickGui.moduleHasSettings(mod) || group.loose() != null;
		float radius = hasSettings ? 24f : 26f;
		ExLook.rounded(graphics, x, y, w, h, radius, ExColors.surfaceHigh);
		float pad = 16f;
		float headH = 52f;
		String title = group.title();
		boolean switchable = mod != null && !mod.hold();
		float titleMax = w - pad * 2 - (switchable ? 48 : 0);
		ExLook.text(graphics, font, ExLook.BOLD, ExLook.fit(font, ExLook.BOLD, title, titleMax), x + pad, y + 19, ExColors.onSurface);
		if (switchable) {
			boolean value = mod.on().getAsBoolean();
			float sx = x + w - pad - 36;
			float sy = y + 15;
			boolean hover = GuiDraw.hovered(mx, my, sx - 4, sy - 4, 44, 30);
			ExLook.toggle(graphics, sx, sy, 1f, anim("sw:" + group.key(), value ? 1f : 0f), hover);
			screen.clickHit(x, y, w, headH - 4, () -> ClickGui.toggle(mod));
		}
		extent = y + headH;
		float iy = y + headH - 6;
		colours = new ArrayList<>();
		try {
			if (mod != null) {
				if (ClickGui.moduleHasSettings(mod) && on(group)) {
					int mark = screen.clickHitMark();
					ClickGui.drawModuleSettings(screen, graphics, font, mx, my, mod, x + pad, iy, w - pad * 2);
					iy = Math.max(iy, Math.max(extent, screen.clickHitsBottom(mark, iy)));
				}
				iy = sharedRows(screen, graphics, font, mod, x + pad, iy, w - pad * 2);
			} else {
				iy = loose(screen, graphics, font, mx, my, group, x + pad, iy, w - pad * 2);
			}
			iy = Math.max(iy, extent);
			if (!colours.isEmpty()) {
				iy = colourChip(screen, graphics, font, mx, my, group.key(), new ArrayList<>(colours), x + pad - 6, iy + 4, w - pad * 2 + 12);
			}
		} finally {
			colours = null;
		}
		return Math.max(headH, iy - y + 12);
	}

	private static float sharedRows(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, ClickGui.Mod mod, float x, float y, float w) {
		StrayConfig config = StrayConfig.get();
		if ("Star mob box".equals(mod.name()) && mod.on().getAsBoolean() && !config.starVisuals.glowEnabled) {
			y = screen.clickToggle(graphics, font, x, y, w, "Highlight bats", config.starMobBats, () -> config.starMobBats = !config.starMobBats);
			y = screen.clickToggle(graphics, font, x, y, w, "Highlight fels", config.starMobFels, () -> config.starMobFels = !config.starMobFels);
		}
		return y;
	}

	private static float loose(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mx, int my, Group group, float x, float y, float w) {
		if ("Mobs".equals(group.category())) {
			return mobList(screen, graphics, font, mx, my, group.loose(), x, y, w);
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
		return y;
	}

	private static float keyRow(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mx, int my, float x, float y, float w, MenuItems.Item item) {
		boolean listening = "Press a key".equals(item.chip());
		String key = listening ? "Press a key…" : UidMenu.keyText(item.chip());
		float kw = ExLook.width(font, ExLook.BODY, key) + 20;
		float kh = 26f;
		float kx = x + w - kw;
		float ky = y + 2;
		ExLook.text(graphics, font, ExLook.BODY, ExLook.fit(font, ExLook.BODY, item.label(), kx - x - 8), x, y + 9, ExColors.onSurface);
		boolean hover = GuiDraw.hovered(mx, my, kx, ky, kw, kh);
		ExLook.rounded(graphics, kx, ky, kw, kh, listening ? kh / 2f : 8f, listening ? ExColors.tertiaryContainer : ExColors.surfaceHighest);
		ExLook.state(graphics, kx, ky, kw, kh, 8f, hover ? 1f : 0f, false, ExColors.onSurface);
		ExLook.text(graphics, font, ExLook.BODY, key, kx + 10, ky + 7, listening ? ExColors.onTertiaryContainer : ExColors.onSurface);
		screen.clickHit(x, y, w, 30, item.left());
		extent(y + 30);
		return y + 34;
	}

	private static float mobListScroll;
	private static final float[] mobBox = new float[4];

	private static float mobList(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mx, int my, List<MenuItems.Item> items, float x, float y, float w) {
		float fh = 36f;
		boolean focused = ClickGui.mobSearchFocused();
		boolean hover = GuiDraw.hovered(mx, my, x, y, w, fh);
		ExLook.field(graphics, x, y, w, fh, focused, hover ? 1f : 0f);
		String query = ClickGui.mobQuery();
		String shown = query.isEmpty() && !focused ? "Search mobs" : query + (focused && (System.currentTimeMillis() / 500L) % 2L == 0L ? "|" : "");
		ExLook.text(graphics, font, ExLook.BODY, ExLook.fit(font, ExLook.BODY, shown, w - 24), x + 12, y + 11, query.isEmpty() && !focused ? ExColors.onSurfaceVariant : ExColors.onSurface);
		ClickGui.mobSearchBox(x, y, w, fh);
		screen.clickHit(x, y, w, fh, ClickGui::focusMobSearch);
		float ly = y + fh + 8;
		float rowH = 32f;
		float listH = Math.min(360f, Math.max(rowH, items.size() * rowH));
		float content = items.size() * rowH;
		mobListScroll = Mth.clamp(mobListScroll, 0f, Math.max(0f, content - listH));
		int mark = screen.clickHitMark();
		boolean clip = GuiDraw.scissor(graphics, x - 6, ly, w + 12, listH);
		try {
			for (int i = 0; i < items.size(); i++) {
				float ry = ly + i * rowH - mobListScroll;
				if (ry + rowH < ly || ry > ly + listH) {
					continue;
				}
				MenuItems.Item item = items.get(i);
				boolean on = Boolean.TRUE.equals(item.on());
				boolean h = GuiDraw.hovered(mx, my, x - 6, ry, w + 12, rowH);
				if (on) {
					ExLook.rounded(graphics, x - 6, ry + 2, w + 12, rowH - 4, (rowH - 4) / 2f, ExColors.secondaryContainer);
				}
				ExLook.state(graphics, x - 6, ry + 2, w + 12, rowH - 4, (rowH - 4) / 2f, h ? 1f : 0f, false, ExColors.onSurface);
				if (on) {
					checkMark(graphics, x + 4, ry + rowH / 2f, 1f, ExColors.onSecondaryContainer);
				}
				ExLook.text(graphics, font, on ? ExLook.BOLD : ExLook.BODY, ExLook.fit(font, ExLook.BODY, item.label(), w - 30), x + (on ? 22 : 4), ry + 10, on ? ExColors.onSecondaryContainer : ExColors.onSurface);
				screen.clickHit(x - 6, ry, w + 12, rowH, item.left());
			}
			if (items.isEmpty()) {
				ExLook.text(graphics, font, ExLook.BODY, "Nothing matches", x + 4, ly + 8, ExColors.onSurfaceVariant);
			}
		} finally {
			if (clip) {
				GuiDraw.disableScissor(graphics);
			}
			screen.clickClipHits(mark, x - 6, ly, w + 12, listH);
		}
		ExLook.scrollbar(graphics, x + w + 2, ly, listH, listH, content, mobListScroll, 1f);
		mobBox[0] = x - 6;
		mobBox[1] = ly;
		mobBox[2] = w + 12;
		mobBox[3] = listH;
		extent(ly + listH);
		return ly + listH + 4;
	}

	/** Switched-off modules, each a row with its switch. */
	private static void offCard(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mx, int my, List<Group> off, float x, float y, float w, float h) {
		ExLook.rounded(graphics, x, y, w, h, 24f, ExColors.surfaceLow);
		ExLook.text(graphics, font, ExLook.BOLD, "Off · " + off.size(), x + 16, y + 17, ExColors.onSurfaceVariant);
		float ry = y + 44;
		for (Group group : off) {
			ClickGui.Mod mod = group.mod();
			boolean hover = GuiDraw.hovered(mx, my, x + 6, ry, w - 12, 34);
			ExLook.state(graphics, x + 6, ry, w - 12, 34, 12f, anim("offh:" + group.key(), hover ? 1f : 0f), false, ExColors.onSurface);
			ExLook.text(graphics, font, ExLook.BODY, ExLook.fit(font, ExLook.BODY, group.title(), w - 80), x + 16, ry + 11, ExColors.onSurfaceVariant);
			if (mod != null && !mod.hold()) {
				ExLook.toggle(graphics, x + w - 16 - 36, ry + 6, 1f, anim("sw:" + group.key(), 0f), hover);
				screen.clickHit(x + 6, ry, w - 12, 34, () -> ClickGui.toggle(mod));
			}
			ry += 36;
		}
	}

	// ------------------------------------------------------------------ colours popover

	/** The row a card's colours fold into: stacked swatches, "Colours", how many, a chevron. */
	private static float colourChip(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mx, int my, String key, List<Colour> list, float x, float y, float w) {
		if (list.size() == 1) {
			Colour c = list.get(0);
			return colourRow(screen, graphics, font, mx, my, c, x, y, w, false, () -> {
				popoverKey = null;
				c.open().at(beside(x, w), y - 40);
			}) + 2;
		}
		float h = 40f;
		boolean open = key.equals(popoverKey);
		boolean hover = GuiDraw.hovered(mx, my, x, y, w, h);
		ExLook.rounded(graphics, x, y, w, h, h / 2f, open ? ExColors.secondaryContainer : ExColors.alpha(ExColors.secondaryContainer, 0.7f));
		ExLook.state(graphics, x, y, w, h, h / 2f, anim("cc:" + key, hover ? 1f : 0f), false, ExColors.onSecondaryContainer);
		float sx = x + 10;
		int shown = Math.min(5, list.size());
		for (int i = 0; i < shown; i++) {
			float px = sx + i * 14f;
			ExLook.rounded(graphics, px - 2, y + 7, 26, 26, 9, ExColors.secondaryContainer);
			ExLook.rounded(graphics, px, y + 9, 22, 22, 8, 0xFF000000 | list.get(i).rgb());
		}
		float tx = sx + (shown - 1) * 14f + 32;
		ExLook.text(graphics, font, ExLook.BOLD, "Colours", tx, y + 14, ExColors.onSecondaryContainer);
		String count = String.valueOf(list.size());
		float cw = ExLook.width(font, ExLook.BODY, count);
		ExLook.text(graphics, font, ExLook.BODY, count, x + w - 32 - cw, y + 14, ExColors.onSecondaryContainer);
		chevron(graphics, x + w - 20, y + h / 2f, ExColors.onSecondaryContainer);
		float ax = x;
		screen.clickHit(x, y, w, h, () -> {
			if (key.equals(popoverKey)) {
				popoverKey = null;
				return;
			}
			popoverKey = key;
			popupScroll = 0f;
			popoverAnchorX = ax;
			popoverAnchorY = y;
			popoverAnchorW = w;
		});
		if (open) {
			popover.clear();
			popover.addAll(list);
			popoverAnchorX = ax;
			popoverAnchorY = y;
			popoverAnchorW = w;
		}
		extent(y + h);
		return y + h + 6;
	}

	private static void chevron(GuiGraphicsExtractor graphics, float x, float cy, int color) {
		GuiDraw.stroke(graphics, x - 3, cy - 5, x + 2, cy, 2f, color);
		GuiDraw.stroke(graphics, x + 2, cy, x - 3, cy + 5, 2f, color);
	}

	/** One colour: its swatch, name and hex; clicking opens the picker. */
	static float colourRow(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mx, int my, Colour c, float x, float y, float w, boolean picked, Runnable click) {
		float h = 34f;
		boolean hover = GuiDraw.hovered(mx, my, x, y, w, h);
		if (picked) {
			ExLook.rounded(graphics, x, y, w, h, 12f, ExColors.surfaceHighest);
		}
		ExLook.state(graphics, x, y, w, h, 12f, hover ? 1f : 0f, false, ExColors.onSurface);
		ExLook.swatch(graphics, x + 6, y + 6, 22, 0xFF000000 | c.rgb(), picked);
		String hex = String.format(Locale.ROOT, "%06X", c.rgb() & 0xFFFFFF);
		float hw = ExLook.width(font, ExLook.SMALL, hex);
		ExLook.text(graphics, font, ExLook.BODY, ExLook.fit(font, ExLook.BODY, c.label(), w - 50 - hw), x + 38, y + 11, ExColors.onSurface);
		ExLook.text(graphics, font, ExLook.SMALL, hex, x + w - 8 - hw, y + 12, ExColors.onSurfaceVariant);
		screen.clickHit(x, y, w, h, click);
		extent(y + h);
		return y + h + 2;
	}

	private static void drawPopover(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mx, int my) {
		if (popover.isEmpty()) {
			popoverKey = null;
			return;
		}
		float pw = 280f;
		float rowsH = popover.size() * 36f;
		float ph = Math.min(winH - 40f, 60f + rowsH);
		float px = popoverAnchorX + popoverAnchorW + 14f;
		float sw = Minecraft.getInstance().getWindow().getGuiScaledWidth() / unit;
		if (px + pw > sw - 8) {
			px = popoverAnchorX - pw - 14f;
		}
		float py = Mth.clamp(popoverAnchorY - 20f, 8f, Math.max(8f, winY + winH - ph - 8));
		popoverBox[0] = px;
		popoverBox[1] = py;
		popoverBox[2] = pw;
		popoverBox[3] = ph;
		// Anywhere outside closes it; its own rows take their clicks first.
		screen.clickHit(-100000, -100000, 200000, 200000, () -> popoverKey = null);
		screen.clickHit(px, py, pw, ph, () -> { });
		ExLook.surface(graphics, px, py, pw, ph, 28f, ExColors.surfaceHighest, false);
		ExLook.text(graphics, font, ExLook.TITLE, "Colours", px + 20, py + 18, ExColors.onSurface);
		float cx = px + pw - 44;
		float cy = py + 12;
		boolean ch = GuiDraw.hovered(mx, my, cx, cy, 32, 32);
		ExLook.rounded(graphics, cx, cy, 32, 32, 16, ch ? ExColors.surfaceHigh : ExColors.surfaceMid);
		GuiDraw.stroke(graphics, cx + 11, cy + 11, cx + 21, cy + 21, 2f, ExColors.onSurface);
		GuiDraw.stroke(graphics, cx + 21, cy + 11, cx + 11, cy + 21, 2f, ExColors.onSurface);
		screen.clickHit(cx, cy, 32, 32, () -> popoverKey = null);
		float listY = py + 54;
		float listH = ph - 62;
		float maxScroll = Math.max(0f, rowsH - listH);
		popupScroll = Mth.clamp(popupScroll, 0f, maxScroll);
		int mark = screen.clickHitMark();
		boolean clip = GuiDraw.scissor(graphics, px + 8, listY, pw - 16, listH);
		try {
			float ry = listY - popupScroll;
			for (Colour c : popover) {
				float rowY = ry;
				ry = colourRow(screen, graphics, font, mx, my, c, px + 12, rowY, pw - 24, false, () -> c.open().at(beside(popoverBox[0], popoverBox[2]), rowY - 40));
			}
		} finally {
			if (clip) {
				GuiDraw.disableScissor(graphics);
			}
			screen.clickClipHits(mark, px + 8, listY, pw - 16, listH);
		}
		ExLook.scrollbar(graphics, px + pw - 10, listY, listH, listH, rowsH, popupScroll, 1f);
	}

	/** Where a picker goes next to a box: on its right when it fits, else on its left. */
	static float beside(float x, float w) {
		float sw = Minecraft.getInstance().getWindow().getGuiScaledWidth() / unit;
		float pw = 280f;
		return x + w + 10 + pw <= sw - 4 ? x + w + 10 : x - pw - 10;
	}

	/** The theme page's seed presets: a row of swatches; the picked one ringed. */
	static float seedRow(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, float x, float y, float w) {
		int mx = (int) Math.round(ClickGui.pointerX());
		int my = (int) Math.round(ClickGui.pointerY());
		ExLook.text(graphics, font, ExLook.BODY, "Seed", x, y + 2, ExColors.onSurface);
		float sy = y + 22;
		float size = 26f;
		float sx = x + 2;
		int seed = StrayConfig.get().exSeed & 0xFFFFFF;
		for (int preset : ExColors.PRESET_SEEDS) {
			if (sx + size > x + w) {
				break;
			}
			boolean picked = seed == preset;
			boolean hover = GuiDraw.hovered(mx, my, sx - 3, sy - 3, size + 6, size + 6);
			ExLook.swatch(graphics, sx, sy, hover && !picked ? size + 1 : size, 0xFF000000 | preset, picked);
			screen.clickHit(sx - 3, sy - 3, size + 6, size + 6, () -> {
				StrayConfig.get().exSeed = preset;
				ExColors.invalidate();
				Theme.refresh();
				UnloadState.markDirty();
			});
			sx += size + 12;
		}
		extent(sy + size + 4);
		return sy + size + 12;
	}

	/** Colour rows draw inline while {@code on} (rows that are the point of their page). */
	static void inline(boolean on) {
		inline = on;
	}

	// ------------------------------------------------------------------ input

	static boolean scroll(double x, double y, double wheel) {
		if (wheel == 0) {
			return false;
		}
		if (popoverKey != null && contains(x, y, popoverBox[0], popoverBox[1], popoverBox[2], popoverBox[3])) {
			popupScroll = Math.max(0f, popupScroll - (float) wheel * 36f);
			return true;
		}
		if (popupOpen && contains(x, y, popupBox[0], popupBox[1], popupBox[2], popupBox[3])) {
			popupScroll = Math.max(0f, popupScroll - (float) wheel * 32f);
			return true;
		}
		if (contains(x, y, mobBox[0], mobBox[1], mobBox[2], mobBox[3])) {
			mobListScroll = Math.max(0f, mobListScroll - (float) wheel * 40f);
			return true;
		}
		if (contains(x, y, areaX, areaY, areaW, areaH)) {
			scroll[active] = Math.max(0f, scroll[active] - (float) wheel * 48f);
			return true;
		}
		return false;
	}

	static boolean press(double x, double y) {
		if (contains(x, y, winX + winW - GRIP - 6, winY + winH - GRIP - 6, GRIP + 6, GRIP + 6)) {
			resizing = true;
			dragDX = (float) x - (winX + winW);
			dragDY = (float) y - (winY + winH);
			return true;
		}
		if (contains(x, y, winX, winY, winW, HEADER) || contains(x, y, winX, winY, RAIL_W + 16, winH)) {
			dragging = true;
			dragDX = (float) x - winX;
			dragDY = (float) y - winY;
			return true;
		}
		return false;
	}

	static boolean drag(double x, double y) {
		if (resizing) {
			winW = Math.max(MIN_W, (float) x - dragDX - winX);
			winH = Math.max(MIN_H, (float) y - dragDY - winY);
			return true;
		}
		if (!dragging) {
			return false;
		}
		winX = (float) x - dragDX;
		winY = (float) y - dragDY;
		return true;
	}

	static void release() {
		dragging = false;
		if (resizing) {
			resizing = false;
			StrayConfig.get().exWidth = Math.round(winW);
			StrayConfig.get().exHeight = Math.round(winH);
			UnloadState.markDirty();
		}
	}

	static void resetPosition() {
		winX = Float.NaN;
		StrayConfig.get().exWidth = 0;
		StrayConfig.get().exHeight = 0;
		UnloadState.markDirty();
	}

	private static boolean contains(double x, double y, float rx, float ry, float rw, float rh) {
		return x >= rx && x <= rx + rw && y >= ry && y <= ry + rh;
	}
}
