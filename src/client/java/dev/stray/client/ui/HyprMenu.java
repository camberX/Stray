package dev.stray.client.ui;

import dev.stray.client.combat.OdinClicks;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.config.UnloadState;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.GuiFrostBlur;
import dev.stray.client.render.MobCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Hyprland click GUI in the style of the illogical-impulse dotfiles: a
 * quickshell-like bar where categories are workspaces, and two tiled Material
 * You windows (modules, settings) over the blurred game. The window under the
 * mouse gets the focused border, like focus-follows-mouse. Colors come from
 * {@link MaterialPalette}; motion uses ii's own bezier curves.
 * Left click toggles a module, right click shows its settings on the right.
 */
public final class HyprMenu {
	private static final float OUT = 7f;
	private static final float GAP = 5f;
	private static final float BAR = 22f;
	/** ii rounds windows at 18px; at GUI scale 2 that is 9. */
	private static final float RADIUS = 9f;
	private static final float ROW = 18f;
	private static final float ROW_GAP = 2f;
	private static final float PAD = 9f;
	private static final int DIM = 0x40000000;

	/** Material Icons codepoints, the set quickshell's bar uses. */
	private static final Map<String, String> GLYPHS = Map.ofEntries(
		Map.entry("World", "\uE80B"),
		Map.entry("Visuals", "\uE8F4"),
		Map.entry("Mobs", "\uE91D"),
		Map.entry("Combat", "\uF889"),
		Map.entry("HUD", "\uE871"),
		Map.entry("Mining", "\uEAD5"),
		Map.entry("Farming", "\uEA79"),
		Map.entry("Menus", "\uE5C3"),
		Map.entry("Keys", "\uE312"),
		Map.entry("Theme", "\uE40A"),
		Map.entry("Player", "\uE7FD")
	);
	private static final String SEARCH = "\uE8B6";
	private static final String CLOSE = "\uE5CD";
	private static final String TUNE = "\uE429";

	// ii curves (hyprland/general.lua)
	private static final float[] EMPHASIZED_DECEL = {0.05f, 0.7f, 0.1f, 1f};
	private static final float[] EXPRESSIVE_SPATIAL = {0.38f, 1.21f, 0.22f, 1f};

	/** True while this menu draws, so shared widgets can switch to the Material look. */
	private static boolean drawing;
	private static Object lastScreen;
	private static long lastNs;
	private static float dt;
	private static float openT;

	private static String category = ClickGui.ORDER[0];
	private static float catT = 1f;
	private static float pillX = -1f;
	private static float pillW;
	private static float pillFromX;
	private static float pillFromW;
	private static float pillToX = -1f;
	private static float pillToW;
	private static float pillT = 1f;
	private static String selected;
	private static float selT = 1f;
	private static boolean focusList;
	private static float focusListT;
	private static float focusSetT = 1f;
	private static float listScroll;
	private static float setScroll;

	private static final Map<String, Float> hover = new HashMap<>();
	private static final Map<String, Float> knob = new HashMap<>();
	private static final List<RowHit> rowHits = new ArrayList<>();

	private static float listX;
	private static float listY;
	private static float listW;
	private static float listH;
	private static float setX;
	private static float setY;
	private static float setW;
	private static float setH;

	private HyprMenu() {
	}

	/** Whether the Hyprland click GUI is drawing right now. */
	public static boolean drawing() {
		return drawing;
	}

	static void draw(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		drawing = true;
		try {
			drawFrame(screen, graphics, font, mouseX, mouseY);
		} finally {
			drawing = false;
		}
	}

	private static void drawFrame(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		tick(screen);
		MaterialPalette.refresh();
		float fade = Mth.clamp(screen.clickAppear(), 0f, 1f);
		float tBar = curve(EMPHASIZED_DECEL, stagger(openT, 0f)) * fade;
		float tList = curve(EMPHASIZED_DECEL, stagger(openT, 0.10f)) * fade;
		float tSet = curve(EMPHASIZED_DECEL, stagger(openT, 0.20f)) * fade;

		GuiDraw.fill(graphics, 0, 0, screen.width, screen.height, Anim.fade(DIM, fade));

		float w = screen.width - OUT * 2f;
		float barY = OUT - (1f - tBar) * 14f;
		drawBar(screen, graphics, font, mouseX, mouseY, OUT, barY, w, tBar);

		float top = OUT + BAR + GAP;
		float h = Math.max(60f, screen.height - top - OUT);
		float split = Math.round((w - GAP) * 0.55f);
		listX = OUT - (1f - tList) * 22f;
		listY = top;
		listW = split;
		listH = h;
		setX = OUT + split + GAP + (1f - tSet) * 22f;
		setY = top;
		setW = w - split - GAP;
		setH = h;

		if (contains(mouseX, mouseY, listX, listY, listW, listH)) {
			focusList = true;
		} else if (contains(mouseX, mouseY, setX, setY, setW, setH)) {
			focusList = false;
		}
		focusListT = approach(focusListT, focusList ? 1f : 0f, 14f);
		focusSetT = approach(focusSetT, focusList ? 0f : 1f, 14f);

		List<Item> items = items(screen);
		keepSelection(items);
		rowHits.clear();
		drawList(screen, graphics, font, mouseX, mouseY, items, tList);
		drawSettings(screen, graphics, font, mouseX, mouseY, tSet);
	}

	static boolean scroll(double x, double y, double wheel) {
		if (wheel == 0) {
			return false;
		}
		float step = (float) wheel * (ROW + ROW_GAP) * 2f;
		if (contains(x, y, listX, listY, listW, listH)) {
			listScroll = Math.max(0f, listScroll - step);
			return true;
		}
		if (contains(x, y, setX, setY, setW, setH)) {
			setScroll = Math.max(0f, setScroll - step);
			return true;
		}
		return false;
	}

	static void rightClick(double x, double y) {
		for (RowHit hit : rowHits) {
			if (hit.item.mod != null && contains(x, y, hit.x, hit.y, hit.w, hit.h)) {
				select(hit.item.mod.name());
				return;
			}
		}
	}

	// ---------------------------------------------------------------- frame

	private static void tick(StrayScreen screen) {
		long now = System.nanoTime();
		dt = lastNs == 0L ? 0.016f : Math.min(0.05f, (now - lastNs) / 1_000_000_000f);
		lastNs = now;
		if (screen != lastScreen) {
			lastScreen = screen;
			openT = 0f;
			catT = 1f;
			selT = 1f;
			pillX = -1f;
			pillToX = -1f;
			pillT = 1f;
			hover.clear();
			knob.clear();
		}
		openT = Math.min(1f, openT + dt * 2.2f);
		pillT = Math.min(1f, pillT + dt * 3.2f);
		catT = approach(catT, 1f, 9f);
		selT = approach(selT, 1f, 11f);
	}

	private static void pickCategory(String id) {
		if (id.equals(category)) {
			return;
		}
		category = id;
		catT = 0f;
		listScroll = 0f;
	}

	private static void select(String name) {
		if (name == null || name.equals(selected)) {
			return;
		}
		selected = name;
		selT = 0f;
		setScroll = 0f;
	}

	/** Keep a module from this category in the settings window when possible. */
	private static void keepSelection(List<Item> items) {
		for (Item item : items) {
			if (item.mod != null && item.mod.name().equals(selected)) {
				return;
			}
		}
		for (Item item : items) {
			if (item.mod != null) {
				select(item.mod.name());
				return;
			}
		}
	}

	// ------------------------------------------------------------------ bar

	private static void drawBar(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float x, float y, float w, float t) {
		if (t <= 0.01f) {
			return;
		}
		GuiFrostBlur.blitWindow(graphics, x, y, w, BAR, RADIUS, t);
		GuiDraw.roundedFine(graphics, x, y, w, BAR, RADIUS, Anim.fade(withAlpha(MaterialPalette.surface, 0xD8), t));

		float groupY = y + 3f;
		float groupH = BAR - 6f;
		float half = groupH * 0.5f;

		// left: logo group
		float cursor = x + 4f;
		float logoW = 12f + 4f + width(font, "Stray") + 9f;
		GuiDraw.roundedFine(graphics, cursor, groupY, logoW, groupH, half, Anim.fade(MaterialPalette.surfaceLow, t));
		glyph(graphics, font, TUNE, cursor + 6f, groupY, groupH, Anim.fade(MaterialPalette.primary, t));
		text(graphics, font, "Stray", cursor + 19f, groupY, groupH, Anim.fade(MaterialPalette.onSurface, t));
		cursor += logoW + 6f;

		// right: search and close
		float closeW = groupH;
		float closeX = x + w - 4f - closeW;
		float searchW = Math.min(130f, Math.max(76f, w * 0.18f));
		float searchX = closeX - 5f - searchW;

		// middle: workspaces
		String[] ids = ClickGui.ORDER;
		float room = searchX - 6f - cursor - 6f;
		float full = 6f;
		for (String id : ids) {
			full += tabWidth(font, id, true) + 2f;
		}
		boolean labels = full <= room;
		float[] tabX = new float[ids.length];
		float[] tabW = new float[ids.length];
		float groupW = 4f;
		for (int i = 0; i < ids.length; i++) {
			tabW[i] = tabWidth(font, ids[i], labels || ids[i].equals(category));
			groupW += tabW[i] + 2f;
		}
		groupW += 2f;
		float groupX = Math.max(cursor, x + (w - groupW) * 0.5f);
		if (groupX + groupW > searchX - 6f) {
			groupX = cursor;
		}
		GuiDraw.roundedFine(graphics, groupX, groupY, groupW, groupH, half, Anim.fade(MaterialPalette.surfaceLow, t));
		float tx = groupX + 3f;
		for (int i = 0; i < ids.length; i++) {
			tabX[i] = tx;
			tx += tabW[i] + 2f;
		}
		float tabY = groupY + 2f;
		float tabH = groupH - 4f;
		for (int i = 0; i < ids.length; i++) {
			if (!ids[i].equals(category)) {
				continue;
			}
			if (pillToX < 0f) {
				pillFromX = pillToX = pillX = tabX[i];
				pillFromW = pillToW = pillW = tabW[i];
				pillT = 1f;
			} else if (Math.abs(pillToX - tabX[i]) > 0.1f || Math.abs(pillToW - tabW[i]) > 0.1f) {
				pillFromX = pillX;
				pillFromW = pillW;
				pillToX = tabX[i];
				pillToW = tabW[i];
				pillT = 0f;
			}
			float k = curve(EXPRESSIVE_SPATIAL, pillT);
			pillX = pillFromX + (pillToX - pillFromX) * k;
			pillW = Math.max(tabH, pillFromW + (pillToW - pillFromW) * k);
		}
		for (int i = 0; i < ids.length; i++) {
			if (!ids[i].equals(category) && contains(mouseX, mouseY, tabX[i], tabY, tabW[i], tabH)) {
				GuiDraw.roundedFine(graphics, tabX[i], tabY, tabW[i], tabH, tabH * 0.5f, Anim.fade(withAlpha(MaterialPalette.onSurface, 0x14), t));
			}
		}
		if (pillW > 0f) {
			GuiDraw.roundedFine(graphics, pillX, tabY, pillW, tabH, tabH * 0.5f, Anim.fade(MaterialPalette.primary, t));
		}
		for (int i = 0; i < ids.length; i++) {
			String id = ids[i];
			boolean active = id.equals(category);
			float mid = tabX[i] + tabW[i] * 0.5f;
			boolean underPill = mid > pillX && mid < pillX + pillW;
			int ink = underPill ? MaterialPalette.onPrimary : MaterialPalette.onSurfaceVariant;
			float ix = tabX[i] + 5f;
			glyph(graphics, font, GLYPHS.getOrDefault(id, TUNE), ix, tabY, tabH, Anim.fade(ink, t));
			if (labels || active) {
				text(graphics, font, label(id), ix + 13f, tabY, tabH, Anim.fade(ink, t));
			}
			screen.clickHit(tabX[i], tabY, tabW[i], tabH, () -> pickCategory(id));
		}

		boolean focused = ClickGui.searchFocused();
		GuiDraw.roundedFine(graphics, searchX, groupY, searchW, groupH, half, Anim.fade(focused ? MaterialPalette.surfaceHigh : MaterialPalette.surfaceLow, t));
		if (focused) {
			GuiDraw.roundedOutline(graphics, searchX, groupY, searchW, groupH, half, Anim.fade(MaterialPalette.primary, t), 1f);
		}
		ClickGui.searchBox(searchX, groupY, searchW, groupH);
		String query = ClickGui.filterText();
		boolean empty = query.isEmpty() && !focused;
		glyph(graphics, font, SEARCH, searchX + 6f, groupY, groupH, Anim.fade(MaterialPalette.onSurfaceVariant, t));
		String shown = empty ? "Search" : query + (focused && (System.currentTimeMillis() / 500L) % 2L == 0L ? "|" : "");
		text(graphics, font, fit(font, shown, searchW - 24f), searchX + 19f, groupY, groupH, Anim.fade(empty ? MaterialPalette.onSurfaceVariant : MaterialPalette.onSurface, t));
		screen.clickHit(searchX, groupY, searchW, groupH, ClickGui::focusSearch);

		boolean closeHot = contains(mouseX, mouseY, closeX, groupY, closeW, groupH);
		GuiDraw.roundedFine(graphics, closeX, groupY, closeW, groupH, half, Anim.fade(closeHot ? MaterialPalette.surfaceHigh : MaterialPalette.surfaceLow, t));
		glyph(graphics, font, CLOSE, closeX + (closeW - 9f) * 0.5f, groupY, groupH, Anim.fade(closeHot ? MaterialPalette.onSurface : MaterialPalette.onSurfaceVariant, t));
		screen.clickHit(closeX, groupY, closeW, groupH, screen::onClose);
	}

	private static float tabWidth(Font font, String id, boolean withLabel) {
		return withLabel ? 5f + 9f + 4f + width(font, label(id)) + 7f : 19f;
	}

	// --------------------------------------------------------------- windows

	private static void window(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float focus, float t) {
		GuiFrostBlur.blitWindow(graphics, x, y, w, h, RADIUS, t);
		GuiDraw.roundedFine(graphics, x, y, w, h, RADIUS, Anim.fade(withAlpha(MaterialPalette.surfaceLow, 0xE6), t));
		// ii: active border is primary, inactive is primary at a third (colors.lua)
		int border = Anim.mix(withAlpha(MaterialPalette.primary, 0x40), MaterialPalette.primary, focus);
		GuiDraw.roundedOutline(graphics, x, y, w, h, RADIUS, Anim.fade(border, t), 1f);
	}

	private static void drawList(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, List<Item> items, float t) {
		if (t <= 0.01f) {
			return;
		}
		window(graphics, listX, listY, listW, listH, focusListT, t);
		float x = listX + PAD;
		float y = listY + PAD;
		float w = listW - PAD * 2f;
		boolean searching = !ClickGui.filterText().isBlank();
		String title = searching ? "Search" : ClickGui.display(category);
		title(graphics, font, title, x, y, Anim.fade(MaterialPalette.onSurface, t));
		int on = 0;
		int toggles = 0;
		for (Item item : items) {
			if (item.on != null) {
				toggles++;
				if (item.on) {
					on++;
				}
			}
		}
		String count = toggles > 0 ? on + " of " + toggles + " on" : items.size() + " items";
		float cw = width(font, count) + 12f;
		GuiDraw.roundedFine(graphics, x + w - cw, y, cw, 12f, 6f, Anim.fade(MaterialPalette.secondaryContainer, t));
		text(graphics, font, count, x + w - cw + 6f, y, 12f, Anim.fade(MaterialPalette.onSecondaryContainer, t));

		float bodyY = y + 20f;
		float bodyH = listY + listH - PAD - bodyY;
		int cols = w >= 250f ? 2 : 1;
		float colGap = 6f;
		float cellW = (w - colGap * (cols - 1)) / cols;
		int rows = (items.size() + cols - 1) / cols;
		float content = rows * (ROW + ROW_GAP) - ROW_GAP;
		listScroll = Mth.clamp(listScroll, 0f, Math.max(0f, content - bodyH));

		if (items.isEmpty()) {
			text(graphics, font, searching ? "No matches" : "Nothing here", x, bodyY, ROW, Anim.fade(MaterialPalette.onSurfaceVariant, t));
			return;
		}
		boolean clip = GuiDraw.scissor(graphics, listX + 2f, bodyY, listW - 4f, bodyH);
		int mark = screen.clickHitMark();
		for (int i = 0; i < items.size(); i++) {
			int col = i % cols;
			int row = i / cols;
			float rowT = curve(EMPHASIZED_DECEL, Mth.clamp(catT * 2.0f - row * 0.07f, 0f, 1f)) * t;
			float rx = x + col * (cellW + colGap);
			float ry = bodyY + row * (ROW + ROW_GAP) - listScroll + (1f - rowT) * 8f;
			if (ry + ROW < bodyY || ry > bodyY + bodyH) {
				continue;
			}
			drawRow(screen, graphics, font, mouseX, mouseY, items.get(i), rx, ry, cellW, rowT, bodyY, bodyH);
		}
		screen.clickClipHits(mark, listX, bodyY, listW, bodyH);
		if (clip) {
			GuiDraw.disableScissor(graphics);
		}
		if (content > bodyH) {
			float thumb = Math.max(14f, bodyH * bodyH / content);
			float ty = bodyY + (bodyH - thumb) * (listScroll / Math.max(1f, content - bodyH));
			GuiDraw.roundedFine(graphics, listX + listW - 4.5f, ty, 2.5f, thumb, 1.25f, Anim.fade(MaterialPalette.outlineVariant, t));
		}
	}

	private static void drawRow(
		StrayScreen screen,
		GuiGraphicsExtractor graphics,
		Font font,
		int mouseX,
		int mouseY,
		Item item,
		float x,
		float y,
		float w,
		float t,
		float clipY,
		float clipH
	) {
		boolean inside = mouseY >= clipY && mouseY < clipY + clipH;
		boolean hot = inside && contains(mouseX, mouseY, x, y, w, ROW);
		boolean chosen = item.mod != null && item.mod.name().equals(selected);
		float hv = approachIn(hover, item.key, hot ? 1f : 0f, 16f);
		float r = ROW * 0.5f;
		if (chosen) {
			GuiDraw.roundedFine(graphics, x, y, w, ROW, r, Anim.fade(MaterialPalette.secondaryContainer, t));
		}
		if (hv > 0.01f) {
			// Material state layer: on-surface at 8% on hover
			GuiDraw.roundedFine(graphics, x, y, w, ROW, r, Anim.fade(withAlpha(MaterialPalette.onSurface, 0x14), hv * t));
		}
		float textX = x + 8f;
		float right = x + w - 6f;
		if (item.on != null) {
			float k = approachIn(knob, item.key, item.on ? 1f : 0f, 16f);
			drawSwitch(graphics, x + w - 6f - 20f, y + (ROW - 11f) * 0.5f, k, hot, t);
			right -= 24f;
		}
		if (item.chip != null && !item.chip.isEmpty()) {
			float cw = width(font, item.chip) + 10f;
			float cx = right - cw;
			GuiDraw.roundedFine(graphics, cx, y + 3f, cw, ROW - 6f, (ROW - 6f) * 0.5f, Anim.fade(MaterialPalette.surfaceHighest, t));
			text(graphics, font, item.chip, cx + 5f, y + 3f, ROW - 6f, Anim.fade(MaterialPalette.onSurfaceVariant, t));
			right = cx - 4f;
		}
		boolean lit = item.on == null || item.on;
		int ink = chosen ? MaterialPalette.onSecondaryContainer : (lit ? MaterialPalette.onSurface : MaterialPalette.onSurfaceVariant);
		text(graphics, font, fit(font, item.label, Math.max(8f, right - textX)), textX, y, ROW, Anim.fade(ink, t));
		rowHits.add(new RowHit(item, x, y, w, ROW));
		Runnable click = item.left != null ? item.left : (item.mod != null ? () -> select(item.mod.name()) : null);
		if (click != null) {
			screen.clickHit(x, y, w, ROW, click);
		}
	}

	private static void drawSwitch(GuiGraphicsExtractor graphics, float x, float y, float k, boolean hot, float t) {
		m3Switch(graphics, x, y, 20f, 11f, k, hot, t);
	}

	/**
	 * Material 3 switch. Off: outlined track, small outline-colored thumb.
	 * On: primary track, bigger on-primary thumb that grows as it slides.
	 * The outline is a filled pill under an inset pill, not a stroked ring,
	 * so its edge stays smooth at small sizes.
	 */
	public static void m3Switch(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float k, boolean hot, float t) {
		MaterialPalette.refresh();
		float half = h * 0.5f;
		int edge = Anim.mix(MaterialPalette.outline, MaterialPalette.primary, k);
		GuiDraw.roundedFine(graphics, x, y, w, h, half, Anim.fade(edge, t));
		float inset = 1f - k;
		if (inset > 0.02f) {
			float ih = h - inset * 2f;
			int inner = Anim.mix(MaterialPalette.surfaceHighest, MaterialPalette.primary, k);
			GuiDraw.roundedFine(graphics, x + inset, y + inset, w - inset * 2f, ih, ih * 0.5f, Anim.fade(inner, t));
		}
		float radius = h * (0.22f + 0.14f * k) + (hot ? 0.35f : 0f);
		float cx = x + half + k * (w - h);
		int thumb = Anim.mix(MaterialPalette.outline, MaterialPalette.onPrimary, k);
		GuiDraw.circle(graphics, cx, y + half, radius, Anim.fade(thumb, t));
	}

	private static void drawSettings(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float t) {
		if (t <= 0.01f) {
			return;
		}
		window(graphics, setX, setY, setW, setH, focusSetT, t);
		ClickGui.Mod mod = find(selected);
		float x = setX + PAD + 2f;
		float y = setY + PAD;
		float w = setW - PAD * 2f - 4f;
		float st = curve(EMPHASIZED_DECEL, selT) * t;
		float lift = (1f - curve(EMPHASIZED_DECEL, selT)) * 8f;
		if (mod == null) {
			text(graphics, font, "Right click a module", x, y, 12f, Anim.fade(MaterialPalette.onSurfaceVariant, t));
			return;
		}
		text(graphics, font, fit(font, ClickGui.display(mod.column()), w), x, y + lift, 10f, Anim.fade(MaterialPalette.primary, st));
		title(graphics, font, fit(font, ClickGui.display(mod.name()), w), x, y + 12f + lift, Anim.fade(MaterialPalette.onSurface, st));
		float lineY = y + 30f;
		GuiDraw.fill(graphics, x, lineY, w, 1f, Anim.fade(MaterialPalette.outlineVariant, t));

		float bodyY = lineY + 6f;
		float bodyH = setY + setH - PAD - bodyY;
		if (!ClickGui.moduleHasSettings(mod)) {
			text(graphics, font, "No settings for this one", x, bodyY, ROW, Anim.fade(MaterialPalette.onSurfaceVariant, st));
			return;
		}
		float content = ClickGui.moduleSettingsHeight(screen, mod);
		setScroll = Mth.clamp(setScroll, 0f, Math.max(0f, content - bodyH));
		boolean clip = GuiDraw.scissor(graphics, setX + 2f, bodyY, setW - 4f, bodyH);
		int mark = screen.clickHitMark();
		int[] saved = swapTheme();
		try {
			ClickGui.drawModuleSettings(screen, graphics, font, mouseX, mouseY, mod, x - 3f, bodyY - setScroll + lift, w + 6f);
		} finally {
			restoreTheme(saved);
		}
		screen.clickClipHits(mark, setX, bodyY, setW, bodyH);
		if (clip) {
			GuiDraw.disableScissor(graphics);
		}
	}

	// ----------------------------------------------------------------- items

	private static List<Item> items(StrayScreen screen) {
		String query = ClickGui.filterText().trim().toLowerCase(Locale.ROOT);
		List<Item> out = new ArrayList<>();
		if (!query.isEmpty()) {
			for (ClickGui.Mod mod : ClickGui.modules()) {
				String name = ClickGui.display(mod.name());
				if (name.toLowerCase(Locale.ROOT).contains(query)) {
					out.add(modItem(mod, label(mod.column())));
				}
			}
			return out;
		}
		if ("Mobs".equals(category)) {
			StrayConfig config = StrayConfig.get();
			for (MobCatalog.Entry entry : MobCatalog.filtered("")) {
				String id = entry.id().toString();
				out.add(new Item("mob:" + id, entry.name(), config.isMobGlowSelected(id), null, () -> {
					StrayConfig.get().toggleMobGlow(id);
					UnloadState.markDirty();
				}, null));
			}
			return out;
		}
		if ("Keys".equals(category)) {
			for (int i = 0; i < ClickGui.BIND_LABEL.length; i++) {
				int which = ClickGui.BIND_WHICH[i];
				String chip = screen.clickBindListening(which)
					? "Press a key"
					: OdinClicks.keyLabel(OdinClicks.parseKey(ClickGui.bindKey(which)));
				out.add(new Item("bind:" + which, ClickGui.BIND_LABEL[i], null, chip, () -> screen.clickListenBind(which), null));
			}
			StrayConfig config = StrayConfig.get();
			out.add(new Item("keys:shortcuts", "Shortcuts", config.commandShortcutsEnabled, null, () -> {
				StrayConfig current = StrayConfig.get();
				current.commandShortcutsEnabled = !current.commandShortcutsEnabled;
				CommandShortcuts.sync();
				UnloadState.markDirty();
			}, null));
			if (config.commandShortcutsEnabled) {
				out.add(new Item("keys:args", "Pass arguments", config.commandShortcutsPassArgs, null, () -> {
					StrayConfig current = StrayConfig.get();
					current.commandShortcutsPassArgs = !current.commandShortcutsPassArgs;
					UnloadState.markDirty();
				}, null));
				out.add(new Item("keys:edit", "Edit shortcuts", null, "Open", () ->
					Minecraft.getInstance().setScreen(new CommandShortcutScreen(screen)), null));
			}
			return out;
		}
		for (ClickGui.Mod mod : ClickGui.modules()) {
			if (category.equals(mod.column())) {
				out.add(modItem(mod, null));
			}
		}
		return out;
	}

	private static Item modItem(ClickGui.Mod mod, String chip) {
		boolean settings = ClickGui.moduleHasSettings(mod);
		Boolean on = mod.hold() ? null : mod.on().getAsBoolean();
		Runnable left = mod.hold() ? null : () -> ClickGui.toggle(mod);
		return new Item("mod:" + mod.name(), ClickGui.display(mod.name()), on, chip, left, settings || mod.hold() ? mod : null);
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

	// --------------------------------------------------------------- drawing

	private static void text(GuiGraphicsExtractor graphics, Font font, String value, float x, float y, float h, int color) {
		if (((color >>> 24) & 0xFF) < 8) {
			return;
		}
		// Nunito at this size sits about 5px below the line top, not 4.5 like
		// the vanilla font, so center on that.
		GuiDraw.text(graphics, font, body(value), x, Math.round(y + h * 0.5f - 5f), color, false);
	}

	private static void title(GuiGraphicsExtractor graphics, Font font, String value, float x, float y, int color) {
		if (((color >>> 24) & 0xFF) < 8) {
			return;
		}
		GuiDraw.text(graphics, font, Component.literal(value).withStyle(MenuFont.TITLE), x, y, color, false);
	}

	private static void glyph(GuiGraphicsExtractor graphics, Font font, String code, float x, float y, float h, int color) {
		if (((color >>> 24) & 0xFF) < 8) {
			return;
		}
		GuiDraw.text(graphics, font, Component.literal(code).withStyle(MenuFont.ICON), x, Math.round(y + (h - font.lineHeight) * 0.5f), color, false);
	}

	private static Component body(String value) {
		return Component.literal(value == null ? "" : value).withStyle(MenuFont.BODY);
	}

	private static float width(Font font, String value) {
		return font.width(body(value));
	}

	private static String fit(Font font, String value, float max) {
		if (value == null) {
			return "";
		}
		if (width(font, value) <= max) {
			return value;
		}
		String trimmed = value;
		while (trimmed.length() > 1 && width(font, trimmed + "...") > max) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed + "...";
	}

	private static String label(String id) {
		return ClickGui.display(id);
	}

	/**
	 * The settings widgets read {@link Theme}; point it at the Material roles
	 * while they draw so they match the windows. Returns the old values.
	 */
	private static int[] swapTheme() {
		int[] old = {Theme.ACCENT, Theme.ACCENT_DIM, Theme.CARD, Theme.CARD_HOVER, Theme.LINE, Theme.TRACK, Theme.PANEL, Theme.TEXT, Theme.MUTED, Theme.OFF, Theme.WINDOW, Theme.WINDOW_SOLID};
		Theme.ACCENT = MaterialPalette.primary;
		Theme.ACCENT_DIM = MaterialPalette.secondaryContainer;
		Theme.CARD = MaterialPalette.surfaceHigh;
		Theme.CARD_HOVER = MaterialPalette.surfaceHighest;
		Theme.LINE = MaterialPalette.outlineVariant;
		Theme.TRACK = MaterialPalette.surfaceHighest;
		Theme.PANEL = MaterialPalette.surfaceContainer;
		Theme.TEXT = MaterialPalette.onSurface;
		Theme.MUTED = MaterialPalette.onSurfaceVariant;
		Theme.OFF = MaterialPalette.outline;
		Theme.WINDOW = MaterialPalette.surfaceLow;
		Theme.WINDOW_SOLID = MaterialPalette.surfaceLow;
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
	}

	private static int withAlpha(int color, int alpha) {
		return (alpha << 24) | (color & 0x00FFFFFF);
	}

	// ------------------------------------------------------------- animation

	private static float approach(float current, float target, float speed) {
		if (Math.abs(target - current) < 0.002f) {
			return target;
		}
		return current + (target - current) * (1f - (float) Math.exp(-speed * dt));
	}

	private static float approachIn(Map<String, Float> map, String key, float target, float speed) {
		float value = approach(map.getOrDefault(key, target), target, speed);
		map.put(key, value);
		return value;
	}

	private static float stagger(float t, float delay) {
		return Mth.clamp((t - delay) / (1f - delay), 0f, 1f);
	}

	/**
	 * CSS-style cubic bezier from (0,0) to (1,1) with control points
	 * (x1,y1) and (x2,y2): solve x for t with Newton steps, return y.
	 */
	private static float curve(float[] p, float x) {
		x = Mth.clamp(x, 0f, 1f);
		if (x <= 0f || x >= 1f) {
			return x;
		}
		float t = x;
		for (int i = 0; i < 8; i++) {
			float bx = bez(p[0], p[2], t) - x;
			float d = bezSlope(p[0], p[2], t);
			if (Math.abs(bx) < 1e-4f || Math.abs(d) < 1e-6f) {
				break;
			}
			t = Mth.clamp(t - bx / d, 0f, 1f);
		}
		return bez(p[1], p[3], t);
	}

	private static float bez(float a, float b, float t) {
		float u = 1f - t;
		return 3f * u * u * t * a + 3f * u * t * t * b + t * t * t;
	}

	private static float bezSlope(float a, float b, float t) {
		float u = 1f - t;
		return 3f * u * u * a + 6f * u * t * (b - a) + 3f * t * t * (1f - b);
	}

	private static boolean contains(double x, double y, float rx, float ry, float rw, float rh) {
		return x >= rx && y >= ry && x < rx + rw && y < ry + rh;
	}

	private record Item(String key, String label, Boolean on, String chip, Runnable left, ClickGui.Mod mod) {
	}

	private record RowHit(Item item, float x, float y, float w, float h) {
	}
}
