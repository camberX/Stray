package dev.stray.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.stray.client.combat.OdinClicks;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.input.CommandKeys;
import dev.stray.client.input.MouseButtons;
import dev.stray.client.render.GuiDraw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Command keys: a list of key → command binds. Click the key box and press a key (or mouse
 * button) to set it, type the command, then Add. Clicking a row loads it for editing.
 */
public class CommandKeysScreen extends Screen {
	private static final float MENU_W = 300;
	private static final float MENU_H = 280;
	private static final float FIELD_H = 18;
	private static final float ROW_H = 18;
	private static final String FIELD_KEY = "command-key-command";

	private final Screen parent;
	private String key = "";
	private String command = "";
	private int cursor;
	/** Waiting for the next key or mouse button to become the bind. */
	private boolean listening;
	/** The key that set a bind also arrives as a typed character right after; drop that one. */
	private boolean swallowChar;
	/** Row being edited, or -1 for a new bind. */
	private int editing = -1;
	private String error = "";
	private boolean placed;
	private float windowX;
	private float windowY;
	private float windowW = MENU_W;
	private float windowH = MENU_H;
	private float listX;
	private float listY;
	private float listW;
	private float listH;
	private float scroll;
	private final List<Hit> hits = new ArrayList<>();
	private long blinkAt = System.currentTimeMillis();

	public CommandKeysScreen(Screen parent) {
		super(Component.literal("Command keys"));
		this.parent = parent;
		CommandKeys.normalize(StrayConfig.get());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		StrayConfig.get().save();
		CommandKeys.syncEdge();
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
		Font font = minecraft.font;
		StrayConfig config = StrayConfig.get();
		layout();

		GuiDraw.fill(graphics, 0, 0, width, height, 0x14000000);
		ClickLook.outside(graphics, windowX, windowY, windowW, windowH, Theme.WINDOW_RADIUS, Theme.WINDOW, Theme.LINE);
		GuiDraw.title(graphics, font, "COMMAND KEYS", windowX + 12, windowY + 8, Theme.TEXT);
		GuiDraw.small(graphics, font, config.commandKeys.size() + "/" + CommandKeys.MAX,
			windowX + 12 + GuiDraw.titleWidth(font, "COMMAND KEYS") + 4, windowY + 10, Theme.ACCENT);

		float x = windowX + 12;
		float y = windowY + 28;
		float w = windowW - 24;
		y = toggleRow(graphics, font, mouseX, mouseY, x, y, w, "Enabled", config.commandKeysEnabled, v -> {
			config.commandKeysEnabled = v;
			config.save();
		});

		float by = windowY + windowH - 26;
		float fieldsH = FIELD_H * 2 + 6;
		float fieldY = by - 16 - fieldsH;
		listX = x;
		listY = y + 2;
		listW = w;
		listH = Math.max(ROW_H * 3, fieldY - 8 - listY);
		ClickLook.outside(graphics, listX, listY, listW, listH, 6, Theme.TRACK, Theme.LINE);
		drawList(graphics, font, mouseX, mouseY, config);

		drawKeyField(graphics, font, mouseX, mouseY, x, fieldY, w);
		drawCommandField(graphics, font, mouseX, mouseY, x, fieldY + FIELD_H + 6, w);

		float bw = 64;
		float bh = 18;
		button(graphics, font, mouseX, mouseY, x, by, bw, bh, editing >= 0 ? "Save" : "Add", Theme.ACCENT, this::add);
		button(graphics, font, mouseX, mouseY, x + bw + 6, by, bw, bh, editing >= 0 ? "New" : "Clear", Theme.TEXT, this::clearFields);
		button(graphics, font, mouseX, mouseY, x + w - bw, by, bw, bh, "Done", Theme.TEXT, this::onClose);
		String hint = !error.isEmpty() ? error : listening ? "Press a key or mouse button · Esc clears" : "Enter adds · runs when you press the key in game";
		GuiDraw.small(graphics, font, hint, x, by - 12, error.isEmpty() ? Theme.MUTED : Theme.DANGER);
	}

	private void drawList(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, StrayConfig config) {
		List<StrayConfig.CommandKey> rows = config.commandKeys;
		float maxScroll = Math.max(0f, rows.size() * ROW_H - listH);
		scroll = Mth.clamp(scroll, 0f, maxScroll);
		if (rows.isEmpty()) {
			GuiDraw.small(graphics, font, "No command keys yet: set one below", listX + 8, listY + listH * 0.5f - 4, Theme.MUTED);
			return;
		}
		boolean clipped = GuiDraw.scissor(graphics, listX + 1, listY + 1, listW - 2, listH - 2);
		for (int i = 0; i < rows.size(); i++) {
			float rowY = listY + 1 + i * ROW_H - scroll;
			if (rowY + ROW_H < listY || rowY > listY + listH) {
				continue;
			}
			StrayConfig.CommandKey row = rows.get(i);
			boolean hover = GuiDraw.hovered(mouseX, mouseY, listX, rowY, listW, ROW_H);
			if (hover || i == editing) {
				GuiDraw.rounded(graphics, listX + 1, rowY, listW - 2, ROW_H, 4, i == editing ? 0x22FFFFFF : 0x14FFFFFF);
			}
			String label = OdinClicks.keyLabel(OdinClicks.parseKey(row.key));
			float chipW = Math.max(26, GuiDraw.smallWidth(font, label) + 10);
			ClickLook.outside(graphics, listX + 5, rowY + 3, chipW, ROW_H - 6, 3, Theme.CARD, Theme.ACCENT);
			GuiDraw.small(graphics, font, label, listX + 5 + (chipW - GuiDraw.smallWidth(font, label)) * 0.5f, rowY + 5, Theme.ACCENT);
			float delW = 16;
			float textX = listX + 12 + chipW;
			String shown = clip(font, "/" + row.command, (int) (listW - delW - (textX - listX) - 6));
			GuiDraw.small(graphics, font, shown, textX, rowY + 5, hover ? Theme.TEXT : Theme.MUTED);
			boolean delHover = GuiDraw.hovered(mouseX, mouseY, listX + listW - delW - 2, rowY, delW, ROW_H);
			GuiDraw.small(graphics, font, "×", listX + listW - 14, rowY + 5, delHover ? Theme.DANGER : Theme.OFF);
			final int index = i;
			hits.add(new Hit(listX + listW - delW - 2, rowY, delW, ROW_H, () -> {
				CommandKeys.remove(index);
				if (editing == index) {
					clearFields();
				} else if (editing > index) {
					editing--;
				}
			}));
			hits.add(new Hit(listX, rowY, listW - delW, ROW_H, () -> {
				editing = index;
				key = row.key;
				command = row.command;
				cursor = command.length();
				listening = false;
				error = "";
				blinkAt = System.currentTimeMillis();
			}));
		}
		if (clipped) {
			GuiDraw.disableScissor(graphics);
		}
	}

	private float toggleRow(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float x, float y, float w, String label, boolean value, java.util.function.Consumer<Boolean> setter) {
		boolean hover = GuiDraw.hovered(mouseX, mouseY, x, y, w, FIELD_H);
		GuiDraw.menu(graphics, font, label, x, GuiDraw.middle(y, FIELD_H), hover ? Theme.TEXT : Theme.HEADER);
		float trackW = 28;
		float trackH = 16;
		ControlChrome.toggle(graphics, x + w - trackW, y + (FIELD_H - trackH) * 0.5f, trackW, trackH, value ? 1f : 0f);
		hits.add(new Hit(x, y, w, FIELD_H, () -> setter.accept(!value)));
		return y + FIELD_H + 2;
	}

	private void drawKeyField(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float x, float y, float w) {
		boolean hover = GuiDraw.hovered(mouseX, mouseY, x, y, w, FIELD_H);
		ClickLook.outside(graphics, x, y, w, FIELD_H, 5, listening || hover ? Theme.CARD_HOVER : Theme.CARD, listening ? Theme.ACCENT : Theme.LINE);
		GuiDraw.small(graphics, font, "Key", x + 6, y + 5, Theme.MUTED);
		float textX = x + 12 + GuiDraw.smallWidth(font, "Key");
		String shown;
		int color;
		if (listening) {
			shown = (System.currentTimeMillis() / 400L) % 2L == 0L ? "Press a key..." : "Press a key";
			color = Theme.ACCENT;
		} else if (key.isEmpty()) {
			shown = "Click to set";
			color = Theme.OFF;
		} else {
			shown = OdinClicks.keyLabel(OdinClicks.parseKey(key));
			color = Theme.TEXT;
		}
		GuiDraw.menu(graphics, font, shown, textX, GuiDraw.middle(y, FIELD_H), color);
		hits.add(new Hit(x, y, w, FIELD_H, () -> {
			listening = true;
			error = "";
		}));
	}

	private void drawCommandField(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float x, float y, float w) {
		boolean focused = !listening;
		boolean hover = GuiDraw.hovered(mouseX, mouseY, x, y, w, FIELD_H);
		ClickLook.outside(graphics, x, y, w, FIELD_H, 5, focused || hover ? Theme.CARD_HOVER : Theme.CARD, focused ? Theme.ACCENT : Theme.LINE);
		if (focused) {
			TextKeys.mark(graphics, FIELD_KEY, x, y, w, FIELD_H);
		}
		float labelW = GuiDraw.smallWidth(font, "Run /") + 6;
		GuiDraw.small(graphics, font, "Run /", x + 6, y + 5, Theme.MUTED);
		String shown = command.isEmpty() && !focused ? "warp garden" : command;
		String clipped = clipField(font, shown, focused ? cursor : shown.length(), (int) (w - labelW - 14));
		float textX = x + 6 + labelW;
		GuiDraw.menu(graphics, font, clipped, textX, GuiDraw.middle(y, FIELD_H), command.isEmpty() && !focused ? Theme.OFF : Theme.TEXT);
		if (focused && !TextKeys.isSelected(FIELD_KEY) && ((System.currentTimeMillis() - blinkAt) / 530) % 2 == 0) {
			int from = shown.length() - clipped.length();
			int caret = Mth.clamp(cursor - Math.max(0, from), 0, clipped.length());
			float cx = textX + GuiDraw.menuWidth(font, clipped.substring(0, caret));
			GuiDraw.fill(graphics, cx, y + 4, 1, FIELD_H - 8, Theme.ACCENT);
		}
		hits.add(new Hit(x, y, w, FIELD_H, () -> {
			listening = false;
			cursor = command.length();
			blinkAt = System.currentTimeMillis();
		}));
	}

	private void button(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float x, float y, float w, float h, String label, int color, Runnable click) {
		boolean hover = GuiDraw.hovered(mouseX, mouseY, x, y, w, h);
		ClickLook.outside(graphics, x, y, w, h, 5, hover ? Theme.CARD_HOVER : Theme.CARD, hover ? Theme.ACCENT : Theme.LINE);
		GuiDraw.menu(graphics, font, label, x + (w - GuiDraw.menuWidth(font, label)) * 0.5f, GuiDraw.middle(y, h), color);
		hits.add(new Hit(x, y, w, h, click));
	}

	private void add() {
		if (key.isEmpty()) {
			error = "Set a key first.";
			return;
		}
		if (CommandKeys.normalizeCommand(command).isEmpty()) {
			error = "Type the command to run.";
			return;
		}
		if (editing < 0 && StrayConfig.get().commandKeys.size() >= CommandKeys.MAX) {
			error = "That's the maximum of " + CommandKeys.MAX + ".";
			return;
		}
		if (CommandKeys.upsert(key, command, editing)) {
			clearFields();
		}
	}

	private void clearFields() {
		key = "";
		command = "";
		cursor = 0;
		editing = -1;
		listening = false;
		error = "";
		blinkAt = System.currentTimeMillis();
	}

	private void setKey(InputConstants.Key bound) {
		key = OdinClicks.keyName(bound);
		listening = false;
		error = "";
		cursor = command.length();
		blinkAt = System.currentTimeMillis();
	}

	private void layout() {
		windowW = Math.min(MENU_W, Math.max(1, width - 16));
		windowH = Math.min(MENU_H, Math.max(1, height - 16));
		if (!placed) {
			windowX = (width - windowW) / 2f;
			windowY = (height - windowH) / 2f;
			placed = true;
		}
		windowX = Mth.clamp(windowX, 4, Math.max(4, width - windowW - 4));
		windowY = Mth.clamp(windowY, 4, Math.max(4, height - windowH - 4));
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

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (listening && !MouseButtons.left(event)) {
			setKey(InputConstants.Type.MOUSE.getOrCreate(event.button()));
			return true;
		}
		if (!MouseButtons.left(event)) {
			return super.mouseClicked(event, doubled);
		}
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (hit.contains(event.x(), event.y())) {
				hit.click.run();
				return true;
			}
		}
		if (listening) {
			// A left click outside the key box binds the left mouse button would be a trap; cancel instead.
			listening = false;
		}
		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0 && GuiDraw.hovered(mouseX, mouseY, listX, listY, listW, listH)) {
			int count = StrayConfig.get().commandKeys == null ? 0 : StrayConfig.get().commandKeys.size();
			float maxScroll = Math.max(0f, count * ROW_H - listH);
			scroll = Mth.clamp(scroll - (float) scrollY * ROW_H * 2.2f, 0f, maxScroll);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int k = event.key();
		if (listening) {
			if (event.isEscape() || k == InputConstants.KEY_BACKSPACE) {
				key = "";
				listening = false;
				return true;
			}
			setKey(InputConstants.Type.KEYBOARD.getOrCreate(k));
			swallowChar = true;
			return true;
		}
		swallowChar = false;
		if (event.isEscape()) {
			onClose();
			return true;
		}
		if (k == InputConstants.KEY_RETURN || k == InputConstants.KEY_NUMPADENTER) {
			add();
			return true;
		}
		if (TextKeys.caretKey(event, FIELD_KEY, command, () -> {
			command = "";
			cursor = 0;
		})) {
			return true;
		}
		if (k == InputConstants.KEY_LEFT) {
			cursor = Math.max(0, cursor - 1);
			return true;
		}
		if (k == InputConstants.KEY_RIGHT) {
			cursor = Math.min(command.length(), cursor + 1);
			return true;
		}
		if (k == InputConstants.KEY_HOME) {
			cursor = 0;
			return true;
		}
		if (k == InputConstants.KEY_END) {
			cursor = command.length();
			return true;
		}
		if (k == InputConstants.KEY_BACKSPACE) {
			if (cursor > 0) {
				command = command.substring(0, cursor - 1) + command.substring(cursor);
				cursor--;
			}
			return true;
		}
		if (k == InputConstants.KEY_DELETE) {
			if (cursor < command.length()) {
				command = command.substring(0, cursor) + command.substring(cursor + 1);
			}
			return true;
		}
		if (event.isPaste()) {
			String clip = minecraft.keyboardHandler.getClipboard();
			if (clip != null && !clip.isBlank()) {
				String insert = TextKeys.singleLine(clip).trim();
				String next = command.substring(0, cursor) + insert + command.substring(cursor);
				if (next.length() > CommandKeys.MAX_COMMAND) {
					insert = insert.substring(0, Math.max(0, CommandKeys.MAX_COMMAND - command.length()));
					next = command.substring(0, cursor) + insert + command.substring(cursor);
				}
				command = next;
				cursor += insert.length();
			}
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (swallowChar) {
			swallowChar = false;
			return true;
		}
		if (listening || !event.isAllowedChatCharacter()) {
			return true;
		}
		String insert = event.codepointAsString();
		if (insert.isEmpty() || insert.charAt(0) < 32) {
			return true;
		}
		if (TextKeys.takeSelection(FIELD_KEY)) {
			command = "";
			cursor = 0;
		}
		if (command.length() >= CommandKeys.MAX_COMMAND) {
			return true;
		}
		command = command.substring(0, cursor) + insert + command.substring(cursor);
		cursor += insert.length();
		error = "";
		blinkAt = System.currentTimeMillis();
		return true;
	}

	private record Hit(float x, float y, float w, float h, Runnable click) {
		boolean contains(double mx, double my) {
			return mx >= x && mx <= x + w && my >= y && my <= y + h;
		}
	}
}
