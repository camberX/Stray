package dev.stray.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.stray.client.input.MouseButtons;
import dev.stray.client.item.AutoHotbar;
import dev.stray.client.render.GuiDraw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Auto hotbar setup, after NoammAddons' AutoHotbarScreen: triggers (a name and the exact chat
 * message) and, for the selected trigger, which item goes to which hotbar slot.
 */
public class AutoHotbarScreen extends Screen {
	private static final float MENU_W = 320;
	private static final float MENU_H = 330;
	private static final float FIELD_H = 18;
	private static final float ROW_H = 16;
	private static final int CELL = 18;
	private static final int MAX_NAME = 32;
	private static final int MAX_MESSAGE = 256;

	private final Screen parent;
	private String name = "";
	private String message = "";
	/** The trigger the fields edit and the grid maps; null for a new one. */
	private String selected;
	private int field;
	private int cursor;
	private float windowX;
	private float windowY;
	private float windowW = MENU_W;
	private float windowH = MENU_H;
	private float listX;
	private float listY;
	private float listW;
	private float listH;
	private float scroll;
	private ItemStack picked = ItemStack.EMPTY;
	private ItemStack hovered = ItemStack.EMPTY;
	private final List<Hit> hits = new ArrayList<>();
	private long blinkAt = System.currentTimeMillis();

	public AutoHotbarScreen(Screen parent) {
		super(Component.literal("Auto hotbar"));
		this.parent = parent;
		List<String> names = new ArrayList<>(AutoHotbar.setup().triggers.keySet());
		if (!names.isEmpty()) {
			select(names.getFirst());
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		AutoHotbar.save();
		if (minecraft != null) {
			minecraft.gui.setScreen(parent);
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		hits.clear();
		hovered = ItemStack.EMPTY;
		Font font = minecraft.font;
		AutoHotbar.Setup setup = AutoHotbar.setup();
		layout();

		GuiDraw.fill(graphics, 0, 0, width, height, 0x14000000);
		ClickLook.outside(graphics, windowX, windowY, windowW, windowH, Theme.WINDOW_RADIUS, Theme.WINDOW, Theme.LINE);
		GuiDraw.title(graphics, font, "AUTO HOTBAR", windowX + 12, windowY + 8, Theme.TEXT);

		float x = windowX + 12;
		float w = windowW - 24;
		float y = windowY + 28;

		listX = x;
		listY = y;
		listW = w;
		listH = ROW_H * 4 + 2;
		ClickLook.outside(graphics, listX, listY, listW, listH, 6, Theme.TRACK, Theme.LINE);
		drawList(graphics, font, mouseX, mouseY, setup);
		y += listH + 8;

		drawField(graphics, font, mouseX, mouseY, 0, "Name", name, "Mask swap", x, y, w);
		y += FIELD_H + 4;
		drawField(graphics, font, mouseX, mouseY, 1, "Message", message, "[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!", x, y, w);
		y += FIELD_H + 6;
		float bw = 70;
		button(graphics, font, mouseX, mouseY, x, y, bw, 18, selected == null ? "Add" : "Save", Theme.ACCENT, this::saveTrigger);
		button(graphics, font, mouseX, mouseY, x + bw + 6, y, bw, 18, "New", Theme.TEXT, this::newTrigger);
		button(graphics, font, mouseX, mouseY, x + w - bw, y, bw, 18, "Done", Theme.TEXT, this::onClose);
		y += 26;

		if (selected == null) {
			GuiDraw.small(graphics, font, setup.triggers.isEmpty() ? "Add a trigger to start." : "Pick a trigger above to map its items.", x, y + 4, Theme.MUTED);
		} else {
			GuiDraw.small(graphics, font, "Click an item, then a hotbar slot. Click a hotbar slot alone to clear it.", x, y, Theme.MUTED);
			drawGrid(graphics, font, mouseX, mouseY, setup, x + (w - CELL * 9) * 0.5f, y + 12);
		}

		if (!picked.isEmpty()) {
			graphics.item(picked, mouseX - 8, mouseY - 8);
		} else if (!hovered.isEmpty()) {
			String label = hovered.getHoverName().getString();
			GuiDraw.small(graphics, font, label, windowX + (windowW - GuiDraw.smallWidth(font, label)) * 0.5f, windowY + windowH - 14, Theme.TEXT);
		}
	}

	private void drawList(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, AutoHotbar.Setup setup) {
		List<Map.Entry<String, String>> rows = new ArrayList<>(setup.triggers.entrySet());
		float maxScroll = Math.max(0f, rows.size() * ROW_H - listH);
		scroll = Mth.clamp(scroll, 0f, maxScroll);
		if (rows.isEmpty()) {
			GuiDraw.small(graphics, font, "No triggers yet", listX + 8, listY + listH * 0.5f - 4, Theme.MUTED);
			return;
		}
		boolean clipped = GuiDraw.scissor(graphics, listX + 1, listY + 1, listW - 2, listH - 2);
		for (int i = 0; i < rows.size(); i++) {
			float rowY = listY + 1 + i * ROW_H - scroll;
			if (rowY + ROW_H < listY || rowY > listY + listH) {
				continue;
			}
			String key = rows.get(i).getKey();
			boolean on = !setup.disabled.contains(key);
			boolean active = key.equals(selected);
			boolean hover = GuiDraw.hovered(mouseX, mouseY, listX, rowY, listW, ROW_H);
			if (active || hover) {
				GuiDraw.rounded(graphics, listX + 1, rowY, listW - 2, ROW_H, 4, active ? 0x22FFFFFF : 0x14FFFFFF);
			}
			float delW = 16;
			float chipW = 26;
			String shownName = clip(font, key, (int) (listW * 0.35f));
			GuiDraw.small(graphics, font, shownName, listX + 6, rowY + 4, on ? Theme.ACCENT : Theme.OFF);
			int count = setup.rules.getOrDefault(key, List.of()).size();
			String right = count + " item" + (count == 1 ? "" : "s") + " · " + rows.get(i).getValue();
			GuiDraw.small(graphics, font, clip(font, right, (int) (listW - delW - chipW - 22 - GuiDraw.smallWidth(font, shownName))),
				listX + 12 + GuiDraw.smallWidth(font, shownName), rowY + 4, hover ? Theme.TEXT : Theme.MUTED);
			float chipX = listX + listW - delW - chipW - 4;
			String chip = on ? "on" : "off";
			boolean chipHover = GuiDraw.hovered(mouseX, mouseY, chipX, rowY, chipW, ROW_H);
			GuiDraw.small(graphics, font, chip, chipX + (chipW - GuiDraw.smallWidth(font, chip)) * 0.5f, rowY + 4, on ? (chipHover ? Theme.TEXT : Theme.ACCENT) : (chipHover ? Theme.TEXT : Theme.OFF));
			boolean delHover = GuiDraw.hovered(mouseX, mouseY, listX + listW - delW - 2, rowY, delW, ROW_H);
			GuiDraw.small(graphics, font, "×", listX + listW - 14, rowY + 4, delHover ? Theme.DANGER : Theme.OFF);
			hits.add(new Hit(listX + listW - delW - 2, rowY, delW, ROW_H, () -> delete(key)));
			hits.add(new Hit(chipX, rowY, chipW, ROW_H, () -> {
				if (!setup.disabled.remove(key)) {
					setup.disabled.add(key);
				}
				AutoHotbar.save();
			}));
			hits.add(new Hit(listX, rowY, chipX - listX, ROW_H, () -> select(key)));
		}
		if (clipped) {
			GuiDraw.disableScissor(graphics);
		}
	}

	private void drawGrid(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, AutoHotbar.Setup setup, float gx, float gy) {
		if (minecraft.player == null) {
			return;
		}
		Inventory inventory = minecraft.player.getInventory();
		List<AutoHotbar.Rule> rules = setup.rules.getOrDefault(selected, List.of());
		// Main inventory (slots 9-35).
		for (int i = 0; i < 27; i++) {
			float cx = gx + (i % 9) * CELL;
			float cy = gy + (i / 9) * CELL;
			ItemStack stack = inventory.getItem(i + 9);
			AutoHotbar.Rule rule = ruleFor(rules, stack);
			cell(graphics, font, mouseX, mouseY, cx, cy, stack, rule != null, rule == null ? null : Integer.toString(rule.hotbarSlot + 1));
			if (!stack.isEmpty()) {
				hits.add(new Hit(cx, cy, CELL, CELL, () -> picked = stack.copy()));
			}
		}
		// Hotbar, a little apart.
		float hy = gy + 3 * CELL + 6;
		for (int i = 0; i < 9; i++) {
			float cx = gx + i * CELL;
			ItemStack stack = inventory.getItem(i);
			int slot = i;
			AutoHotbar.Rule target = null;
			for (AutoHotbar.Rule rule : rules) {
				if (rule.hotbarSlot == i) {
					target = rule;
				}
			}
			cell(graphics, font, mouseX, mouseY, cx, hy, stack, target != null, target == null ? null : Integer.toString(i + 1));
			hits.add(new Hit(cx, hy, CELL, CELL, () -> mapTo(slot, stack)));
		}
	}

	private void cell(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float x, float y, ItemStack stack, boolean mapped, String label) {
		boolean hover = GuiDraw.hovered(mouseX, mouseY, x, y, CELL, CELL);
		int fill = hover ? 0x3CFFFFFF : mapped ? 0x2E00FFFF : 0x14FFFFFF;
		GuiDraw.rounded(graphics, x + 0.5f, y + 0.5f, CELL - 1, CELL - 1, 3, fill);
		if (!stack.isEmpty()) {
			graphics.item(stack, Math.round(x + 1), Math.round(y + 1));
			if (hover) {
				hovered = stack;
			}
		}
		if (label != null) {
			GuiDraw.text(graphics, font, Component.literal(label), x + CELL - font.width(label) - 1, y + CELL - 8, 0xFF55FFFF, true);
		}
	}

	private static AutoHotbar.Rule ruleFor(List<AutoHotbar.Rule> rules, ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		String id = AutoHotbar.idOf(stack);
		for (AutoHotbar.Rule rule : rules) {
			if (rule.id.equals(id)) {
				return rule;
			}
		}
		return null;
	}

	private void mapTo(int slot, ItemStack hotbarStack) {
		if (selected == null) {
			return;
		}
		List<AutoHotbar.Rule> rules = AutoHotbar.setup().rules.computeIfAbsent(selected, key -> new ArrayList<>());
		if (picked.isEmpty()) {
			rules.removeIf(rule -> rule.hotbarSlot == slot);
		} else {
			String id = AutoHotbar.idOf(picked);
			rules.removeIf(rule -> rule.hotbarSlot == slot || rule.id.equals(id));
			rules.add(new AutoHotbar.Rule(id, slot));
			picked = ItemStack.EMPTY;
		}
		AutoHotbar.save();
	}

	// ---------------------------------------------------------------- triggers

	private void select(String key) {
		selected = key;
		name = key;
		message = AutoHotbar.setup().triggers.getOrDefault(key, "");
		field = 1;
		cursor = message.length();
		picked = ItemStack.EMPTY;
		blinkAt = System.currentTimeMillis();
	}

	private void newTrigger() {
		selected = null;
		name = "";
		message = "";
		field = 0;
		cursor = 0;
		picked = ItemStack.EMPTY;
		blinkAt = System.currentTimeMillis();
	}

	private void saveTrigger() {
		String newName = name.trim();
		String newMessage = message.trim();
		if (newName.isEmpty() || newMessage.isEmpty()) {
			return;
		}
		AutoHotbar.Setup setup = AutoHotbar.setup();
		if (selected != null && !selected.equals(newName)) {
			if (setup.triggers.containsKey(newName)) {
				return;
			}
			List<AutoHotbar.Rule> rules = setup.rules.remove(selected);
			boolean off = setup.disabled.remove(selected);
			setup.triggers.remove(selected);
			if (rules != null) {
				setup.rules.put(newName, rules);
			}
			if (off) {
				setup.disabled.add(newName);
			}
		}
		setup.triggers.put(newName, newMessage);
		AutoHotbar.save();
		select(newName);
	}

	private void delete(String key) {
		AutoHotbar.Setup setup = AutoHotbar.setup();
		setup.triggers.remove(key);
		setup.rules.remove(key);
		setup.disabled.remove(key);
		AutoHotbar.save();
		if (key.equals(selected)) {
			newTrigger();
		}
	}

	// ---------------------------------------------------------------- widgets

	private void drawField(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, int which, String label, String value, String placeholder, float x, float y, float w) {
		boolean focused = field == which;
		boolean hover = GuiDraw.hovered(mouseX, mouseY, x, y, w, FIELD_H);
		ClickLook.outside(graphics, x, y, w, FIELD_H, 5, focused || hover ? Theme.CARD_HOVER : Theme.CARD, focused ? Theme.ACCENT : Theme.LINE);
		if (focused) {
			TextKeys.mark(graphics, "auto-hotbar-" + which, x, y, w, FIELD_H);
		}
		float labelW = GuiDraw.smallWidth(font, label) + 6;
		GuiDraw.small(graphics, font, label, x + 6, y + 5, Theme.MUTED);
		String shown = value.isEmpty() && !focused ? placeholder : value;
		String clipped = clipField(font, shown, focused ? cursor : shown.length(), (int) (w - labelW - 14));
		float textX = x + 6 + labelW;
		float textY = GuiDraw.middle(y, FIELD_H);
		GuiDraw.menu(graphics, font, clipped, textX, textY, value.isEmpty() && !focused ? Theme.OFF : Theme.TEXT);
		if (focused && ((System.currentTimeMillis() - blinkAt) / 530) % 2 == 0) {
			int caret = Math.min(cursor, clipped.length());
			if (!value.isEmpty() && !shown.equals(value)) {
				caret = clipped.length();
			}
			float cx = textX + GuiDraw.menuWidth(font, clipped.substring(0, Math.min(caret, clipped.length())));
			GuiDraw.fill(graphics, cx, y + 4, 1, FIELD_H - 8, Theme.ACCENT);
		}
		hits.add(new Hit(x, y, w, FIELD_H, () -> {
			field = which;
			cursor = current().length();
			blinkAt = System.currentTimeMillis();
		}));
	}

	private void button(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float x, float y, float w, float h, String label, int color, Runnable click) {
		boolean hover = GuiDraw.hovered(mouseX, mouseY, x, y, w, h);
		ClickLook.outside(graphics, x, y, w, h, 5, hover ? Theme.CARD_HOVER : Theme.CARD, hover ? Theme.ACCENT : Theme.LINE);
		GuiDraw.menu(graphics, font, label, x + (w - GuiDraw.menuWidth(font, label)) * 0.5f, GuiDraw.middle(y, h), color);
		hits.add(new Hit(x, y, w, h, click));
	}

	private String current() {
		return field == 0 ? name : message;
	}

	private void set(String value) {
		if (field == 0) {
			name = value;
		} else {
			message = value;
		}
	}

	private int cap() {
		return field == 0 ? MAX_NAME : MAX_MESSAGE;
	}

	private void layout() {
		windowW = Math.min(MENU_W, Math.max(1, width - 16));
		windowH = Math.min(MENU_H, Math.max(1, height - 16));
		windowX = (width - windowW) / 2f;
		windowY = (height - windowH) / 2f;
	}

	private static String clipField(Font font, String value, int cursor, int maxWidth) {
		if (GuiDraw.menuWidth(font, value) <= maxWidth) {
			return value;
		}
		int from = 0;
		while (from < cursor && GuiDraw.menuWidth(font, value.substring(from)) > maxWidth) {
			from++;
		}
		String slice = value.substring(from);
		while (slice.length() > 1 && GuiDraw.menuWidth(font, slice) > maxWidth) {
			slice = slice.substring(0, slice.length() - 1);
		}
		return slice;
	}

	private static String clip(Font font, String value, int maxWidth) {
		if (maxWidth <= 8 || GuiDraw.smallWidth(font, value) <= maxWidth) {
			return value;
		}
		String trimmed = value;
		while (trimmed.length() > 1 && GuiDraw.smallWidth(font, trimmed + "..") > maxWidth) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed + "..";
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (!MouseButtons.left(event)) {
			picked = ItemStack.EMPTY;
			return super.mouseClicked(event, doubled);
		}
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (hit.contains(event.x(), event.y())) {
				hit.click.run();
				return true;
			}
		}
		picked = ItemStack.EMPTY;
		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0 && GuiDraw.hovered(mouseX, mouseY, listX, listY, listW, listH)) {
			float maxScroll = Math.max(0f, AutoHotbar.setup().triggers.size() * ROW_H - listH);
			scroll = Mth.clamp(scroll - (float) scrollY * ROW_H * 2f, 0f, maxScroll);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			onClose();
			return true;
		}
		int key = event.key();
		if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			saveTrigger();
			return true;
		}
		if (key == InputConstants.KEY_TAB) {
			field = field == 0 ? 1 : 0;
			cursor = current().length();
			blinkAt = System.currentTimeMillis();
			return true;
		}
		if (TextKeys.caretKey(event, "auto-hotbar-" + field, current(), () -> {
			set("");
			cursor = 0;
		})) {
			return true;
		}
		String value = current();
		cursor = Math.min(cursor, value.length());
		if (key == InputConstants.KEY_LEFT) {
			cursor = Math.max(0, cursor - 1);
			return true;
		}
		if (key == InputConstants.KEY_RIGHT) {
			cursor = Math.min(value.length(), cursor + 1);
			return true;
		}
		if (key == InputConstants.KEY_HOME) {
			cursor = 0;
			return true;
		}
		if (key == InputConstants.KEY_END) {
			cursor = value.length();
			return true;
		}
		if (key == InputConstants.KEY_BACKSPACE) {
			if (cursor > 0) {
				set(value.substring(0, cursor - 1) + value.substring(cursor));
				cursor--;
			}
			return true;
		}
		if (key == InputConstants.KEY_DELETE) {
			if (cursor < value.length()) {
				set(value.substring(0, cursor) + value.substring(cursor + 1));
			}
			return true;
		}
		if (event.isPaste()) {
			String clip = minecraft.keyboardHandler.getClipboard();
			if (clip != null && !clip.isBlank()) {
				String insert = clip.replace("\n", "").replace("\r", "");
				insert = insert.substring(0, Math.max(0, Math.min(insert.length(), cap() - value.length())));
				set(value.substring(0, cursor) + insert + value.substring(cursor));
				cursor += insert.length();
			}
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (!event.isAllowedChatCharacter()) {
			return super.charTyped(event);
		}
		String insert = event.codepointAsString();
		if (insert.isEmpty() || insert.charAt(0) < 32) {
			return true;
		}
		if (TextKeys.takeSelection("auto-hotbar-" + field)) {
			set("");
			cursor = 0;
		}
		String value = current();
		if (value.length() >= cap()) {
			return true;
		}
		cursor = Math.min(cursor, value.length());
		set(value.substring(0, cursor) + insert + value.substring(cursor));
		cursor += insert.length();
		blinkAt = System.currentTimeMillis();
		return true;
	}

	private record Hit(float x, float y, float w, float h, Runnable click) {
		boolean contains(double mx, double my) {
			return mx >= x && mx <= x + w && my >= y && my <= y + h;
		}
	}
}
