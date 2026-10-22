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
 * Hyprland-style click GUI: a Waybar-like top bar where categories are
 * workspaces, and two tiled windows (modules, settings) with gaps. The window
 * under the mouse gets the focused gradient border, like focus-follows-mouse.
 * Left click toggles a module, right click shows its settings on the right.
 */
final class HyprMenu {
	private static final float OUT = 10f;
	private static final float GAP = 5f;
	private static final float BAR = 20f;
	private static final float RADIUS = 6f;
	private static final float ROW = 17f;
	private static final float ROW_GAP = 3f;
	private static final float PAD = 8f;

	private static final int TEXT = 0xFFE6EAF0;
	private static final int MUTED = 0xFF9AA6B4;
	private static final int FILL = 0xC8111419;
	private static final int BAR_FILL = 0xB8111419;
	private static final int ROW_FILL = 0x0AFFFFFF;
	private static final int ROW_HOT = 0x18FFFFFF;
	private static final int INACTIVE = 0xAA595959;
	private static final int OFF_TRACK = 0x30FFFFFF;
	private static final int DIM = 0x50000000;

	private static Object lastScreen;
	private static long lastNs;
	private static float dt;
	private static float openT;

	private static String category = ClickGui.ORDER[0];
	private static float catT = 1f;
	private static float pillX = -1f;
	private static float pillW;
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

	static void draw(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		tick(screen);
		float fade = Mth.clamp(screen.clickAppear(), 0f, 1f);
		float tBar = ease(stagger(openT, 0f)) * fade;
		float tList = ease(stagger(openT, 0.12f)) * fade;
		float tSet = ease(stagger(openT, 0.24f)) * fade;

		GuiDraw.fill(graphics, 0, 0, screen.width, screen.height, Anim.fade(DIM, fade));

		float w = screen.width - OUT * 2f;
		float barY = OUT - (1f - tBar) * 10f;
		drawBar(screen, graphics, font, mouseX, mouseY, OUT, barY, w, tBar);

		float top = OUT + BAR + GAP;
		float h = Math.max(60f, screen.height - top - OUT);
		float split = Math.round((w - GAP) * 0.55f);
		listX = OUT - (1f - tList) * 16f;
		listY = top;
		listW = split;
		listH = h;
		setX = OUT + split + GAP + (1f - tSet) * 16f;
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
			hover.clear();
			knob.clear();
		}
		openT = Math.min(1f, openT + dt * 2.6f);
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
		GuiFrostBlur.blitWindow(graphics, x, y, w, BAR, RADIUS);
		GuiDraw.roundedFine(graphics, x, y, w, BAR, RADIUS, Anim.fade(BAR_FILL, t));

		int accent = Theme.ACCENT;
		float cursor = x + 8f;
		text(graphics, font, "stray", cursor, y, BAR, Anim.fade(accent, t));
		cursor += width(font, "stray") + 10f;

		float closeW = 16f;
		float searchW = Math.min(130f, Math.max(70f, w * 0.18f));
		float searchX = x + w - 4f - closeW - 4f - searchW;
		float room = searchX - 8f - cursor;

		String[] ids = ClickGui.ORDER;
		float full = 0f;
		for (String id : ids) {
			full += tabWidth(font, id, true) + 2f;
		}
		boolean labels = full <= room;

		float[] tabX = new float[ids.length];
		float[] tabW = new float[ids.length];
		float tx = cursor;
		for (int i = 0; i < ids.length; i++) {
			boolean active = ids[i].equals(category);
			tabX[i] = tx;
			tabW[i] = tabWidth(font, ids[i], labels || active);
			tx += tabW[i] + 2f;
		}
		float tabY = y + 3f;
		float tabH = BAR - 6f;
		for (int i = 0; i < ids.length; i++) {
			if (ids[i].equals(category)) {
				if (pillX < 0f) {
					pillX = tabX[i];
					pillW = tabW[i];
				}
				pillX = approach(pillX, tabX[i], 16f);
				pillW = approach(pillW, tabW[i], 16f);
			}
		}
		for (int i = 0; i < ids.length; i++) {
			String id = ids[i];
			if (!id.equals(category) && contains(mouseX, mouseY, tabX[i], tabY, tabW[i], tabH)) {
				GuiDraw.roundedFine(graphics, tabX[i], tabY, tabW[i], tabH, 4f, Anim.fade(ROW_HOT, t));
			}
		}
		if (pillW > 0f) {
			GuiDraw.roundedFine(graphics, pillX, tabY, pillW, tabH, 4f, Anim.fade(accent, t));
		}
		for (int i = 0; i < ids.length; i++) {
			String id = ids[i];
			boolean active = id.equals(category);
			boolean underPill = tabX[i] + tabW[i] > pillX + 1f && tabX[i] < pillX + pillW - 1f;
			int ink = active && underPill ? 0xFF0B0E14 : (active ? TEXT : 0xFFAEB8C4);
			float ix = tabX[i] + 5f;
			icon(graphics, id, ix, tabY + (tabH - 9f) * 0.5f, Anim.fade(ink, t));
			if (labels || active) {
				text(graphics, font, label(id), ix + 12f, tabY, tabH, Anim.fade(ink, t));
			}
			screen.clickHit(tabX[i], tabY, tabW[i], tabH, () -> pickCategory(id));
		}

		float fieldY = y + 3f;
		float fieldH = BAR - 6f;
		boolean focused = ClickGui.searchFocused();
		GuiDraw.roundedFine(graphics, searchX, fieldY, searchW, fieldH, 4f, Anim.fade(focused ? 0x1EFFFFFF : 0x10FFFFFF, t));
		if (focused) {
			GuiDraw.roundedOutline(graphics, searchX, fieldY, searchW, fieldH, 4f, Anim.fade(accent, t), 1f);
		}
		ClickGui.searchBox(searchX, fieldY, searchW, fieldH);
		String query = ClickGui.filterText();
		boolean empty = query.isEmpty() && !focused;
		text(graphics, font, ">", searchX + 5f, fieldY, fieldH, Anim.fade(hueShift(accent), t));
		String shown = empty ? "search" : query + (focused && (System.currentTimeMillis() / 500L) % 2L == 0L ? "_" : "");
		text(graphics, font, fit(font, shown, searchW - 20f), searchX + 14f, fieldY, fieldH, Anim.fade(empty ? MUTED : TEXT, t));
		screen.clickHit(searchX, fieldY, searchW, fieldH, ClickGui::focusSearch);

		float closeX = x + w - 4f - closeW;
		boolean closeHot = contains(mouseX, mouseY, closeX, fieldY, closeW, fieldH);
		if (closeHot) {
			GuiDraw.roundedFine(graphics, closeX, fieldY, closeW, fieldH, 4f, Anim.fade(ROW_HOT, t));
		}
		float cx = closeX + closeW * 0.5f;
		float cy = fieldY + fieldH * 0.5f;
		int crossInk = Anim.fade(closeHot ? TEXT : MUTED, t);
		GuiDraw.stroke(graphics, cx - 3f, cy - 3f, cx + 3f, cy + 3f, 1.2f, crossInk);
		GuiDraw.stroke(graphics, cx + 3f, cy - 3f, cx - 3f, cy + 3f, 1.2f, crossInk);
		screen.clickHit(closeX, fieldY, closeW, fieldH, screen::onClose);
	}

	private static float tabWidth(Font font, String id, boolean withLabel) {
		return withLabel ? 5f + 9f + 3f + width(font, label(id)) + 6f : 19f;
	}

	// --------------------------------------------------------------- windows

	private static void window(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float focus, float t) {
		GuiFrostBlur.blitWindow(graphics, x, y, w, h, RADIUS);
		GuiDraw.roundedFine(graphics, x, y, w, h, RADIUS, Anim.fade(FILL, t));
		float idle = (1f - focus) * t;
		if (idle > 0.01f) {
			GuiDraw.roundedOutline(graphics, x, y, w, h, RADIUS, Anim.fade(INACTIVE, idle), 1f);
		}
		float lit = focus * t;
		if (lit > 0.01f) {
			GuiDraw.gradientRim(graphics, x, y, w, h, RADIUS, Anim.fade(Theme.ACCENT, lit), Anim.fade(hueShift(Theme.ACCENT), lit));
		}
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
		String title = searching ? "search" : label(category);
		text(graphics, font, title, x, y, 10f, Anim.fade(TEXT, t));
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
		String count = toggles > 0 ? on + "/" + toggles + " on" : items.size() + "";
		text(graphics, font, count, x + w - width(font, count), y, 10f, Anim.fade(MUTED, t));

		float bodyY = y + 16f;
		float bodyH = listY + listH - PAD - bodyY;
		int cols = w >= 250f ? 2 : 1;
		float colGap = 6f;
		float cellW = (w - colGap * (cols - 1)) / cols;
		int rows = (items.size() + cols - 1) / cols;
		float content = rows * (ROW + ROW_GAP) - ROW_GAP;
		listScroll = Mth.clamp(listScroll, 0f, Math.max(0f, content - bodyH));

		if (items.isEmpty()) {
			text(graphics, font, searching ? "no matches" : "nothing here", x, bodyY, ROW, Anim.fade(MUTED, t));
			return;
		}
		boolean clip = GuiDraw.scissor(graphics, listX + 2f, bodyY, listW - 4f, bodyH);
		int mark = screen.clickHitMark();
		for (int i = 0; i < items.size(); i++) {
			int col = i % cols;
			int row = i / cols;
			float rowT = ease(Mth.clamp(catT * 2.2f - row * 0.08f, 0f, 1f)) * t;
			float rx = x + col * (cellW + colGap);
			float ry = bodyY + row * (ROW + ROW_GAP) - listScroll + (1f - rowT) * 6f;
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
			float track = bodyH;
			float thumb = Math.max(14f, track * bodyH / content);
			float ty = bodyY + (track - thumb) * (listScroll / Math.max(1f, content - bodyH));
			GuiDraw.roundedFine(graphics, listX + listW - 4f, ty, 2f, thumb, 1f, Anim.fade(0x40FFFFFF, t));
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
		int fill = Anim.mix(ROW_FILL, ROW_HOT, Math.max(hv, chosen ? 0.6f : 0f));
		GuiDraw.roundedFine(graphics, x, y, w, ROW, 4f, Anim.fade(fill, t));
		if (chosen) {
			GuiDraw.roundedOutline(graphics, x, y, w, ROW, 4f, Anim.fade(Theme.ACCENT, t * 0.9f), 1f);
		}
		float textX = x + 6f;
		if (item.on != null) {
			float k = approachIn(knob, item.key, item.on ? 1f : 0f, 18f);
			GuiDraw.circle(graphics, x + 6f, y + ROW * 0.5f, 1.6f, Anim.fade(Anim.mix(0x30FFFFFF, hueShift(Theme.ACCENT), k), t));
			textX = x + 11f;
			float sw = 16f;
			float sh = 9f;
			float sx = x + w - 5f - sw;
			float sy = y + (ROW - sh) * 0.5f;
			GuiDraw.roundedFine(graphics, sx, sy, sw, sh, sh * 0.5f, Anim.fade(Anim.mix(OFF_TRACK, Theme.ACCENT, k), t));
			float r = 3f;
			float kx = sx + 1.5f + r + k * (sw - 3f - r * 2f);
			GuiDraw.circle(graphics, kx, sy + sh * 0.5f, r, Anim.fade(Anim.mix(0xFFAEB8C4, 0xFF0B0E14, k), t));
		}
		float right = x + w - 6f - (item.on != null ? 20f : 0f);
		if (item.chip != null && !item.chip.isEmpty()) {
			float cw = width(font, item.chip) + 8f;
			float cx = right - cw;
			GuiDraw.roundedFine(graphics, cx, y + 3f, cw, ROW - 6f, 3f, Anim.fade(0x14FFFFFF, t));
			text(graphics, font, item.chip, cx + 4f, y + 3f, ROW - 6f, Anim.fade(MUTED, t));
			right = cx - 4f;
		}
		boolean lit = item.on == null || item.on;
		text(graphics, font, fit(font, item.label, Math.max(8f, right - textX)), textX, y, ROW, Anim.fade(lit ? TEXT : MUTED, t));
		rowHits.add(new RowHit(item, x, y, w, ROW));
		Runnable click = item.left != null ? item.left : (item.mod != null ? () -> select(item.mod.name()) : null);
		if (click != null) {
			screen.clickHit(x, y, w, ROW, click);
		}
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
		float st = ease(selT) * t;
		float lift = (1f - ease(selT)) * 5f;
		if (mod == null) {
			text(graphics, font, "right click a module", x, y, 10f, Anim.fade(MUTED, t));
			return;
		}
		String path = label(mod.column()) + " / " + label(mod.name()).replace(' ', '-');
		text(graphics, font, fit(font, path, w), x, y + lift, 10f, Anim.fade(MUTED, st));
		Component title = ClickGui.styled(ClickGui.display(mod.name()));
		GuiDraw.text(graphics, font, title, x, y + 13f + lift, 1.25f, Anim.fade(TEXT, st), false);
		float lineY = y + 30f;
		GuiDraw.fill(graphics, x, lineY, w, 1f, Anim.fade(0x14FFFFFF, t));

		float bodyY = lineY + 6f;
		float bodyH = setY + setH - PAD - bodyY;
		if (!ClickGui.moduleHasSettings(mod)) {
			text(graphics, font, "no settings for this one", x, bodyY, ROW, Anim.fade(MUTED, st));
			return;
		}
		float content = ClickGui.moduleSettingsHeight(screen, mod);
		setScroll = Mth.clamp(setScroll, 0f, Math.max(0f, content - bodyH));
		boolean clip = GuiDraw.scissor(graphics, setX + 2f, bodyY, setW - 4f, bodyH);
		int mark = screen.clickHitMark();
		ClickGui.drawModuleSettings(screen, graphics, font, mouseX, mouseY, mod, x - 3f, bodyY - setScroll + lift, w + 6f);
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
					? "press a key"
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
				out.add(new Item("keys:edit", "Edit shortcuts", null, "open", () ->
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
		GuiDraw.text(graphics, font, ClickGui.styled(value), x, y + (h - font.lineHeight) * 0.5f + 1f, color, false);
	}

	private static float width(Font font, String value) {
		return font.width(ClickGui.styled(value));
	}

	private static String fit(Font font, String value, float max) {
		if (value == null) {
			return "";
		}
		if (width(font, value) <= max) {
			return value;
		}
		String trimmed = value;
		while (trimmed.length() > 1 && width(font, trimmed + "..") > max) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed + "..";
	}

	private static String label(String id) {
		return id == null ? "" : id.toLowerCase(Locale.ROOT);
	}

	/** 9x9 pixel icon per category, one GUI pixel per cell so it stays crisp. */
	private static void icon(GuiGraphicsExtractor graphics, String id, float x, float y, int color) {
		String[] rows = ICONS.get(id);
		if (rows == null) {
			GuiDraw.circle(graphics, x + 4.5f, y + 4.5f, 2f, color);
			return;
		}
		float ox = Math.round(x);
		float oy = Math.round(y);
		for (int r = 0; r < rows.length; r++) {
			String line = rows[r];
			int run = -1;
			for (int c = 0; c <= line.length(); c++) {
				boolean on = c < line.length() && line.charAt(c) == '#';
				if (on && run < 0) {
					run = c;
				} else if (!on && run >= 0) {
					GuiDraw.fill(graphics, ox + run, oy + r, c - run, 1f, color);
					run = -1;
				}
			}
		}
	}

	private static final Map<String, String[]> ICONS = Map.ofEntries(
		Map.entry("World", new String[]{
			"..#####..", ".#..#..#.", "#...#...#", "#########", "#...#...#",
			"#########", "#...#...#", ".#..#..#.", "..#####.."}),
		Map.entry("Visuals", new String[]{
			".........", "..#####..", ".#.....#.", "#...#...#", "#..###..#",
			"#...#...#", ".#.....#.", "..#####..", "........."}),
		Map.entry("Mobs", new String[]{
			"..#####..", ".#.....#.", "#.......#", "#.##.##.#", "#.......#",
			"#.......#", "#.......#", "#.#.#.#.#", ".#.#.#.#."}),
		Map.entry("Combat", new String[]{
			"......###", ".....#..#", "....#..#.", "#..#..#..", ".##..#...",
			".#.##....", "#.#.#....", "##...#...", "#........"}),
		Map.entry("HUD", new String[]{
			"#########", "#.......#", "#.###...#", "#.......#", "#.#####.#",
			"#.......#", "#########", "....#....", "..#####.."}),
		Map.entry("Mining", new String[]{
			"..#####..", ".#..#..#.", "#...#...#", "....#....", "....#....",
			"....#....", "....#....", "....#....", "....#...."}),
		Map.entry("Farming", new String[]{
			".##...##.", "#..#.#..#", "#...#...#", ".#..#..#.", "..#.#.#..",
			"....#....", "....#....", "....#....", "..#####.."}),
		Map.entry("Menus", new String[]{
			".........", "#########", ".........", ".........", "#########",
			".........", ".........", "#####....", "........."}),
		Map.entry("Keys", new String[]{
			".........", "#########", "#.#.#.#.#", "#########", "#.#.#.#.#",
			"#########", "#..###..#", "#########", "........."}),
		Map.entry("Theme", new String[]{
			"..#####..", ".#.....#.", "#.#...#.#", "#.......#", "#.#.....#",
			"#....##.#", ".#..#..#.", "..##...#.", "......#.."}),
		Map.entry("Player", new String[]{
			"...###...", "..#...#..", "..#...#..", "...###...", ".........",
			"..#####..", ".#.....#.", "#.......#", "#########"})
	);

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

	/** Ease-out cubic: fast start, soft landing. */
	private static float ease(float t) {
		float inv = 1f - Mth.clamp(t, 0f, 1f);
		return 1f - inv * inv * inv;
	}

	/** Second border color: the accent turned about 60 degrees back around the hue wheel (cyan to green, like the Hyprland default). */
	private static int hueShift(int argb) {
		float r = ((argb >> 16) & 0xFF) / 255f;
		float g = ((argb >> 8) & 0xFF) / 255f;
		float b = (argb & 0xFF) / 255f;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float d = max - min;
		float hue = 0f;
		if (d > 0f) {
			if (max == r) {
				hue = ((g - b) / d) % 6f;
			} else if (max == g) {
				hue = (b - r) / d + 2f;
			} else {
				hue = (r - g) / d + 4f;
			}
			hue /= 6f;
		}
		float sat = max == 0f ? 0f : d / max;
		hue = (hue + 0.83f) % 1f;
		float h6 = hue * 6f;
		float c = max * sat;
		float x = c * (1f - Math.abs(h6 % 2f - 1f));
		float m = max - c;
		float[] rgb = switch ((int) h6) {
			case 0 -> new float[]{c, x, 0f};
			case 1 -> new float[]{x, c, 0f};
			case 2 -> new float[]{0f, c, x};
			case 3 -> new float[]{0f, x, c};
			case 4 -> new float[]{x, 0f, c};
			default -> new float[]{c, 0f, x};
		};
		int ri = Math.round((rgb[0] + m) * 255f);
		int gi = Math.round((rgb[1] + m) * 255f);
		int bi = Math.round((rgb[2] + m) * 255f);
		return (argb & 0xFF000000) | (ri << 16) | (gi << 8) | bi;
	}

	private static boolean contains(double x, double y, float rx, float ry, float rw, float rh) {
		return x >= rx && y >= ry && x < rx + rw && y < ry + rh;
	}

	private record Item(String key, String label, Boolean on, String chip, Runnable left, ClickGui.Mod mod) {
	}

	private record RowHit(Item item, float x, float y, float w, float h) {
	}
}
