package dev.stray.client.ui;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.config.UnloadState;
import dev.stray.client.render.GuiDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Panels click GUI. A bar along the top holds search, the HUD editor and the theme; under it
 * every category is a panel, all side by side and always shown. Clicking a module turns it on or
 * off (on is a white row with an accent bar); right-clicking opens its settings in a window you
 * can drag. Searching dims what doesn't match rather than hiding it, so nothing moves.
 */
public final class PanelMenu {
	private static final float MARGIN = 12f;
	private static final float PANEL_W = 126f;
	private static final float GAP = 6f;
	private static final float BAR_Y = 10f;
	private static final float BAR_H = 20f;
	private static final float TOP = 40f;
	private static final float HEAD_H = 20f;
	private static final float ROW_H = 15f;
	private static final float SUB_H = 12f;
	private static final float WIN_W = 250f;
	private static final float WIN_HEAD = 30f;

	/** A panel: its title and the click GUI categories it holds, shown as headed groups. */
	private record Panel(String title, String[] categories) {
	}

	private static final Panel[] PANELS = {
		new Panel("Dungeons", new String[] {"Dungeons", "M4"}),
		new Panel("Slayer", new String[] {"Slayer"}),
		new Panel("Skills", new String[] {"Mining", "Farming", "Foraging"}),
		new Panel("World", new String[] {"World", "Entities", "Mobs"}),
		new Panel("HUD", new String[] {"HUD"}),
		new Panel("Chat & menus", new String[] {"Chat", "Menus"}),
		new Panel("Player & client", new String[] {"Player", "Keys", "Client"}),
	};

	/** What a row does: a module, a loose item (a key bind, an action) or the mob list. */
	private record Row(String label, ClickGui.Mod mod, MenuItems.Item item, String category) {
	}

	/** Where a row was drawn this frame, for right clicks. */
	private record Spot(float x, float y, float w, float h, Row row) {
	}

	private static final String MOBS = "Mob glow";

	private static boolean drawing;
	private static float unit = 1f;
	private static final float[] scroll = new float[PANELS.length];
	private static final float[][] panelBox = new float[PANELS.length][4];
	private static final float[] panelContent = new float[PANELS.length];
	private static final List<Spot> spots = new ArrayList<>();

	/** The open settings window: a module, or the mob list. */
	private static ClickGui.Mod window;
	private static boolean mobWindow;
	private static float winX = Float.NaN;
	private static float winY;
	private static float winH;
	private static float winScroll;
	private static float winContent;
	private static float bodyY;
	private static float bodyH;
	private static boolean dragging;
	private static float dragDX;
	private static float dragDY;

	private static String hoverKey;
	private static long hoverSince;
	private static float fade = 1f;
	private static long lastFrame;
	private static Object fadeScreen;

	private PanelMenu() {
	}

	public static boolean active() {
		return Looks.panels();
	}

	public static boolean drawing() {
		return drawing;
	}

	/** Menu units per GUI unit: 1 when everything fits, smaller on narrow screens. */
	static float unit() {
		Minecraft client = Minecraft.getInstance();
		float w = client == null || client.getWindow() == null ? 960f : client.getWindow().getGuiScaledWidth();
		float need = MARGIN * 2f + PANELS.length * PANEL_W + (PANELS.length - 1) * GAP;
		return Math.min(1f, w / need);
	}

	static boolean faded(Object screen) {
		return screen != fadeScreen || fade <= 0.001f;
	}

	static boolean windowOpen() {
		return window != null || mobWindow;
	}

	static void closeWindow() {
		window = null;
		mobWindow = false;
		ClickGui.blurMobSearch();
	}

	/** Opens a module's settings window by name (the Theme button, "Accent colour"). */
	static void open(String name) {
		for (ClickGui.Mod mod : ClickGui.modules()) {
			if (mod.name().equals(name)) {
				openMod(mod);
				return;
			}
		}
	}

	private static void openMod(ClickGui.Mod mod) {
		if (window != mod) {
			winScroll = 0f;
		}
		window = mod;
		mobWindow = false;
		winX = Float.NaN;
	}

	// ------------------------------------------------------------------ drawing

	static void draw(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		unit = unit();
		float sw = screen.width / unit;
		float sh = screen.height / unit;
		int mx = Math.round(mouseX / unit);
		int my = Math.round(mouseY / unit);
		ClickGui.pointer(mx, my);
		long now = System.nanoTime();
		if (screen != fadeScreen) {
			fadeScreen = screen;
			fade = 0f;
			lastFrame = now;
		}
		float dt = Math.min(1f / 30f, (now - lastFrame) / 1.0e9f);
		lastFrame = now;
		float target = screen.clickClosing() ? 0f : 1f;
		// A short fade both ways (Material's "effects" speed: about 150 ms).
		fade = target > fade ? Math.min(target, fade + dt / 0.15f) : Math.max(target, fade - dt / 0.12f);
		drawing = true;
		spots.clear();
		graphics.pose().pushMatrix();
		graphics.pose().scale(unit, unit);
		try {
			GuiDraw.alphaScale = fade;
			GuiDraw.fill(graphics, 0, 0, sw, sh, 0x8C000000);
			float total = PANELS.length * PANEL_W + (PANELS.length - 1) * GAP;
			float left = Math.round((sw - total) / 2f);
			bar(screen, graphics, font, mx, my, left, total);
			String query = ClickGui.filterText().trim().toLowerCase(Locale.ROOT);
			Row hovered = null;
			float hx = 0f;
			float hy = 0f;
			for (int i = 0; i < PANELS.length; i++) {
				Row h = panel(screen, graphics, font, mx, my, i, left + i * (PANEL_W + GAP), TOP, sh - TOP - MARGIN, query);
				if (h != null) {
					hovered = h;
					hx = mx;
					hy = my;
				}
			}
			if (windowOpen()) {
				window(screen, graphics, font, mx, my, sw, sh);
			}
			if (hovered != null && !windowOpen()) {
				tooltip(graphics, font, hovered, hx, hy, sw, sh);
			} else {
				hoverKey = null;
			}
			screen.clickPicker(graphics, font);
		} finally {
			GuiDraw.alphaScale = 1f;
			graphics.pose().popMatrix();
			drawing = false;
		}
	}

	/** The top bar: the name in the accent, search across the middle, Edit HUD and Theme. */
	private static void bar(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mx, int my, float left, float total) {
		int accent = PanelLook.accent();
		// The name on its own chip, so nothing behind the menu shows through beside it.
		float brandW = GuiDraw.titleWidth(font, "stray") + 18f;
		PanelLook.panel(graphics, left, BAR_Y, brandW, BAR_H, 0xFF1C1C1C);
		GuiDraw.title(graphics, font, "stray", left + 9f, GuiDraw.middle(BAR_Y, BAR_H), accent);
		float bx = left + brandW + 6f;
		String hud = "Edit HUD";
		String theme = "Theme";
		float hudW = font.width(MenuFont.shadow(hud)) + 18f;
		float themeW = font.width(MenuFont.shadow(theme)) + 18f;
		float right = left + total;
		float searchW = right - bx - hudW - themeW - 12f;
		boolean focused = ClickGui.searchFocused();
		PanelLook.panel(graphics, bx, BAR_Y, searchW, BAR_H, 0xFF1C1C1C);
		if (focused) {
			GuiDraw.hairline(graphics, bx, BAR_Y, searchW, BAR_H, 4f, accent);
		}
		String query = ClickGui.filterText();
		boolean empty = query.isEmpty() && !focused;
		String shown = empty ? "Search modules, or just start typing" : query + (focused && (System.currentTimeMillis() / 500L) % 2L == 0L ? "|" : "");
		GuiDraw.text(graphics, font, MenuFont.shadow(fit(font, shown, searchW - 20f)), bx + 8f, GuiDraw.middle(BAR_Y, BAR_H), empty ? PanelLook.DGREY : PanelLook.WHITE, false);
		ClickGui.searchBox(bx, BAR_Y, searchW, BAR_H);
		screen.clickHit(bx, BAR_Y, searchW, BAR_H, ClickGui::focusSearch);
		if (!query.isEmpty()) {
			float cx = bx + searchW - 14f;
			GuiDraw.text(graphics, font, MenuFont.shadow("x"), cx + 2f, GuiDraw.middle(BAR_Y, BAR_H), GuiDraw.hovered(mx, my, cx, BAR_Y, 14f, BAR_H) ? PanelLook.WHITE : PanelLook.GREY, false);
			screen.clickHit(cx, BAR_Y, 14f, BAR_H, ClickGui::clearSearch);
		}
		float x = bx + searchW + 6f;
		button(screen, graphics, font, mx, my, x, hud, hudW, () -> Minecraft.getInstance().gui.setScreen(new HudEditorScreen()));
		button(screen, graphics, font, mx, my, x + hudW + 6f, theme, themeW, () -> open(ClickGui.HUD_LOOK));
	}

	private static void button(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mx, int my, float x, String label, float w, Runnable click) {
		boolean hover = GuiDraw.hovered(mx, my, x, BAR_Y, w, BAR_H);
		PanelLook.panel(graphics, x, BAR_Y, w, BAR_H, hover ? 0xFF262626 : 0xFF1C1C1C);
		Component text = MenuFont.shadow(label);
		GuiDraw.text(graphics, font, text, x + Math.round((w - font.width(text)) / 2f), GuiDraw.middle(BAR_Y, BAR_H), PanelLook.WHITE, false);
		screen.clickHit(x, BAR_Y, w, BAR_H, click);
	}

	private static List<List<Row>> rows(StrayScreen screen, Panel panel) {
		List<List<Row>> out = new ArrayList<>();
		for (String category : panel.categories()) {
			List<Row> group = new ArrayList<>();
			if ("Mobs".equals(category)) {
				group.add(new Row(MOBS, null, null, category));
			} else {
				for (MenuItems.Item item : MenuItems.items(screen, category, "")) {
					ClickGui.Mod mod = item.mod();
					if (mod == null && item.key().startsWith("mod:")) {
						mod = find(item.key().substring(4));
					}
					group.add(new Row(item.label(), mod, mod == null ? item : null, category));
				}
			}
			out.add(group);
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

	private static boolean isOn(Row row) {
		if (row.mod() != null) {
			return !row.mod().hold() && row.mod().on().getAsBoolean();
		}
		if (row.item() != null) {
			return Boolean.TRUE.equals(row.item().on());
		}
		return false;
	}

	/** One panel; returns the row under the pointer, if any. */
	private static Row panel(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mx, int my, int index, float x, float y, float maxH, String query) {
		Panel panel = PANELS[index];
		List<List<Row>> groups = rows(screen, panel);
		int on = 0;
		int count = 0;
		int found = 0;
		float content = 0f;
		boolean headed = groups.size() > 1;
		for (List<Row> group : groups) {
			content += headed ? SUB_H : 0f;
			for (Row row : group) {
				count++;
				on += isOn(row) ? 1 : 0;
				found += !query.isEmpty() && matches(row, query) ? 1 : 0;
				content += ROW_H + 1f;
			}
		}
		content += 8f;
		float bodyMax = maxH - HEAD_H;
		float bodyHeight = Math.min(content, bodyMax);
		float h = HEAD_H + bodyHeight;
		PanelLook.panel(graphics, x, y, PANEL_W, h, 0xF21C1C1C);
		// Header: the name, and how many are on (or found while searching).
		GuiDraw.text(graphics, font, MenuFont.shadow(panel.title()), x + 8f, GuiDraw.middle(y, HEAD_H), PanelLook.WHITE, false);
		Component meta;
		int metaColor;
		if (!query.isEmpty()) {
			meta = MenuFont.shadowSmall(found > 0 ? found + " found" : "none");
			metaColor = found > 0 ? PanelLook.accent() : PanelLook.DGREY;
		} else {
			meta = MenuFont.shadowSmall(on + "/" + count);
			metaColor = on > 0 ? PanelLook.accent() : PanelLook.DGREY;
		}
		GuiDraw.text(graphics, font, meta, x + PANEL_W - 8f - font.width(meta), GuiDraw.middle(y, HEAD_H) + 0.5f, metaColor, false);
		GuiDraw.fillSmooth(graphics, x + 1f, y + HEAD_H - 1f, PANEL_W - 2f, 1f, PanelLook.EDGE);
		panelBox[index][0] = x;
		panelBox[index][1] = y + HEAD_H;
		panelBox[index][2] = PANEL_W;
		panelBox[index][3] = bodyHeight;
		panelContent[index] = content;
		scroll[index] = Mth.clamp(scroll[index], 0f, Math.max(0f, content - bodyHeight));
		float ry = y + HEAD_H + 3f - scroll[index];
		Row hovered = null;
		int mark = screen.clickHitMark();
		boolean clip = GuiDraw.scissor(graphics, x, y + HEAD_H, PANEL_W, bodyHeight);
		try {
			for (int g = 0; g < groups.size(); g++) {
				if (headed) {
					GuiDraw.text(graphics, font, MenuFont.shadowSmall(ClickGui.display(panel.categories()[g])), x + 8f, ry + 3f, PanelLook.DGREY, false);
					ry += SUB_H;
				}
				for (Row row : groups.get(g)) {
					if (ry + ROW_H > y + HEAD_H && ry < y + HEAD_H + bodyHeight) {
						boolean hover = GuiDraw.hovered(mx, my, x + 3f, ry, PANEL_W - 6f, ROW_H) && GuiDraw.hovered(mx, my, x, y + HEAD_H, PANEL_W, bodyHeight);
						row(screen, graphics, font, row, x + 3f, ry, PANEL_W - 6f, hover, query);
						if (hover) {
							hovered = row;
						}
					}
					ry += ROW_H + 1f;
				}
			}
		} finally {
			if (clip) {
				GuiDraw.disableScissor(graphics);
			}
			screen.clickClipHits(mark, x, y + HEAD_H, PANEL_W, bodyHeight);
		}
		if (content > bodyHeight + 1f) {
			float track = bodyHeight - 6f;
			float thumb = Math.max(12f, track * bodyHeight / content);
			float ty = y + HEAD_H + 3f + (track - thumb) * (scroll[index] / Math.max(1f, content - bodyHeight));
			GuiDraw.fillSmooth(graphics, x + PANEL_W - 3f, ty, 1.5f, thumb, PanelLook.DGREY);
		}
		return hovered;
	}

	private static boolean matches(Row row, String query) {
		String label = ClickGui.display(row.label()).toLowerCase(Locale.ROOT);
		if (label.contains(query)) {
			return true;
		}
		String desc = row.mod() == null ? "" : ClickGui.description(row.mod()).toLowerCase(Locale.ROOT);
		return !desc.isEmpty() && desc.contains(query);
	}

	private static void row(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, Row row, float x, float y, float w, boolean hover, String query) {
		boolean on = isOn(row);
		boolean dim = !query.isEmpty() && !matches(row, query);
		float was = GuiDraw.alphaScale;
		if (dim) {
			GuiDraw.alphaScale = was * 0.45f;
		}
		try {
			if (on) {
				GuiDraw.roundedFine(graphics, x, y, w, ROW_H, 3f, PanelLook.G3);
				PanelLook.onBar(graphics, x, y, ROW_H);
			} else if (hover) {
				GuiDraw.roundedFine(graphics, x, y, w, ROW_H, 3f, PanelLook.G2);
			}
			String chip = row.item() == null ? null : row.item().chip();
			float chipW = 0f;
			if (chip != null && !chip.isEmpty()) {
				Component c = MenuFont.shadowSmall("[" + UidMenu.keyText(chip).toLowerCase(Locale.ROOT) + "]");
				chipW = font.width(c) + 6f;
				GuiDraw.text(graphics, font, c, x + w - chipW + 2f, GuiDraw.middle(y, ROW_H) + 0.5f, PanelLook.GREY, false);
			}
			Component label = MenuFont.shadow(fit(font, ClickGui.display(row.label()), w - 12f - chipW));
			GuiDraw.text(graphics, font, label, x + 6f, GuiDraw.middle(y, ROW_H), on ? PanelLook.WHITE : hover ? 0xFFD4D4D4 : PanelLook.GREY, false);
			if (!query.isEmpty() && !dim) {
				GuiDraw.fillSmooth(graphics, x + 6f, y + ROW_H - 2.5f, Math.min(font.width(label), w - 12f), 1f, PanelLook.accent());
			}
		} finally {
			GuiDraw.alphaScale = was;
		}
		spots.add(new Spot(x, y, w, ROW_H, row));
		screen.clickHit(x, y, w, ROW_H, () -> leftClick(row));
	}

	private static void leftClick(Row row) {
		if (MOBS.equals(row.label()) && row.mod() == null && row.item() == null) {
			openMobs();
			return;
		}
		ClickGui.Mod mod = row.mod();
		if (mod != null) {
			if (mod.hold()) {
				openMod(mod);
			} else {
				ClickGui.toggle(mod);
			}
			return;
		}
		if (row.item() != null && row.item().left() != null) {
			row.item().left().run();
		}
	}

	private static void openMobs() {
		window = null;
		mobWindow = true;
		winScroll = 0f;
		winX = Float.NaN;
	}

	/** The module's line of description, and how to reach its settings. */
	private static void tooltip(GuiGraphicsExtractor graphics, Font font, Row row, float mx, float my, float sw, float sh) {
		String key = row.category() + "/" + row.label();
		if (!key.equals(hoverKey)) {
			hoverKey = key;
			hoverSince = System.currentTimeMillis();
		}
		if (System.currentTimeMillis() - hoverSince < 450L) {
			return;
		}
		String desc = row.mod() == null ? "" : ClickGui.description(row.mod());
		boolean settings = row.mod() != null && (ClickGui.moduleHasSettings(row.mod()) || row.mod().hold());
		String hint = row.mod() == null ? (MOBS.equals(row.label()) ? "Click to pick mobs to glow" : null)
			: row.mod().hold() ? "Click to open" : settings ? "Click to turn on or off, right-click for settings" : "Click to turn on or off";
		List<String> lines = desc.isEmpty() ? new ArrayList<>() : wrap(font, desc, 170f);
		float w = 0f;
		for (String l : lines) {
			w = Math.max(w, font.width(MenuFont.shadowSmall(l)));
		}
		Component title = MenuFont.shadow(ClickGui.display(row.label()));
		w = Math.max(w, font.width(title));
		if (hint != null) {
			w = Math.max(w, font.width(MenuFont.shadowSmall(hint)));
		}
		float h = 8f + 10f + lines.size() * 9f + (hint != null ? 11f : 0f) + 4f;
		float tw = w + 16f;
		float tx = mx + 10f + tw > sw - 4f ? mx - tw - 6f : mx + 10f;
		float ty = Math.min(my + 10f, sh - h - 4f);
		PanelLook.panel(graphics, tx, ty, tw, h, 0xFA121212);
		float y = ty + 6f;
		GuiDraw.text(graphics, font, title, tx + 8f, y, PanelLook.WHITE, false);
		y += 11f;
		for (String l : lines) {
			GuiDraw.text(graphics, font, MenuFont.shadowSmall(l), tx + 8f, y, PanelLook.GREY, false);
			y += 9f;
		}
		if (hint != null) {
			GuiDraw.text(graphics, font, MenuFont.shadowSmall(hint), tx + 8f, y + 2f, PanelLook.accent(), false);
		}
	}

	private static List<String> wrap(Font font, String text, float max) {
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String next = line.length() == 0 ? word : line + " " + word;
			if (line.length() > 0 && font.width(MenuFont.shadowSmall(next)) > max) {
				out.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(next);
			}
		}
		if (line.length() > 0) {
			out.add(line.toString());
		}
		return out;
	}

	private static String fit(Font font, String text, float max) {
		if (font.width(MenuFont.shadow(text)) <= max) {
			return text;
		}
		String s = text;
		while (s.length() > 1 && font.width(MenuFont.shadow(s + "...")) > max) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "...";
	}

	// ------------------------------------------------------------------ the settings window

	private static boolean insideWindow(double x, double y) {
		return windowOpen() && !Float.isNaN(winX) && x >= winX && x <= winX + WIN_W && y >= winY && y <= winY + winH;
	}

	private static void window(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mx, int my, float sw, float sh) {
		ClickGui.Mod mod = window;
		String title = mobWindow ? MOBS : ClickGui.display(mod.name());
		String where = mobWindow ? "World" : ClickGui.display(mod.column());
		float maxBody = Math.max(80f, sh * 0.72f - WIN_HEAD);
		float wantBody = Math.max(40f, winContent);
		bodyH = Math.min(wantBody, maxBody);
		winH = WIN_HEAD + bodyH + 8f;
		if (Float.isNaN(winX)) {
			winX = Math.round((sw - WIN_W) / 2f);
			winY = Math.round(Math.max(TOP, (sh - winH) / 2f));
		}
		winX = Mth.clamp(winX, 0f, Math.max(0f, sw - WIN_W));
		winY = Mth.clamp(winY, 0f, Math.max(0f, sh - winH));
		// The window takes clicks over what is under it; on its title bar a press starts a drag.
		screen.clickHit(winX, winY, WIN_W, winH, () -> press(ClickGui.pointerX(), ClickGui.pointerY()));
		PanelLook.panel(graphics, winX, winY, WIN_W, winH, 0xFC1C1C1C);
		GuiDraw.fillSmooth(graphics, winX + 3f, winY, WIN_W - 6f, 1.5f, PanelLook.accent());
		GuiDraw.text(graphics, font, MenuFont.shadowSmall(where), winX + 10f, winY + 6f, PanelLook.GREY, false);
		GuiDraw.text(graphics, font, MenuFont.shadow(fit(font, title, WIN_W - 80f)), winX + 10f, winY + 16f, PanelLook.WHITE, false);
		float cx = winX + WIN_W - 18f;
		boolean closeHover = GuiDraw.hovered(mx, my, cx - 3f, winY + 6f, 16f, 18f);
		GuiDraw.text(graphics, font, MenuFont.shadow("x"), cx + 2f, winY + 10f, closeHover ? PanelLook.WHITE : PanelLook.GREY, false);
		screen.clickHit(cx - 3f, winY + 6f, 16f, 18f, PanelMenu::closeWindow);
		if (mod != null && !mod.hold()) {
			float tx = cx - 30f;
			boolean on = mod.on().getAsBoolean();
			PanelLook.toggle(graphics, tx, winY + 10f, 22f, 11f, on ? 1f : 0f);
			screen.clickHit(tx - 2f, winY + 7f, 26f, 17f, () -> ClickGui.toggle(mod));
		}
		GuiDraw.fillSmooth(graphics, winX + 1f, winY + WIN_HEAD - 1f, WIN_W - 2f, 1f, PanelLook.EDGE);
		bodyY = winY + WIN_HEAD;
		float bx = winX + 10f;
		float bw = WIN_W - 20f;
		winScroll = Mth.clamp(winScroll, 0f, Math.max(0f, winContent - bodyH));
		float y = bodyY + 6f - winScroll;
		float start = y;
		int mark = screen.clickHitMark();
		boolean clip = GuiDraw.scissor(graphics, winX + 1f, bodyY, WIN_W - 2f, bodyH + 6f);
		try {
			if (mobWindow) {
				y = mobs(screen, graphics, font, mx, my, bx, y, bw);
			} else {
				String desc = ClickGui.description(mod);
				if (!desc.isEmpty()) {
					for (String line : wrap(font, desc, bw)) {
						GuiDraw.text(graphics, font, MenuFont.shadowSmall(line), bx, y, PanelLook.GREY, false);
						y += 9f;
					}
					y += 6f;
				}
				boolean on = mod.hold() || mod.on().getAsBoolean();
				if (!on) {
					GuiDraw.text(graphics, font, MenuFont.shadowSmall("Switch it on to change its settings."), bx, y, PanelLook.DGREY, false);
					y += 14f;
				} else if (ClickGui.moduleHasSettings(mod)) {
					int rows = screen.clickHitMark();
					ClickGui.drawModuleSettings(screen, graphics, font, mx, my, mod, bx, y, bw);
					y = Math.max(y + ClickGui.moduleSettingsHeight(screen, mod), screen.clickHitsBottom(rows, y));
					y = sharedRows(screen, graphics, font, mod, bx, y, bw);
				}
			}
		} finally {
			if (clip) {
				GuiDraw.disableScissor(graphics);
			}
			screen.clickClipHits(mark, winX, bodyY, WIN_W, bodyH + 6f);
		}
		winContent = y - start + 4f;
		if (winContent > bodyH + 1f) {
			float track = bodyH - 4f;
			float thumb = Math.max(14f, track * bodyH / winContent);
			float ty = bodyY + 4f + (track - thumb) * (winScroll / Math.max(1f, winContent - bodyH));
			GuiDraw.fillSmooth(graphics, winX + WIN_W - 4f, ty, 1.5f, thumb, PanelLook.DGREY);
		}
	}

	/** Settings two modules share: which star mobs count lives under Star mob box too. */
	private static float sharedRows(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, ClickGui.Mod mod, float x, float y, float w) {
		StrayConfig config = StrayConfig.get();
		if ("Star mob box".equals(mod.name()) && !config.starVisuals.glowEnabled) {
			y = screen.clickToggle(graphics, font, x, y, w, "Highlight bats", config.starMobBats, () -> config.starMobBats = !config.starMobBats);
			y = screen.clickToggle(graphics, font, x, y, w, "Highlight fels", config.starMobFels, () -> config.starMobFels = !config.starMobFels);
		}
		return y;
	}

	/** Mob glow: a search box and every mob as a row; picked ones are on. */
	private static float mobs(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mx, int my, float x, float y, float w) {
		boolean focused = ClickGui.mobSearchFocused();
		String query = ClickGui.mobQuery();
		PanelLook.control(graphics, x, y, w, 16f, false, focused);
		String shown = query.isEmpty() && !focused ? "Search mobs" : query + (focused && (System.currentTimeMillis() / 500L) % 2L == 0L ? "|" : "");
		GuiDraw.text(graphics, font, MenuFont.shadow(fit(font, shown, w - 12f)), x + 6f, GuiDraw.middle(y, 16f), query.isEmpty() && !focused ? PanelLook.DGREY : PanelLook.WHITE, false);
		ClickGui.mobSearchBox(x, y, w, 16f);
		screen.clickHit(x, y, w, 16f, ClickGui::focusMobSearch);
		y += 21f;
		List<MenuItems.Item> items = MenuItems.items(screen, "Mobs", "");
		if (items.isEmpty()) {
			GuiDraw.text(graphics, font, MenuFont.shadowSmall("Nothing matches"), x, y + 2f, PanelLook.DGREY, false);
			return y + 14f;
		}
		for (MenuItems.Item item : items) {
			if (y + ROW_H > bodyY - 20f && y < bodyY + bodyH + 20f) {
				boolean on = Boolean.TRUE.equals(item.on());
				boolean hover = GuiDraw.hovered(mx, my, x, y, w, ROW_H);
				if (on) {
					GuiDraw.roundedFine(graphics, x, y, w, ROW_H, 3f, PanelLook.G3);
					PanelLook.onBar(graphics, x, y, ROW_H);
				} else if (hover) {
					GuiDraw.roundedFine(graphics, x, y, w, ROW_H, 3f, PanelLook.G2);
				}
				GuiDraw.text(graphics, font, MenuFont.shadow(fit(font, item.label(), w - 12f)), x + 6f, GuiDraw.middle(y, ROW_H), on ? PanelLook.WHITE : PanelLook.GREY, false);
				screen.clickHit(x, y, w, ROW_H, item.left());
			}
			y += ROW_H + 1f;
		}
		return y;
	}

	// ------------------------------------------------------------------ input

	static boolean scroll(double x, double y, double wheel) {
		if (wheel == 0) {
			return false;
		}
		if (insideWindow(x, y)) {
			winScroll = Math.max(0f, winScroll - (float) wheel * 30f);
			return true;
		}
		for (int i = 0; i < PANELS.length; i++) {
			float[] b = panelBox[i];
			if (x >= b[0] && x <= b[0] + b[2] && y >= b[1] - HEAD_H && y <= b[1] + b[3]) {
				scroll[i] = Math.max(0f, scroll[i] - (float) wheel * 30f);
				return true;
			}
		}
		return false;
	}

	/** Right-click on a module opens its settings (again closes them). */
	static void rightClick(double x, double y) {
		if (insideWindow(x, y)) {
			return;
		}
		for (Spot spot : spots) {
			if (x >= spot.x() && x <= spot.x() + spot.w() && y >= spot.y() && y <= spot.y() + spot.h()) {
				Row row = spot.row();
				if (MOBS.equals(row.label()) && row.mod() == null && row.item() == null) {
					if (mobWindow) {
						closeWindow();
					} else {
						openMobs();
					}
					return;
				}
				ClickGui.Mod mod = row.mod();
				if (mod != null && (ClickGui.moduleHasSettings(mod) || mod.hold())) {
					if (window == mod) {
						closeWindow();
					} else {
						openMod(mod);
					}
				}
				return;
			}
		}
	}

	/** A press nothing else took: the window's title bar starts a drag; outside it, the window closes. */
	static boolean press(double x, double y) {
		if (windowOpen() && x >= winX && x <= winX + WIN_W && y >= winY && y <= winY + WIN_HEAD) {
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

	static void markDirty() {
		UnloadState.markDirty();
	}
}
