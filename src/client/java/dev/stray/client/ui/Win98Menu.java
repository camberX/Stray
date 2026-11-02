package dev.stray.client.ui;

import dev.stray.client.config.UnloadState;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.render.GuiDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The click GUI as Windows 98: a taskbar along the bottom (Start menu, one task button per
 * category, the clock in the tray) and an Explorer-style window with a menu bar, an address bar
 * for search, a white list of modules with checkboxes and a Properties pane for the selected one.
 * The window moves by its title bar, resizes by the grip and maximizes like the real thing.
 */
public final class Win98Menu {
	private static final float TASK_H = 20;
	/** Taskbar height this frame: grows by a row when the category buttons need one. */
	private static float taskH = TASK_H;
	private static final float ROW = 11;
	private static final float MENU_H = 12;
	private static final float BAR_H = 16;
	private static final float STATUS_H = 13;
	private static final float SCROLL_W = 10;
	private static final float MIN_W = 300;
	private static final float MIN_H = 180;

	private static boolean drawing;
	private static String category = ClickGui.ORDER[0];
	private static String selected;
	private static float listScroll;
	private static float setScroll;
	private static float winX = -1;
	private static float winY;
	private static float winW;
	private static float winH;
	private static boolean maximized;
	private static boolean moving;
	private static boolean resizing;
	private static float grabX;
	private static float grabY;
	private static boolean startOpen;
	private static String menuOpen;
	/** Marks a checked menu item; stripped before drawing. */
	private static final String CHECKED = "\u0001";
	private static boolean addressOpen;
	private static float addrX;
	private static float addrY;
	private static float addrW;
	private static long lastClickAt;
	private static String lastClickKey;

	private static final List<RowHit> rowHits = new ArrayList<>();

	// Last layout, for scrolling.
	private static float listX;
	private static float listY;
	private static float listW;
	private static float listH;
	private static float listContent;
	private static float setX;
	private static float setY;
	private static float setW;
	private static float setH;
	private static float setContent;

	private Win98Menu() {
	}

	/** True while this menu draws, so shared widgets switch to the Windows 98 look. */
	public static boolean drawing() {
		return drawing;
	}

	static void draw(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		Win98.sync();
		drawing = true;
		try {
			drawFrame(screen, graphics, font, mouseX, mouseY);
		} finally {
			drawing = false;
		}
	}

	static void drawPicker(StrayScreen screen, GuiGraphicsExtractor graphics, Font font) {
		drawing = true;
		int[] saved = swapTheme();
		try {
			screen.clickPicker(graphics, font);
		} finally {
			restoreTheme(saved);
			drawing = false;
		}
	}

	private static void drawFrame(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		float sw = screen.width / viewScale();
		float sh = screen.height / viewScale();
		GuiDraw.fill(graphics, 0, 0, sw, sh, 0x38000000);
		taskH = TASK_H + (taskRows(font, sw) - 1) * (TASK_H - 4);
		layout(sw, sh);
		float x = maximized ? 0 : winX;
		float y = maximized ? 0 : winY;
		float w = maximized ? sw : winW;
		float h = maximized ? sh - taskH : winH;
		drawWindow(screen, graphics, font, mouseX, mouseY, x, y, w, h);
		drawTaskbar(screen, graphics, font, mouseX, mouseY, sw, sh);
		if (startOpen) {
			drawStartMenu(screen, graphics, font, mouseX, mouseY, sh);
		}
	}

	private static void layout(float sw, float sh) {
		float maxW = Math.max(MIN_W, sw - 8);
		float maxH = Math.max(MIN_H, sh - taskH - 8);
		if (winX < 0) {
			winW = Math.min(maxW, Math.max(MIN_W, sw * 0.72f));
			winH = Math.min(maxH, Math.max(MIN_H, (sh - taskH) * 0.78f));
			winX = (sw - winW) / 2f;
			winY = (sh - taskH - winH) / 2f;
		}
		winW = Mth.clamp(winW, MIN_W, maxW);
		winH = Mth.clamp(winH, MIN_H, maxH);
		winX = Mth.clamp(winX, 40 - winW, sw - 40);
		winY = Mth.clamp(winY, 0, sh - taskH - Win98.TITLE_H - 6);
	}

	// ------------------------------------------------------------------ window

	private static void drawWindow(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float x, float y, float w, float h) {
		Win98.window(graphics, font, x, y, w, h, "Stray - " + HyprMenu.label(searching() ? "Search" : category), true);
		float tx = x + 3;
		float ty = y + 3;
		float tw = w - 6;
		// Title bar: drag to move, double click to maximize.
		screen.clickHit(tx, ty, tw - 3 * 16, Win98.TITLE_H, () -> {
			long now = System.currentTimeMillis();
			if ("title".equals(lastClickKey) && now - lastClickAt < 350) {
				maximized = !maximized;
				lastClickKey = null;
				return;
			}
			lastClickKey = "title";
			lastClickAt = now;
			if (!maximized) {
				moving = true;
				grabX = (float) ClickGui.pointerX() - winX;
				grabY = (float) ClickGui.pointerY() - winY;
			}
		});
		float bw = 14;
		float bh = Win98.TITLE_H - 3;
		float by = ty + 1.5f;
		float closeX = tx + tw - bw - 2;
		float maxX = closeX - bw - 2;
		float minX = maxX - bw;
		Win98.captionButton(graphics, minX, by, bw, bh, '_', false);
		Win98.captionButton(graphics, maxX, by, bw, bh, maximized ? 'r' : 'o', false);
		Win98.captionButton(graphics, closeX, by, bw, bh, 'x', false);
		screen.clickHit(minX, by, bw, bh, screen::onClose);
		screen.clickHit(maxX, by, bw, bh, () -> maximized = !maximized);
		screen.clickHit(closeX, by, bw, bh, screen::onClose);

		// Menu bar.
		float my = ty + Win98.TITLE_H + 1;
		float mx = x + 4;
		for (String entry : new String[] {"File", "Edit", "View", "Help"}) {
			float ew = Win98.width(font, entry) + 10;
			boolean open = entry.equals(menuOpen);
			if (open) {
				GuiDraw.fill(graphics, mx, my, ew, MENU_H, Win98.NAVY);
			} else if (hot(mouseX, mouseY, mx, my, ew, MENU_H)) {
				Win98.shallow(graphics, mx, my, ew, MENU_H, Win98.FACE);
			}
			Win98.text(graphics, font, entry, mx + 5, my + 2, open ? Win98.HILITE_TEXT : Win98.TEXT);
			GuiDraw.fill(graphics, mx + 5, my + 10, Win98.width(font, entry.substring(0, 1)) - 1, 1, open ? Win98.HILITE_TEXT : Win98.TEXT);
			screen.clickHit(mx, my, ew, MENU_H, () -> menuOpen = entry.equals(menuOpen) ? null : entry);
			mx += ew;
		}
		// Etched line under the menu bar.
		GuiDraw.fill(graphics, x + 3, my + MENU_H, w - 6, 1, Win98.SHADOW);
		GuiDraw.fill(graphics, x + 3, my + MENU_H + 1, w - 6, 1, Win98.WHITE);

		// Address bar: the search field.
		float ay = my + MENU_H + 3;
		Win98.text(graphics, font, "Address", x + 6, ay + 4, Win98.TEXT);
		float fx = x + 8 + Win98.width(font, "Address") + 4;
		float fw = x + w - 6 - fx;
		boolean focused = ClickGui.searchFocused();
		Win98.sunken(graphics, fx, ay, fw, BAR_H, Win98.WINDOW);
		String query = ClickGui.filterText();
		boolean empty = query.isEmpty() && !focused;
		float iconX = fx + 4;
		folder(graphics, iconX, ay + 4);
		String shown = empty ? "C:\\Stray\\" + HyprMenu.label(category) : query + (focused && (System.currentTimeMillis() / 500L) % 2L == 0L ? "|" : "");
		float arrowW = BAR_H - 4;
		Win98.text(graphics, font, Win98.fit(font, shown, (int) (fw - arrowW - 26)), fx + 16, ay + 4, Win98.TEXT);
		ClickGui.searchBox(fx, ay, fw - arrowW - 2, BAR_H);
		screen.clickHit(fx, ay, fw - arrowW - 2, BAR_H, () -> {
			addressOpen = false;
			ClickGui.focusSearch();
		});
		float abx = fx + fw - arrowW - 2;
		if (addressOpen) {
			Win98.pressed(graphics, abx, ay + 2, arrowW, BAR_H - 4);
		} else {
			Win98.raised(graphics, abx, ay + 2, arrowW, BAR_H - 4);
		}
		Win98.arrowDown(graphics, abx + arrowW / 2f + (addressOpen ? 1 : 0), ay + BAR_H / 2f + (addressOpen ? 1 : 0), Win98.TEXT);
		screen.clickHit(abx, ay, arrowW + 2, BAR_H, () -> addressOpen = !addressOpen);
		addrX = fx;
		addrY = ay + BAR_H;
		addrW = fw;

		// Body: list on the left, properties on the right, status bar under both.
		float bodyY = ay + BAR_H + 3;
		float statusY = y + h - 3 - STATUS_H;
		float bodyH = statusY - 2 - bodyY;
		float split = Math.round((w - 12) * 0.42f);
		List<HyprMenu.Item> items = HyprMenu.items(screen, category);
		keepSelection(items);
		drawList(screen, graphics, font, mouseX, mouseY, items, x + 4, bodyY, split, bodyH);
		drawProperties(screen, graphics, font, mouseX, mouseY, x + 4 + split + 4, bodyY, w - 12 - split, bodyH);

		// Status bar.
		int on = 0;
		int toggles = 0;
		for (HyprMenu.Item item : items) {
			if (item.on() != null) {
				toggles++;
				if (item.on()) {
					on++;
				}
			}
		}
		float half = Math.round((w - 8) * 0.42f);
		Win98.shallow(graphics, x + 3, statusY, half, STATUS_H, Win98.FACE);
		Win98.text(graphics, font, items.size() + " object(s)", x + 7, statusY + 3, Win98.TEXT);
		Win98.shallow(graphics, x + 5 + half, statusY, w - 8 - half - 2, STATUS_H, Win98.FACE);
		String right = toggles > 0 ? on + " of " + toggles + " enabled" : "";
		Win98.text(graphics, font, right, x + 9 + half, statusY + 3, Win98.TEXT);
		if (!maximized) {
			Win98.sizeGrip(graphics, x + w - 3, y + h - 3);
			screen.clickHit(x + w - 12, y + h - 12, 12, 12, () -> {
				resizing = true;
				grabX = (float) (winX + winW - ClickGui.pointerX());
				grabY = (float) (winY + winH - ClickGui.pointerY());
			});
		}
		if (menuOpen != null) {
			drawMenuDropdown(screen, graphics, font, mouseX, mouseY, x + 4, my + MENU_H);
		}
		if (addressOpen) {
			drawAddressList(screen, graphics, font, mouseX, mouseY);
		}
	}

	/** The address bar's dropdown: every category as a folder, like Explorer's drive list. */
	private static void drawAddressList(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		String[] ids = ClickGui.ORDER;
		// Shrinks the rows rather than running under the taskbar.
		float room = screen.height / viewScale() - taskH - 2 - addrY;
		float itemH = Math.max(9, Math.min(ROW + 1, (room - 2) / (ids.length + 1)));
		float h = 2 + (ids.length + 1) * itemH;
		GuiDraw.fill(graphics, addrX, addrY, addrW, h, Win98.DARK);
		GuiDraw.fill(graphics, addrX + 1, addrY, addrW - 2, h - 1, Win98.WINDOW);
		screen.clickHit(addrX, addrY, addrW, h, () -> {
		});
		float iy = addrY + 1;
		// The root.
		computer(graphics, addrX + 4, iy + 2);
		Win98.text(graphics, font, "Stray (C:)", addrX + 17, iy + 2, Win98.TEXT);
		iy += itemH;
		for (String id : ids) {
			boolean hot = hot(mouseX, mouseY, addrX + 1, iy, addrW - 2, itemH);
			if (hot) {
				GuiDraw.fill(graphics, addrX + 1, iy, addrW - 2, itemH, Win98.NAVY);
			}
			folder(graphics, addrX + 12, iy + 2);
			Win98.text(graphics, font, HyprMenu.label(id), addrX + 25, iy + 2, hot ? Win98.HILITE_TEXT : Win98.TEXT);
			screen.clickHit(addrX + 1, iy, addrW - 2, itemH, () -> {
				addressOpen = false;
				pickCategory(id);
			});
			iy += itemH;
		}
	}

	/** Yellow folder icon, 10x8. */
	private static void folder(GuiGraphicsExtractor graphics, float x, float y) {
		GuiDraw.fill(graphics, x, y, 4, 1, 0xFF808000);
		GuiDraw.fill(graphics, x, y + 1, 10, 7, 0xFF808000);
		GuiDraw.fill(graphics, x + 1, y + 2, 8, 5, 0xFFFFFF80);
		GuiDraw.fill(graphics, x + 1, y + 2, 8, 1, 0xFFFFFFFF);
	}

	/** "My Computer" icon, 10x8. */
	private static void computer(GuiGraphicsExtractor graphics, float x, float y) {
		GuiDraw.fill(graphics, x, y, 10, 6, 0xFF404040);
		GuiDraw.fill(graphics, x + 1, y + 1, 8, 4, 0xFF008080);
		GuiDraw.fill(graphics, x + 2, y + 6, 6, 2, 0xFFC0C0C0);
	}

	/** The menu bar's dropdowns, with a few real actions. */
	private static void drawMenuDropdown(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float baseX, float y) {
		List<String> labels = new ArrayList<>();
		List<Runnable> actions = new ArrayList<>();
		float x = baseX;
		for (String entry : new String[] {"File", "Edit", "View", "Help"}) {
			if (entry.equals(menuOpen)) {
				break;
			}
			x += Win98.width(font, entry) + 10;
		}
		switch (menuOpen) {
			case "File" -> {
				labels.add("HUD Editor...");
				actions.add(() -> Minecraft.getInstance().gui.setScreen(new HudEditorScreen()));
				labels.add("-");
				actions.add(null);
				labels.add("Close");
				actions.add(screen::onClose);
			}
			case "Edit" -> {
				labels.add("Find...");
				actions.add(ClickGui::focusSearch);
				labels.add("Command keys...");
				actions.add(() -> Minecraft.getInstance().gui.setScreen(new CommandKeysScreen(screen)));
			}
			case "View" -> {
				labels.add(maximized ? "Restore" : "Maximize");
				actions.add(() -> maximized = !maximized);
				labels.add("-");
				actions.add(null);
				labels.add((StrayConfig.get().win98Dark ? CHECKED : "") + "Dark mode");
				actions.add(Win98Menu::toggleDark);
			}
			default -> {
				labels.add("About Stray");
				actions.add(() -> menuOpen = null);
			}
		}
		float w = 20;
		for (String label : labels) {
			w = Math.max(w, Win98.width(font, label) + 26);
		}
		float h = 4;
		for (String label : labels) {
			h += "-".equals(label) ? 6 : ROW + 1;
		}
		Win98.raised(graphics, x, y, w, h);
		float iy = y + 2;
		// Swallow clicks on the menu's own frame.
		screen.clickHit(x, y, w, h, () -> {
		});
		for (int i = 0; i < labels.size(); i++) {
			String label = labels.get(i);
			if ("-".equals(label)) {
				GuiDraw.fill(graphics, x + 3, iy + 2, w - 6, 1, Win98.SHADOW);
				GuiDraw.fill(graphics, x + 3, iy + 3, w - 6, 1, Win98.WHITE);
				iy += 6;
				continue;
			}
			boolean hot = hot(mouseX, mouseY, x + 2, iy, w - 4, ROW + 1);
			if (hot) {
				GuiDraw.fill(graphics, x + 2, iy, w - 4, ROW + 1, Win98.NAVY);
			}
			int ink = hot ? Win98.HILITE_TEXT : Win98.TEXT;
			if (label.startsWith(CHECKED)) {
				label = label.substring(CHECKED.length());
				// The menu check mark in the left column, like a checked menu item.
				int[][] tick = {{0, 2}, {1, 3}, {2, 4}, {3, 3}, {4, 2}, {5, 1}, {6, 0}};
				for (int[] p : tick) {
					GuiDraw.fill(graphics, x + 4 + p[0], iy + 3 + p[1], 1, 3, ink);
				}
			}
			Win98.text(graphics, font, label, x + 14, iy + 2, ink);
			Runnable action = actions.get(i);
			screen.clickHit(x + 2, iy, w - 4, ROW + 1, () -> {
				menuOpen = null;
				if (action != null) {
					action.run();
				}
			});
			iy += ROW + 1;
		}
	}

	private static void drawList(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, List<HyprMenu.Item> items, float x, float y, float w, float h) {
		boolean mobs = "Mobs".equals(category) && !searching();
		if (mobs) {
			Win98.sunken(graphics, x, y, w, BAR_H, Win98.WINDOW);
			boolean focused = ClickGui.mobSearchFocused();
			String q = ClickGui.mobQuery();
			String shown = q.isEmpty() && !focused ? "Find mobs..." : q + (focused && (System.currentTimeMillis() / 500L) % 2L == 0L ? "|" : "");
			Win98.text(graphics, font, Win98.fit(font, shown, (int) w - 8), x + 4, y + 4, q.isEmpty() && !focused ? Win98.SHADOW : Win98.TEXT);
			ClickGui.mobSearchBox(x, y, w, BAR_H);
			screen.clickHit(x, y, w, BAR_H, ClickGui::focusMobSearch);
			y += BAR_H + 2;
			h -= BAR_H + 2;
		} else {
			ClickGui.mobSearchBox(0, 0, 0, 0);
		}
		Win98.sunken(graphics, x, y, w, h, Win98.WINDOW);
		listX = x;
		listY = y;
		listW = w;
		listH = h;
		float innerX = x + 2;
		float innerY = y + 2;
		float innerW = w - 4 - SCROLL_W;
		float innerH = h - 4;
		listContent = items.size() * ROW;
		listScroll = Mth.clamp(listScroll, 0, Math.max(0, listContent - innerH));
		rowHits.clear();
		boolean clip = GuiDraw.scissor(graphics, innerX, innerY, innerW, innerH);
		int mark = screen.clickHitMark();
		for (int i = 0; i < items.size(); i++) {
			HyprMenu.Item item = items.get(i);
			float ry = innerY + i * ROW - listScroll;
			if (ry + ROW < innerY || ry > innerY + innerH) {
				continue;
			}
			boolean chosen = item.mod() != null && item.mod().name().equals(selected);
			float textX = innerX + 3;
			if (item.on() != null) {
				Win98.checkbox(graphics, innerX + 2, ry + 1, 9, item.on(), true);
				textX = innerX + 14;
				Runnable toggle = item.left();
				if (toggle != null) {
					screen.clickHit(innerX, ry, 13, ROW, toggle);
				}
			}
			String chip = item.chip();
			float chipW = chip == null || chip.isEmpty() ? 0 : Win98.width(font, chip) + 6;
			String label = Win98.fit(font, item.label(), (int) (innerX + innerW - chipW - textX - 2));
			float lw = Win98.width(font, label) + 3;
			if (chosen) {
				GuiDraw.fill(graphics, textX - 2, ry, lw + 1, ROW, Win98.NAVY);
				// Dotted focus rectangle.
				for (float d = 0; d < lw + 1; d += 2) {
					GuiDraw.fill(graphics, textX - 2 + d, ry, 1, 1, 0xFFFFFF80);
					GuiDraw.fill(graphics, textX - 2 + d, ry + ROW - 1, 1, 1, 0xFFFFFF80);
				}
			}
			Win98.text(graphics, font, label, textX, ry + 2, chosen ? Win98.HILITE_TEXT : Win98.TEXT);
			if (chipW > 0) {
				Win98.text(graphics, font, chip, innerX + innerW - chipW + 3, ry + 2, Win98.SHADOW);
			}
			rowHits.add(new RowHit(item, innerX, ry, innerW, ROW));
			float labelX = item.on() != null ? innerX + 13 : innerX;
			screen.clickHit(labelX, ry, innerX + innerW - labelX, ROW, () -> clickRow(item));
		}
		screen.clickClipHits(mark, innerX, innerY, innerW, innerH);
		if (clip) {
			GuiDraw.disableScissor(graphics);
		}
		if (items.isEmpty()) {
			Win98.text(graphics, font, searching() ? "No items match your search." : "This folder is empty.", innerX + 4, innerY + 4, Win98.SHADOW);
		}
		Win98.scrollbar(graphics, x + w - 2 - SCROLL_W, y + 2, SCROLL_W, h - 4, listContent, innerH, listScroll);
		screen.clickHit(x + w - 2 - SCROLL_W, y + 2, SCROLL_W, SCROLL_W, () -> listScroll = Math.max(0, listScroll - ROW * 3));
		screen.clickHit(x + w - 2 - SCROLL_W, y + h - 2 - SCROLL_W, SCROLL_W, SCROLL_W, () -> listScroll += ROW * 3);
	}

	/** Click a row: modules with settings open in Properties; the rest do their thing; double click toggles. */
	private static void clickRow(HyprMenu.Item item) {
		long now = System.currentTimeMillis();
		boolean doubled = item.key().equals(lastClickKey) && now - lastClickAt < 350;
		lastClickKey = item.key();
		lastClickAt = now;
		if (item.mod() != null) {
			select(item.mod().name());
			if (doubled && item.left() != null) {
				item.left().run();
			}
			return;
		}
		if (item.left() != null) {
			item.left().run();
		}
	}

	private static void drawProperties(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float x, float y, float w, float h) {
		ClickGui.Mod mod = find(selected);
		String caption = mod == null ? "Properties" : ClickGui.display(mod.name()) + " Properties";
		Win98.groupBox(graphics, font, x, y + 4, w, h - 4, caption);
		setX = x;
		setY = y;
		setW = w;
		setH = h;
		float ix = x + 8;
		float iy = y + 12;
		float iw = w - 16 - SCROLL_W;
		float ih = h - 18;
		if (mod == null) {
			Win98.text(graphics, font, "Select an item to view its properties.", ix, iy + 2, Win98.TEXT);
			return;
		}
		if (!ClickGui.moduleHasSettings(mod)) {
			Win98.text(graphics, font, "There are no settings for this item.", ix, iy + 2, Win98.TEXT);
			describe(graphics, font, ClickGui.description(mod), ix, iy + 18, iw, y + h - 6);
			return;
		}
		setContent = ClickGui.moduleSettingsHeight(screen, mod);
		setScroll = Mth.clamp(setScroll, 0, Math.max(0, setContent - ih));
		boolean clip = GuiDraw.scissor(graphics, x + 2, iy, w - 4 - SCROLL_W, ih);
		int mark = screen.clickHitMark();
		int[] saved = swapTheme();
		try {
			ClickGui.drawModuleSettings(screen, graphics, font, mouseX, mouseY, mod, ix - 3, iy - setScroll, iw + 6 - (setContent > ih ? SCROLL_W + 4 : 0));
		} finally {
			restoreTheme(saved);
		}
		screen.clickClipHits(mark, x + 2, iy, w - 4 - SCROLL_W, ih);
		if (clip) {
			GuiDraw.disableScissor(graphics);
		}
		if (setContent > ih) {
			Win98.scrollbar(graphics, x + w - 4 - SCROLL_W, iy, SCROLL_W, ih, setContent, ih, setScroll);
		}
		float below = iy - setScroll + setContent + 8;
		if (below + 12 < y + h - 6) {
			describe(graphics, font, ClickGui.description(mod), ix, below, iw, y + h - 6);
		}
	}

	private static void describe(GuiGraphicsExtractor graphics, Font font, String text, float x, float y, float w, float bottom) {
		if (text == null || text.isEmpty()) {
			return;
		}
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String next = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && Win98.width(font, next) > w) {
				if (y + 10 > bottom) {
					return;
				}
				Win98.text(graphics, font, line.toString(), x, y, Win98.MUTED);
				y += 10;
				line.setLength(0);
				line.append(word);
			} else {
				line.setLength(0);
				line.append(next);
			}
		}
		if (!line.isEmpty() && y + 10 <= bottom) {
			Win98.text(graphics, font, line.toString(), x, y, Win98.MUTED);
		}
	}

	// ------------------------------------------------------------------ taskbar

	private static void drawTaskbar(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float sw, float sh) {
		float y = sh - taskH;
		GuiDraw.fill(graphics, 0, y, sw, taskH, Win98.FACE);
		GuiDraw.fill(graphics, 0, y, sw, 1, Win98.LIGHT);
		GuiDraw.fill(graphics, 0, y + 1, sw, 1, Win98.WHITE);
		// Swallow clicks on the bar itself.
		screen.clickHit(0, y, sw, taskH, () -> {
		});

		// Start button.
		float bx = 2;
		float by = y + 3;
		float bh = TASK_H - 5;
		float startW = Win98.boldWidth(font, "Start") + 21;
		if (startOpen) {
			Win98.pressed(graphics, bx, by, startW, bh);
		} else {
			Win98.raised(graphics, bx, by, startW, bh);
		}
		float o = startOpen ? 1 : 0;
		logo(graphics, bx + 4 + o, by + 3 + o);
		Win98.bold(graphics, font, "Start", bx + 17 + o, by + 4 + o, Win98.TEXT);
		screen.clickHit(bx, by, startW, bh, () -> startOpen = !startOpen);

		// Tray with the clock.
		String clock = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
		float trayW = Win98.width(font, clock) + 24;
		float trayX = sw - trayW - 2;
		Win98.shallow(graphics, trayX, by, trayW, bh, Win98.FACE);
		// A little speaker in the tray.
		GuiDraw.fill(graphics, trayX + 4, by + 5, 2, 4, Win98.TEXT);
		GuiDraw.fill(graphics, trayX + 6, by + 4, 1, 6, Win98.TEXT);
		GuiDraw.fill(graphics, trayX + 7, by + 3, 1, 8, Win98.TEXT);
		Win98.text(graphics, font, clock, trayX + trayW - Win98.width(font, clock) - 5, by + 4, Win98.TEXT);

		// Quick launch separator, then the task button for the open window.
		float x = bx + startW + 4;
		GuiDraw.fill(graphics, x, by, 1, bh, Win98.SHADOW);
		GuiDraw.fill(graphics, x + 1, by, 1, bh, Win98.WHITE);
		x += 5;
		// One task button per category, sized to its label. Like a resized Windows taskbar,
		// the buttons wrap onto more rows when they would otherwise be cut short.
		String[] ids = ClickGui.ORDER;
		float room = trayX - 4 - x;
		int rows = taskRows(font, sw);
		int perRow = (ids.length + rows - 1) / rows;
		String active = searching() ? null : category;
		for (int r = 0; r < rows; r++) {
			int from = r * perRow;
			int to = Math.min(ids.length, from + perRow);
			drawTaskRow(screen, graphics, font, ids, from, to, active, x, by + r * (bh + 1), room, bh);
		}
	}

	private static float tabsRoom(Font font, float sw) {
		float startW = Win98.boldWidth(font, "Start") + 21;
		String clock = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
		float trayW = Win98.width(font, clock) + 24;
		return (sw - trayW - 2 - 4) - (2 + startW + 4 + 5);
	}

	private static float tabsNatural(Font font, String[] ids, int from, int to) {
		return tabsNatural(font, ids, from, to, 21);
	}

	private static float tabsNatural(Font font, String[] ids, int from, int to, float pad) {
		float natural = 0;
		for (int i = from; i < to; i++) {
			natural += Win98.width(font, HyprMenu.label(ids[i])) + pad + 2;
		}
		return natural;
	}

	private static int taskRows(Font font, float sw) {
		String[] ids = ClickGui.ORDER;
		float room = tabsRoom(font, sw);
		for (int rows = 1; rows < 4; rows++) {
			int per = (ids.length + rows - 1) / rows;
			// With folder icons, or failing that without them, before adding another row.
			for (float pad : new float[] {21, 8}) {
				boolean fits = true;
				for (int from = 0; from < ids.length; from += per) {
					if (tabsNatural(font, ids, from, Math.min(ids.length, from + per), pad) > room) {
						fits = false;
						break;
					}
				}
				if (fits) {
					return rows;
				}
			}
		}
		return 4;
	}

	private static void drawTaskRow(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, String[] ids, int from, int to, String active, float x, float by, float room, float bh) {
		float gap = 2;
		int n = to - from;
		float[] textW = new float[n];
		for (int i = 0; i < n; i++) {
			textW[i] = Win98.width(font, HyprMenu.label(ids[from + i]));
		}
		boolean icons = tabsNatural(font, ids, from, to) <= room;
		float pad = icons ? 21 : 8;
		float total = 0;
		for (float t : textW) {
			total += t + pad + gap;
		}
		float scale = total <= room ? 1 : Math.max(0.2f, (room - n * (pad + gap)) / (total - n * (pad + gap)));
		for (int i = 0; i < n; i++) {
			String id = ids[from + i];
			float bw = Math.min(140, textW[i] * scale + pad);
			boolean on = id.equals(active);
			if (on) {
				// The active task: pressed, with the dithered lighter face.
				Win98.pressed(graphics, x, by, bw, bh);
				for (float py = by + 2; py < by + bh - 2; py += 2) {
					for (float px = x + 2 + ((py - by) % 4 == 0 ? 0 : 1); px < x + bw - 2; px += 2) {
						GuiDraw.fill(graphics, px, py, 1, 1, Win98.WHITE);
					}
				}
			} else {
				Win98.raised(graphics, x, by, bw, bh);
			}
			float d = on ? 1 : 0;
			float tx = x + 4 + d;
			if (icons) {
				folder(graphics, tx, by + 4 + d);
				tx += 13;
			}
			String label = Win98.fit(font, HyprMenu.label(id), (int) (x + bw - 3 - tx));
			Win98.text(graphics, font, label, tx, by + 5 + d, Win98.TEXT);
			screen.clickHit(x, by, bw, bh, () -> pickCategory(id));
			x += bw + gap;
		}
	}

	/** Stray's mark for the Start button: four little colored panes. */
	private static void logo(GuiGraphicsExtractor graphics, float x, float y) {
		GuiDraw.fill(graphics, x, y, 4, 4, 0xFFE0402A);
		GuiDraw.fill(graphics, x + 5, y, 4, 4, 0xFF34A853);
		GuiDraw.fill(graphics, x, y + 5, 4, 4, 0xFF2D7CF6);
		GuiDraw.fill(graphics, x + 5, y + 5, 4, 4, 0xFFF9BB2D);
	}

	private static void drawStartMenu(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float sh) {
		String[] labels = {"Stray Settings", "HUD Editor", "Command keys", "World editor", "Freecam", "-", "Shut Down..."};
		float w = 120;
		float h = 6;
		for (String label : labels) {
			h += "-".equals(label) ? 6 : 17;
		}
		float x = 2;
		float y = sh - taskH - h + 1;
		Win98.raised(graphics, x, y, w, h);
		screen.clickHit(x, y, w, h, () -> {
		});
		// The side banner.
		float bannerW = 18;
		GuiDraw.fillGradient(graphics, x + 3, y + 3, bannerW, h - 6, Win98.NAVY_LIGHT, Win98.NAVY);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + 7, y + h - 6);
		graphics.pose().rotate((float) (-Math.PI / 2));
		Win98.bold(graphics, font, "Stray", 0, 0, Win98.HILITE_TEXT);
		Win98.text(graphics, font, "98", Win98.boldWidth(font, "Stray") + 2, 0, Win98.FACE);
		graphics.pose().popMatrix();
		float iy = y + 3;
		Minecraft client = Minecraft.getInstance();
		for (String label : labels) {
			if ("-".equals(label)) {
				GuiDraw.fill(graphics, x + bannerW + 5, iy + 2, w - bannerW - 9, 1, Win98.SHADOW);
				GuiDraw.fill(graphics, x + bannerW + 5, iy + 3, w - bannerW - 9, 1, Win98.WHITE);
				iy += 6;
				continue;
			}
			float ix = x + bannerW + 3;
			float iw = w - bannerW - 6;
			boolean hot = hot(mouseX, mouseY, ix, iy, iw, 17);
			if (hot) {
				GuiDraw.fill(graphics, ix, iy, iw, 17, Win98.NAVY);
			}
			Win98.text(graphics, font, label, ix + 6, iy + 5, hot ? Win98.HILITE_TEXT : Win98.TEXT);
			Runnable action = switch (label) {
				case "HUD Editor" -> () -> client.gui.setScreen(new HudEditorScreen());
				case "Command keys" -> () -> client.gui.setScreen(new CommandKeysScreen(screen));
				case "World editor" -> () -> {
					client.gui.setScreen(null);
					dev.stray.client.editor.WorldEditor.enable();
				};
				case "Freecam" -> () -> {
					client.gui.setScreen(null);
					dev.stray.client.movement.Freecam.toggle();
				};
				case "Shut Down..." -> screen::onClose;
				default -> () -> pickCategory("Theme");
			};
			screen.clickHit(ix, iy, iw, 17, () -> {
				startOpen = false;
				action.run();
			});
			iy += 17;
		}
	}

	// ------------------------------------------------------------------ state

	private static boolean searching() {
		return !ClickGui.filterText().isBlank();
	}

	private static void pickCategory(String id) {
		startOpen = false;
		menuOpen = null;
		addressOpen = false;
		if (id.equals(category)) {
			return;
		}
		if ("Mobs".equals(category)) {
			ClickGui.blurMobSearch();
		}
		category = id;
		listScroll = 0;
	}

	private static void select(String name) {
		if (name == null || name.equals(selected)) {
			return;
		}
		selected = name;
		setScroll = 0;
	}

	private static void keepSelection(List<HyprMenu.Item> items) {
		for (HyprMenu.Item item : items) {
			if (item.mod() != null && item.mod().name().equals(selected)) {
				return;
			}
		}
		for (HyprMenu.Item item : items) {
			if (item.mod() != null) {
				select(item.mod().name());
				return;
			}
		}
	}

	private static ClickGui.Mod find(String name) {
		if (name == null) {
			return null;
		}
		for (ClickGui.Mod mod : ClickGui.modules()) {
			if (mod.name().equals(name)) {
				return mod;
			}
		}
		return null;
	}

	static boolean drag(double x, double y) {
		if (moving) {
			winX = (float) x - grabX;
			winY = (float) y - grabY;
			return true;
		}
		if (resizing) {
			winW = Math.max(MIN_W, (float) x + grabX - winX);
			winH = Math.max(MIN_H, (float) y + grabY - winY);
			return true;
		}
		return false;
	}

	static void endDrag() {
		moving = false;
		resizing = false;
	}

	static boolean scroll(double x, double y, double wheel) {
		if (wheel == 0) {
			return false;
		}
		float step = (float) wheel * ROW * 3;
		if (hot(x, y, listX, listY, listW, listH)) {
			listScroll = Math.max(0, listScroll - step);
			return true;
		}
		if (hot(x, y, setX, setY, setW, setH)) {
			setScroll = Math.max(0, setScroll - step);
			return true;
		}
		return false;
	}

	static void rightClick(double x, double y) {
		startOpen = false;
		menuOpen = null;
		for (RowHit hit : rowHits) {
			if (hit.item.mod() != null && hot(x, y, hit.x, hit.y, hit.w, hit.h)) {
				select(hit.item.mod().name());
				return;
			}
		}
	}

	static void closePopups() {
		startOpen = false;
		menuOpen = null;
		addressOpen = false;
	}

	/**
	 * How much smaller than the GUI scale the window is drawn: about 75% of it times the menu
	 * scale setting, snapped so each GUI pixel still covers whole screen pixels and the bitmap
	 * font stays sharp.
	 */
	static float viewScale() {
		Minecraft client = Minecraft.getInstance();
		double gui = client.getWindow() == null ? 2.0 : client.getWindow().getGuiScale();
		float target = 0.75f * StrayConfig.normalizeMenuScale(StrayConfig.get().menuScale);
		long px = Math.max(1, Math.round(gui * target));
		return (float) Math.min(1.0, px / gui);
	}

	static void toggleDark() {
		StrayConfig config = StrayConfig.get();
		config.win98Dark = !config.win98Dark;
		UnloadState.markDirty();
		Win98.sync();
	}

	private static boolean hot(double mx, double my, float x, float y, float w, float h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	/** The settings widgets read {@link Theme}; give them Windows colors while they draw. */
	private static int[] swapTheme() {
		int[] old = {Theme.ACCENT, Theme.ACCENT_DIM, Theme.CARD, Theme.CARD_HOVER, Theme.LINE, Theme.TRACK, Theme.PANEL, Theme.TEXT, Theme.MUTED, Theme.OFF, Theme.WINDOW, Theme.WINDOW_SOLID, Theme.HEADER, Theme.SHEET};
		Theme.ACCENT = Win98.ACCENT;
		Theme.ACCENT_DIM = Win98.SHADOW;
		Theme.CARD = Win98.FACE;
		Theme.CARD_HOVER = Win98.LIGHT;
		Theme.LINE = Win98.SHADOW;
		Theme.TRACK = Win98.WINDOW;
		Theme.PANEL = Win98.FACE;
		Theme.TEXT = Win98.TEXT;
		Theme.MUTED = Win98.MUTED;
		Theme.OFF = Win98.SHADOW;
		Theme.WINDOW = Win98.FACE;
		Theme.WINDOW_SOLID = Win98.FACE;
		Theme.HEADER = Win98.TEXT;
		Theme.SHEET = Win98.FACE;
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
		Theme.HEADER = old[12];
		Theme.SHEET = old[13];
	}

	private record RowHit(HyprMenu.Item item, float x, float y, float w, float h) {
	}
}
