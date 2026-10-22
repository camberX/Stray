package dev.stray.client.ui;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.config.UnloadState;
import dev.stray.client.combat.OdinClicks;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.MobCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One Dear ImGui window: title bar, menu bar, dropdown menus.
 * A menu item is a checkmark plus a label. An arrow opens a settings menu
 * beside it. This is {@code BeginMenuBar} / {@code BeginMenu} / {@code MenuItem},
 * not a column of module rows.
 */
final class ImGuiMenu {
	private static final float TITLE = 18f;
	private static final float BAR = 16f;
	private static final float ITEM = 16f;
	private static final float PAD = 8f;

	private static float winX;
	private static float winY;
	private static float winW = 460f;
	private static float winH = 250f;
	private static boolean placed;
	private static boolean collapsed;
	private static boolean moving;
	private static boolean resizing;
	private static float moveOffX;
	private static float moveOffY;
	private static float resizeOffW;
	private static float resizeOffH;

	private static String openMenu;
	private static String openSub;
	private static float menuScroll;
	private static float subScroll;
	private static float bodyScroll;
	private static boolean enabledOpen = true;

	private static float popX;
	private static float popY;
	private static float popW;
	private static float popH;
	private static float subX;
	private static float subY;
	private static float subW;
	private static float subH;
	private static float bodyX;
	private static float bodyY;
	private static float bodyW;
	private static float bodyH;
	private static final float[] menuAnchor = new float[ClickGui.ORDER.length];

	private ImGuiMenu() {
	}

	static void closePopups() {
		openMenu = null;
		openSub = null;
	}

	static void draw(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		place(screen, font);
		winX = Mth.clamp(winX, -winW + 80f, Math.max(0, screen.width - 48));
		winY = Mth.clamp(winY, 0, Math.max(0, screen.height - TITLE));
		float shown = collapsed ? TITLE : winH;
		ImGuiLook.window(graphics, winX, winY, winW, shown);
		ImGuiLook.titleBar(graphics, winX, winY, winW, TITLE);
		boolean openArrow = !collapsed;
		arrow(graphics, winX + 8f, winY + 7f, openArrow);
		label(graphics, font, "Stray", winX + 22f, winY, TITLE - 2f, ImGuiLook.TEXT);
		float closeX = winX + winW - 20f;
		float closeY = winY + 3f;
		cross(graphics, closeX + 3f, closeY + 3f, mouseOver(mouseX, mouseY, closeX, closeY, 16f, 16f));
		screen.clickHit(closeX, closeY, 16f, 16f, () -> {
			StrayConfig.get().clickGui = false;
			UnloadState.markDirty();
		});
		screen.clickHit(winX + 2f, winY + 2f, 18f, TITLE - 4f, () -> {
			collapsed = !collapsed;
			closePopups();
		});
		screen.clickHit(winX + 22f, winY, Math.max(8f, winW - 46f), TITLE, ImGuiMenu::beginMove);

		if (collapsed) {
			return;
		}
		grip(graphics, winX + winW, winY + winH);
		float grip = 14f;
		screen.clickHit(winX + winW - grip, winY + winH - grip, grip, grip, ImGuiMenu::beginResize);

		drawBar(screen, graphics, font, mouseX, mouseY);
		drawBody(screen, graphics, font, mouseX, mouseY);
		if (openMenu != null) {
			drawMenu(screen, graphics, font, mouseX, mouseY);
		} else {
			openSub = null;
		}
	}

	static boolean drag(double x, double y) {
		if (moving) {
			winX = (float) x - moveOffX;
			winY = (float) y - moveOffY;
			return true;
		}
		if (resizing) {
			winW = Math.max(280f, (float) x - winX + resizeOffW);
			winH = Math.max(160f, (float) y - winY + resizeOffH);
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
		float step = (float) wheel * ITEM;
		if (openSub != null && mouseOver(x, y, subX, subY, subW, subH)) {
			subScroll = Math.max(0f, subScroll - step);
			return true;
		}
		if (openMenu != null && mouseOver(x, y, popX, popY, popW, popH)) {
			menuScroll = Math.max(0f, menuScroll - step);
			return true;
		}
		if (mouseOver(x, y, bodyX, bodyY, bodyW, bodyH)) {
			bodyScroll = Math.max(0f, bodyScroll - step);
			return true;
		}
		return false;
	}

	private static void place(StrayScreen screen, Font font) {
		if (placed) {
			return;
		}
		float labels = 4f;
		for (String id : ClickGui.ORDER) {
			labels += width(font, id) + 12f;
		}
		float wantW = labels + 72f + 20f;
		float maxW = Math.max(280f, screen.width - 24f);
		float maxH = Math.max(160f, screen.height - 48f);
		winW = Math.min(Math.max(wantW, 280f), maxW);
		winH = Math.min(250f, maxH);
		winX = (screen.width - winW) * 0.5f;
		winY = (screen.height - winH) * 0.5f;
		placed = true;
	}

	private static void beginMove() {
		moving = true;
		moveOffX = (float) ClickGui.pointerX() - winX;
		moveOffY = (float) ClickGui.pointerY() - winY;
	}

	private static void beginResize() {
		resizing = true;
		resizeOffW = winX + winW - (float) ClickGui.pointerX();
		resizeOffH = winY + winH - (float) ClickGui.pointerY();
	}

	private static void drawBar(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		float y = winY + TITLE;
		GuiDraw.fill(graphics, winX + 1f, y, winW - 2f, BAR, ImGuiLook.MENU_BAR_BG);
		GuiDraw.fill(graphics, winX + 1f, y + BAR - 1f, winW - 2f, 1f, ImGuiLook.BORDER);

		float labels = 4f;
		for (String id : ClickGui.ORDER) {
			labels += width(font, id) + 12f;
		}
		float filterW = Math.min(132f, Math.max(72f, winW - labels - 16f));
		float filterX = winX + winW - PAD - filterW;
		float filterY = y + 2f;
		float limit = filterX - 4f;
		float cursor = winX + 4f;
		String hovered = null;
		for (int i = 0; i < ClickGui.ORDER.length; i++) {
			String id = ClickGui.ORDER[i];
			float tw = width(font, id) + 12f;
			float room = limit - cursor;
			if (room < 8f) {
				break;
			}
			if (tw > room) {
				tw = room;
			}
			menuAnchor[i] = cursor;
			boolean hot = mouseOver(mouseX, mouseY, cursor, y, tw, BAR);
			boolean on = id.equals(openMenu);
			if (hot) {
				hovered = id;
			}
			if (hot || on) {
				GuiDraw.fill(graphics, cursor, y + 2f, tw, BAR - 4f, on ? ImGuiLook.HEADER : ImGuiLook.HEADER_HOVERED);
			}
			label(graphics, font, fit(font, id, Math.max(4f, tw - 12f)), cursor + 8f, y, BAR, ImGuiLook.TEXT);
			String pick = id;
			screen.clickHit(cursor, y, tw, BAR, () -> {
				if (pick.equals(openMenu)) {
					openMenu = null;
					openSub = null;
				} else {
					openMenu = pick;
					openSub = null;
					menuScroll = 0f;
				}
			});
			cursor += tw;
		}
		if (openMenu != null && hovered != null && !hovered.equals(openMenu)) {
			openMenu = hovered;
			openSub = null;
			menuScroll = 0f;
		}

		boolean filterHot = mouseOver(mouseX, mouseY, filterX, filterY, filterW, BAR - 4f);
		ImGuiLook.frame(graphics, filterX, filterY, filterW, BAR - 4f, filterHot, ClickGui.searchFocused());
		ClickGui.searchBox(filterX, filterY, filterW, BAR - 4f);
		String query = ClickGui.filterText();
		boolean empty = query.isEmpty() && !ClickGui.searchFocused();
		String shown = empty ? "Filter" : query + (ClickGui.searchFocused() ? "|" : "");
		label(graphics, font, fit(font, shown, filterW - 10f), filterX + 5f, filterY, BAR - 4f, empty ? ImGuiLook.TEXT_DISABLED : ImGuiLook.TEXT);
		screen.clickHit(filterX, filterY, filterW, BAR - 4f, ClickGui::focusSearch);
	}

	private static void drawBody(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		bodyX = winX + 1f;
		bodyY = winY + TITLE + BAR;
		bodyW = winW - 2f;
		bodyH = winH - TITLE - BAR - 1f;
		float x = winX + PAD;
		float y = bodyY + 6f;
		label(graphics, font, "Stray", x, y, 16f, ImGuiLook.TEXT);
		GuiDraw.fill(graphics, x, y + 18f, winW - PAD * 2f, 1f, ImGuiLook.BORDER);
		label(graphics, font, "Open a menu above. A check is on. An arrow opens settings.", x, y + 22f, 16f, ImGuiLook.TEXT_DISABLED);

		String query = ClickGui.filterText().trim().toLowerCase(Locale.ROOT);
		float listTop = y + 46f;
		float listH = Math.max(ITEM, winY + winH - 10f - listTop);
		if (!query.isEmpty()) {
			List<ClickGui.Mod> found = new ArrayList<>();
			for (ClickGui.Mod mod : ClickGui.modules()) {
				if (ClickGui.display(mod.name()).toLowerCase(Locale.ROOT).contains(query)) {
					found.add(mod);
				}
			}
			float max = Math.max(0f, found.size() * ITEM - listH);
			bodyScroll = Mth.clamp(bodyScroll, 0f, max);
			boolean clip = GuiDraw.scissor(graphics, x, listTop, winW - PAD * 2f, listH);
			if (found.isEmpty()) {
				label(graphics, font, "No matches", x, listTop, ITEM, ImGuiLook.TEXT_DISABLED);
			}
			for (int i = 0; i < found.size(); i++) {
				float row = listTop - bodyScroll + i * ITEM;
				if (row + ITEM <= listTop || row >= listTop + listH) {
					continue;
				}
				ClickGui.Mod mod = found.get(i);
				item(
					screen,
					graphics,
					font,
					x,
					row,
					winW - PAD * 2f,
					ClickGui.display(mod.name()),
					!mod.hold() && mod.on().getAsBoolean(),
					false,
					ClickGui.display(mod.column()),
					mouseOver(mouseX, mouseY, x, row, winW - PAD * 2f, ITEM),
					() -> {
						if (!mod.hold()) {
							ClickGui.toggle(mod);
						}
					}
				);
			}
			if (clip) {
				GuiDraw.disableScissor(graphics);
			}
			return;
		}

		float headerY = listTop;
		boolean hot = mouseOver(mouseX, mouseY, x, headerY, winW - PAD * 2f, ITEM);
		GuiDraw.fill(graphics, x, headerY, winW - PAD * 2f, ITEM, hot || enabledOpen ? ImGuiLook.HEADER_HOVERED : ImGuiLook.HEADER);
		arrow(graphics, x + 6f, headerY + 6f, enabledOpen);
		int count = 0;
		List<String> names = new ArrayList<>();
		for (ClickGui.Mod mod : ClickGui.modules()) {
			if (mod.hold() || !mod.on().getAsBoolean()) {
				continue;
			}
			count++;
			names.add(ClickGui.display(mod.name()));
		}
		label(graphics, font, "Enabled (" + count + ")", x + 20f, headerY, ITEM, ImGuiLook.TEXT);
		screen.clickHit(x, headerY, winW - PAD * 2f, ITEM, () -> enabledOpen = !enabledOpen);
		if (!enabledOpen) {
			return;
		}
		float rowsTop = headerY + ITEM + 4f;
		float rowsH = Math.max(8f, winY + winH - 10f - rowsTop);
		float max = Math.max(0f, names.size() * 16f - rowsH);
		bodyScroll = Mth.clamp(bodyScroll, 0f, max);
		boolean clip = GuiDraw.scissor(graphics, x, rowsTop, winW - PAD * 2f, rowsH);
		if (names.isEmpty()) {
			label(graphics, font, "Nothing is on.", x + 8f, rowsTop, 16f, ImGuiLook.TEXT_DISABLED);
		}
		for (int i = 0; i < names.size(); i++) {
			float row = rowsTop - bodyScroll + i * 16f;
			if (row + 16f <= rowsTop || row >= rowsTop + rowsH) {
				continue;
			}
			GuiDraw.circle(graphics, x + 10f, row + 8f, 2f, ImGuiLook.TEXT);
			label(graphics, font, names.get(i), x + 20f, row, 16f, ImGuiLook.TEXT);
		}
		if (clip) {
			GuiDraw.disableScissor(graphics);
		}
	}

	private static void drawMenu(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		int index = -1;
		for (int i = 0; i < ClickGui.ORDER.length; i++) {
			if (ClickGui.ORDER[i].equals(openMenu)) {
				index = i;
				break;
			}
		}
		if (index < 0) {
			openMenu = null;
			return;
		}
		List<Row> rows = rowsFor(screen, openMenu);
		float width = 168f;
		for (Row row : rows) {
			float need = PAD + 22f + width(font, row.label) + 16f;
			if (row.shortcut != null) {
				need += width(font, row.shortcut) + 12f;
			}
			if (row.subId != null) {
				need += 14f;
			}
			width = Math.max(width, need);
		}
		width = Math.min(width, 380f);
		float content = Math.max(ITEM, rows.size() * ITEM);
		float x = menuAnchor[index];
		float y = winY + TITLE + BAR;
		float maxH = Math.max(ITEM + 8f, screen.height - y - 8f);
		float h = Math.min(content + 8f, maxH);
		if (x + width > screen.width - 4f) {
			x = Math.max(4f, screen.width - 4f - width);
		}
		popX = x;
		popY = y;
		popW = width;
		popH = h;
		menuScroll = Mth.clamp(menuScroll, 0f, Math.max(0f, content + 8f - h));
		popup(graphics, x, y, width, h);
		boolean clip = GuiDraw.scissor(graphics, x, y, width, h);
		int mark = screen.clickHitMark();
		String hoveredSub = null;
		float hoveredRow = 0f;
		if (rows.isEmpty()) {
			label(graphics, font, "No matches", x + PAD, y + 4f, ITEM, ImGuiLook.TEXT_DISABLED);
		}
		for (int i = 0; i < rows.size(); i++) {
			float rowY = y + 4f - menuScroll + i * ITEM;
			if (rowY + ITEM <= y || rowY >= y + h) {
				continue;
			}
			Row row = rows.get(i);
			boolean hot = mouseOver(mouseX, mouseY, x, rowY, width, ITEM);
			if (hot && row.subId != null) {
				hoveredSub = row.subId;
				hoveredRow = rowY;
			}
			item(screen, graphics, font, x, rowY, width, row.label, row.checked, row.subId != null, row.shortcut, hot || (row.subId != null && row.subId.equals(openSub)), row.action);
		}
		if (clip) {
			GuiDraw.disableScissor(graphics);
		}
		screen.clickClipHits(mark, x, y, width, h);
		boolean overSub = openSub != null && mouseOver(mouseX, mouseY, subX, subY, subW, subH);
		String next = hoveredSub != null ? hoveredSub : (overSub ? openSub : null);
		if (next == null || !next.equals(openSub)) {
			subScroll = 0f;
		}
		openSub = next;
		if (openSub != null) {
			drawSub(screen, graphics, font, mouseX, mouseY, x + width - 4f, hoveredSub != null ? hoveredRow : subY);
		}
	}

	private static void drawSub(StrayScreen screen, GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float anchorX, float anchorY) {
		ClickGui.Mod mod = null;
		for (ClickGui.Mod candidate : ClickGui.modules()) {
			if (candidate.name().equals(openSub)) {
				mod = candidate;
				break;
			}
		}
		if (mod == null) {
			openSub = null;
			return;
		}
		float settings = ClickGui.moduleSettingsHeight(screen, mod);
		float w = 280f;
		float inner = settings + 28f;
		float maxH = Math.max(48f, screen.height - 8f);
		float h = Math.min(inner, maxH);
		float x = anchorX;
		float y = anchorY;
		if (x + w > screen.width - 4f) {
			x = Math.max(4f, popX - w + 4f);
		}
		if (y + h > screen.height - 4f) {
			y = Math.max(4f, screen.height - 4f - h);
		}
		subX = x;
		subY = y;
		subW = w;
		subH = h;
		subScroll = Mth.clamp(subScroll, 0f, Math.max(0f, inner - h));
		popup(graphics, x, y, w, h);
		label(graphics, font, ClickGui.display(mod.name()), x + PAD, y + 2f - (subScroll > 0f ? 0f : 0f), 20f, ImGuiLook.TEXT);
		GuiDraw.fill(graphics, x + PAD, y + 22f, w - PAD * 2f, 1f, ImGuiLook.BORDER);
		boolean clip = GuiDraw.scissor(graphics, x, y + 24f, w, h - 24f);
		int mark = screen.clickHitMark();
		ClickGui.drawModuleSettings(screen, graphics, font, mouseX, mouseY, mod, x + 4f, y + 26f - subScroll, w - 8f);
		screen.clickClipHits(mark, x, y, w, h);
		if (clip) {
			GuiDraw.disableScissor(graphics);
		}
	}

	private static List<Row> rowsFor(StrayScreen screen, String category) {
		String query = ClickGui.filterText().trim().toLowerCase(Locale.ROOT);
		List<Row> rows = new ArrayList<>();
		if ("Mobs".equals(category)) {
			StrayConfig config = StrayConfig.get();
			for (MobCatalog.Entry entry : MobCatalog.filtered("")) {
				if (!query.isEmpty() && !entry.name().toLowerCase(Locale.ROOT).contains(query)) {
					continue;
				}
				String id = entry.id().toString();
				boolean on = config.isMobGlowSelected(id);
				rows.add(new Row(entry.name(), on, null, null, () -> {
					StrayConfig.get().toggleMobGlow(id);
					UnloadState.markDirty();
				}));
			}
			return rows;
		}
		if ("Keys".equals(category)) {
			for (int i = 0; i < ClickGui.BIND_LABEL.length; i++) {
				String name = ClickGui.BIND_LABEL[i];
				if (!query.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(query)) {
					continue;
				}
				int which = ClickGui.BIND_WHICH[i];
				String chip = screen.clickBindListening(which)
					? "..."
					: OdinClicks.keyLabel(OdinClicks.parseKey(ClickGui.bindKey(which)));
				rows.add(new Row(name, false, chip, null, () -> screen.clickListenBind(which)));
			}
			StrayConfig config = StrayConfig.get();
			if (query.isEmpty() || "shortcuts".contains(query)) {
				rows.add(new Row("Shortcuts", config.commandShortcutsEnabled, null, null, () -> {
					StrayConfig current = StrayConfig.get();
					current.commandShortcutsEnabled = !current.commandShortcutsEnabled;
					CommandShortcuts.sync();
					UnloadState.markDirty();
				}));
			}
			if (config.commandShortcutsEnabled && (query.isEmpty() || "pass arguments".contains(query))) {
				rows.add(new Row("Pass arguments", config.commandShortcutsPassArgs, null, null, () -> {
					StrayConfig current = StrayConfig.get();
					current.commandShortcutsPassArgs = !current.commandShortcutsPassArgs;
					UnloadState.markDirty();
				}));
			}
			if (config.commandShortcutsEnabled && (query.isEmpty() || "edit".contains(query))) {
				rows.add(new Row("Edit shortcuts", false, null, null, () ->
					Minecraft.getInstance().setScreen(new CommandShortcutScreen(screen))
				));
			}
			return rows;
		}
		for (ClickGui.Mod mod : ClickGui.modules()) {
			if (!category.equals(mod.column())) {
				continue;
			}
			String name = ClickGui.display(mod.name());
			if (!query.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(query)) {
				continue;
			}
			boolean settings = ClickGui.moduleHasSettings(mod);
			rows.add(new Row(
				name,
				!mod.hold() && mod.on().getAsBoolean(),
				null,
				settings ? mod.name() : null,
				() -> {
					if (!mod.hold()) {
						ClickGui.toggle(mod);
					}
				}
			));
		}
		return rows;
	}

	private static void item(
		StrayScreen screen,
		GuiGraphicsExtractor graphics,
		Font font,
		float x,
		float y,
		float w,
		String text,
		boolean checked,
		boolean arrow,
		String shortcut,
		boolean hover,
		Runnable action
	) {
		if (hover) {
			GuiDraw.fill(graphics, x + 2f, y, w - 4f, ITEM, ImGuiLook.HEADER_HOVERED);
		}
		if (checked) {
			ImGuiLook.checkMark(graphics, x + 5f, y + 3f, 10f);
		}
		float textX = x + 24f;
		float right = x + w - 10f;
		if (arrow) {
			label(graphics, font, ">", right - 8f, y, ITEM, ImGuiLook.TEXT);
			right -= 16f;
		}
		if (shortcut != null && !shortcut.isEmpty()) {
			float sw = width(font, shortcut);
			label(graphics, font, shortcut, right - sw, y, ITEM, ImGuiLook.TEXT_DISABLED);
			right -= sw + 8f;
		}
		label(graphics, font, fit(font, text, Math.max(8f, right - textX)), textX, y, ITEM, ImGuiLook.TEXT);
		screen.clickHit(x, y, w, ITEM, action);
	}

	private static void popup(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		GuiDraw.fill(graphics, x, y, w, h, ImGuiLook.POPUP_BG);
		ImGuiLook.border(graphics, x, y, w, h, ImGuiLook.BORDER);
	}

	private static void label(GuiGraphicsExtractor graphics, Font font, String text, float x, float y, float h, int color) {
		Component line = Component.literal(text == null ? "" : text).withStyle(MenuFont.PROGGY);
		GuiDraw.text(graphics, font, line, x, y + (h - font.lineHeight) * 0.5f, color, false);
	}

	private static float width(Font font, String text) {
		return font.width(Component.literal(text == null ? "" : text).withStyle(MenuFont.PROGGY));
	}

	private static String fit(Font font, String text, float max) {
		if (text == null) {
			return "";
		}
		if (width(font, text) <= max) {
			return text;
		}
		String trimmed = text;
		while (trimmed.length() > 1 && width(font, trimmed + ".") > max) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed + ".";
	}

	private static void arrow(GuiGraphicsExtractor graphics, float x, float y, boolean down) {
		if (down) {
			for (int row = 0; row < 4; row++) {
				float span = 7f - row * 2f;
				GuiDraw.fill(graphics, x + row, y + 2f + row, span, 1f, ImGuiLook.TEXT);
			}
			return;
		}
		for (int col = 0; col < 4; col++) {
			float span = 7f - col * 2f;
			GuiDraw.fill(graphics, x + 2f + col, y + col, 1f, span, ImGuiLook.TEXT);
		}
	}

	private static void cross(GuiGraphicsExtractor graphics, float x, float y, boolean hover) {
		int color = hover ? ImGuiLook.BUTTON_HOVERED : ImGuiLook.TEXT;
		for (int i = 0; i < 8; i++) {
			GuiDraw.fill(graphics, x + i, y + i, 1f, 1f, color);
			GuiDraw.fill(graphics, x + 7f - i, y + i, 1f, 1f, color);
		}
	}

	private static void grip(GuiGraphicsExtractor graphics, float right, float bottom) {
		GuiDraw.fill(graphics, right - 10f, bottom - 4f, 6f, 1f, ImGuiLook.CHECK);
		GuiDraw.fill(graphics, right - 7f, bottom - 7f, 3f, 1f, ImGuiLook.CHECK);
		GuiDraw.fill(graphics, right - 4f, bottom - 10f, 1f, 1f, ImGuiLook.CHECK);
	}

	private static boolean mouseOver(double x, double y, float rx, float ry, float rw, float rh) {
		return x >= rx && y >= ry && x < rx + rw && y < ry + rh;
	}

	private record Row(String label, boolean checked, String shortcut, String subId, Runnable action) {
	}
}
