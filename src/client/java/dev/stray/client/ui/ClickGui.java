package dev.stray.client.ui;

import dev.stray.Stray;
import dev.stray.client.combat.OdinClicks;
import dev.stray.client.config.EntityKind;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.config.UnloadState;
import dev.stray.client.render.ArrayListHud;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.MobCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Column click GUI. Left click toggles a feature. Right click slides its settings
 * open under that row. Right click a category header to close that column.
 * Headers drag. Each column scrolls on its own.
 */
public final class ClickGui {
	static final int COL_W = 106;
	private static final int GAP = 2;
	private static final int HEADER = 14;
	private static final int BOX = 14;
	private static final int V_GAP = 2;
	private static final int EDGE = 5;
	private static final int H_PAD = 3;
	private static final float STROKE = 0.5f;
	private static final Style FEATURE_FONT = Style.EMPTY.withFont(new FontDescription.Resource(Stray.id("anonymous_pro")));
	private static final int STRIDE = BOX + V_GAP;
	private static final int NEST = 3;
	private static final float SWITCH_W = 16f;
	private static final float SWITCH_H = 8f;
	private static final int OUTLINE = 0xFF000000;
	private static final int PANEL = 0x99000000;
	private static final int VEIL = 0x6A000000;
	private static final float COLUMN_R = 10f;
	private static final float CARD_R = 4f;
	/** Feature cards keep only a trace of the lens. Category panes stay at full shine. */
	private static final float CARD_SHINE = 0f;
	private static final int OFF_FILL = 0x88000000;
	private static final int ACCENT_ALPHA = 115;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int DIM = 0xFFAAAAAA;
	private static final String[] ORDER = {"World", "Visuals", "Mobs", "Combat", "HUD", "Mining", "Farming", "Menus", "Keys", "Theme", "Player"};
	private static final int[] BIND_WHICH = {3, 4, 13, 5, 7, 11, 12, 14};
	private static final String[] BIND_LABEL = {
		"Open menu", "Loadouts", "Swap loadouts", "Wardrobe", "Profile", "Chat peek", "Lobby ping", "Empty bag"
	};

	private static final Map<String, Column> columns = new LinkedHashMap<>();
	private static final List<Row> rows = new ArrayList<>();
	private static boolean placed;
	private static boolean lightInk;
	private static String expandedName;
	private static String shownName;
	private static float expandT;
	private static long expandNs;
	private static boolean reveal;
	private static String dragging;
	private static float dragOffX;
	private static float dragOffY;
	private static double pointerX;
	private static double pointerY;
	private static float panelX;
	private static float panelY;
	private static float panelW;
	private static float panelH;
	private static boolean panelLive;
	private static final int SEARCH_W = 168;
	private static String searchQuery = "";
	private static boolean searchFocused;
	private static float searchX;
	private static float searchY;
	private static float searchW;
	private static float searchH;
	private static String mobQuery = "";
	private static boolean mobSearchFocused;
	private static float mobSearchX;
	private static float mobSearchY;
	private static float mobSearchW;
	private static float mobSearchH;

	private ClickGui() {
	}

	public static boolean lightInk() {
		return lightInk;
	}

	static void useLightInk(boolean value) {
		lightInk = value;
	}

	static void pointer(double x, double y) {
		pointerX = x;
		pointerY = y;
	}

	static void extract(StrayScreen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Font font = Minecraft.getInstance().font;
		tickExpand();
		syncColumns(screen);
		rows.clear();
		panelLive = false;
		for (String id : ORDER) {
			drawColumn(screen, graphics, font, mouseX, mouseY, columns.get(id));
		}
		screen.clickPicker(graphics, font);
		drawSearch(screen, graphics, font);
		if (StrayConfig.get().arrayList) {
			ArrayListHud.draw(graphics, font, screen.width);
		}
	}

	static boolean searchFocused() {
		return searchFocused;
	}

	static void focusSearch() {
		searchFocused = true;
		mobSearchFocused = false;
	}

	static void blurSearch() {
		searchFocused = false;
	}

	static boolean mobSearchFocused() {
		return mobSearchFocused;
	}

	static void focusMobSearch() {
		mobSearchFocused = true;
		searchFocused = false;
	}

	static void blurMobSearch() {
		mobSearchFocused = false;
	}

	static void typeMobSearch(String text) {
		if (text == null || text.isEmpty() || mobQuery.length() >= 32) {
			return;
		}
		mobQuery += text;
		if (mobQuery.length() > 32) {
			mobQuery = mobQuery.substring(0, 32);
		}
	}

	static void backspaceMobSearch() {
		if (!mobQuery.isEmpty()) {
			mobQuery = mobQuery.substring(0, mobQuery.length() - 1);
		}
	}

	static boolean mobSearchContains(double x, double y) {
		return contains(x, y, mobSearchX, mobSearchY, mobSearchW, mobSearchH);
	}

	static void typeSearch(String text) {
		if (text == null || text.isEmpty() || searchQuery.length() >= 32) {
			return;
		}
		searchQuery += text;
		if (searchQuery.length() > 32) {
			searchQuery = searchQuery.substring(0, 32);
		}
	}

	static void backspaceSearch() {
		if (!searchQuery.isEmpty()) {
			searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
		}
	}

	static boolean searchContains(double x, double y) {
		return contains(x, y, searchX, searchY, searchW, searchH);
	}

	static void rightClick(double x, double y) {
		if (searchContains(x, y) || mobSearchContains(x, y)) {
			return;
		}
		if (panelLive && contains(x, y, panelX, panelY, panelW, panelH)) {
			return;
		}
		for (String id : ORDER) {
			Column column = columns.get(id);
			if (column != null && headerHit(column, x, y)) {
				toggleClosed(column);
				return;
			}
		}
		for (int i = rows.size() - 1; i >= 0; i--) {
			Row row = rows.get(i);
			if (!contains(x, y, row.x, row.y, row.w, row.h)) {
				continue;
			}
			if ("Custom title".equals(row.mod.name)) {
				Minecraft client = Minecraft.getInstance();
				if (client != null) {
					client.setScreen(new CustomTitleScreen(client.screen));
				}
				return;
			}
			if (row.mod.feature != null || row.mod.timeout || row.mod.menuStyle || "Array list".equals(row.mod.name) || "Themed GUIs".equals(row.mod.name)) {
				if (row.mod.name.equals(expandedName)) {
					expandedName = null;
				} else {
					expandedName = row.mod.name;
					shownName = row.mod.name;
					expandT = 0f;
					reveal = true;
				}
			}
			return;
		}
		expandedName = null;
	}

	static void collapse() {
		expandedName = null;
	}

	static boolean drag(double x, double y) {
		if (dragging == null) {
			return false;
		}
		Column column = columns.get(dragging);
		if (column == null) {
			dragging = null;
			return false;
		}
		column.x = (float) x - dragOffX;
		column.y = (float) y - dragOffY;
		return true;
	}

	static void endDrag() {
		if (dragging == null) {
			return;
		}
		dragging = null;
		saveColumns();
	}

	static boolean scroll(double x, double y, double wheel) {
		if (wheel == 0) {
			return false;
		}
		for (Column column : columns.values()) {
			if (contains(x, y, column.x, column.y, COL_W, column.height)) {
				column.scroll = Math.max(0f, column.scroll - (float) wheel * STRIDE);
				return true;
			}
		}
		return false;
	}

	public static List<String> enabledLabels() {
		List<String> labels = new ArrayList<>();
		StrayConfig config = StrayConfig.get();
		for (Mod mod : modules()) {
			if (mod.list && mod.on.getAsBoolean() && config.arrayListShows(mod.name)) {
				labels.add(mod.name);
			}
		}
		return labels;
	}

	public static int colorOf(String name) {
		int hash = name.hashCode();
		float hue = ((hash & 0xFFFF) % 360) / 360f;
		int rgb = hsb(hue, 0.62f, 1f);
		return 0xFF000000 | rgb;
	}

	private static void drawSearch(StrayScreen screen, GuiGraphicsExtractor graphics, Font font) {
		int width = Math.min(SEARCH_W, Math.max(BOX, screen.width - 16));
		int x = (screen.width - width) / 2;
		int y = screen.height - BOX - 8;
		searchX = x;
		searchY = y;
		searchW = width;
		searchH = BOX;
		outlined(graphics, x, y, width, BOX, searchFocused ? accentFill() : OFF_FILL);
		boolean placeholder = searchQuery.isEmpty() && !searchFocused;
		String shown = placeholder ? "Search" : searchQuery + (searchFocused ? "|" : "");
		String label = fit(font, shown, width - 8);
		GuiDraw.text(graphics, font, label, x + 4, textY(font, y, BOX), placeholder ? DIM : TEXT, true);
		screen.clickHit(x, y, width, BOX, ClickGui::focusSearch);
	}

	private static boolean searchHit(Mod mod) {
		String needle = searchQuery.trim().toLowerCase(Locale.ROOT);
		if (needle.isEmpty()) {
			return true;
		}
		if (display(mod.column).toLowerCase(Locale.ROOT).contains(needle)) {
			return true;
		}
		return display(mod.name).toLowerCase(Locale.ROOT).contains(needle);
	}

	private static void drawColumn(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, Column column) {
		if ("Mobs".equals(column.id)) {
			drawMobs(screen, graphics, font, column);
			return;
		}
		if ("Keys".equals(column.id)) {
			drawKeys(screen, graphics, font, column);
			return;
		}
		List<Mod> mods = new ArrayList<>();
		for (Mod mod : modules()) {
			if (column.id.equals(mod.column) && searchHit(mod)) {
				mods.add(mod);
			}
		}
		if (!searchQuery.isBlank() && mods.isEmpty()) {
			column.height = 0f;
			return;
		}
		if (drawIfClosed(screen, graphics, font, column, display(column.id))) {
			return;
		}
		int x = Math.round(column.x);
		int top = Math.round(column.y);
		float content = columnContent(screen, mods);
		boolean lens = glass();
		int topPad = lens ? EDGE : 0;
		int rowPad = lens ? EDGE : H_PAD;
		int bottomPad = lens ? EDGE : H_PAD;
		int viewTop = top + topPad + HEADER + V_GAP;
		int visible = Math.max(BOX, screen.height - viewTop - bottomPad - BOX - 10);
		int shownH = Math.min(Math.round(content), visible);
		column.height = topPad + HEADER + V_GAP + shownH + bottomPad;
		float maxScroll = Math.max(0f, content - visible);
		if (reveal) {
			float cursor = 0f;
			for (int i = 0; i < mods.size(); i++) {
				Mod mod = mods.get(i);
				if (mod.name.equals(shownName)) {
					float settingsTop = cursor + BOX + V_GAP;
					float full = settingsFull(screen, mod);
					maxScroll = Math.max(maxScroll, cursor + BOX + V_GAP + full - visible);
					if (settingsTop + Math.min(full, 48f) > column.scroll + visible) {
						column.scroll = Math.max(0f, settingsTop + Math.min(full, visible * 0.55f) - visible);
					}
					reveal = false;
					break;
				}
				cursor += BOX;
				if (i + 1 < mods.size()) {
					cursor += V_GAP;
				}
			}
		}
		column.scroll = Math.round(Mth.clamp(column.scroll, 0f, maxScroll));

		int boxX = x + rowPad;
		int boxW = COL_W - rowPad * 2;
		int headerX = lens ? boxX : x;
		int headerW = lens ? boxW : COL_W;
		int headerY = top + topPad;
		paintColumn(graphics, x, top, column.height, headerX, headerY, headerW);
		departure(graphics, font, display(column.id), headerX, headerY, headerW, HEADER, TEXT);
		screen.clickHit(headerX, headerY, headerW, HEADER, () -> beginDrag(column.id));

		boolean clipped = GuiDraw.scissor(graphics, lens ? boxX : x, viewTop, lens ? boxW : COL_W, shownH);
		float y = viewTop - column.scroll;
		for (int i = 0; i < mods.size(); i++) {
			Mod mod = mods.get(i);
			boolean shown = y + BOX > viewTop && y < viewTop + shownH;
			if (shown) {
				boolean on = mod.on.getAsBoolean();
				int rowY = Math.round(y);
				moduleBox(graphics, boxX, rowY, boxW, BOX, on);
				String label = fitFeature(font, display(mod.name), boxW - 4);
				departure(graphics, font, label, boxX, rowY, boxW, BOX, on ? TEXT : DIM);
				if (mod.menuStyle || mod.hold) {
					screen.clickHit(boxX, rowY, boxW, BOX, () -> {
					});
				} else {
					screen.clickHit(boxX, rowY, boxW, BOX, () -> toggle(mod));
				}
				rows.add(new Row(mod, boxX, rowY, boxW, BOX));
			}
			y += BOX;
			if (mod.name.equals(shownName) && expandT > 0.001f) {
				float full = settingsFull(screen, mod);
				float open = full * expandT;
				y += V_GAP;
				if (open > 1f && y + open > viewTop && y < viewTop + shownH) {
					drawInline(screen, graphics, font, mouseX, mouseY, mod, viewTop, shownH, boxX, y, boxW, open, clipped);
				}
				y += open;
			}
			if (i + 1 < mods.size()) {
				y += V_GAP;
			}
		}
		if (clipped) {
			GuiDraw.disableScissor(graphics);
		}
	}

	private static void drawMobs(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, Column column) {
		String global = searchQuery.trim().toLowerCase(Locale.ROOT);
		boolean titleHit = global.isEmpty() || "mobs".contains(global);
		List<MobCatalog.Entry> entries = new ArrayList<>();
		for (MobCatalog.Entry entry : MobCatalog.filtered(mobQuery)) {
			if (global.isEmpty() || entry.name().toLowerCase(Locale.ROOT).contains(global)) {
				entries.add(entry);
			}
		}
		if (!titleHit && entries.isEmpty()) {
			column.height = 0f;
			return;
		}
		if (drawIfClosed(screen, graphics, font, column, display(column.id))) {
			return;
		}
		int x = Math.round(column.x);
		int top = Math.round(column.y);
		boolean lens = glass();
		int topPad = lens ? EDGE : 0;
		int rowPad = lens ? EDGE : H_PAD;
		int bottomPad = lens ? EDGE : H_PAD;
		int searchY = top + topPad + HEADER + V_GAP;
		int viewTop = searchY + BOX + V_GAP;
		int room = Math.max(BOX, screen.height - viewTop - bottomPad - BOX - 10);
		int visible = Math.min(room, stackH(8));
		float content = stackH(entries.size());
		int shownH = Math.min(Math.round(content), visible);
		column.height = topPad + HEADER + V_GAP + BOX + V_GAP + shownH + bottomPad;
		column.scroll = Math.round(Mth.clamp(column.scroll, 0f, Math.max(0f, content - visible)));

		int boxX = x + rowPad;
		int boxW = COL_W - rowPad * 2;
		int headerX = lens ? boxX : x;
		int headerW = lens ? boxW : COL_W;
		int headerY = top + topPad;
		paintColumn(graphics, x, top, column.height, headerX, headerY, headerW);
		departure(graphics, font, display(column.id), headerX, headerY, headerW, HEADER, TEXT);
		screen.clickHit(headerX, headerY, headerW, HEADER, () -> beginDrag(column.id));
		mobSearchX = boxX;
		mobSearchY = searchY;
		mobSearchW = boxW;
		mobSearchH = BOX;
		outlined(graphics, boxX, searchY, boxW, BOX, mobSearchFocused ? accentFill() : OFF_FILL);
		boolean placeholder = mobQuery.isEmpty() && !mobSearchFocused;
		String shown = placeholder ? "Search" : mobQuery + (mobSearchFocused ? "|" : "");
		GuiDraw.text(graphics, font, fit(font, shown, boxW - 8), boxX + 4, textY(font, searchY, BOX), placeholder ? DIM : TEXT, true);
		screen.clickHit(boxX, searchY, boxW, BOX, ClickGui::focusMobSearch);

		boolean clipped = shownH > 0 && GuiDraw.scissor(graphics, lens ? boxX : x, viewTop, lens ? boxW : COL_W, shownH);
		StrayConfig config = StrayConfig.get();
		float y = viewTop - column.scroll;
		for (int i = 0; i < entries.size(); i++) {
			MobCatalog.Entry entry = entries.get(i);
			if (y + BOX > viewTop && y < viewTop + shownH) {
				boolean on = config.isMobGlowSelected(entry.id().toString());
				int rowY = Math.round(y);
				moduleBox(graphics, boxX, rowY, boxW, BOX, on);
				String label = fitFeature(font, entry.name(), boxW - 4);
				departure(graphics, font, label, boxX, rowY, boxW, BOX, on ? TEXT : DIM);
				String id = entry.id().toString();
				screen.clickHit(boxX, rowY, boxW, BOX, () -> {
					StrayConfig.get().toggleMobGlow(id);
					UnloadState.markDirty();
				});
			}
			y += BOX;
			if (i + 1 < entries.size()) {
				y += V_GAP;
			}
		}
		if (clipped) {
			GuiDraw.disableScissor(graphics);
		}
	}

	private static void drawInline(
		StrayScreen screen,
		GuiGraphicsExtractor graphics,
		Font font,
		int mouseX,
		int mouseY,
		Mod mod,
		int viewTop,
		int viewH,
		int boxX,
		float y,
		int boxW,
		float open,
		boolean columnClipped
	) {
		float clipTop = Math.max(y, viewTop);
		float clipBot = Math.min(y + open, viewTop + viewH);
		if (columnClipped) {
			GuiDraw.disableScissor(graphics);
		}
		boolean settingsClip = clipBot > clipTop + 0.5f && GuiDraw.scissor(graphics, boxX, clipTop, boxW, clipBot - clipTop);
		int mark = screen.clickHitMark();
		if ("Array list".equals(mod.name)) {
			drawArrayList(screen, graphics, font, boxX, y, boxW);
		} else if ("Themed GUIs".equals(mod.name)) {
			drawGuiStyle(screen, graphics, font, boxX, y, boxW);
		} else if (mod.menuStyle) {
			if ("HUD".equals(mod.name)) {
				drawHud(screen, graphics, font, boxX, y, boxW);
			} else {
				drawMenuStyle(screen, graphics, font, boxX, y, boxW);
			}
		} else if (mod.timeout) {
			drawTimeout(screen, graphics, font, boxX, y, boxW);
		} else if (mod.feature != null) {
			screen.clickVisuals(mod.kind);
			screen.clickSettings(graphics, font, mouseX, mouseY, boxX, y, boxW, mod.feature);
		}
		if (expandT < 0.92f) {
			screen.clickHitRewind(mark);
		} else {
			screen.clickClipHits(mark, boxX, clipTop, boxW, Math.max(0f, clipBot - clipTop));
		}
		if (settingsClip) {
			GuiDraw.disableScissor(graphics);
		}
		if (columnClipped) {
			GuiDraw.scissor(graphics, boxX, viewTop, boxW, viewH);
		}
		panelLive = true;
		panelX = boxX;
		panelY = clipTop;
		panelW = boxW;
		panelH = Math.max(0f, clipBot - clipTop);
	}

	private static void drawArrayList(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, float x, float y, float w) {
		StrayConfig config = StrayConfig.get();
		drawChoice(screen, graphics, font, x, y, w, "Accent Color", config.arrayListAccent, () -> {
			StrayConfig.get().arrayListAccent = !StrayConfig.get().arrayListAccent;
			UnloadState.markDirty();
		});
		y += STRIDE;
		for (Mod mod : modules()) {
			if (!mod.list) {
				continue;
			}
			boolean shown = config.arrayListShows(mod.name);
			String name = mod.name;
			drawChoice(screen, graphics, font, x, y, w, fit(font, display(name), Math.round(w) - 4), shown, () -> {
				StrayConfig.get().toggleArrayListShown(name);
				UnloadState.markDirty();
			});
			y += STRIDE;
		}
	}

	private static int arrayListRows() {
		int count = 1;
		for (Mod mod : modules()) {
			if (mod.list) {
				count++;
			}
		}
		return count;
	}

	private static void drawMenuStyle(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, float x, float y, float w) {
		StrayConfig config = StrayConfig.get();
		drawButton(screen, graphics, font, x, y, w, "Style " + config.clickGuiStyleLabel(), () -> {
			StrayConfig.get().cycleClickGuiStyle();
			UnloadState.markDirty();
		});
		y += STRIDE;
		drawButton(screen, graphics, font, x, y, w, "Stray Menu", () -> setClickGui(false));
	}

	private static void drawGuiStyle(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, float x, float y, float w) {
		drawButton(screen, graphics, font, x, y, w, "Style " + StrayConfig.get().guiStyleLabel(), () -> {
			StrayConfig.get().cycleGuiStyle();
			UnloadState.markDirty();
		});
	}

	private static void drawHud(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, float x, float y, float w) {
		StrayConfig config = StrayConfig.get();
		drawChoice(screen, graphics, font, x, y, w, "HUD Stars", config.hudStarfield, () -> {
			StrayConfig current = StrayConfig.get();
			current.hudStarfield = !current.hudStarfield;
			UnloadState.markDirty();
		});
		y += STRIDE;
		drawButton(screen, graphics, font, x, y, w, "Style " + config.hudStyleLabel(), () -> {
			StrayConfig.get().cycleHudStyle();
			UnloadState.markDirty();
		});
		y += STRIDE;
		drawChoice(screen, graphics, font, x, y, w, "Accent Outlines", config.accentOutlines, () -> {
			StrayConfig current = StrayConfig.get();
			current.accentOutlines = !current.accentOutlines;
			Theme.refresh();
			UnloadState.markDirty();
		});
		y += STRIDE;
		drawButton(screen, graphics, font, x, y, w, "HUD Editor", () -> Minecraft.getInstance().setScreen(new HudEditorScreen()));
	}

	/** Boolean subsetting. A switch on the right, not a module-sized accent fill. */
	private static void drawChoice(
		StrayScreen screen,
		GuiGraphicsExtractor graphics,
		Font font,
		float x,
		float y,
		float w,
		String label,
		boolean on,
		Runnable pick
	) {
		int ix = Math.round(x) + NEST;
		int iy = Math.round(y);
		int iw = Math.round(w) - NEST * 2;
		outlined(graphics, ix, iy, iw, BOX, OFF_FILL);
		float sw = SWITCH_W;
		float sh = SWITCH_H;
		float sx = ix + iw - 2f - sw;
		float sy = y + (BOX - sh) * 0.5f;
		drawSwitch(graphics, sx, sy, sw, sh, on);
		int max = Math.max(4, Math.round(sx - 2f - (ix + 2f)));
		String shown = fitFeature(font, label, max);
		departureLeft(graphics, font, shown, ix + 2f, iy, BOX, TEXT);
		screen.clickHit(ix, iy, iw, BOX, pick);
	}

	/** Action row. Left label and a chevron, so it does not read as an enabled module. */
	private static void drawButton(
		StrayScreen screen,
		GuiGraphicsExtractor graphics,
		Font font,
		float x,
		float y,
		float w,
		String label,
		Runnable pick
	) {
		int ix = Math.round(x) + NEST;
		int iy = Math.round(y);
		int iw = Math.round(w) - NEST * 2;
		outlined(graphics, ix, iy, iw, BOX, OFF_FILL);
		String mark = ">";
		float markX = ix + iw - 2f - font.width(featureText(mark));
		departureLeft(graphics, font, mark, markX, iy, BOX, DIM);
		int max = Math.max(4, Math.round(markX - 2f - (ix + 2f)));
		String shown = fitFeature(font, label, max);
		departureLeft(graphics, font, shown, ix + 2f, iy, BOX, TEXT);
		screen.clickHit(ix, iy, iw, BOX, pick);
	}

	private static void setClickGui(boolean click) {
		StrayConfig.get().clickGui = click;
		UnloadState.markDirty();
	}

	private static void drawTimeout(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, float x, float y, float w) {
		int current = StrayConfig.get().noCursorResetTimeout;
		int[] choices = {50, 100, 150, 300, 500, 1000};
		for (int choice : choices) {
			boolean on = current == choice;
			String label = choice + " ms";
			int ix = Math.round(x) + NEST;
			int iy = Math.round(y);
			int iw = Math.round(w) - NEST * 2;
			outlined(graphics, ix, iy, iw, BOX, OFF_FILL);
			float textLeft = ix + 4f;
			if (on) {
				GuiDraw.roundedFine(graphics, ix + 3f, iy + 3f, 2.5f, BOX - 6f, 1.2f, Theme.ACCENT);
				textLeft = ix + 8f;
			}
			departureLeft(graphics, font, label, textLeft, iy, BOX, on ? TEXT : DIM);
			screen.clickHit(ix, iy, iw, BOX, () -> {
				StrayConfig.get().noCursorResetTimeout = choice;
				UnloadState.markDirty();
			});
			y += STRIDE;
		}
	}

	private static boolean headerHit(Column column, double x, double y) {
		if (column.height <= 0f) {
			return false;
		}
		boolean lens = glass();
		int left = Math.round(column.x);
		int top = Math.round(column.y);
		float hx = left + (lens ? EDGE : 0);
		float hy = top + (lens ? EDGE : 0);
		float hw = lens ? COL_W - EDGE * 2f : COL_W;
		return contains(x, y, hx, hy, hw, HEADER);
	}

	private static void toggleClosed(Column column) {
		column.closed = !column.closed;
		if (column.closed && expandedName != null) {
			for (Mod mod : modules()) {
				if (column.id.equals(mod.column) && mod.name.equals(expandedName)) {
					expandedName = null;
					break;
				}
			}
		}
		saveColumns();
	}

	/** Category header only. Returns true when the column is closed. */
	private static boolean drawIfClosed(
		StrayScreen screen,
		GuiGraphicsExtractor graphics,
		Font font,
		Column column,
		String title
	) {
		if (!column.closed) {
			return false;
		}
		int x = Math.round(column.x);
		int top = Math.round(column.y);
		boolean lens = glass();
		int pad = lens ? EDGE : 0;
		column.height = pad + HEADER + pad;
		int headerX = lens ? x + EDGE : x;
		int headerW = lens ? COL_W - EDGE * 2 : COL_W;
		int headerY = top + pad;
		paintColumn(graphics, x, top, column.height, headerX, headerY, headerW);
		departure(graphics, font, title, headerX, headerY, headerW, HEADER, TEXT);
		screen.clickHit(headerX, headerY, headerW, HEADER, () -> beginDrag(column.id));
		return true;
	}

	private static void beginDrag(String id) {
		Column column = columns.get(id);
		if (column == null) {
			return;
		}
		dragging = id;
		dragOffX = (float) pointerX - column.x;
		dragOffY = (float) pointerY - column.y;
	}

	private static void toggle(Mod mod) {
		mod.set.accept(!mod.on.getAsBoolean());
		UnloadState.markDirty();
	}

	private static void syncColumns(StrayScreen screen) {
		StrayConfig config = StrayConfig.get();
		if (config.clickColumns == null) {
			config.clickColumns = new ArrayList<>();
		}
		for (String id : ORDER) {
			columns.computeIfAbsent(id, Column::new);
		}
		if (!placed) {
			Map<String, StrayConfig.ClickColumnPos> saved = new LinkedHashMap<>();
			for (StrayConfig.ClickColumnPos pos : config.clickColumns) {
				if (pos != null && pos.id != null) {
					saved.put(pos.id, pos);
				}
			}
			int fit = Math.max(1, (screen.width - 2) / (COL_W + GAP));
			int bands = (ORDER.length + fit - 1) / fit;
			float band = Math.max(HEADER + STRIDE * 4, (screen.height - 8f) / bands);
			for (int i = 0; i < ORDER.length; i++) {
				Column column = columns.get(ORDER[i]);
				StrayConfig.ClickColumnPos pos = saved.get(column.id);
				if (pos != null) {
					column.x = pos.x;
					column.y = pos.y;
					column.closed = pos.closed;
				} else {
					column.x = 2 + (i % fit) * (COL_W + GAP);
					column.y = (i / fit) * band;
				}
			}
			placed = true;
		}
		for (Column column : columns.values()) {
			column.x = Mth.clamp(column.x, 0, Math.max(0, screen.width - COL_W));
			column.y = Mth.clamp(column.y, 0, Math.max(0, screen.height - HEADER));
		}
	}

	private static void saveColumns() {
		StrayConfig config = StrayConfig.get();
		config.clickColumns = new ArrayList<>();
		for (String id : ORDER) {
			Column column = columns.get(id);
			if (column == null) {
				continue;
			}
			StrayConfig.ClickColumnPos pos = new StrayConfig.ClickColumnPos();
			pos.id = column.id;
			pos.x = column.x;
			pos.y = column.y;
			pos.closed = column.closed;
			config.clickColumns.add(pos);
		}
		UnloadState.markDirty();
	}

	private static int stackH(int count) {
		if (count <= 0) {
			return 0;
		}
		return count * BOX + (count - 1) * V_GAP;
	}

	private static float columnContent(StrayScreen screen, List<Mod> mods) {
		float h = 0f;
		for (int i = 0; i < mods.size(); i++) {
			Mod mod = mods.get(i);
			h += BOX;
			if (mod.name.equals(shownName) && expandT > 0.001f) {
				h += V_GAP + settingsFull(screen, mod) * expandT;
			}
			if (i + 1 < mods.size()) {
				h += V_GAP;
			}
		}
		return h;
	}

	private static float settingsFull(StrayScreen screen, Mod mod) {
		if ("Array list".equals(mod.name)) {
			return stackH(arrayListRows());
		}
		if ("Themed GUIs".equals(mod.name)) {
			return stackH(1);
		}
		if (mod.menuStyle) {
			if ("HUD".equals(mod.name)) {
				return stackH(4);
			}
			if ("Click GUI".equals(mod.name)) {
				return stackH(2);
			}
			return stackH(1);
		}
		if (mod.timeout) {
			return stackH(6);
		}
		if (mod.feature == null) {
			return 0f;
		}
		return mod.feature.rows() * screen.clickSettingRow();
	}

	private static void tickExpand() {
		long now = System.nanoTime();
		float dt = expandNs == 0L ? 0.016f : Math.min(0.05f, (now - expandNs) / 1_000_000_000f);
		expandNs = now;
		float target = expandedName != null && expandedName.equals(shownName) ? 1f : 0f;
		expandT += (target - expandT) * (1f - (float) Math.exp(-16f * dt));
		if (Math.abs(target - expandT) < 0.01f) {
			expandT = target;
		}
		if (expandT == 0f) {
			shownName = expandedName;
		}
	}

	public static String display(String label) {
		if (label == null || label.isEmpty()) {
			return "";
		}
		StringBuilder out = new StringBuilder(label.length());
		boolean cap = true;
		for (int i = 0; i < label.length(); i++) {
			char c = label.charAt(i);
			if (Character.isWhitespace(c)) {
				cap = true;
				out.append(c);
				continue;
			}
			if (cap && Character.isLetter(c)) {
				out.append(Character.toUpperCase(c));
			} else {
				out.append(c);
			}
			cap = false;
		}
		return out.toString();
	}

	private static int accentFill() {
		return Theme.withAlpha(Theme.ACCENT, ACCENT_ALPHA);
	}

	private static void drawSwitch(GuiGraphicsExtractor graphics, float x, float y, float w, float h, boolean on) {
		float stroke = 0.5f;
		GuiDraw.roundedFine(graphics, x, y, w, h, h * 0.5f, OUTLINE);
		int fill = on ? Theme.ACCENT : OFF_FILL;
		float innerH = h - stroke * 2f;
		GuiDraw.roundedFine(graphics, x + stroke, y + stroke, w - stroke * 2f, innerH, innerH * 0.5f, fill);
		float knobR = innerH * 0.34f;
		float t = on ? 1f : 0f;
		float knobX = x + stroke + knobR + 1f + t * (w - stroke * 2f - knobR * 2f - 2f);
		GuiDraw.circle(graphics, knobX, y + h * 0.5f, knobR, 0xFFFFFFFF);
	}

	private static void drawKeys(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, Column column) {
		String needle = searchQuery.trim().toLowerCase(Locale.ROOT);
		boolean title = needle.isEmpty() || "keys".contains(needle);
		List<Integer> binds = new ArrayList<>();
		for (int i = 0; i < BIND_LABEL.length; i++) {
			if (title || BIND_LABEL[i].toLowerCase(Locale.ROOT).contains(needle)) {
				binds.add(i);
			}
		}
		boolean shortcuts = title || "shortcuts".contains(needle) || "pass arguments".contains(needle);
		boolean commands = title || "commands".contains(needle) || "/loadouts".contains(needle) || "/wardrobe".contains(needle) || "/pv".contains(needle);
		if (!title && binds.isEmpty() && !shortcuts && !commands) {
			column.height = 0f;
			return;
		}
		if (drawIfClosed(screen, graphics, font, column, "Keys")) {
			return;
		}
		StrayConfig config = StrayConfig.get();
		boolean shortcutOn = config.commandShortcutsEnabled;
		int extra = shortcuts ? 1 + (shortcutOn ? 2 : 0) : 0;
		int commandLines = commands ? 5 : 0;
		float commandsH = commandLines == 0 ? 0f : commandLines * (font.lineHeight + 1) + 6f;
		float content = stackH(binds.size() + extra) + (commandsH > 0f ? V_GAP + commandsH : 0f);
		int x = Math.round(column.x);
		int top = Math.round(column.y);
		boolean lens = glass();
		int topPad = lens ? EDGE : 0;
		int rowPad = lens ? EDGE : H_PAD;
		int bottomPad = lens ? EDGE : H_PAD;
		int viewTop = top + topPad + HEADER + V_GAP;
		int room = Math.max(BOX, screen.height - viewTop - bottomPad - BOX - 10);
		int shownH = Math.min(Math.round(content), room);
		column.height = topPad + HEADER + V_GAP + shownH + bottomPad;
		column.scroll = Math.round(Mth.clamp(column.scroll, 0f, Math.max(0f, content - shownH)));

		int boxX = x + rowPad;
		int boxW = COL_W - rowPad * 2;
		int headerX = lens ? boxX : x;
		int headerW = lens ? boxW : COL_W;
		int headerY = top + topPad;
		paintColumn(graphics, x, top, column.height, headerX, headerY, headerW);
		departure(graphics, font, "Keys", headerX, headerY, headerW, HEADER, TEXT);
		screen.clickHit(headerX, headerY, headerW, HEADER, () -> beginDrag(column.id));

		boolean clipped = shownH > 0 && GuiDraw.scissor(graphics, lens ? boxX : x, viewTop, lens ? boxW : COL_W, shownH);
		float y = viewTop - column.scroll;
		for (int index : binds) {
			if (y + BOX > viewTop && y < viewTop + shownH) {
				drawBind(screen, graphics, font, boxX, Math.round(y), boxW, BIND_LABEL[index], BIND_WHICH[index]);
			}
			y += STRIDE;
		}
		if (shortcuts) {
			if (y + BOX > viewTop && y < viewTop + shownH) {
				drawChoice(screen, graphics, font, boxX, y, boxW, "Shortcuts", shortcutOn, () -> {
					StrayConfig current = StrayConfig.get();
					current.commandShortcutsEnabled = !current.commandShortcutsEnabled;
					CommandShortcuts.sync();
					UnloadState.markDirty();
				});
			}
			y += STRIDE;
			if (shortcutOn) {
				if (y + BOX > viewTop && y < viewTop + shownH) {
					drawChoice(screen, graphics, font, boxX, y, boxW, "Pass arguments", config.commandShortcutsPassArgs, () -> {
						StrayConfig current = StrayConfig.get();
						current.commandShortcutsPassArgs = !current.commandShortcutsPassArgs;
						UnloadState.markDirty();
					});
				}
				y += STRIDE;
				if (y + BOX > viewTop && y < viewTop + shownH) {
					drawButton(screen, graphics, font, boxX, y, boxW, "Edit", () ->
						Minecraft.getInstance().setScreen(new CommandShortcutScreen(screen))
					);
				}
				y += STRIDE;
			}
		}
		if (commands && y + commandsH > viewTop && y < viewTop + shownH) {
			soft(graphics, boxX, y, boxW, commandsH, OFF_FILL);
			String[] lines = {
				"/loadouts  /ld",
				"/wardrobe  /wd",
				"/pv  /profile",
				"/autoclicker add left",
				MenuSlotBinds.hint() + " equips and closes"
			};
			float textY = y + 3f;
			for (String line : lines) {
				GuiDraw.text(graphics, font, featureText(fitFeature(font, line, boxW - 8)), boxX + 4f, textY, 1f, DIM, true);
				textY += font.lineHeight + 1;
			}
		}
		if (clipped) {
			GuiDraw.disableScissor(graphics);
		}
	}

	private static void drawBind(
		StrayScreen screen,
		GuiGraphicsExtractor graphics,
		Font font,
		int x,
		int y,
		int w,
		String label,
		int which
	) {
		soft(graphics, x, y, w, BOX, OFF_FILL);
		boolean listening = screen.clickBindListening(which);
		String chip = listening ? "..." : OdinClicks.keyLabel(OdinClicks.parseKey(bindKey(which)));
		int chipW = font.width(featureText(chip));
		int labelMax = Math.max(4, w - 8 - chipW);
		departureLeft(graphics, font, fitFeature(font, label, labelMax), x + 4f, y, BOX, TEXT);
		departureLeft(graphics, font, chip, x + w - 4f - chipW, y, BOX, Theme.ACCENT);
		screen.clickHit(x, y, w, BOX, () -> screen.clickListenBind(which));
	}

	private static String bindKey(int which) {
		StrayConfig config = StrayConfig.get();
		return switch (which) {
			case 3 -> config.openGuiKey;
			case 4 -> config.openLoadoutsKey;
			case 5 -> config.openWardrobeKey;
			case 7 -> config.openProfileKey;
			case 11 -> config.chatPeekKey;
			case 12 -> config.strayPingKey;
			case 13 -> config.loadoutSwapKey;
			case 14 -> config.emptyVacuumKey;
			case 15 -> config.pestPlotKey;
			default -> "";
		};
	}

	private static boolean glass() {
		return StrayConfig.get().clickGuiGlass();
	}

	private static void moduleBox(GuiGraphicsExtractor graphics, int x, int y, int w, int h, boolean enabled) {
		soft(graphics, x, y, w, h, enabled ? accentFill() : OFF_FILL);
	}

	private static void outlined(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int fill) {
		soft(graphics, x, y, w, h, fill);
	}

	private static void soft(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int fill) {
		if (glass()) {
			card(graphics, x, y, w, h, denser(fill), CARD_SHINE);
			return;
		}
		flat(graphics, x, y, w, h, fill);
	}

	/** Pull the feature-card tint up so the lens does not show through the middle. */
	private static int denser(int fill) {
		int alpha = (fill >>> 24) & 0xFF;
		int lifted = Math.min(255, Math.max(alpha + 70, 210));
		return (lifted << 24) | (fill & 0xFFFFFF);
	}

	private static void paintColumn(
		GuiGraphicsExtractor graphics,
		float x,
		float y,
		float h,
		float headerX,
		float headerY,
		float headerW
	) {
		if (glass()) {
			columnBackdrop(graphics, x, y, COL_W, h);
			card(graphics, headerX, headerY, headerW, HEADER, accentFill());
			return;
		}
		GuiDraw.fill(graphics, x, y, COL_W, h, PANEL);
		flat(graphics, headerX, headerY, headerW, HEADER, accentFill());
	}

	private static void flat(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int fill) {
		if (w < 2f || h < 2f) {
			return;
		}
		GuiDraw.fillSmooth(graphics, x, y, w, STROKE, OUTLINE);
		GuiDraw.fillSmooth(graphics, x, y + h - STROKE, w, STROKE, OUTLINE);
		GuiDraw.fillSmooth(graphics, x, y, STROKE, h, OUTLINE);
		GuiDraw.fillSmooth(graphics, x + w - STROKE, y, STROKE, h, OUTLINE);
		GuiDraw.fillSmooth(graphics, x + STROKE, y + STROKE, w - STROKE * 2f, h - STROKE * 2f, fill);
	}

	private static void card(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int fill) {
		card(graphics, x, y, w, h, fill, 1f);
	}

	private static void card(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int fill, float shine) {
		float r = Math.min(CARD_R, Math.min(w, h) * 0.5f);
		ControlChrome.lens(graphics, x, y, w, h, r, shine);
		if ((fill & 0xFF000000) != 0) {
			GuiDraw.roundedFine(graphics, x, y, w, h, r, fill);
		}
	}

	private static void columnBackdrop(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		GuiDraw.roundedFine(graphics, x, y, w, h, COLUMN_R, PANEL);
		ControlChrome.backdrop(graphics, x, y, w, h, COLUMN_R);
		GuiDraw.roundedFine(graphics, x, y, w, h, COLUMN_R, VEIL);
	}

	public static Component styled(String label) {
		return Component.literal(label == null ? "" : label).withStyle(FEATURE_FONT);
	}

	private static Component featureText(String label) {
		return styled(label);
	}

	private static void departure(GuiGraphicsExtractor graphics, Font font, String label, float x, float y, float w, int h, int color) {
		Component text = featureText(label);
		float width = font.width(text);
		GuiDraw.text(graphics, font, text, x + (w - width) / 2f, y + (h - font.lineHeight) / 2f, 1f, color, true);
	}

	private static void departureLeft(GuiGraphicsExtractor graphics, Font font, String label, float x, float y, int h, int color) {
		GuiDraw.text(graphics, font, featureText(label), x, y + (h - font.lineHeight) / 2f, 1f, color, true);
	}

	private static String fitFeature(Font font, String label, int max) {
		if (font.width(featureText(label)) <= max) {
			return label;
		}
		String trimmed = label;
		while (trimmed.length() > 1 && font.width(featureText(trimmed + ".")) > max) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed + ".";
	}

	private static float textX(Font font, String label, float x, float w) {
		return x + (w - font.width(label)) / 2f;
	}

	private static float textY(Font font, float y, int h) {
		return y + (h - font.lineHeight) / 2f;
	}

	private static String fit(Font font, String label, int max) {
		if (font.width(label) <= max) {
			return label;
		}
		String trimmed = label;
		while (trimmed.length() > 1 && font.width(trimmed + ".") > max) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed + ".";
	}

	private static boolean contains(double x, double y, float rx, float ry, float rw, float rh) {
		return x >= rx && y >= ry && x < rx + rw && y < ry + rh;
	}

	private static int hsb(float hue, float sat, float bri) {
		int sector = (int) (hue * 6f);
		float f = hue * 6f - sector;
		float p = bri * (1f - sat);
		float q = bri * (1f - f * sat);
		float t = bri * (1f - (1f - f) * sat);
		float r;
		float g;
		float b;
		switch (sector % 6) {
			case 0 -> { r = bri; g = t; b = p; }
			case 1 -> { r = q; g = bri; b = p; }
			case 2 -> { r = p; g = bri; b = t; }
			case 3 -> { r = p; g = q; b = bri; }
			case 4 -> { r = t; g = p; b = bri; }
			default -> { r = bri; g = p; b = q; }
		}
		return (Math.round(r * 255f) << 16) | (Math.round(g * 255f) << 8) | Math.round(b * 255f);
	}

	private static List<Mod> modules() {
		StrayConfig config = StrayConfig.get();
		List<Mod> mods = new ArrayList<>();
		mods.add(mod("World tint", "World", StrayScreen.Feature.WORLD, null, () -> config.worldTintEnabled, v -> config.worldTintEnabled = v, true, false));
		mods.add(mod("Skybox", "World", StrayScreen.Feature.SKY, null, () -> config.skyTintEnabled, v -> config.skyTintEnabled = v, true, false));
		mods.add(mod("Ambience", "World", StrayScreen.Feature.AMBIENCE, null, () -> config.ambienceEnabled, v -> config.ambienceEnabled = v, true, false));
		mods.add(mod("Fog", "World", StrayScreen.Feature.FOG, null, () -> config.fogEnabled, v -> config.fogEnabled = v, true, false));
		mods.add(mod("Aspect", "World", StrayScreen.Feature.VIEW, null, () -> config.aspectEnabled, v -> config.aspectEnabled = v, true, false));
		mods.add(mod("Motion blur", "World", StrayScreen.Feature.MOTION, null, () -> config.motionBlurEnabled, v -> config.motionBlurEnabled = v, true, false));
		mods.add(mod("Skull hold", "World", null, null, () -> config.legacySkullHold, v -> config.legacySkullHold = v, true, false));
		mods.add(mod("Backwards walk", "World", null, null, () -> config.legacyBackwardsWalk, v -> config.legacyBackwardsWalk = v, true, false));

		mods.add(mod("Player glow", "Visuals", StrayScreen.Feature.MOB, EntityKind.PLAYER, () -> config.playerVisuals.glowEnabled, v -> config.playerVisuals.glowEnabled = v, true, false));
		mods.add(mod("Nametags", "Visuals", StrayScreen.Feature.NAMETAGS, EntityKind.PLAYER, () -> config.playerVisuals.nametagsEnabled, v -> config.playerVisuals.nametagsEnabled = v, true, false));
		mods.add(mod("Health bar", "Visuals", StrayScreen.Feature.HEALTH, EntityKind.PLAYER, () -> config.playerVisuals.healthEnabled, v -> config.playerVisuals.healthEnabled = v, true, false));
		mods.add(mod("2D box", "Visuals", StrayScreen.Feature.BOX, EntityKind.PLAYER, () -> config.playerVisuals.boxEnabled, v -> config.playerVisuals.boxEnabled = v, true, false));
		mods.add(mod("Shader", "Visuals", StrayScreen.Feature.FILL, EntityKind.PLAYER, () -> config.playerVisuals.shaderEnabled, v -> config.playerVisuals.shaderEnabled = v, true, false));
		mods.add(mod("Mob glow", "Visuals", StrayScreen.Feature.MOB, EntityKind.MOB, () -> config.mobVisuals.glowEnabled, v -> config.mobVisuals.glowEnabled = v, true, false));
		mods.add(mod("Mob tags", "Visuals", StrayScreen.Feature.NAMETAGS, EntityKind.MOB, () -> config.mobVisuals.nametagsEnabled, v -> config.mobVisuals.nametagsEnabled = v, true, false));
		mods.add(mod("Mob health", "Visuals", StrayScreen.Feature.HEALTH, EntityKind.MOB, () -> config.mobVisuals.healthEnabled, v -> config.mobVisuals.healthEnabled = v, true, false));
		mods.add(mod("Mob box", "Visuals", StrayScreen.Feature.BOX, EntityKind.MOB, () -> config.mobVisuals.boxEnabled, v -> config.mobVisuals.boxEnabled = v, true, false));
		mods.add(mod("Mob shader", "Visuals", StrayScreen.Feature.FILL, EntityKind.MOB, () -> config.mobVisuals.shaderEnabled, v -> config.mobVisuals.shaderEnabled = v, true, false));
		mods.add(mod("Star mobs", "Visuals", StrayScreen.Feature.STAR, EntityKind.STAR, () -> config.starVisuals.glowEnabled, v -> {
			config.starVisuals.glowEnabled = v;
			config.starMobEsp = v;
		}, true, false));
		mods.add(mod("Held item", "Visuals", StrayScreen.Feature.HELD_ITEM, null, () -> config.heldItemShaderEnabled, v -> config.heldItemShaderEnabled = v, true, false));
		mods.add(mod("Block outline", "Visuals", StrayScreen.Feature.BLOCK, null, () -> config.blockOutlineGlow, v -> config.blockOutlineGlow = v, true, false));
		mods.add(mod("Fairy souls", "Visuals", StrayScreen.Feature.FAIRY, null, () -> config.fairySoulEsp, v -> config.fairySoulEsp = v, true, false));

		mods.add(mod("Hitsound", "Combat", StrayScreen.Feature.HITSOUND, null, () -> config.hitsoundEnabled, v -> config.hitsoundEnabled = v, true, false));
		mods.add(mod("Auto clicker", "Combat", StrayScreen.Feature.AUTO_CLICKER, null, () -> config.autoClickerEnabled, v -> config.autoClickerEnabled = v, true, false));
		mods.add(mod("Auto rogue", "Combat", null, null, () -> config.autoRogueEnabled, v -> config.autoRogueEnabled = v, true, false));
		mods.add(mod("Triggerbot", "Combat", null, null, () -> config.triggerbotEnabled, v -> config.triggerbotEnabled = v, true, false));
		mods.add(mod("Party stats", "Combat", null, null, () -> config.partyFinderStats, v -> config.partyFinderStats = v, true, false));
		mods.add(mod("Array list", "HUD", null, null, () -> config.arrayList, v -> config.arrayList = v, false, false));
		mods.add(mod("Watermark", "HUD", StrayScreen.Feature.WATERMARK, null, () -> config.watermarkEnabled, v -> config.watermarkEnabled = v, true, false));
		mods.add(mod("Music", "HUD", StrayScreen.Feature.MUSIC, null, () -> config.musicHudEnabled, v -> config.musicHudEnabled = v, true, false));
		mods.add(mod("Picture in picture", "HUD", StrayScreen.Feature.PIP, null, () -> config.pipEnabled, v -> config.pipEnabled = v, true, false));
		mods.add(mod("Raw mats", "HUD", StrayScreen.Feature.RAWMATS, null, () -> config.rawmatsHudEnabled, v -> config.rawmatsHudEnabled = v, true, false));
		mods.add(mod("Pickup log", "HUD", null, null, () -> config.pickupLogEnabled, v -> config.pickupLogEnabled = v, true, false));
		mods.add(mod("Skill progress", "HUD", StrayScreen.Feature.SKILL, null, () -> config.skillProgressHudEnabled, v -> config.skillProgressHudEnabled = v, true, false));
		mods.add(mod("Inventory", "HUD", StrayScreen.Feature.INVENTORY, null, () -> config.inventoryHudEnabled, v -> config.inventoryHudEnabled = v, true, false));
		mods.add(mod("Custom title", "HUD", null, null, () -> config.customTitleEnabled, v -> config.customTitleEnabled = v, false, false));
		mods.add(mod("Scoreboard", "HUD", null, null, () -> config.hudScoreboard, v -> config.hudScoreboard = v, true, false));
		mods.add(mod("Boss bar", "HUD", null, null, () -> config.hudBossBar, v -> config.hudBossBar = v, true, false));
		mods.add(mod("Effects", "HUD", null, null, () -> config.hudEffects, v -> config.hudEffects = v, true, false));
		mods.add(mod("Item name", "HUD", null, null, () -> config.hudHeldItem, v -> config.hudHeldItem = v, true, false));

		mods.add(mod("Mining HUD", "Mining", StrayScreen.Feature.MINING, null, () -> config.miningHudEnabled, v -> config.miningHudEnabled = v, true, false));
		mods.add(mod("Titanium ESP", "Mining", StrayScreen.Feature.TITANIUM, null, () -> config.titaniumEsp, v -> config.titaniumEsp = v, true, false));
		mods.add(mod("CH waypoints", "Mining", StrayScreen.Feature.CRYSTAL, null, () -> config.crystalHollowsWaypoints, v -> config.crystalHollowsWaypoints = v, true, false));
		mods.add(mod("Route miner", "Mining", StrayScreen.Feature.ROUTE, null, () -> config.routeMiner, v -> config.routeMiner = v, true, false));
		mods.add(mod("Powder Mining", "Mining", StrayScreen.Feature.CHEST, null, () -> config.powderMiningEnabled, v -> config.powderMiningEnabled = v, true, false));
		mods.add(mod("CH map", "Mining", StrayScreen.Feature.CH_MAP, null, () -> config.crystalHollowsMap, v -> config.crystalHollowsMap = v, true, false));
		mods.add(mod("Scatha timer", "Mining", null, null, () -> config.scathaHudEnabled, v -> config.scathaHudEnabled = v, true, false));
		mods.add(mod("Metal detector", "Mining", StrayScreen.Feature.METAL, null, () -> config.metalDetectorSolver, v -> config.metalDetectorSolver = v, true, false));
		mods.add(mod("Robot parts", "Mining", StrayScreen.Feature.NUCLEUS, null, () -> config.nucleusAlertParts, v -> config.nucleusAlertParts = v, true, false));
		mods.add(mod("Divan tools", "Mining", null, null, () -> config.nucleusAlertTools, v -> config.nucleusAlertTools = v, true, false));
		mods.add(mod("Nodes", "Mining", StrayScreen.Feature.NODES, null, () -> config.markersEnabled, v -> config.markersEnabled = v, true, false));
		mods.add(mod("Node ESP", "Mining", StrayScreen.Feature.NODE_ESP, null, () -> config.boxFill, v -> config.boxFill = v, true, false));

		mods.add(mod("Top down", "Farming", StrayScreen.Feature.TOP_DOWN, null, () -> config.topDownView, v -> config.topDownView = v, true, false));
		mods.add(mod("Yaw / Pitch", "Farming", StrayScreen.Feature.FARMING, null, () -> config.farmingYawPitch, v -> config.farmingYawPitch = v, true, false));
		mods.add(mod("Jacob contest", "Farming", null, null, () -> config.jacobContestHudEnabled, v -> config.jacobContestHudEnabled = v, true, false));
		mods.add(mod("Composter", "Farming", null, null, () -> config.composterHudEnabled, v -> config.composterHudEnabled = v, true, false));
		mods.add(mod("Garden plots", "Farming", StrayScreen.Feature.PLOTS, null, () -> config.gardenPlotsWidget, v -> config.gardenPlotsWidget = v, true, false));
		mods.add(mod("Shopping list", "Farming", StrayScreen.Feature.SHOPPING, null, () -> config.gardenShoppingHudEnabled, v -> config.gardenShoppingHudEnabled = v, true, false));
		mods.add(mod("Next contest", "Farming", null, null, () -> config.gardenContestHudEnabled, v -> config.gardenContestHudEnabled = v, true, false));
		mods.add(mod("Visitors", "Farming", null, null, () -> config.gardenVisitorHudEnabled, v -> config.gardenVisitorHudEnabled = v, true, false));
		mods.add(mod("Hoe level", "Farming", null, null, () -> config.gardenHoeHudEnabled, v -> config.gardenHoeHudEnabled = v, true, false));
		mods.add(mod("Crop milestone", "Farming", null, null, () -> config.gardenMilestoneHudEnabled, v -> config.gardenMilestoneHudEnabled = v, true, false));
		mods.add(mod("Crop fever", "Farming", null, null, () -> config.cropFeverHudEnabled, v -> config.cropFeverHudEnabled = v, true, false));
		mods.add(mod("Pest ESP", "Farming", StrayScreen.Feature.PEST, null, () -> config.pestEspEnabled, v -> config.pestEspEnabled = v, true, false));
		mods.add(mod("Pest cooldown", "Farming", StrayScreen.Feature.PEST_COOLDOWN, null, () -> config.pestCooldownHudEnabled, v -> config.pestCooldownHudEnabled = v, true, false));
		mods.add(mod("Lasso", "Farming", null, null, () -> config.lassoDisplay, v -> config.lassoDisplay = v, true, false));
		mods.add(mod("Auto DNA", "Farming", StrayScreen.Feature.AUTO_DNA, null, () -> config.autoDnaEnabled, v -> config.autoDnaEnabled = v, true, false));

		mods.add(mod("Click GUI", "Menus", null, null, () -> config.clickGui, v -> config.clickGui = v, false, false, true));
		mods.add(mod("Experiments", "Menus", StrayScreen.Feature.AUTO_EXPERIMENTS, null, () -> config.autoExperimentsEnabled, v -> config.autoExperimentsEnabled = v, true, false));
		mods.add(mod("Book combine", "Menus", StrayScreen.Feature.AUTO_BOOKS, null, () -> config.autoBookCombineEnabled, v -> config.autoBookCombineEnabled = v, true, false));
		mods.add(mod("Loadouts", "Menus", StrayScreen.Feature.LOADOUTS, null, () -> config.loadoutsMenuEnabled, v -> config.loadoutsMenuEnabled = v, true, false));
		mods.add(mod("Wardrobe", "Menus", StrayScreen.Feature.WARDROBE, null, () -> config.wardrobeMenuEnabled, v -> config.wardrobeMenuEnabled = v, true, false));
		mods.add(mod("Profile viewer", "Menus", null, null, () -> config.profileViewerEnabled, v -> config.profileViewerEnabled = v, true, false));
		mods.add(mod("Storage preview", "Menus", null, null, () -> config.storagePreviewEnabled, v -> config.storagePreviewEnabled = v, true, false));
		mods.add(mod("Themed GUIs", "Menus", null, null, () -> config.themedGuisEnabled, v -> config.themedGuisEnabled = v, true, false));
		mods.add(mod("Themed chat", "Menus", null, null, () -> config.themedChatEnabled, v -> config.themedChatEnabled = v, true, false));
		mods.add(mod("Compact stash", "Menus", null, null, () -> config.stashChatCompact, v -> config.stashChatCompact = v, true, false));
		mods.add(mod("Cleaner NPC", "Menus", null, null, () -> config.npcChatClean, v -> config.npcChatClean = v, true, false));
		mods.add(mod("No cursor reset", "Menus", null, null, () -> config.noCursorReset, v -> config.noCursorReset = v, true, true));
		mods.add(mod("Disabled potions", "Menus", null, null, () -> config.disabledPotionsHighlight, v -> config.disabledPotionsHighlight = v, true, false));
		mods.add(mod("Sack recipe", "Menus", null, null, () -> config.sackRecipe, v -> config.sackRecipe = v, true, false));
		mods.add(mod("Global IRC", "Menus", null, null, () -> config.strayIrcEnabled, v -> config.strayIrcEnabled = v, true, false));
		mods.add(mod("Lobby pings", "Menus", null, null, () -> config.strayPingEnabled, v -> config.strayPingEnabled = v, true, false));
		mods.add(mod("Block marks", "Menus", StrayScreen.Feature.MARKS, null, () -> config.blockMarksEnabled, v -> config.blockMarksEnabled = v, true, false));
		mods.add(mod("Command rings", "Menus", StrayScreen.Feature.RINGS, null, () -> config.commandRingsEnabled, v -> config.commandRingsEnabled = v, true, false));
		mods.add(mod("Paths", "Menus", StrayScreen.Feature.PATHS, null, () -> config.pathsEnabled, v -> config.pathsEnabled = v, true, false));
		mods.add(mod("Movement rings", "Menus", StrayScreen.Feature.MOVE, null, () -> config.movementRingsEnabled, v -> config.movementRingsEnabled = v, true, false));

		mods.add(hold("Accent", "Theme", StrayScreen.Feature.ACCENT));
		mods.add(mod("HUD", "Theme", null, null, () -> true, v -> {
		}, false, false, true));
		mods.add(mod("Auto update", "Theme", null, null, () -> config.autoUpdate, v -> config.autoUpdate = v, false, false));
		mods.add(mod("Auto close", "Theme", null, null, () -> config.updateAutoClose, v -> config.updateAutoClose = v, false, false));
		mods.add(mod("Update notify", "Theme", null, null, () -> config.updateNotify, v -> {
			config.updateNotify = v;
			config.save();
		}, false, false));

		mods.add(mod("Nick hider", "Player", StrayScreen.Feature.NICK, null, () -> config.nickEnabled, v -> config.nickEnabled = v, true, false));
		mods.add(hold("Cape", "Player", StrayScreen.Feature.CAPE, ClickGui::capeOn));
		return mods;
	}

	private static Mod mod(
		String name,
		String column,
		StrayScreen.Feature feature,
		EntityKind kind,
		BooleanSupplier on,
		Consumer<Boolean> set,
		boolean list,
		boolean timeout
	) {
		return mod(name, column, feature, kind, on, set, list, timeout, false);
	}

	private static Mod mod(
		String name,
		String column,
		StrayScreen.Feature feature,
		EntityKind kind,
		BooleanSupplier on,
		Consumer<Boolean> set,
		boolean list,
		boolean timeout,
		boolean menuStyle
	) {
		return new Mod(name, column, feature, kind, on, set, list, timeout, menuStyle, false);
	}

	private static Mod hold(String name, String column, StrayScreen.Feature feature) {
		return hold(name, column, feature, () -> true);
	}

	private static Mod hold(String name, String column, StrayScreen.Feature feature, BooleanSupplier on) {
		return new Mod(name, column, feature, null, on, v -> {
		}, false, false, false, true);
	}

	private static boolean capeOn() {
		StrayConfig config = StrayConfig.get();
		return (config.capeUrl != null && !config.capeUrl.isBlank()) || (config.capePath != null && !config.capePath.isBlank());
	}

	private record Mod(
		String name,
		String column,
		StrayScreen.Feature feature,
		EntityKind kind,
		BooleanSupplier on,
		Consumer<Boolean> set,
		boolean list,
		boolean timeout,
		boolean menuStyle,
		boolean hold
	) {
	}

	private static final class Column {
		final String id;
		float x;
		float y;
		float scroll;
		float height = HEADER;
		boolean closed;

		Column(String id) {
			this.id = id;
		}
	}

	private record Row(Mod mod, float x, float y, float w, float h) {
	}
}
