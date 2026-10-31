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
	static final String[] ORDER = {"World", "Visuals", "Mobs", "Combat", "Slayer", "HUD", "Mining", "Farming", "Menus", "Keys", "Theme", "Player"};
	static final int[] BIND_WHICH = {3, 4, 13, 5, 7, 11, 12, 14, 16, 17};
	static final String[] BIND_LABEL = {
		"Open menu", "Loadouts", "Swap loadouts", "Wardrobe", "Profile", "Chat peek", "Lobby ping", "Empty bag", "Freecam", "World editor"
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
		pointer(mouseX, mouseY);
		if (hypr()) {
			// No array list here: it sat on top of the tiled windows.
			HyprMenu.draw(screen, graphics, font, mouseX, mouseY);
			HyprMenu.drawPicker(screen, graphics, font);
			return;
		}
		if (imgui()) {
			ImGuiMenu.draw(screen, graphics, font, mouseX, mouseY);
			screen.clickPicker(graphics, font);
			if (StrayConfig.get().arrayList) {
				ArrayListHud.draw(graphics, font, screen.width);
			}
			return;
		}
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

	static final TextKeys.Field SEARCH_FIELD = new TextKeys.Field("click-search", () -> searchQuery, v -> searchQuery = v, 32);
	static final TextKeys.Field MOB_SEARCH_FIELD = new TextKeys.Field("click-mob-search", () -> mobQuery, v -> mobQuery = v, 32);



	static boolean mobSearchContains(double x, double y) {
		return contains(x, y, mobSearchX, mobSearchY, mobSearchW, mobSearchH);
	}

	static String mobQuery() {
		return mobQuery;
	}

	static void mobSearchBox(float x, float y, float w, float h) {
		mobSearchX = x;
		mobSearchY = y;
		mobSearchW = w;
		mobSearchH = h;
	}



	static boolean searchContains(double x, double y) {
		return contains(x, y, searchX, searchY, searchW, searchH);
	}

	static void rightClick(double x, double y) {
		if (hypr()) {
			HyprMenu.rightClick(x, y);
			return;
		}
		if (imgui()) {
			ImGuiMenu.closePopups();
			return;
		}
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
					client.gui.setScreen(new CustomTitleScreen(client.gui.screen()));
				}
				return;
			}
			if (row.mod.feature != null || row.mod.timeout || row.mod.menuStyle || "Array list".equals(row.mod.name) || "Themed GUIs".equals(row.mod.name) || "Themed chat".equals(row.mod.name) || CUSTOMIZE.equals(row.mod.name) || CS2.equals(row.mod.name)) {
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
		if (hypr()) {
			return;
		}
		if (imgui()) {
			ImGuiMenu.closePopups();
			return;
		}
		expandedName = null;
	}

	static boolean drag(double x, double y) {
		if (hypr()) {
			return HyprMenu.drag(x, y);
		}
		if (imgui()) {
			return ImGuiMenu.drag(x, y);
		}
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
		if (hypr()) {
			HyprMenu.endDrag();
			return;
		}
		if (imgui()) {
			ImGuiMenu.endDrag();
			return;
		}
		if (dragging == null) {
			return;
		}
		dragging = null;
		saveColumns();
	}

	static boolean scroll(double x, double y, double wheel) {
		if (hypr()) {
			return HyprMenu.scroll(x, y, wheel);
		}
		if (imgui()) {
			return ImGuiMenu.scroll(x, y, wheel);
		}
		if (wheel == 0) {
			return false;
		}
		for (Column column : columns.values()) {
			if (contains(x, y, column.x, column.y, COL_W, column.height)) {
				column.scroll = Math.max(0f, column.scroll - (float) wheel * stride());
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
		int width = Math.min(SEARCH_W, Math.max(box(), screen.width - 16));
		int x = (screen.width - width) / 2;
		int y = screen.height - box() - 8;
		searchX = x;
		searchY = y;
		searchW = width;
		searchH = box();
		outlined(graphics, x, y, width, box(), searchFocused ? accentFill() : OFF_FILL);
		TextKeys.mark(graphics, SEARCH_FIELD.key(), x, y, width, box());
		boolean placeholder = searchQuery.isEmpty() && !searchFocused;
		String shown = placeholder ? "Search" : TextKeys.shown(SEARCH_FIELD.key(), searchQuery, searchFocused);
		String label = imgui() ? fitFeature(font, shown, width - 8) : fit(font, shown, width - 8);
		fieldText(graphics, font, label, x + 4, y, box(), placeholder);
		screen.clickHit(x, y, width, box(), ClickGui::focusSearch);
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
		int rowPad = imgui() ? 7 : lens ? EDGE : H_PAD;
		int bottomPad = imgui() ? 6 : lens ? EDGE : H_PAD;
		int viewTop = top + topPad + head() + gap();
		int visible = Math.max(box(), screen.height - viewTop - bottomPad - box() - 10);
		int shownH = Math.min(Math.round(content), visible);
		column.height = topPad + head() + gap() + shownH + bottomPad;
		float maxScroll = Math.max(0f, content - visible);
		if (reveal) {
			float cursor = 0f;
			for (int i = 0; i < mods.size(); i++) {
				Mod mod = mods.get(i);
				if (mod.name.equals(shownName)) {
					float settingsTop = cursor + box() + gap();
					float full = settingsFull(screen, mod);
					maxScroll = Math.max(maxScroll, cursor + box() + gap() + full - visible);
					if (settingsTop + Math.min(full, 48f) > column.scroll + visible) {
						column.scroll = Math.max(0f, settingsTop + Math.min(full, visible * 0.55f) - visible);
					}
					reveal = false;
					break;
				}
				cursor += box();
				if (i + 1 < mods.size()) {
					cursor += gap();
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
		heading(graphics, font, display(column.id), headerX, headerY, headerW, head());
		screen.clickHit(headerX, headerY, headerW, head(), () -> beginDrag(column.id));

		boolean clipped = GuiDraw.scissor(graphics, lens ? boxX : x, viewTop, lens ? boxW : COL_W, shownH);
		float y = viewTop - column.scroll;
		for (int i = 0; i < mods.size(); i++) {
			Mod mod = mods.get(i);
			boolean shown = y + box() > viewTop && y < viewTop + shownH;
			if (shown) {
				boolean on = mod.on.getAsBoolean();
				int rowY = Math.round(y);
				paintModule(graphics, font, boxX, rowY, boxW, box(), display(mod.name), on);
				if (mod.menuStyle || mod.hold) {
					screen.clickHit(boxX, rowY, boxW, box(), () -> {
					});
				} else {
					screen.clickHit(boxX, rowY, boxW, box(), () -> toggle(mod));
				}
				rows.add(new Row(mod, boxX, rowY, boxW, box()));
			}
			y += box();
			if (mod.name.equals(shownName) && expandT > 0.001f) {
				float full = settingsFull(screen, mod);
				float open = full * expandT;
				y += gap();
				if (open > 1f && y + open > viewTop && y < viewTop + shownH) {
					drawInline(screen, graphics, font, mouseX, mouseY, mod, viewTop, shownH, boxX, y, boxW, open, clipped);
				}
				y += open;
			}
			if (i + 1 < mods.size()) {
				y += gap();
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
		int rowPad = imgui() ? 7 : lens ? EDGE : H_PAD;
		int bottomPad = imgui() ? 6 : lens ? EDGE : H_PAD;
		int searchY = top + topPad + head() + gap();
		int viewTop = searchY + box() + gap();
		int room = Math.max(box(), screen.height - viewTop - bottomPad - box() - 10);
		int visible = Math.min(room, stackH(8));
		float content = stackH(entries.size());
		int shownH = Math.min(Math.round(content), visible);
		column.height = topPad + head() + gap() + box() + gap() + shownH + bottomPad;
		column.scroll = Math.round(Mth.clamp(column.scroll, 0f, Math.max(0f, content - visible)));

		int boxX = x + rowPad;
		int boxW = COL_W - rowPad * 2;
		int headerX = lens ? boxX : x;
		int headerW = lens ? boxW : COL_W;
		int headerY = top + topPad;
		paintColumn(graphics, x, top, column.height, headerX, headerY, headerW);
		heading(graphics, font, display(column.id), headerX, headerY, headerW, head());
		screen.clickHit(headerX, headerY, headerW, head(), () -> beginDrag(column.id));
		mobSearchX = boxX;
		mobSearchY = searchY;
		mobSearchW = boxW;
		mobSearchH = box();
		outlined(graphics, boxX, searchY, boxW, box(), mobSearchFocused ? accentFill() : OFF_FILL);
		TextKeys.mark(graphics, MOB_SEARCH_FIELD.key(), boxX, searchY, boxW, box());
		boolean placeholder = mobQuery.isEmpty() && !mobSearchFocused;
		String shown = placeholder ? "Search" : TextKeys.shown(MOB_SEARCH_FIELD.key(), mobQuery, mobSearchFocused);
		String label = imgui() ? fitFeature(font, shown, boxW - 8) : fit(font, shown, boxW - 8);
		fieldText(graphics, font, label, boxX + 4, searchY, box(), placeholder);
		screen.clickHit(boxX, searchY, boxW, box(), ClickGui::focusMobSearch);

		boolean clipped = shownH > 0 && GuiDraw.scissor(graphics, lens ? boxX : x, viewTop, lens ? boxW : COL_W, shownH);
		StrayConfig config = StrayConfig.get();
		float y = viewTop - column.scroll;
		for (int i = 0; i < entries.size(); i++) {
			MobCatalog.Entry entry = entries.get(i);
			if (y + box() > viewTop && y < viewTop + shownH) {
				boolean on = config.isMobGlowSelected(entry.id().toString());
				int rowY = Math.round(y);
				paintModule(graphics, font, boxX, rowY, boxW, box(), entry.name(), on);
				String id = entry.id().toString();
				screen.clickHit(boxX, rowY, boxW, box(), () -> {
					StrayConfig.get().toggleMobGlow(id);
					UnloadState.markDirty();
				});
			}
			y += box();
			if (i + 1 < entries.size()) {
				y += gap();
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
		} else if ("Themed chat".equals(mod.name)) {
			drawChatStyle(screen, graphics, font, boxX, y, boxW);
		} else if (CUSTOMIZE.equals(mod.name)) {
			drawCustomize(screen, graphics, font, boxX, y, boxW);
		} else if (CS2.equals(mod.name)) {
			drawCs2(screen, graphics, font, boxX, y, boxW);
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
		y += stride();
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
			y += stride();
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
		y = drawDropdown(screen, graphics, font, x, y, w, "click", "Style", StrayConfig.CLICK_GUI_STYLE_LABELS, config.clickGuiStyleIndex(),
			i -> StrayConfig.get().setClickGuiStyle(StrayConfig.CLICK_GUI_STYLES[i]));
		if (config.clickGuiHypr()) {
			drawChoice(screen, graphics, font, x, y, w, "Floating window", config.hyprFloating, () -> {
				StrayConfig current = StrayConfig.get();
				current.hyprFloating = !current.hyprFloating;
				UnloadState.markDirty();
			});
			y += stride();
		}

	}

	private static void drawGuiStyle(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, float x, float y, float w) {
		drawDropdown(screen, graphics, font, x, y, w, "gui", "Style", StrayConfig.GUI_STYLE_LABELS, StrayConfig.get().guiStyleIndex(),
			i -> StrayConfig.get().setGuiStyle(StrayConfig.GUI_STYLES[i]));
	}

	private static void drawHud(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, float x, float y, float w) {
		StrayConfig config = StrayConfig.get();
		drawChoice(screen, graphics, font, x, y, w, "HUD Stars", config.hudStarfield, () -> {
			StrayConfig current = StrayConfig.get();
			current.hudStarfield = !current.hudStarfield;
			UnloadState.markDirty();
		});
		y += stride();
		y = drawDropdown(screen, graphics, font, x, y, w, "hud", "Style", StrayConfig.HUD_STYLE_LABELS, config.hudStyleIndex(),
			i -> StrayConfig.get().setHudStyle(StrayConfig.HUD_STYLES[i]));
		drawChoice(screen, graphics, font, x, y, w, "Accent Outlines", config.accentOutlines, () -> {
			StrayConfig current = StrayConfig.get();
			current.accentOutlines = !current.accentOutlines;
			Theme.refresh();
			UnloadState.markDirty();
		});
		y += stride();
		drawButton(screen, graphics, font, x, y, w, "HUD Editor", () -> Minecraft.getInstance().gui.setScreen(new HudEditorScreen()));
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
		if (imgui()) {
			boolean hover = contains(pointerX, pointerY, ix, iy, iw, box());
			if (hover) {
				GuiDraw.fill(graphics, ix, iy, iw, box(), ImGuiLook.HEADER_HOVERED);
			}
			float mark = 13f;
			ImGuiLook.checkbox(graphics, ix + 2f, iy + (box() - mark) * 0.5f, mark, on, hover);
			int max = Math.max(4, Math.round(iw - 6f - mark - 4f));
			departureLeft(graphics, font, fitFeature(font, label, max), ix + 2f + mark + 4f, iy, box(), ImGuiLook.TEXT);
			screen.clickHit(ix, iy, iw, box(), pick);
			return;
		}
		outlined(graphics, ix, iy, iw, box(), OFF_FILL);
		float sw = SWITCH_W;
		float sh = SWITCH_H;
		float sx = ix + iw - 2f - sw;
		float sy = y + (box() - sh) * 0.5f;
		drawSwitch(graphics, sx, sy, sw, sh, on);
		int max = Math.max(4, Math.round(sx - 2f - (ix + 2f)));
		String shown = fitFeature(font, label, max);
		departureLeft(graphics, font, shown, ix + 2f, iy, box(), TEXT);
		screen.clickHit(ix, iy, iw, box(), pick);
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
		if (imgui()) {
			boolean hover = contains(pointerX, pointerY, ix, iy, iw, box());
			ImGuiLook.button(graphics, ix, iy, iw, box(), hover);
			String shown = fitFeature(font, label, Math.max(4, iw - 8));
			departure(graphics, font, shown, ix, iy, iw, box(), ImGuiLook.TEXT);
			screen.clickHit(ix, iy, iw, box(), pick);
			return;
		}
		outlined(graphics, ix, iy, iw, box(), OFF_FILL);
		String mark = ">";
		float markX = ix + iw - 2f - font.width(featureText(mark));
		departureLeft(graphics, font, mark, markX, iy, box(), DIM);
		int max = Math.max(4, Math.round(markX - 2f - (ix + 2f)));
		String shown = fitFeature(font, label, max);
		departureLeft(graphics, font, shown, ix + 2f, iy, box(), TEXT);
		screen.clickHit(ix, iy, iw, box(), pick);
	}

	/**
	 * Style picker: the Stray menu's dropdown (the one in the watermark
	 * settings). Returns the y where the next row starts.
	 */
	private static float drawDropdown(
		StrayScreen screen,
		GuiGraphicsExtractor graphics,
		Font font,
		float x,
		float y,
		float w,
		String key,
		String label,
		String[] options,
		int selected,
		java.util.function.IntConsumer pick
	) {
		float bottom = screen.clickDropdown(graphics, font, x + NEST, y, w - NEST * 2, key, label, options, selected, pick);
		return bottom + gap();
	}

	static final String CUSTOMIZE = "Customize item";
	static final String CS2 = "CS2 knife";

	/** CS2 knives (local build): pick the knife, finish, seed and wear. */
	private static void drawCs2(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, float x, float y, float w) {
		drawButton(screen, graphics, font, x, y, w, "Choose knife & skin", () -> {
			Minecraft client = Minecraft.getInstance();
			client.gui.setScreen(new Cs2KnifeScreen(client.gui.screen()));
		});
	}

	/** Item customization: open the editor for the held item, or reset it. */
	private static void drawCustomize(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, float x, float y, float w) {
		drawButton(screen, graphics, font, x, y, w, "Edit held item", CustomizeScreen::openForHeld);
		drawButton(screen, graphics, font, x, y + stride(), w, "Reset held item", CustomizeScreen::resetHeld);
	}

	private static void drawChatStyle(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, float x, float y, float w) {
		drawDropdown(screen, graphics, font, x, y, w, "chat", "Style", StrayConfig.CHAT_STYLE_LABELS, StrayConfig.get().chatStyleIndex(),
			i -> StrayConfig.get().setChatStyle(StrayConfig.CHAT_STYLES[i]));
	}

	private static void drawTimeout(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, float x, float y, float w) {
		drawChoice(screen, graphics, font, x, y, w, "Skip leap menus", StrayConfig.get().noCursorResetSkipLeap, () -> {
			StrayConfig.get().noCursorResetSkipLeap = !StrayConfig.get().noCursorResetSkipLeap;
			UnloadState.markDirty();
		});
		y += stride();
		int current = StrayConfig.get().noCursorResetTimeout;
		int[] choices = {50, 100, 150, 300, 500, 1000};
		for (int choice : choices) {
			boolean on = current == choice;
			String label = choice + " ms";
			int ix = Math.round(x) + NEST;
			int iy = Math.round(y);
			int iw = Math.round(w) - NEST * 2;
			if (imgui()) {
				boolean hover = contains(pointerX, pointerY, ix, iy, iw, box());
				ImGuiLook.button(graphics, ix, iy, iw, box(), hover || on, on && hover);
				departure(graphics, font, label, ix, iy, iw, box(), ImGuiLook.TEXT);
				screen.clickHit(ix, iy, iw, box(), () -> {
					StrayConfig.get().noCursorResetTimeout = choice;
					UnloadState.markDirty();
				});
				y += stride();
				continue;
			}
			outlined(graphics, ix, iy, iw, box(), OFF_FILL);
			float textLeft = ix + 4f;
			if (on) {
				GuiDraw.roundedFine(graphics, ix + 3f, iy + 3f, 2.5f, box() - 6f, 1.2f, Theme.ACCENT);
				textLeft = ix + 8f;
			}
			departureLeft(graphics, font, label, textLeft, iy, box(), on ? TEXT : DIM);
			screen.clickHit(ix, iy, iw, box(), () -> {
				StrayConfig.get().noCursorResetTimeout = choice;
				UnloadState.markDirty();
			});
			y += stride();
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
		return contains(x, y, hx, hy, hw, head());
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
		column.height = pad + head() + pad;
		int headerX = lens ? x + EDGE : x;
		int headerW = lens ? COL_W - EDGE * 2 : COL_W;
		int headerY = top + pad;
		paintColumn(graphics, x, top, column.height, headerX, headerY, headerW);
		heading(graphics, font, title, headerX, headerY, headerW, head());
		screen.clickHit(headerX, headerY, headerW, head(), () -> beginDrag(column.id));
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

	static void toggle(Mod mod) {
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
			float band = Math.max(head() + stride() * 4, (screen.height - 8f) / bands);
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
			column.y = Mth.clamp(column.y, 0, Math.max(0, screen.height - head()));
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
		return count * box() + (count - 1) * gap();
	}

	private static float columnContent(StrayScreen screen, List<Mod> mods) {
		float h = 0f;
		for (int i = 0; i < mods.size(); i++) {
			Mod mod = mods.get(i);
			h += box();
			if (mod.name.equals(shownName) && expandT > 0.001f) {
				h += gap() + settingsFull(screen, mod) * expandT;
			}
			if (i + 1 < mods.size()) {
				h += gap();
			}
		}
		return h;
	}

	private static float settingsFull(StrayScreen screen, Mod mod) {
		if ("Array list".equals(mod.name)) {
			return stackH(arrayListRows());
		}
		if ("Themed GUIs".equals(mod.name)) {
			return screen.clickDropdownHeight("gui");
		}
		if ("Themed chat".equals(mod.name)) {
			return screen.clickDropdownHeight("chat");
		}
		if (CUSTOMIZE.equals(mod.name)) {
			return stackH(2);
		}
		if (CS2.equals(mod.name)) {
			return stackH(1);
		}
		if (mod.menuStyle) {
			if ("HUD".equals(mod.name)) {
				return stackH(3) + gap() + screen.clickDropdownHeight("hud");
			}
			if ("Click GUI".equals(mod.name)) {
				return StrayConfig.get().clickGuiHypr()
					? screen.clickDropdownHeight("click") + gap() + stackH(1)
					: screen.clickDropdownHeight("click");
			}
			return stackH(1);
		}
		if (mod.timeout) {
			return stackH(7);
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
		if (hypr()) {
			HyprMenu.m3Switch(graphics, x, y, w, h, on ? 1f : 0f, false, HyprMenu.alpha());
			return;
		}
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
		boolean commandKeys = title || "command keys".contains(needle) || "world editor".contains(needle);
		boolean commands = title || "commands".contains(needle) || "/loadouts".contains(needle) || "/wardrobe".contains(needle) || "/pv".contains(needle);
		if (!title && binds.isEmpty() && !shortcuts && !commands && !commandKeys) {
			column.height = 0f;
			return;
		}
		if (drawIfClosed(screen, graphics, font, column, "Keys")) {
			return;
		}
		StrayConfig config = StrayConfig.get();
		boolean shortcutOn = config.commandShortcutsEnabled;
		boolean keysOn = config.commandKeysEnabled;
		int extra = (shortcuts ? 1 + (shortcutOn ? 2 : 0) : 0) + (commandKeys ? 2 + (keysOn ? 1 : 0) : 0);
		int commandLines = commands ? 5 : 0;
		float commandsH = commandLines == 0 ? 0f : commandLines * (font.lineHeight + 1) + 6f;
		float content = stackH(binds.size() + extra) + (commandsH > 0f ? gap() + commandsH : 0f);
		int x = Math.round(column.x);
		int top = Math.round(column.y);
		boolean lens = glass();
		int topPad = lens ? EDGE : 0;
		int rowPad = imgui() ? 7 : lens ? EDGE : H_PAD;
		int bottomPad = imgui() ? 6 : lens ? EDGE : H_PAD;
		int viewTop = top + topPad + head() + gap();
		int room = Math.max(box(), screen.height - viewTop - bottomPad - box() - 10);
		int shownH = Math.min(Math.round(content), room);
		column.height = topPad + head() + gap() + shownH + bottomPad;
		column.scroll = Math.round(Mth.clamp(column.scroll, 0f, Math.max(0f, content - shownH)));

		int boxX = x + rowPad;
		int boxW = COL_W - rowPad * 2;
		int headerX = lens ? boxX : x;
		int headerW = lens ? boxW : COL_W;
		int headerY = top + topPad;
		paintColumn(graphics, x, top, column.height, headerX, headerY, headerW);
		heading(graphics, font, "Keys", headerX, headerY, headerW, head());
		screen.clickHit(headerX, headerY, headerW, head(), () -> beginDrag(column.id));

		boolean clipped = shownH > 0 && GuiDraw.scissor(graphics, lens ? boxX : x, viewTop, lens ? boxW : COL_W, shownH);
		float y = viewTop - column.scroll;
		for (int index : binds) {
			if (y + box() > viewTop && y < viewTop + shownH) {
				drawBind(screen, graphics, font, boxX, Math.round(y), boxW, BIND_LABEL[index], BIND_WHICH[index]);
			}
			y += stride();
		}
		if (shortcuts) {
			if (y + box() > viewTop && y < viewTop + shownH) {
				drawChoice(screen, graphics, font, boxX, y, boxW, "Shortcuts", shortcutOn, () -> {
					StrayConfig current = StrayConfig.get();
					current.commandShortcutsEnabled = !current.commandShortcutsEnabled;
					CommandShortcuts.sync();
					UnloadState.markDirty();
				});
			}
			y += stride();
			if (shortcutOn) {
				if (y + box() > viewTop && y < viewTop + shownH) {
					drawChoice(screen, graphics, font, boxX, y, boxW, "Pass arguments", config.commandShortcutsPassArgs, () -> {
						StrayConfig current = StrayConfig.get();
						current.commandShortcutsPassArgs = !current.commandShortcutsPassArgs;
						UnloadState.markDirty();
					});
				}
				y += stride();
				if (y + box() > viewTop && y < viewTop + shownH) {
					drawButton(screen, graphics, font, boxX, y, boxW, "Edit", () ->
						Minecraft.getInstance().gui.setScreen(new CommandShortcutScreen(screen))
					);
				}
				y += stride();
			}
		}
		if (commandKeys) {
			if (y + box() > viewTop && y < viewTop + shownH) {
				drawChoice(screen, graphics, font, boxX, y, boxW, "Command keys", keysOn, () -> {
					StrayConfig current = StrayConfig.get();
					current.commandKeysEnabled = !current.commandKeysEnabled;
					UnloadState.markDirty();
				});
			}
			y += stride();
			if (keysOn) {
				if (y + box() > viewTop && y < viewTop + shownH) {
					drawButton(screen, graphics, font, boxX, y, boxW, "Edit command keys", () ->
						Minecraft.getInstance().gui.setScreen(new CommandKeysScreen(screen))
					);
				}
				y += stride();
			}
			if (y + box() > viewTop && y < viewTop + shownH) {
				drawButton(screen, graphics, font, boxX, y, boxW, "Open world editor", () -> {
					Minecraft.getInstance().gui.setScreen(null);
					dev.stray.client.editor.WorldEditor.enable();
				});
			}
			y += stride();
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
				int ink = imgui() ? ImGuiLook.TEXT_DISABLED : DIM;
				GuiDraw.text(graphics, font, featureText(fitFeature(font, line, boxW - 8)), boxX + 4f, textY, 1f, ink, true);
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
		boolean listening = screen.clickBindListening(which);
		if (imgui()) {
			boolean hover = contains(pointerX, pointerY, x, y, w, box());
			ImGuiLook.frame(graphics, x, y, w, box(), hover, listening);
		} else {
			soft(graphics, x, y, w, box(), OFF_FILL);
		}
		String chip = listening ? "..." : OdinClicks.keyLabel(OdinClicks.parseKey(bindKey(which)));
		int chipW = font.width(featureText(chip));
		int labelMax = Math.max(4, w - 8 - chipW);
		int chipInk = imgui() ? ImGuiLook.CHECK : Theme.ACCENT;
		departureLeft(graphics, font, fitFeature(font, label, labelMax), x + 4f, y, box(), imgui() ? ImGuiLook.TEXT : TEXT);
		departureLeft(graphics, font, chip, x + w - 4f - chipW, y, box(), chipInk);
		screen.clickHit(x, y, w, box(), () -> screen.clickListenBind(which));
	}

	static String bindKey(int which) {
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
			case 16 -> config.freecamKey;
			case 17 -> config.editorKey;
			default -> "";
		};
	}

	private static boolean glass() {
		return StrayConfig.get().clickGuiGlass();
	}

	private static boolean imgui() {
		return StrayConfig.get().clickGuiImgui();
	}

	private static boolean hypr() {
		return StrayConfig.get().clickGuiHypr();
	}

	private static int box() {
		return imgui() ? 18 : BOX;
	}

	private static int head() {
		return imgui() ? 18 : HEADER;
	}

	private static int gap() {
		return imgui() ? 4 : V_GAP;
	}

	private static int stride() {
		return box() + gap();
	}

	private static void moduleBox(GuiGraphicsExtractor graphics, int x, int y, int w, int h, boolean enabled) {
		soft(graphics, x, y, w, h, enabled ? accentFill() : OFF_FILL);
	}

	private static void outlined(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int fill) {
		soft(graphics, x, y, w, h, fill);
	}

	private static void soft(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int fill) {
		if (hypr()) {
			// No pill behind rows: just the Material hover layer.
			if (contains(pointerX, pointerY, x, y, w, h)) {
				ClickLook.material(graphics, x, y, w, h, HyprMenu.faded(MaterialPalette.withAlpha(MaterialPalette.onSurface, 0x12)), 0);
			}
			return;
		}
		if (imgui()) {
			boolean hot = contains(pointerX, pointerY, x, y, w, h);
			ImGuiLook.frame(graphics, x, y, w, h, hot, fill == accentFill());
			return;
		}
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
		if (imgui()) {
			ImGuiLook.window(graphics, x, y, COL_W, h);
			ImGuiLook.titleBar(graphics, x, y, COL_W, head());
			return;
		}
		if (glass()) {
			columnBackdrop(graphics, x, y, COL_W, h);
			card(graphics, headerX, headerY, headerW, head(), accentFill());
			return;
		}
		GuiDraw.fill(graphics, x, y, COL_W, h, PANEL);
		flat(graphics, headerX, headerY, headerW, head(), accentFill());
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
		if (hypr()) {
			return MenuFont.hypr(label);
		}
		return Component.literal(label == null ? "" : label).withStyle(FEATURE_FONT);
	}

	private static Component featureText(String label) {
		if (hypr()) {
			return MenuFont.hypr(label);
		}
		if (imgui()) {
			return Component.literal(label == null ? "" : label).withStyle(MenuFont.PROGGY);
		}
		return styled(label);
	}

	private static void heading(GuiGraphicsExtractor graphics, Font font, String label, float x, float y, float w, int h) {
		if (imgui()) {
			String shown = fitFeature(font, label, Math.max(4, Math.round(w) - 16));
			departureLeft(graphics, font, shown, x + 8f, y, h, ImGuiLook.TEXT);
			return;
		}
		departure(graphics, font, label, x, y, w, h, TEXT);
	}

	private static void paintModule(
		GuiGraphicsExtractor graphics,
		Font font,
		int x,
		int y,
		int w,
		int h,
		String label,
		boolean on
	) {
		if (imgui()) {
			boolean hover = contains(pointerX, pointerY, x, y, w, h);
			if (hover) {
				GuiDraw.fill(graphics, x, y, w, h, ImGuiLook.HEADER_HOVERED);
			}
			float mark = 13f;
			ImGuiLook.checkbox(graphics, x + 2f, y + (h - mark) * 0.5f, mark, on, hover);
			int max = Math.max(4, Math.round(w - 8f - mark));
			departureLeft(graphics, font, fitFeature(font, label, max), x + 2f + mark + 4f, y, h, ImGuiLook.TEXT);
			return;
		}
		moduleBox(graphics, x, y, w, h, on);
		String shown = fitFeature(font, label, w - 4);
		departure(graphics, font, shown, x, y, w, h, on ? TEXT : DIM);
	}

	private static void fieldText(
		GuiGraphicsExtractor graphics,
		Font font,
		String label,
		float x,
		float y,
		int h,
		boolean placeholder
	) {
		int color = placeholder
			? (imgui() ? ImGuiLook.TEXT_DISABLED : DIM)
			: (imgui() ? ImGuiLook.TEXT : TEXT);
		if (imgui()) {
			GuiDraw.text(graphics, font, featureText(label), x, textY(font, y, h), color, true);
			return;
		}
		GuiDraw.text(graphics, font, label, x, textY(font, y, h), color, true);
	}

	private static void departure(GuiGraphicsExtractor graphics, Font font, String label, float x, float y, float w, int h, int color) {
		Component text = featureText(label);
		float width = font.width(text);
		if (hypr()) {
			int ink = HyprMenu.faded(color == TEXT ? MaterialPalette.onSurface : color);
			if ((ink >>> 24) >= 8) {
				GuiDraw.text(graphics, font, text, x + (w - width) / 2f, Math.round(y + (h - font.lineHeight) / 2f), 1f, ink, false);
			}
			return;
		}
		GuiDraw.text(graphics, font, text, x + (w - width) / 2f, y + (h - font.lineHeight) / 2f, 1f, color, true);
	}

	private static void departureLeft(GuiGraphicsExtractor graphics, Font font, String label, float x, float y, int h, int color) {
		if (hypr()) {
			int ink = HyprMenu.faded(color == TEXT ? MaterialPalette.onSurface : color == DIM ? MaterialPalette.onSurfaceVariant : color);
			if ((ink >>> 24) >= 8) {
				GuiDraw.text(graphics, font, featureText(label), x, Math.round(y + (h - font.lineHeight) / 2f), 1f, ink, false);
			}
			return;
		}
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

	static String filterText() {
		return searchQuery;
	}

	static void searchBox(float x, float y, float w, float h) {
		searchX = x;
		searchY = y;
		searchW = w;
		searchH = h;
	}

	static double pointerX() {
		return pointerX;
	}

	static double pointerY() {
		return pointerY;
	}

	private static final java.util.Map<String, String> DESCRIPTIONS = java.util.Map.ofEntries(
		java.util.Map.entry("World tint", "Tints the world's lighting in a colour of your choice."),
		java.util.Map.entry("Skybox", "Recolours the sky, or swaps in the End sky."),
		java.util.Map.entry("Ambience", "Sets your own time of day and weather, client side."),
		java.util.Map.entry("Fog", "Custom fog colour and distance."),
		java.util.Map.entry("Aspect", "Stretches the view to another aspect ratio, like 4:3."),
		java.util.Map.entry("Motion blur", "Blurs the screen while you move and turn."),
		java.util.Map.entry("Skull hold", "Holds skulls and heads the 1.8 way."),
		java.util.Map.entry("Backwards walk", "1.8 style body turn when walking backwards."),
		java.util.Map.entry("Player glow", "Outlines other players so you can see them through walls."),
		java.util.Map.entry("Nametags", "Stray's own nametags for players, with distance."),
		java.util.Map.entry("Health bar", "Health bars over players."),
		java.util.Map.entry("2D box", "Flat boxes around players on screen."),
		java.util.Map.entry("Shader", "Fills players with a see-through shader."),
		java.util.Map.entry("Mob glow", "Outlines mobs you pick from the list or by name."),
		java.util.Map.entry("Mob tags", "Stray's own nametags for mobs."),
		java.util.Map.entry("Mob health", "Health bars over mobs."),
		java.util.Map.entry("Mob box", "Flat boxes around mobs on screen."),
		java.util.Map.entry("Mob shader", "Fills mobs with a see-through shader."),
		java.util.Map.entry("Star mobs", "Highlights starred mobs in dungeons."),
		java.util.Map.entry("CS2 knife", "Replaces your sword with a CS2 knife, with skins, inspect and swing animations."),
		java.util.Map.entry("Held item", "A shader on the item in your hand."),
		java.util.Map.entry("Block outline", "A glowing outline on the block you look at."),
		java.util.Map.entry("Fairy souls", "Shows fairy souls you have not collected yet."),
		java.util.Map.entry("Auto clicker", "Clicks for you while you hold the bind."),
		java.util.Map.entry("Party stats", "Shows a stats card when someone joins from the party finder."),
		java.util.Map.entry("Array list", "Lists the modules you have turned on."),
		java.util.Map.entry("Watermark", "The Stray watermark in a corner of the screen."),
		java.util.Map.entry("Music", "What is playing right now, with controls."),
		java.util.Map.entry("Picture in picture", "A small second view of the world."),
		java.util.Map.entry("Raw mats", "Tracks raw materials you gather."),
		java.util.Map.entry("Pickup log", "Shows the items you just picked up."),
		java.util.Map.entry("Skill progress", "Skill XP gains and progress."),
		java.util.Map.entry("Inventory", "Your inventory on the HUD."),
		java.util.Map.entry("Custom title", "Changes the game window's title."),
		java.util.Map.entry("Scoreboard", "Stray's restyled sidebar."),
		java.util.Map.entry("Boss bar", "Stray's restyled boss bar."),
		java.util.Map.entry("Effects", "Your active potion effects."),
		java.util.Map.entry("Item name", "The name of the item you switch to."),
		java.util.Map.entry("Mining HUD", "Mining stats and ability cooldown."),
		java.util.Map.entry("Titanium ESP", "Highlights titanium ore near you."),
		java.util.Map.entry("CH waypoints", "Waypoints for the Crystal Hollows."),
		java.util.Map.entry("Route miner", "Follows a saved mining route."),
		java.util.Map.entry("Powder Mining", "Helps with powder mining chests."),
		java.util.Map.entry("CH map", "A map of the Crystal Hollows."),
		java.util.Map.entry("Scatha timer", "Times Scatha and worm spawns."),
		java.util.Map.entry("Metal detector", "Solves the metal detector in the Mines of Divan."),
		java.util.Map.entry("Robot parts", "Alerts for the Nucleus robot parts."),
		java.util.Map.entry("Divan tools", "Alerts for the Mines of Divan tools."),
		java.util.Map.entry("Nodes", "Marks ore nodes."),
		java.util.Map.entry("Node ESP", "Fills the marked nodes so you can see them through walls."),
		java.util.Map.entry("Top down", "A top-down view of your plot."),
		java.util.Map.entry("Yaw / Pitch", "Shows your yaw and pitch for farming."),
		java.util.Map.entry("Jacob contest", "Your Jacob's contest progress."),
		java.util.Map.entry("Composter", "Composter levels and timers."),
		java.util.Map.entry("Garden plots", "A quick plot overview on the Garden."),
		java.util.Map.entry("Shopping list", "What you need for the Garden visitors."),
		java.util.Map.entry("Next contest", "When the next Jacob's contest starts."),
		java.util.Map.entry("Visitors", "Garden visitors waiting for you."),
		java.util.Map.entry("Hoe level", "Your farming tool's level."),
		java.util.Map.entry("Crop milestone", "Progress to your next crop milestone."),
		java.util.Map.entry("Crop fever", "The Crop Fever timer."),
		java.util.Map.entry("Pest ESP", "Highlights pests on the Garden."),
		java.util.Map.entry("Pest cooldown", "When the next pest can spawn."),
		java.util.Map.entry("Lasso", "Shows the lasso's pull bar and reels in for you."),
		java.util.Map.entry("Auto DNA", "Solves the DNA puzzle."),
		java.util.Map.entry("Click GUI", "Settings for this menu."),
		java.util.Map.entry("Experiments", "Solves the experimentation table."),
		java.util.Map.entry("Book combine", "Combines enchanted books for you."),
		java.util.Map.entry("Loadouts", "A cleaner equipment loadouts menu."),
		java.util.Map.entry("Wardrobe", "A cleaner wardrobe menu."),
		java.util.Map.entry("Profile viewer", "Look at anyone's SkyBlock profile."),
		java.util.Map.entry("Storage preview", "Preview storage pages without opening them."),
		java.util.Map.entry("Themed GUIs", "Stray's look for chest menus."),
		java.util.Map.entry("Themed chat", "Stray's look for chat."),
		java.util.Map.entry("Compact stash", "Collapses stash pickup spam into one line."),
		java.util.Map.entry("Cleaner NPC", "Shortens NPC chat lines."),
		java.util.Map.entry("No cursor reset", "Keeps the cursor where it was between menus."),
		java.util.Map.entry("Disabled potions", "Highlights disabled potion effects."),
		java.util.Map.entry("Sack recipe", "Recipes that pull from your sacks."),
		java.util.Map.entry("Global IRC", "Chat with other Stray users."),
		java.util.Map.entry("Lobby pings", "Ping spots for other Stray users in your lobby."),
		java.util.Map.entry("Block marks", "Mark blocks with colours."),
		java.util.Map.entry("Command rings", "Rings on the ground that run a command when you walk in."),
		java.util.Map.entry("Paths", "Record and show walking paths."),
		java.util.Map.entry("Movement rings", "Movement guide rings (unfinished)."),
		java.util.Map.entry("Accent", "The accent colour of the menus."),
		java.util.Map.entry("HUD", "HUD style and editor."),
		java.util.Map.entry("Auto update", "Updates Stray by itself."),
		java.util.Map.entry("Auto close", "Closes the game after updating."),
		java.util.Map.entry("Update notify", "Tells you when an update is out."),
		java.util.Map.entry("Customize item", "Rename and restyle your items, client side."),
		java.util.Map.entry("Nick hider", "Shows a different name for you, client side."),
		java.util.Map.entry("Cape", "A custom cape."),
		java.util.Map.entry("Skin", "A skin only you see, from a PNG on your computer."),
		java.util.Map.entry("Slayer HUD", "Your slayer quest, boss health, timer and phase."),
		java.util.Map.entry("Slayer profit", "What your slayer drops are worth, per boss and per hour."),
		java.util.Map.entry("RNG meter", "Your slayer RNG meter and how many bosses are left."),
		java.util.Map.entry("Remaining kills", "How many mobs are left until your boss spawns."),
		java.util.Map.entry("Slayer alerts", "Boss soon, wrong quest, cocoon, kill times and personal bests."),
		java.util.Map.entry("Slayer ESP", "Highlights your boss, mini-bosses, egg sacs and valuable drops."),
		java.util.Map.entry("Voidgloom", "Beacons, Nukekubi skulls and particle hiding for Voidgloom Seraph."),
		java.util.Map.entry("Inferno", "Hellion shields, daggers, fire pits and fire pillars for Inferno Demonlord."),
		java.util.Map.entry("Bloodfiend", "Boss highlights, steak and twinclaws alerts, ichors and springs."),
		java.util.Map.entry("Slayer cleanup", "Hides clutter while you slay.")
	);

	/** One line on what a module does, shown under its settings. */
	static String description(Mod mod) {
		return mod == null ? "" : DESCRIPTIONS.getOrDefault(mod.name, "");
	}

	static boolean moduleHasSettings(Mod mod) {
		return mod.feature != null
			|| mod.timeout
			|| mod.menuStyle
			|| "Array list".equals(mod.name)
			|| "Themed GUIs".equals(mod.name)
			|| "Themed chat".equals(mod.name)
			|| CUSTOMIZE.equals(mod.name)
			|| CS2.equals(mod.name)
			|| "Custom title".equals(mod.name);
	}

	static float moduleSettingsHeight(StrayScreen screen, Mod mod) {
		if ("Custom title".equals(mod.name)) {
			return stride();
		}
		return settingsFull(screen, mod);
	}

	static void drawModuleSettings(
		StrayScreen screen,
		GuiGraphicsExtractor graphics,
		Font font,
		int mouseX,
		int mouseY,
		Mod mod,
		float x,
		float y,
		float w
	) {
		if ("Custom title".equals(mod.name)) {
			drawButton(screen, graphics, font, x, y, w, "Edit title", () -> {
				Minecraft client = Minecraft.getInstance();
				if (client != null) {
					client.gui.setScreen(new CustomTitleScreen(client.gui.screen()));
				}
			});
			return;
		}
		if ("Array list".equals(mod.name)) {
			drawArrayList(screen, graphics, font, x, y, w);
			return;
		}
		if ("Themed GUIs".equals(mod.name)) {
			drawGuiStyle(screen, graphics, font, x, y, w);
			return;
		}
		if ("Themed chat".equals(mod.name)) {
			drawChatStyle(screen, graphics, font, x, y, w);
			return;
		}
		if (CUSTOMIZE.equals(mod.name)) {
			drawCustomize(screen, graphics, font, x, y, w);
			return;
		}
		if (CS2.equals(mod.name)) {
			drawCs2(screen, graphics, font, x, y, w);
			return;
		}
		if (mod.menuStyle) {
			if ("HUD".equals(mod.name)) {
				drawHud(screen, graphics, font, x, y, w);
			} else {
				drawMenuStyle(screen, graphics, font, x, y, w);
			}
			return;
		}
		if (mod.timeout) {
			drawTimeout(screen, graphics, font, x, y, w);
			return;
		}
		if (mod.feature != null) {
			screen.clickVisuals(mod.kind);
			screen.clickSettings(graphics, font, mouseX, mouseY, x, y, w, mod.feature);
		}
	}

	static List<Mod> modules() {
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
		mods.add(mod(CS2, "Visuals", null, null, () -> config.cs2KnifeEnabled, v -> config.cs2KnifeEnabled = v, true, false));
		mods.add(mod("Held item", "Visuals", StrayScreen.Feature.HELD_ITEM, null, () -> config.heldItemShaderEnabled, v -> config.heldItemShaderEnabled = v, true, false));
		mods.add(mod("Block outline", "Visuals", StrayScreen.Feature.BLOCK, null, () -> config.blockOutlineGlow, v -> config.blockOutlineGlow = v, true, false));
		mods.add(mod("Fairy souls", "Visuals", StrayScreen.Feature.FAIRY, null, () -> config.fairySoulEsp, v -> config.fairySoulEsp = v, true, false));

		mods.add(mod("Auto clicker", "Combat", StrayScreen.Feature.AUTO_CLICKER, null, () -> config.autoClickerEnabled, v -> config.autoClickerEnabled = v, true, false));
		mods.add(mod("Party stats", "Combat", null, null, () -> config.partyFinderStats, v -> config.partyFinderStats = v, true, false));
		addSlayer(mods, config);
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

		// Settings only: the click GUI is always the menu now.
		mods.add(new Mod("Click GUI", "Menus", null, null, () -> true, v -> { }, false, false, true, true));
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

		mods.add(new Mod(CUSTOMIZE, "Player", null, null, () -> true, v -> { }, false, false, false, true));
		mods.add(mod("Nick hider", "Player", StrayScreen.Feature.NICK, null, () -> config.nickEnabled, v -> config.nickEnabled = v, true, false));
		mods.add(hold("Cape", "Player", StrayScreen.Feature.CAPE, ClickGui::capeOn));
		mods.add(mod("Skin", "Player", StrayScreen.Feature.SKIN, null, () -> config.skinEnabled, v -> config.skinEnabled = v, true, false));
		return mods;
	}

	private static void addSlayer(List<Mod> mods, StrayConfig config) {
		var s = config.slayer;
		mods.add(mod("Slayer HUD", "Slayer", StrayScreen.Feature.SLAYER_HUD, null, () -> s.hud, v -> s.hud = v, true, false));
		mods.add(mod("Slayer profit", "Slayer", StrayScreen.Feature.SLAYER_PROFIT, null, () -> s.profit, v -> s.profit = v, true, false));
		mods.add(mod("RNG meter", "Slayer", StrayScreen.Feature.SLAYER_RNG, null, () -> s.rng, v -> s.rng = v, true, false));
		mods.add(mod("Remaining kills", "Slayer", StrayScreen.Feature.SLAYER_KILLS, null, () -> s.kills, v -> s.kills = v, true, false));
		mods.add(mod("Slayer alerts", "Slayer", StrayScreen.Feature.SLAYER_ALERTS, null, () -> s.alerts, v -> s.alerts = v, true, false));
		mods.add(mod("Slayer ESP", "Slayer", StrayScreen.Feature.SLAYER_ESP, null, () -> s.esp, v -> s.esp = v, true, false));
		mods.add(mod("Voidgloom", "Slayer", StrayScreen.Feature.SLAYER_VOID, null, () -> s.voidgloom, v -> s.voidgloom = v, true, false));
		mods.add(mod("Inferno", "Slayer", StrayScreen.Feature.SLAYER_INFERNO, null, () -> s.inferno, v -> s.inferno = v, true, false));
		mods.add(mod("Bloodfiend", "Slayer", StrayScreen.Feature.SLAYER_VAMPIRE, null, () -> s.bloodfiend, v -> s.bloodfiend = v, true, false));
		mods.add(mod("Slayer cleanup", "Slayer", StrayScreen.Feature.SLAYER_CLEANUP, null, () -> s.cleanup, v -> s.cleanup = v, true, false));
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

	record Mod(
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
