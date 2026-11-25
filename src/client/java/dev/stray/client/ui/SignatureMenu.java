package dev.stray.client.ui;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.HudStats;
import dev.stray.client.render.TextMetrics;
import dev.stray.client.util.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The click GUI in the Stray look: one window with a search bar on top, the categories down
 * the left (numbered), the selected category's features in the middle and the picked feature's
 * settings on the right, with a status line along the bottom.
 *
 * <p>Motion: the window rises and fades in when it opens and sinks away when it closes, the
 * category marker glides to the one you pick, rows come in one after another, hover fades in
 * and out, switches move on a spring, and the settings side slides in when you pick a feature.
 * Reduce motion (Look) turns all of it off.
 */
public final class SignatureMenu {
	// Everything sits on a 4-unit grid: 12 padding, 20 rows, 4 / 8 / 12 gaps.
	private static final float W_MAX = 560f;
	private static final float H_MAX = 360f;
	private static final float MARGIN = 16f;
	private static final float TOP_H = 32f;
	private static final float STATUS_H = 20f;
	private static final float RAIL_W = 116f;
	private static final float LIST_W = 168f;
	private static final float HEAD_H = 28f;
	private static final float ROW = 20f;
	private static final float FIELD_H = 16f;
	private static final float PAD = 12f;
	private static final float GAP = 8f;
	/** List switches: the same size as the ones in the settings. */
	private static final float SWITCH_W = 18f;
	private static final float SWITCH_H = 10f;
	private static final float OPEN_S = 0.32f;
	private static final float CLOSE_S = 0.14f;
	private static final float ROW_S = 0.2f;
	private static final float ROW_STAGGER = 0.018f;
	private static final float PICK_S = 0.18f;

	private static boolean drawing;
	private static float unit = 1f;
	private static String category = ClickGui.ORDER[0];
	private static String selected;
	private static float listScroll;
	private static float setScroll;
	private static final float[] listBox = new float[4];
	private static final float[] setBox = new float[4];
	private static final List<RowHit> rowHits = new ArrayList<>();

	private static Object fadeScreen;
	private static long openedAt;
	private static long closingAt;
	private static long lastFrame;
	private static long categoryAt;
	private static long pickedAt;
	private static float fade = 1f;
	private static float dt;
	private static float railY = Float.NaN;
	private static final Map<String, Float> hovers = new HashMap<>();
	/** How tall each feature's settings really drew last time (open dropdowns included). */
	private static final Map<String, Float> drawnHeight = new HashMap<>();

	private SignatureMenu() {
	}

	public static boolean active() {
		return Looks.signature();
	}

	/** True while this menu draws, so shared setting widgets switch to the Stray look. */
	public static boolean drawing() {
		return drawing;
	}

	/** Menu units per GUI unit: the menu scale setting, smaller when the window wouldn't fit. */
	static float unit() {
		Minecraft client = Minecraft.getInstance();
		float w = client == null || client.getWindow() == null ? 960f : client.getWindow().getGuiScaledWidth();
		float h = client == null || client.getWindow() == null ? 540f : client.getWindow().getGuiScaledHeight();
		float wanted = StrayConfig.normalizeMenuScale(StrayConfig.get().menuScale) / 0.75f;
		return Math.min(wanted, Math.min(w / (W_MAX + MARGIN * 2f), h / (H_MAX + MARGIN * 2f)));
	}

	/** Done closing: StrayScreen may finish. */
	static boolean faded(Object screen) {
		return screen != fadeScreen || fade <= 0.001f;
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
		if (screen != fadeScreen || now - lastFrame > 250_000_000L) {
			fadeScreen = screen;
			openedAt = now;
			closingAt = 0L;
			lastFrame = now;
			categoryAt = now;
			pickedAt = now;
			railY = Float.NaN;
			hovers.clear();
		}
		dt = Math.min(0.05f, (now - lastFrame) / 1.0e9f);
		lastFrame = now;
		boolean closing = screen.clickClosing();
		if (closing && closingAt == 0L) {
			closingAt = now;
		} else if (!closing) {
			closingAt = 0L;
		}
		boolean still = still();
		float in = still ? 1f : SignatureLook.ease(seconds(now, openedAt) / OPEN_S);
		float out = closingAt == 0L ? 0f : still ? 1f : Mth.clamp(seconds(now, closingAt) / CLOSE_S, 0f, 1f);
		fade = closingAt != 0L && out >= 1f ? 0f : Math.min(in, 1f - out * out);

		drawing = true;
		rowHits.clear();
		graphics.pose().pushMatrix();
		graphics.pose().scale(unit, unit);
		try {
			GuiDraw.alphaScale = fade;
			GuiDraw.fill(graphics, 0, 0, sw, sh, 0x8C000000);
			float w = Math.min(W_MAX, sw - MARGIN * 2f);
			float h = Math.min(H_MAX, sh - MARGIN * 2f);
			float x = Math.round((sw - w) / 2f);
			// Rises 10 into place on open, sinks 6 on close.
			float y = Math.round((sh - h) / 2f) + (1f - in) * 10f + out * 6f;
			float cx = x + w / 2f;
			float cy = y + h / 2f;
			float s = 0.975f + 0.025f * in;
			graphics.pose().pushMatrix();
			graphics.pose().translate(cx, cy);
			graphics.pose().scale(s, s);
			graphics.pose().translate(-cx, -cy);
			try {
				ShadowLook.shadow(graphics, x, y, w, h, SignatureLook.WINDOW_R, 18, 0x90);
				GuiDraw.roundedFine(graphics, x, y, w, h, SignatureLook.WINDOW_R, SignatureLook.BG);
				GuiDraw.hairline(graphics, x, y, w, h, SignatureLook.WINDOW_R, SignatureLook.LINE);
				topBar(screen, graphics, font, mx, my, x, y, w);
				float by = y + TOP_H;
				float bh = h - TOP_H - STATUS_H;
				List<MenuItems.Item> items = MenuItems.items(screen, category);
				keepSelection(items);
				rail(screen, graphics, font, mx, my, x, by, bh, now);
				list(screen, graphics, font, mx, my, items, x + RAIL_W, by, LIST_W, bh, now);
				settings(screen, graphics, font, mx, my, x + RAIL_W + LIST_W, by, w - RAIL_W - LIST_W, bh, now);
				status(graphics, font, x, y + h - STATUS_H, w);
				GuiDraw.alphaScale = fade;
				screen.clickPicker(graphics, font);
			} finally {
				graphics.pose().popMatrix();
			}
		} finally {
			GuiDraw.alphaScale = 1f;
			graphics.pose().popMatrix();
			drawing = false;
		}
	}

	/** The wordmark, the search field and the version. */
	private static void topBar(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mx, int my, float x, float y, float w) {
		float ty = titleY(font, y, TOP_H);
		GuiDraw.title(graphics, font, "stray", x + PAD, ty, SignatureLook.TEXT);
		float bw = GuiDraw.titleWidth(font, "stray");
		GuiDraw.title(graphics, font, ".", x + PAD + bw, ty, SignatureLook.accent());
		bw += GuiDraw.titleWidth(font, ".");

		String version = Utils.modVersion() + "  " + (dev.stray.Edition.LEGIT ? "legit" : "full");
		float vw = GuiDraw.smallWidth(font, version);
		GuiDraw.small(graphics, font, version, x + w - PAD - vw, smallY(font, y, TOP_H), SignatureLook.FAINT);

		// The search field lines up with the list column, and stops one gap before the version.
		float fx = Math.max(x + PAD + bw + PAD, x + RAIL_W + PAD);
		float fw = x + w - PAD - vw - PAD - fx;
		float fy = y + (TOP_H - FIELD_H) / 2f;
		boolean focused = ClickGui.searchFocused();
		SignatureLook.field(graphics, fx, fy, fw, FIELD_H, hot(mx, my, fx, fy, fw, FIELD_H), focused);
		float iconX = fx + 6f;
		GuiDraw.icon(graphics, font, MenuFont.SEARCH, iconX, iconY(font, MenuFont.SEARCH, fy, FIELD_H), focused ? SignatureLook.SOFT : SignatureLook.FAINT);
		float tx = iconX + GuiDraw.iconWidth(font, MenuFont.SEARCH) + 4f;
		float textY = menuY(font, fy, FIELD_H);
		String query = ClickGui.filterText();
		if (query.isEmpty() && !focused) {
			GuiDraw.menu(graphics, font, "Search " + ClickGui.modules().size() + " features", tx, textY, SignatureLook.FAINT);
			String keys = "Ctrl F";
			float kh = FIELD_H - 6f;
			float kw = GuiDraw.smallWidth(font, keys) + 8f;
			float kx = fx + fw - kw - 3f;
			float ky = fy + 3f;
			GuiDraw.roundedFine(graphics, kx, ky, kw, kh, 2f, SignatureLook.PICKED);
			GuiDraw.small(graphics, font, keys, kx + 4f, smallY(font, ky, kh), SignatureLook.MUTED);
		} else {
			String shown = GuiDraw.ellipsize(font, query, fx + fw - 8f - tx, false);
			GuiDraw.menu(graphics, font, shown, tx, textY, SignatureLook.TEXT);
			if (focused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
				GuiDraw.fillSmooth(graphics, tx + GuiDraw.menuWidth(font, shown) + 1f, fy + 4f, 1f, FIELD_H - 8f, SignatureLook.accent());
			}
		}
		ClickGui.searchBox(fx, fy, fw, FIELD_H);
		screen.clickHit(fx, fy, fw, FIELD_H, ClickGui::focusSearch);
		GuiDraw.fillSmooth(graphics, x, y + TOP_H - line(), w, line(), SignatureLook.LINE);
	}

	/** The categories, numbered, with the accent marker gliding to the picked one. */
	private static void rail(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mx, int my, float x, float y, float h, long now) {
		String[] ids = ClickGui.ORDER;
		// 20 per row, or less (in steps of 2) when the window is short.
		float rowH = Math.min(ROW, (float) Math.floor((h - GAP * 2f) / ids.length / 2f) * 2f);
		float top = y + GAP;
		boolean searching = searching();
		int picked = -1;
		for (int i = 0; i < ids.length; i++) {
			if (ids[i].equals(category)) {
				picked = i;
			}
		}
		float targetY = top + picked * rowH;
		railY = Float.isNaN(railY) || still() ? targetY : SignatureLook.exp(railY, targetY, 20f, dt);
		float markX = x + 4f;
		float markW = RAIL_W - 8f;
		if (picked >= 0 && !searching) {
			GuiDraw.roundedFine(graphics, markX, railY, markW, rowH, SignatureLook.CONTROL_R, SignatureLook.RAISED);
			GuiDraw.roundedFine(graphics, markX, railY + 4f, 2f, rowH - 8f, 1f, SignatureLook.accent());
		}
		float numberX = x + PAD;
		float labelX = numberX + GuiDraw.smallWidth(font, "00") + GAP;
		for (int i = 0; i < ids.length; i++) {
			String id = ids[i];
			float ry = top + i * rowH;
			boolean on = i == picked && !searching;
			float hover = hover("rail:" + id, hot(mx, my, x, ry, RAIL_W, rowH));
			String number = (i < 9 ? "0" : "") + (i + 1);
			GuiDraw.small(graphics, font, number, numberX, smallY(font, ry, rowH), on ? SignatureLook.accent() : SignatureLook.FAINT);
			int ink = on ? SignatureLook.TEXT : Anim.mix(SignatureLook.MUTED, SignatureLook.TEXT, hover);
			String label = GuiDraw.ellipsize(font, MenuItems.label(id), x + RAIL_W - PAD - labelX, false);
			GuiDraw.menu(graphics, font, label, labelX, menuY(font, ry, rowH), ink);
			screen.clickHit(x, ry, RAIL_W, rowH, () -> pickCategory(id));
		}
		GuiDraw.fillSmooth(graphics, x + RAIL_W - line(), y, line(), h, SignatureLook.LINE);
	}

	/** The category's features: a big title, how many are on, and the rows. */
	private static void list(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mx, int my, List<MenuItems.Item> items, float x, float y, float w, float h, long now) {
		float base = GuiDraw.alphaScale;
		float since = seconds(now, categoryAt);
		float headT = still() ? 1f : SignatureLook.ease(since / ROW_S);
		GuiDraw.alphaScale = base * headT;
		String title = searching() ? "Results" : MenuItems.label(category);
		float countRoom = 0f;
		int on = 0;
		int toggles = 0;
		for (MenuItems.Item item : items) {
			if (item.on() != null) {
				toggles++;
				if (item.on()) {
					on++;
				}
			}
		}
		if (toggles > 0) {
			String count = on + "/" + toggles + " on";
			countRoom = GuiDraw.smallWidth(font, count) + GAP;
			GuiDraw.small(graphics, font, count, x + w - PAD - GuiDraw.smallWidth(font, count), smallY(font, y, HEAD_H), SignatureLook.FAINT);
		}
		String shownTitle = GuiDraw.ellipsize(font, title, w - PAD * 2f - countRoom, false);
		GuiDraw.title(graphics, font, shownTitle, x + PAD - (1f - headT) * 6f, titleY(font, y, HEAD_H), SignatureLook.TEXT);
		GuiDraw.alphaScale = base;

		float top = y + HEAD_H;
		if ("Mobs".equals(category) && !searching()) {
			float fx = x + PAD;
			float fw = w - PAD * 2f;
			boolean focused = ClickGui.mobSearchFocused();
			SignatureLook.field(graphics, fx, top, fw, FIELD_H, hot(mx, my, fx, top, fw, FIELD_H), focused);
			String q = ClickGui.mobQuery();
			boolean empty = q.isEmpty() && !focused;
			String shown = empty ? "Find mobs" : q + (focused && (System.currentTimeMillis() / 500L) % 2L == 0L ? "|" : "");
			GuiDraw.menu(graphics, font, GuiDraw.ellipsize(font, shown, fw - 12f, false), fx + 6f, menuY(font, top, FIELD_H), empty ? SignatureLook.FAINT : SignatureLook.TEXT);
			ClickGui.mobSearchBox(fx, top, fw, FIELD_H);
			screen.clickHit(fx, top, fw, FIELD_H, ClickGui::focusMobSearch);
			top += FIELD_H + GAP;
		} else {
			ClickGui.mobSearchBox(0, 0, 0, 0);
		}
		float viewH = y + h - top;
		listBox[0] = x;
		listBox[1] = top;
		listBox[2] = w;
		listBox[3] = viewH;
		float content = items.size() * ROW;
		listScroll = Mth.clamp(listScroll, 0f, Math.max(0f, content - viewH));
		GuiDraw.fillSmooth(graphics, x, top - line(), w - line(), line(), SignatureLook.LINE);
		boolean clip = GuiDraw.scissor(graphics, x, top, w, viewH);
		int mark = screen.clickHitMark();
		for (int i = 0; i < items.size(); i++) {
			MenuItems.Item item = items.get(i);
			float ry = top + i * ROW - listScroll;
			if (ry + ROW < top || ry > top + viewH) {
				continue;
			}
			// Rows come in one after another when the category changes.
			float rowT = still() ? 1f : SignatureLook.ease((since - i * ROW_STAGGER) / ROW_S);
			if (rowT <= 0.001f) {
				continue;
			}
			GuiDraw.alphaScale = base * rowT;
			float shift = (1f - rowT) * 8f;
			boolean chosen = item.mod() != null && item.mod().name().equals(selected);
			boolean rowHot = hot(mx, my, x, ry, w, ROW) && hot(mx, my, x, top, w, viewH);
			float hover = hover("row:" + item.key(), rowHot);
			if (chosen) {
				GuiDraw.fillSmooth(graphics, x, ry, w - line(), ROW, SignatureLook.PICKED);
				GuiDraw.fillSmooth(graphics, x, ry + 4f, 2f, ROW - 8f, SignatureLook.accent());
			} else if (hover > 0.01f) {
				GuiDraw.fillSmooth(graphics, x, ry, w - line(), ROW, Anim.fade(SignatureLook.RAISED, hover));
			}
			GuiDraw.fillSmooth(graphics, x, ry + ROW - line(), w - line(), line(), SignatureLook.LINE);
			float right = x + w - PAD;
			if (item.on() != null) {
				float tx = right - SWITCH_W;
				SignatureLook.toggle(graphics, tx, ry + (ROW - SWITCH_H) / 2f, SWITCH_W, SWITCH_H, SignatureLook.spring("row:" + item.key(), item.on()));
				right = tx - GAP;
			} else if (item.chip() != null && !item.chip().isEmpty()) {
				String chip = item.chip();
				float cw = GuiDraw.smallWidth(font, chip);
				GuiDraw.small(graphics, font, chip, right - cw, smallY(font, ry, ROW), SignatureLook.MUTED);
				right -= cw + GAP;
			}
			String label = GuiDraw.ellipsize(font, item.label(), right - (x + PAD) - shift, false);
			GuiDraw.menu(graphics, font, label, x + PAD + shift, menuY(font, ry, ROW), chosen || item.on() == Boolean.TRUE ? SignatureLook.TEXT : SignatureLook.SOFT);
			rowHits.add(new RowHit(item, x, ry, w, ROW));
			screen.clickHit(x, ry, w, ROW, () -> clickRow(item));
			if (item.on() != null && item.left() != null) {
				screen.clickHit(x + w - PAD - SWITCH_W - 4f, ry, PAD + SWITCH_W + 4f, ROW, item.left());
			}
		}
		GuiDraw.alphaScale = base;
		screen.clickClipHits(mark, x, top, w, viewH);
		if (clip) {
			GuiDraw.disableScissor(graphics);
		}
		if (items.isEmpty()) {
			String empty = searching() ? "Nothing matches" : "Nothing here";
			GuiDraw.menu(graphics, font, empty, x + PAD, menuY(font, top, ROW), SignatureLook.FAINT);
		}
		scrollbar(graphics, x + w - 4f, top, viewH, content, listScroll);
		GuiDraw.fillSmooth(graphics, x + w - line(), y, line(), h, SignatureLook.LINE);
	}

	/** The picked feature: where it lives, its name and switch, what it does, its settings. */
	private static void settings(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mx, int my, float x, float y, float w, float h, long now) {
		ClickGui.Mod mod = find(selected);
		setBox[0] = x;
		setBox[1] = y;
		setBox[2] = w;
		setBox[3] = h;
		if (mod == null) {
			String hint = searching() ? "Pick a result to see its settings" : "Pick a feature to see its settings";
			float hw = GuiDraw.menuWidth(font, hint);
			GuiDraw.menu(graphics, font, hint, x + (w - hw) / 2f, menuY(font, y, h), SignatureLook.FAINT);
			return;
		}
		float base = GuiDraw.alphaScale;
		float pickT = still() ? 1f : SignatureLook.ease(seconds(now, pickedAt) / PICK_S);
		GuiDraw.alphaScale = base * pickT;
		float ix = x + PAD + (1f - pickT) * 6f;
		float iw = w - PAD * 2f;
		// One header row, the list header's height: the name, and where it lives on the right.
		String path = MenuItems.label(mod.column()).toLowerCase(Locale.ROOT);
		float pathW = GuiDraw.smallWidth(font, path);
		GuiDraw.small(graphics, font, path, x + w - PAD - pathW, smallY(font, y, HEAD_H), SignatureLook.FAINT);
		String name = GuiDraw.ellipsize(font, ClickGui.display(mod.name()), iw - pathW - GAP, false);
		GuiDraw.title(graphics, font, name, ix, titleY(font, y, HEAD_H), SignatureLook.TEXT);
		float sy = y + HEAD_H;
		GuiDraw.fillSmooth(graphics, x, sy - line(), w, line(), SignatureLook.LINE);
		sy = wrap(graphics, font, ClickGui.description(mod), ix, sy + GAP, iw, 3);
		if (sy > y + HEAD_H + GAP) {
			sy += GAP;
		}
		float viewH = y + h - PAD - sy;
		if (!ClickGui.moduleHasSettings(mod)) {
			GuiDraw.menu(graphics, font, "No settings for this feature", ix, menuY(font, sy, ROW), SignatureLook.FAINT);
			GuiDraw.alphaScale = base;
			return;
		}
		// The estimate leaves out open dropdowns: what was really drawn last frame counts too.
		float content = Math.max(ClickGui.moduleSettingsHeight(screen, mod), drawnHeight.getOrDefault(mod.name(), 0f));
		setScroll = Mth.clamp(setScroll, 0f, Math.max(0f, content - viewH));
		setBox[1] = sy;
		setBox[3] = viewH;
		boolean clip = GuiDraw.scissor(graphics, x + 1f, sy, w - 2f, viewH);
		int mark = screen.clickHitMark();
		try {
			float top = sy - setScroll;
			ClickGui.drawModuleSettings(screen, graphics, font, mx, my, mod, ix, top, iw - (content > viewH ? GAP : 0f));
			drawnHeight.put(mod.name(), screen.clickHitsBottom(mark, top) - top);
		} finally {
			screen.clickClipHits(mark, x + 1f, sy, w - 2f, viewH);
			if (clip) {
				GuiDraw.disableScissor(graphics);
			}
			GuiDraw.alphaScale = base;
		}
		scrollbar(graphics, x + w - 4f, sy, viewH, content, setScroll);
	}

	/** Server, ping and frame rate on the left, how to close on the right. */
	private static void status(GuiGraphicsExtractor graphics, Font font, float x, float y, float w) {
		GuiDraw.fillSmooth(graphics, x, y, w, line(), SignatureLook.LINE);
		ServerData server = Minecraft.getInstance().getCurrentServer();
		String where = server == null ? "singleplayer" : server.ip.toLowerCase(Locale.ROOT);
		String[] parts = {where, HudStats.ping() + " ms", HudStats.fps() + " fps"};
		float tx = x + PAD;
		float ty = smallY(font, y, STATUS_H);
		for (String part : parts) {
			GuiDraw.small(graphics, font, part, tx, ty, SignatureLook.FAINT);
			tx += GuiDraw.smallWidth(font, part) + PAD;
		}
		String close = "Esc to close";
		GuiDraw.small(graphics, font, close, x + w - PAD - GuiDraw.smallWidth(font, close), ty, SignatureLook.FAINT);
	}

	// ------------------------------------------------------------------ text placement

	/** The y for body text centred in a box. */
	private static float menuY(Font font, float y, float h) {
		return TextMetrics.centered(font, MenuFont.shadow("H"), y, h);
	}

	private static float smallY(Font font, float y, float h) {
		return TextMetrics.centered(font, MenuFont.shadowSmall("H"), y, h);
	}

	private static float titleY(Font font, float y, float h) {
		return TextMetrics.centered(font, MenuFont.shadowTitle("H"), y, h);
	}

	/** Icons are drawn half a unit lower by {@link GuiDraw#icon}; this undoes that. */
	private static float iconY(Font font, String glyph, float y, float h) {
		return TextMetrics.centered(font, MenuFont.icon(glyph), y, h) - 0.5f;
	}

	// ------------------------------------------------------------------ pieces

	/** Wraps text into at most {@code lines} lines; returns the y under it. */
	private static float wrap(GuiGraphicsExtractor graphics, Font font, String text, float x, float y, float w, int lines) {
		if (text == null || text.isBlank()) {
			return y;
		}
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String next = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && GuiDraw.menuWidth(font, next) > w) {
				out.add(line.toString());
				line.setLength(0);
				line.append(word);
			} else {
				line.setLength(0);
				line.append(next);
			}
		}
		if (!line.isEmpty()) {
			out.add(line.toString());
		}
		for (int i = 0; i < out.size() && i < lines; i++) {
			String shown = i == lines - 1 && out.size() > lines ? GuiDraw.ellipsize(font, out.get(i) + " ...", w, false) : out.get(i);
			GuiDraw.menu(graphics, font, shown, x, menuY(font, y, 12f), SignatureLook.MUTED);
			y += 12f;
		}
		return y;
	}

	private static void scrollbar(GuiGraphicsExtractor graphics, float x, float y, float h, float content, float scroll) {
		if (content <= h + 0.5f) {
			return;
		}
		float thumb = Math.max(16f, h * h / content);
		float ty = y + (h - thumb) * (scroll / (content - h));
		GuiDraw.roundedFine(graphics, x, ty, 2f, thumb, 1f, SignatureLook.LINE_HOT);
	}

	/** One screen pixel in menu units: the hairlines. */
	private static float line() {
		return GuiDraw.pixel() / unit;
	}

	/** A hover that fades in and out instead of switching. */
	private static float hover(String key, boolean on) {
		float now = hovers.getOrDefault(key, 0f);
		float next = still() ? (on ? 1f : 0f) : SignatureLook.exp(now, on ? 1f : 0f, 16f, dt);
		if (next <= 0.001f) {
			hovers.remove(key);
		} else {
			hovers.put(key, next);
		}
		return next;
	}

	/** Reduce motion (Look): everything jumps straight to where it ends. */
	private static boolean still() {
		return ExMotion.reduced();
	}

	private static float seconds(long now, long since) {
		return (now - since) / 1.0e9f;
	}

	private static boolean hot(double mx, double my, float x, float y, float w, float h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	// ------------------------------------------------------------------ state

	private static boolean searching() {
		return !ClickGui.filterText().isBlank();
	}

	private static void pickCategory(String id) {
		if (searching()) {
			ClickGui.clearSearch();
		}
		if (id.equals(category)) {
			return;
		}
		if ("Mobs".equals(category)) {
			ClickGui.blurMobSearch();
		}
		category = id;
		listScroll = 0f;
		categoryAt = System.nanoTime();
	}

	/** A row: features open on the right; loose items (key binds, actions, mobs) do their thing. */
	private static void clickRow(MenuItems.Item item) {
		if (item.mod() != null) {
			select(item.mod().name());
			return;
		}
		if (item.left() != null) {
			item.left().run();
		}
	}

	private static void select(String name) {
		if (name == null || name.equals(selected)) {
			return;
		}
		selected = name;
		setScroll = 0f;
		pickedAt = System.nanoTime();
	}

	private static void keepSelection(List<MenuItems.Item> items) {
		for (MenuItems.Item item : items) {
			if (item.mod() != null && item.mod().name().equals(selected)) {
				return;
			}
		}
		for (MenuItems.Item item : items) {
			if (item.mod() != null) {
				select(item.mod().name());
				return;
			}
		}
		selected = null;
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

	static boolean scroll(double x, double y, double wheel) {
		if (wheel == 0) {
			return false;
		}
		float step = (float) wheel * ROW * 2f;
		if (hot(x, y, listBox[0], listBox[1], listBox[2], listBox[3])) {
			listScroll = Math.max(0f, listScroll - step);
			return true;
		}
		if (hot(x, y, setBox[0], setBox[1], setBox[2], setBox[3])) {
			setScroll = Math.max(0f, setScroll - step);
			return true;
		}
		return false;
	}

	/** Right click on a row picks it, like a left click on its name. */
	static void rightClick(double x, double y) {
		for (RowHit hit : rowHits) {
			if (hit.item.mod() != null && hot(x, y, hit.x, hit.y, hit.w, hit.h)) {
				select(hit.item.mod().name());
				return;
			}
		}
	}

	/** Opens a feature by name (the Theme settings' shortcuts). */
	static void open(String name) {
		ClickGui.Mod mod = find(name);
		if (mod != null) {
			pickCategory(mod.column());
			select(mod.name());
		}
	}

	private record RowHit(MenuItems.Item item, float x, float y, float w, float h) {
	}
}
