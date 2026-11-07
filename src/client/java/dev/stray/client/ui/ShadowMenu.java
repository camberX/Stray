package dev.stray.client.ui;

import dev.stray.client.render.GuiDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The "Shadow" click GUI: an island. A pill bar at the top holds the categories
 * as icons (the picked one grows into a white pill with its name), search, the
 * HUD editor and close. Under it a see-through black sheet with a soft shadow
 * holds the category's modules as chips and, below them, the picked module's
 * settings. The sheet eases to the height of what it shows.
 */
public final class ShadowMenu {
	private static final float BAR_H = 24f;
	private static final float ICON = 20f;
	private static final float CHIP_H = 16f;
	private static final float CHIP_GAP = 4f;
	private static final float PAD = 10f;
	private static final int WHITE = 0xFFEEF1F5;
	private static final int INK_ON_WHITE = 0xFF0B0E13;

	private static final Map<String, String> GLYPHS = Map.ofEntries(
		Map.entry("World", ""),
		Map.entry("Visuals", ""),
		Map.entry("Mobs", ""),
		Map.entry("Combat", ""),
		Map.entry("Slayer", ""),
		Map.entry("HUD", ""),
		Map.entry("Mining", ""),
		Map.entry("Farming", ""),
		Map.entry("Foraging", "\uEA63"),
		Map.entry("Menus", ""),
		Map.entry("Keys", ""),
		Map.entry("Theme", ""),
		Map.entry("Player", "")
	);
	private static final String SEARCH = "";
	private static final String CLOSE = "";
	private static final String HUD_EDIT = "";
	private static final String TUNE = "";

	private static boolean drawing;
	private static float contentAlpha = 1f;
	private static Object lastScreen;
	private static long lastNs;
	private static float dt = 0.016f;
	private static float openT;

	private static String category = ClickGui.ORDER[0];
	private static float catT = 1f;
	private static String selected;
	private static float selT = 1f;
	private static float sheetH = -1f;
	private static float chipScroll;
	private static float setScroll;
	private static float searchOpen;
	private static final Map<String, Float> hover = new HashMap<>();
	private static final Map<String, Float> knob = new HashMap<>();
	private static final Map<String, Float> pillX = new HashMap<>();
	private static final List<ChipHit> chipHits = new ArrayList<>();

	private static float sheetX;
	private static float sheetY;
	private static float sheetW;
	private static float chipsY;
	private static float chipsH;
	private static float setTop;
	private static float setBottom;

	private ShadowMenu() {
	}

	public static boolean drawing() {
		return drawing;
	}

	public static float alpha() {
		return drawing ? contentAlpha : 1f;
	}

	public static int faded(int color) {
		return drawing && contentAlpha < 0.999f ? Anim.fade(color, contentAlpha) : color;
	}

	static void draw(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		drawing = true;
		try {
			frame(screen, graphics, font, mouseX, mouseY);
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

	private static void frame(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		tick(screen);
		float t = ease(Mth.clamp(screen.clickAppear(), 0f, 1f) * openT);
		GuiDraw.fill(graphics, 0, 0, screen.width, screen.height, Anim.fade(0x30000000, t));
		sheetW = Math.min(440f, screen.width - 24f);
		sheetX = (screen.width - sheetW) * 0.5f;
		float barY = 8f - (1f - t) * 16f;
		bar(screen, graphics, font, mouseX, mouseY, barY, t);
		sheetY = barY + BAR_H + 7f;
		sheet(screen, graphics, font, mouseX, mouseY, screen.height - 8f - sheetY, t);
	}

	// ------------------------------------------------------------------- bar

	private static void bar(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mx, int my, float y, float t) {
		String[] ids = ClickGui.ORDER;
		boolean searching = !ClickGui.filterText().isBlank() || ClickGui.searchFocused();
		searchOpen = approach(searchOpen, searching ? 1f : 0f, 16f);
		String active = searching ? null : category;
		float activeExtra = active == null ? 0f : textWidth(font, ClickGui.display(active)) + 10f;
		float searchExtra = searchOpen * 90f;
		float w = 6f + ids.length * (ICON + 2f) + activeExtra + 6f + (ICON + 2f) * 3f + searchExtra + 4f;
		float x = (screen.width - w) * 0.5f;
		float r = BAR_H * 0.5f;
		ShadowLook.shadow(graphics, x, y, w, BAR_H, r, 10, Math.round(0x60 * t));
		GuiDraw.roundedFine(graphics, x, y, w, BAR_H, r, Anim.fade(ShadowLook.FILL, t));
		GuiDraw.hairline(graphics, x, y, w, BAR_H, r, Anim.fade(0x1CFFFFFF, t));

		float cx = x + 6f;
		float iy = y + (BAR_H - ICON) * 0.5f;
		for (String id : ids) {
			boolean on = id.equals(active);
			float target = on ? ICON + activeExtra : ICON;
			float iw = approachIn(pillX, id, target, 18f);
			boolean hot = contains(mx, my, cx, iy, iw, ICON);
			if (on || iw > ICON + 1f) {
				GuiDraw.roundedFine(graphics, cx, iy, iw, ICON, ICON * 0.5f, Anim.fade(WHITE, t * Mth.clamp((iw - ICON) / Math.max(1f, activeExtra), 0.35f, 1f)));
			} else if (hot) {
				GuiDraw.roundedFine(graphics, cx, iy, iw, ICON, ICON * 0.5f, Anim.fade(ShadowLook.CARD_HOVER, t));
			}
			int ink = on ? INK_ON_WHITE : hot ? WHITE : ShadowLook.MUTED;
			glyph(graphics, font, GLYPHS.getOrDefault(id, TUNE), cx + (ICON - 9f) * 0.5f, iy, ICON, Anim.fade(ink, t));
			if (iw > ICON + 6f) {
				boolean clip = GuiDraw.scissor(graphics, cx, iy, iw - 4f, ICON);
				text(graphics, font, ClickGui.display(id), cx + ICON - 2f, iy, ICON, Anim.fade(on ? INK_ON_WHITE : ShadowLook.MUTED, t));
				if (clip) {
					GuiDraw.disableScissor(graphics);
				}
			}
			screen.clickHit(cx, iy, iw, ICON, () -> pick(id));
			cx += iw + 2f;
		}
		cx += 4f;
		GuiDraw.fill(graphics, cx - 3f, y + 6f, 0.5f, BAR_H - 12f, Anim.fade(0x22FFFFFF, t));

		// Search: an icon that opens into a field.
		float sw = ICON + searchExtra;
		boolean focused = ClickGui.searchFocused();
		boolean sHot = contains(mx, my, cx, iy, sw, ICON);
		if (searchOpen > 0.02f || sHot) {
			GuiDraw.roundedFine(graphics, cx, iy, sw, ICON, ICON * 0.5f, Anim.fade(focused ? ShadowLook.CARD_HOVER : ShadowLook.CARD, t));
		}
		glyph(graphics, font, SEARCH, cx + (ICON - 9f) * 0.5f, iy, ICON, Anim.fade(focused || sHot ? WHITE : ShadowLook.MUTED, t));
		ClickGui.searchBox(cx, iy, sw, ICON);
		if (searchOpen > 0.3f) {
			String query = ClickGui.filterText();
			String shown = query.isEmpty() && !focused ? "Search" : query + (focused && (System.currentTimeMillis() / 500L) % 2L == 0L ? "|" : "");
			boolean clip = GuiDraw.scissor(graphics, cx + ICON - 2f, iy, sw - ICON - 2f, ICON);
			text(graphics, font, shown, cx + ICON - 2f, iy, ICON, Anim.fade(query.isEmpty() ? ShadowLook.FAINT : WHITE, t * searchOpen));
			if (clip) {
				GuiDraw.disableScissor(graphics);
			}
		}
		screen.clickHit(cx, iy, sw, ICON, ClickGui::focusSearch);
		cx += sw + 2f;
		iconButton(screen, graphics, font, mx, my, cx, iy, HUD_EDIT, t, () -> Minecraft.getInstance().gui.setScreen(new HudEditorScreen()));
		cx += ICON + 2f;
		iconButton(screen, graphics, font, mx, my, cx, iy, CLOSE, t, screen::onClose);
	}

	private static void iconButton(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mx, int my, float x, float y, String glyph, float t, Runnable click) {
		boolean hot = contains(mx, my, x, y, ICON, ICON);
		if (hot) {
			GuiDraw.roundedFine(graphics, x, y, ICON, ICON, ICON * 0.5f, Anim.fade(ShadowLook.CARD_HOVER, t));
		}
		glyph(graphics, font, glyph, x + (ICON - 9f) * 0.5f, y, ICON, Anim.fade(hot ? WHITE : ShadowLook.MUTED, t));
		screen.clickHit(x, y, ICON, ICON, click);
	}

	// ----------------------------------------------------------------- sheet

	private static void sheet(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mx, int my, float maxH, float t) {
		// Last frame's chips must not take right clicks while the sheet is closed or closing.
		chipHits.clear();
		List<HyprMenu.Item> items = HyprMenu.items(screen, category);
		keepSelection(items);
		boolean searching = !ClickGui.filterText().isBlank();
		boolean mobs = "Mobs".equals(category) && !searching;
		float innerW = sheetW - PAD * 2f;

		// Chip layout first, to know the height the sheet wants.
		List<float[]> spots = new ArrayList<>();
		float lx = 0f;
		float ly = 0f;
		for (HyprMenu.Item item : items) {
			float cw = chipWidth(font, item);
			if (lx > 0f && lx + cw > innerW) {
				lx = 0f;
				ly += CHIP_H + CHIP_GAP;
			}
			spots.add(new float[] {lx, ly, cw});
			lx += cw + CHIP_GAP;
		}
		float chipsContent = items.isEmpty() ? CHIP_H : ly + CHIP_H;
		float searchRow = mobs ? CHIP_H + 6f : 0f;
		float chipMax = Math.max(CHIP_H * 2f + CHIP_GAP, maxH * 0.42f - searchRow);
		chipsH = Math.min(chipsContent, chipMax);
		ClickGui.Mod mod = find(selected);
		float settingsH = settingsHeight(screen, font, mod, innerW);
		float wanted = PAD + searchRow + chipsH + (mod == null ? 0f : 12f + settingsH) + PAD;
		float target = Math.min(maxH, wanted);
		if (sheetH < 0f) {
			sheetH = target;
		}
		sheetH = approach(sheetH, target, 14f);
		float h = sheetH * t;
		if (h < 8f) {
			return;
		}
		float r = 16f;
		ShadowLook.shadow(graphics, sheetX, sheetY, sheetW, h, r, 14, Math.round(0x60 * t));
		GuiDraw.roundedFine(graphics, sheetX, sheetY, sheetW, h, r, Anim.fade(ShadowLook.FILL, t));
		GuiDraw.hairline(graphics, sheetX, sheetY, sheetW, h, r, Anim.fade(0x1CFFFFFF, t));
		boolean sheetClip = GuiDraw.scissor(graphics, sheetX, sheetY, sheetW, h);
		try {
			float x = sheetX + PAD;
			float y = sheetY + PAD;
			if (mobs) {
				mobSearch(screen, graphics, font, x, y, innerW, t);
				y += CHIP_H + 6f;
			} else {
				ClickGui.mobSearchBox(0f, 0f, 0f, 0f);
			}
			chipsY = y;
			chips(screen, graphics, font, mx, my, items, spots, x, y, innerW, chipsContent, t);
			if (mod != null) {
				float sy = y + chipsH + 6f;
				GuiDraw.fill(graphics, x, sy, innerW, 0.5f, Anim.fade(0x18FFFFFF, t));
				settings(screen, graphics, font, mx, my, mod, x, sy + 6f, innerW, sheetY + h - PAD, t);
			}
		} finally {
			if (sheetClip) {
				GuiDraw.disableScissor(graphics);
			}
		}
	}

	private static void mobSearch(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, float x, float y, float w, float t) {
		boolean focused = ClickGui.mobSearchFocused();
		GuiDraw.roundedFine(graphics, x, y, w, CHIP_H, CHIP_H * 0.5f, Anim.fade(focused ? ShadowLook.CARD_HOVER : ShadowLook.CARD, t));
		ClickGui.mobSearchBox(x, y, w, CHIP_H);
		String q = ClickGui.mobQuery();
		String shown = q.isEmpty() && !focused ? "Find a mob" : q + (focused && (System.currentTimeMillis() / 500L) % 2L == 0L ? "|" : "");
		glyph(graphics, font, SEARCH, x + 5f, y, CHIP_H, Anim.fade(ShadowLook.FAINT, t));
		text(graphics, font, fit(font, shown, w - 24f), x + 17f, y, CHIP_H, Anim.fade(q.isEmpty() ? ShadowLook.FAINT : WHITE, t));
		screen.clickHit(x, y, w, CHIP_H, ClickGui::focusMobSearch);
	}

	private static float chipWidth(Font font, HyprMenu.Item item) {
		float w = 8f + textWidth(font, item.label()) + 8f;
		if (item.on() != null) {
			w += 9f;
		}
		if (item.chip() != null && !item.chip().isEmpty()) {
			w += textWidth(font, item.chip()) * 0.8f + 8f;
		}
		return w;
	}

	private static void chips(
		StrayScreen screen,
		GuiGraphicsExtractor graphics,
		Font font,
		int mx,
		int my,
		List<HyprMenu.Item> items,
		List<float[]> spots,
		float x,
		float y,
		float w,
		float content,
		float t
	) {
		chipHits.clear();
		if (items.isEmpty()) {
			text(graphics, font, ClickGui.filterText().isBlank() ? "Nothing here" : "No matches", x, y, CHIP_H, Anim.fade(ShadowLook.MUTED, t));
			return;
		}
		chipScroll = Mth.clamp(chipScroll, 0f, Math.max(0f, content - chipsH));
		boolean clip = GuiDraw.scissor(graphics, x - 2f, y - 1f, w + 4f, chipsH + 2f);
		int mark = screen.clickHitMark();
		float ct = ease(catT);
		for (int i = 0; i < items.size(); i++) {
			HyprMenu.Item item = items.get(i);
			float[] s = spots.get(i);
			float delay = Math.min(i, 16) * 0.025f;
			float k = ease(Mth.clamp((catT - delay) / (1f - delay), 0f, 1f)) * t;
			float cx = x + s[0];
			float cy = y + s[1] - chipScroll + (1f - k) * 5f;
			if (cy + CHIP_H < y || cy > y + chipsH) {
				continue;
			}
			chip(screen, graphics, font, mx, my, item, cx, cy, s[2], k * (0.4f + 0.6f * ct), y, chipsH);
		}
		screen.clickClipHits(mark, x - 2f, y - 1f, w + 4f, chipsH + 2f);
		if (clip) {
			GuiDraw.disableScissor(graphics);
		}
	}

	private static void chip(
		StrayScreen screen,
		GuiGraphicsExtractor graphics,
		Font font,
		int mx,
		int my,
		HyprMenu.Item item,
		float x,
		float y,
		float w,
		float t,
		float clipY,
		float clipH
	) {
		boolean inside = my >= clipY && my < clipY + clipH;
		boolean hot = inside && contains(mx, my, x, y, w, CHIP_H);
		boolean chosen = item.mod() != null && item.mod().name().equals(selected);
		boolean lit = item.on() != null && item.on();
		float hv = approachIn(hover, item.key(), hot ? 1f : 0f, 16f);
		int fill = chosen ? 0x26FFFFFF : lit ? 0x16FFFFFF : 0x0AFFFFFF;
		GuiDraw.roundedFine(graphics, x, y, w, CHIP_H, CHIP_H * 0.5f, Anim.fade(fill, t));
		if (hv > 0.01f) {
			GuiDraw.roundedFine(graphics, x, y, w, CHIP_H, CHIP_H * 0.5f, Anim.fade(0x0EFFFFFF, hv * t));
		}
		if (chosen) {
			GuiDraw.hairline(graphics, x, y, w, CHIP_H, CHIP_H * 0.5f, Anim.fade(0x38FFFFFF, t));
		}
		float tx = x + 8f;
		if (item.on() != null) {
			float k = approachIn(knob, item.key(), lit ? 1f : 0f, 16f);
			float dx = x + 9f;
			// Centred on the label's letters (the label snaps to whole pixels), not the chip box.
			float dy = Math.round(GuiDraw.middle(y, CHIP_H)) + 4.5f;
			if (k > 0.05f) {
				GuiDraw.circle(graphics, dx, dy, 3.6f, Anim.fade(0x30FFFFFF, k * t));
			}
			GuiDraw.circle(graphics, dx, dy, 2f, Anim.fade(Anim.mix(0x40FFFFFF, WHITE, k), t));
			tx += 9f;
		}
		text(graphics, font, item.label(), tx, y, CHIP_H, Anim.fade(chosen || lit ? WHITE : 0xFFAEB6C0, t));
		if (item.chip() != null && !item.chip().isEmpty()) {
			float cx = tx + textWidth(font, item.label()) + 6f;
			GuiDraw.text(graphics, font, item.chip(), cx, y + (CHIP_H - 8f * 0.8f) * 0.5f, 0.8f, Anim.fade(ShadowLook.MUTED, t), false);
		}
		chipHits.add(new ChipHit(item, x, y, w, CHIP_H));
		// Left click toggles (or runs the action); a module without a toggle opens instead.
		// Right click opens the settings (see rightClick).
		Runnable click = item.left() != null ? item.left() : item.mod() != null ? () -> select(item.mod().name()) : null;
		if (click != null) {
			screen.clickHit(x, y, w, CHIP_H, click);
		}
	}

	// -------------------------------------------------------------- settings

	private static float settingsHeight(StrayScreen screen, Font font, ClickGui.Mod mod, float w) {
		if (mod == null) {
			return 0f;
		}
		float head = 34f;
		float body = ClickGui.moduleHasSettings(mod) ? ClickGui.moduleSettingsHeight(screen, mod) + 6f : 0f;
		int lines = descriptionLines(font, ClickGui.description(mod), w);
		return head + body + lines * 11f + 4f;
	}

	private static void settings(
		StrayScreen screen,
		GuiGraphicsExtractor graphics,
		Font font,
		int mx,
		int my,
		ClickGui.Mod mod,
		float x,
		float y,
		float w,
		float bottom,
		float t
	) {
		float st = ease(selT) * t;
		float lift = (1f - ease(selT)) * 6f;
		setTop = y;
		setBottom = bottom;
		float content = settingsHeight(screen, Minecraft.getInstance().font, mod, w);
		float view = bottom - y;
		setScroll = Mth.clamp(setScroll, 0f, Math.max(0f, content - view));
		boolean clip = GuiDraw.scissor(graphics, x - 4f, y, w + 8f, Math.max(0f, view));
		int mark = screen.clickHitMark();
		try {
			float top = y - setScroll + lift;
			bigText(graphics, font, fitTitle(font, ClickGui.display(mod.name()), w - 40f), x, top + 2f, 1f, Anim.fade(WHITE, st));
			label(graphics, font, ClickGui.display(mod.column()).toUpperCase(java.util.Locale.ROOT), x, top + 18f, Anim.fade(ShadowLook.FAINT, st));
			if (!mod.hold()) {
				float k = approachIn(knob, "set:" + mod.name(), mod.on().getAsBoolean() ? 1f : 0f, 16f);
				float sx = x + w - 24f;
				toggle(graphics, sx, top + 4f, 24f, 12f, k, st);
				screen.clickHit(sx - 2f, top + 2f, 28f, 16f, () -> ClickGui.toggle(mod));
			}
			float by = top + 34f;
			if (ClickGui.moduleHasSettings(mod) && st > 0.06f) {
				contentAlpha = st;
				int[] saved = swapTheme();
				try {
					// Feature panels draw their labels 1px in from x; Click GUI rows inset by NEST:
					// both land on the title's left edge and the divider's right edge.
					if (mod.feature() != null) {
						ClickGui.drawModuleSettings(screen, graphics, font, mx, my, mod, x - 1f, by, w + 1f);
					} else {
						ClickGui.drawModuleSettings(screen, graphics, font, mx, my, mod, x - 3f, by, w + 6f);
					}
				} finally {
					restoreTheme(saved);
					contentAlpha = 1f;
				}
				by += ClickGui.moduleSettingsHeight(screen, mod) + 6f;
			}
			describe(graphics, font, ClickGui.description(mod), x, by, w, Anim.fade(ShadowLook.MUTED, st));
		} finally {
			screen.clickClipHits(mark, x - 4f, y, w + 8f, Math.max(0f, view));
			if (clip) {
				GuiDraw.disableScissor(graphics);
			}
		}
		if (content > view + 1f) {
			float thumb = Math.max(12f, view * view / content);
			float ty = y + (view - thumb) * (setScroll / Math.max(1f, content - view));
			GuiDraw.roundedFine(graphics, x + w + 4f, ty, 2f, thumb, 1f, Anim.fade(0x30FFFFFF, t));
		}
	}

	/** Small switch: gray track off, white track with a dark knob on. */
	public static void toggle(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float k, float t) {
		float half = h * 0.5f;
		GuiDraw.roundedFine(graphics, x, y, w, h, half, Anim.fade(Anim.mix(0x26FFFFFF, WHITE, k), t));
		float knobR = half - 1.6f;
		float kx = x + half + k * (w - h);
		GuiDraw.circle(graphics, kx, y + half, knobR, Anim.fade(Anim.mix(0xFFB8C0CA, INK_ON_WHITE, k), t));
	}

	private static int descriptionLines(Font font, String text, float w) {
		if (text == null || text.isEmpty()) {
			return 0;
		}
		int lines = 1;
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String next = line.length() == 0 ? word : line + " " + word;
			if (textWidth(font, next) > w && line.length() > 0) {
				lines++;
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(next);
			}
		}
		return lines;
	}

	private static void describe(GuiGraphicsExtractor graphics, Font font, String text, float x, float y, float w, int color) {
		if (text == null || text.isEmpty()) {
			return;
		}
		StringBuilder line = new StringBuilder();
		float ly = y;
		for (String word : text.split(" ")) {
			String next = line.length() == 0 ? word : line + " " + word;
			if (textWidth(font, next) > w && line.length() > 0) {
				text(graphics, font, line.toString(), x, ly, 10f, color);
				ly += 11f;
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(next);
			}
		}
		if (line.length() > 0) {
			text(graphics, font, line.toString(), x, ly, 10f, color);
		}
	}

	// ------------------------------------------------------------- behaviour

	private static void tick(StrayScreen screen) {
		long now = System.nanoTime();
		dt = lastNs == 0L ? 0.016f : Math.min(0.05f, (now - lastNs) / 1_000_000_000f);
		lastNs = now;
		if (screen != lastScreen) {
			lastScreen = screen;
			openT = 0f;
			catT = 0f;
			selT = 0f;
			sheetH = -1f;
			hover.clear();
			knob.clear();
			pillX.clear();
		}
		openT = Math.min(1f, openT + dt / 0.24f);
		catT = Math.min(1f, catT + dt / 0.34f);
		selT = Math.min(1f, selT + dt / 0.24f);
	}

	private static void pick(String id) {
		if (!ClickGui.filterText().isBlank() || ClickGui.searchFocused()) {
			ClickGui.clearSearch();
		}
		if (id.equals(category)) {
			return;
		}
		if ("Mobs".equals(category)) {
			ClickGui.blurMobSearch();
		}
		category = id;
		catT = 0f;
		chipScroll = 0f;
	}

	private static void select(String name) {
		if (name == null || name.equals(selected)) {
			return;
		}
		selected = name;
		selT = 0f;
		setScroll = 0f;
	}

	private static void keepSelection(List<HyprMenu.Item> items) {
		for (HyprMenu.Item item : items) {
			if (item.mod() != null && item.mod().name().equals(selected)) {
				return;
			}
		}
		selected = null;
	}

	static boolean scroll(double x, double y, double wheel) {
		if (wheel == 0) {
			return false;
		}
		float step = (float) wheel * (CHIP_H + CHIP_GAP) * 2f;
		if (contains(x, y, sheetX, chipsY, sheetW, chipsH)) {
			chipScroll = Math.max(0f, chipScroll - step);
			return true;
		}
		if (contains(x, y, sheetX, setTop, sheetW, setBottom - setTop)) {
			setScroll = Math.max(0f, setScroll - step);
			return true;
		}
		return false;
	}

	/** Right click on a module opens its settings; nothing when it has none. */
	static void rightClick(double x, double y) {
		for (ChipHit hit : chipHits) {
			if (!contains(x, y, hit.x, hit.y, hit.w, hit.h)) {
				continue;
			}
			ClickGui.Mod mod = hit.item.mod();
			if (mod != null && ClickGui.moduleHasSettings(mod)) {
				if (mod.name().equals(selected)) {
					selected = null;
				} else {
					select(mod.name());
				}
			}
			return;
		}
	}

	private static int[] swapTheme() {
		int[] old = {Theme.ACCENT, Theme.ACCENT_DIM, Theme.CARD, Theme.CARD_HOVER, Theme.LINE, Theme.TRACK, Theme.PANEL, Theme.TEXT, Theme.MUTED, Theme.OFF, Theme.WINDOW, Theme.WINDOW_SOLID, Theme.SHEET};
		Theme.ACCENT = faded(WHITE);
		Theme.ACCENT_DIM = faded(0x66EEF1F5);
		Theme.CARD = faded(ShadowLook.CARD);
		Theme.CARD_HOVER = faded(ShadowLook.CARD_HOVER);
		Theme.LINE = faded(0x24FFFFFF);
		Theme.TRACK = faded(0x1AFFFFFF);
		Theme.PANEL = faded(0x14000000);
		Theme.TEXT = faded(ShadowLook.TEXT);
		Theme.MUTED = faded(ShadowLook.MUTED);
		Theme.OFF = faded(0xFF3A424C);
		Theme.WINDOW = faded(0xE6050709);
		Theme.WINDOW_SOLID = faded(0xFF050709);
		Theme.SHEET = faded(0xF2050709);
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

	// --------------------------------------------------------------- drawing

	private static void text(GuiGraphicsExtractor graphics, Font font, String value, float x, float y, float h, int color) {
		if (((color >>> 24) & 0xFF) < 8) {
			return;
		}
		GuiDraw.text(graphics, font, MenuFont.shadow(value), x, Math.round(GuiDraw.middle(y, h)), color, false);
	}

	private static void bigText(GuiGraphicsExtractor graphics, Font font, String value, float x, float y, float scale, int color) {
		if (((color >>> 24) & 0xFF) < 8) {
			return;
		}
		// The title font is drawn at its own size; scale only shrinks names that do not fit.
		GuiDraw.text(graphics, font, MenuFont.shadowTitle(value), x, y, Math.min(1f, scale), color, false);
	}

	private static void label(GuiGraphicsExtractor graphics, Font font, String value, float x, float y, int color) {
		if (((color >>> 24) & 0xFF) < 8) {
			return;
		}
		GuiDraw.small(graphics, font, value, x, y, color);
	}

	private static void glyph(GuiGraphicsExtractor graphics, Font font, String code, float x, float y, float h, int color) {
		if (((color >>> 24) & 0xFF) < 8) {
			return;
		}
		GuiDraw.text(graphics, font, Component.literal(code).withStyle(MenuFont.ICON), x, Math.round(y + (h - font.lineHeight) * 0.5f), color, false);
	}

	private static float textWidth(Font font, String value) {
		return font.width(MenuFont.shadow(value));
	}

	private static String fit(Font font, String value, float max) {
		if (value == null) {
			return "";
		}
		if (textWidth(font, value) <= max) {
			return value;
		}
		String trimmed = value;
		while (trimmed.length() > 1 && textWidth(font, trimmed + "...") > max) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed + "...";
	}

	private static String fitTitle(Font font, String value, float max) {
		String trimmed = value == null ? "" : value;
		if (font.width(MenuFont.shadowTitle(trimmed)) <= max) {
			return trimmed;
		}
		while (trimmed.length() > 1 && font.width(MenuFont.shadowTitle(trimmed + "...")) > max) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed + "...";
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

	private static float approach(float current, float target, float speed) {
		if (Math.abs(target - current) < 0.05f) {
			return target;
		}
		return current + (target - current) * (1f - (float) Math.exp(-speed * dt));
	}

	private static float approachIn(Map<String, Float> map, String key, float target, float speed) {
		float current = map.getOrDefault(key, target);
		float value = Math.abs(target - current) < 0.002f ? target : current + (target - current) * (1f - (float) Math.exp(-speed * dt));
		map.put(key, value);
		return value;
	}

	private static float ease(float t) {
		float u = 1f - Mth.clamp(t, 0f, 1f);
		return 1f - u * u * u;
	}

	private static boolean contains(double x, double y, float rx, float ry, float rw, float rh) {
		return x >= rx && y >= ry && x < rx + rw && y < ry + rh;
	}

	private record ChipHit(HyprMenu.Item item, float x, float y, float w, float h) {
	}
}
