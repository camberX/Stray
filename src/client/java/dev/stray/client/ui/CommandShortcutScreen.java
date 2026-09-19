package dev.stray.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.stray.client.config.StrayConfig;
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

/** Add and edit chat command aliases. Esc returns to the parent menu. */
public class CommandShortcutScreen extends Screen {
	private static final float MENU_W = 300;
	private static final float MENU_H = 280;
	private static final float FIELD_H = 18;
	private static final float ROW_H = 16;

	private final Screen parent;
	private String alias = "";
	private String command = "";
	private int field;
	private int cursor;
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

	public CommandShortcutScreen(Screen parent) {
		super(Component.literal("Shortcuts"));
		this.parent = parent;
		this.cursor = 0;
		CommandShortcuts.normalize(StrayConfig.get());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		StrayConfig.get().save();
		if (minecraft != null) {
			minecraft.setScreen(parent);
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
		CommandShortcuts.normalize(config);
		layout();

		GuiDraw.fill(graphics, 0, 0, width, height, 0x14000000);
		GuiDraw.panel(graphics, windowX, windowY, windowW, windowH, Theme.WINDOW_RADIUS, Theme.WINDOW, Theme.LINE);
		GuiDraw.title(graphics, font, "SHORTCUTS", windowX + 12, windowY + 8, Theme.TEXT);
		GuiDraw.small(
			graphics,
			font,
			config.commandShortcuts.size() + "/" + CommandShortcuts.MAX,
			windowX + 12 + GuiDraw.titleWidth(font, "SHORTCUTS") + 4,
			windowY + 10,
			Theme.ACCENT
		);

		float x = windowX + 12;
		float y = windowY + 28;
		float w = windowW - 24;
		y = toggleRow(graphics, font, mouseX, mouseY, x, y, w, "Enabled", config.commandShortcutsEnabled, v -> {
			config.commandShortcutsEnabled = v;
			config.save();
			CommandShortcuts.sync();
		});
		y = toggleRow(graphics, font, mouseX, mouseY, x, y, w, "Pass arguments", config.commandShortcutsPassArgs, v -> {
			config.commandShortcutsPassArgs = v;
			config.save();
		});
		GuiDraw.small(
			graphics,
			font,
			config.commandShortcutsPassArgs ? "/alias extra keeps extra" : "/alias extra drops extra",
			x,
			y,
			Theme.MUTED
		);
		y += 12;

		float by = windowY + windowH - 26;
		float fieldsH = FIELD_H * 2 + 6;
		float fieldY = by - 14 - fieldsH;
		listX = x;
		listY = y;
		listW = w;
		listH = Math.max(ROW_H * 3, fieldY - 8 - listY);
		GuiDraw.panel(graphics, listX, listY, listW, listH, 6, Theme.TRACK, Theme.LINE);
		drawList(graphics, font, mouseX, mouseY, config);

		drawField(graphics, font, mouseX, mouseY, 0, "Alias", alias, x, fieldY, w);
		drawField(graphics, font, mouseX, mouseY, 1, "Run", command, x, fieldY + FIELD_H + 6, w);

		float bw = 64;
		float bh = 18;
		button(graphics, font, mouseX, mouseY, x, by, bw, bh, "Add", Theme.ACCENT, this::add);
		button(graphics, font, mouseX, mouseY, x + bw + 6, by, bw, bh, "Clear", Theme.TEXT, this::clearFields);
		button(graphics, font, mouseX, mouseY, x + windowW - 24 - bw, by, bw, bh, "Done", Theme.TEXT, this::onClose);
		GuiDraw.small(graphics, font, "Enter adds · Tab switches · Chat Tab completes", x, by - 12, Theme.MUTED);
	}

	private void drawList(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, StrayConfig config) {
		List<StrayConfig.CommandShortcut> rows = config.commandShortcuts;
		float maxScroll = Math.max(0f, rows.size() * ROW_H - listH);
		scroll = Mth.clamp(scroll, 0f, maxScroll);
		if (rows.isEmpty()) {
			GuiDraw.small(graphics, font, "Add a shortcut below", listX + 8, listY + listH * 0.5f - 4, Theme.MUTED);
			return;
		}
		boolean clipped = GuiDraw.scissor(graphics, listX + 1, listY + 1, listW - 2, listH - 2);
		int visible = Math.max(3, (int) (listH / ROW_H) + 2);
		int first = (int) (scroll / ROW_H);
		int last = Math.min(rows.size(), first + visible);
		for (int i = first; i < last; i++) {
			float rowY = listY + i * ROW_H - scroll;
			if (rowY + ROW_H < listY || rowY > listY + listH) {
				continue;
			}
			StrayConfig.CommandShortcut row = rows.get(i);
			boolean hover = GuiDraw.hovered(mouseX, mouseY, listX, rowY, listW, ROW_H);
			if (hover) {
				GuiDraw.rounded(graphics, listX + 1, rowY, listW - 2, ROW_H, 4, 0x14FFFFFF);
			}
			String left = "/" + row.alias;
			String right = "/" + row.command;
			float delW = 16;
			String shownLeft = clip(font, left, (int) (listW * 0.32f));
			String shownRight = clip(font, right, (int) (listW - delW - 18 - GuiDraw.smallWidth(font, shownLeft)));
			GuiDraw.small(graphics, font, shownLeft, listX + 6, rowY + 3, Theme.ACCENT);
			GuiDraw.small(
				graphics,
				font,
				shownRight,
				listX + 10 + GuiDraw.smallWidth(font, shownLeft),
				rowY + 3,
				hover ? Theme.TEXT : Theme.MUTED
			);
			boolean delHover = GuiDraw.hovered(mouseX, mouseY, listX + listW - delW - 2, rowY, delW, ROW_H);
			GuiDraw.small(graphics, font, "×", listX + listW - 14, rowY + 3, delHover ? Theme.DANGER : Theme.OFF);
			final int index = i;
			hits.add(new Hit(listX + listW - delW - 2, rowY, delW, ROW_H, () -> CommandShortcuts.remove(index)));
			hits.add(new Hit(listX, rowY, listW - delW, ROW_H, () -> {
				alias = row.alias;
				command = row.command;
				field = 1;
				cursor = command.length();
				blinkAt = System.currentTimeMillis();
			}));
		}
		if (clipped) {
			GuiDraw.disableScissor(graphics);
		}
	}

	private float toggleRow(
		GuiGraphicsExtractor graphics,
		Font font,
		int mouseX,
		int mouseY,
		float x,
		float y,
		float w,
		String label,
		boolean value,
		java.util.function.Consumer<Boolean> setter
	) {
		boolean hover = GuiDraw.hovered(mouseX, mouseY, x, y, w, FIELD_H);
		GuiDraw.menu(graphics, font, label, x, GuiDraw.middle(y, FIELD_H), hover ? Theme.TEXT : Theme.HEADER);
		float trackW = 28;
		float trackH = 16;
		float tx = x + w - trackW;
		float ty = y + (FIELD_H - trackH) * 0.5f;
		ControlChrome.toggle(graphics, tx, ty, trackW, trackH, value ? 1f : 0f);
		hits.add(new Hit(x, y, w, FIELD_H, () -> setter.accept(!value)));
		return y + FIELD_H + 2;
	}

	private void drawField(
		GuiGraphicsExtractor graphics,
		Font font,
		int mouseX,
		int mouseY,
		int which,
		String label,
		String value,
		float x,
		float y,
		float w
	) {
		boolean focused = field == which;
		boolean hover = GuiDraw.hovered(mouseX, mouseY, x, y, w, FIELD_H);
		GuiDraw.panel(graphics, x, y, w, FIELD_H, 5, focused || hover ? Theme.CARD_HOVER : Theme.CARD, focused ? Theme.ACCENT : Theme.LINE);
		float labelW = GuiDraw.smallWidth(font, label) + 6;
		GuiDraw.small(graphics, font, label, x + 6, y + 5, Theme.MUTED);
		String placeholder = which == 0 ? "hub" : "warp garden";
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
		GuiDraw.panel(graphics, x, y, w, h, 5, hover ? Theme.CARD_HOVER : Theme.CARD, hover ? Theme.ACCENT : Theme.LINE);
		GuiDraw.menu(graphics, font, label, x + (w - GuiDraw.menuWidth(font, label)) * 0.5f, GuiDraw.middle(y, h), color);
		hits.add(new Hit(x, y, w, h, click));
	}

	private void add() {
		if (CommandShortcuts.upsert(alias, command)) {
			clearFields();
		}
	}

	private void clearFields() {
		alias = "";
		command = "";
		field = 0;
		cursor = 0;
		blinkAt = System.currentTimeMillis();
	}

	private String current() {
		return field == 0 ? alias : command;
	}

	private void set(String value) {
		if (field == 0) {
			alias = value;
		} else {
			command = value;
		}
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
		if (event.button() != 0) {
			return super.mouseClicked(event, doubled);
		}
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (hit.contains(event.x(), event.y())) {
				hit.click.run();
				return true;
			}
		}
		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0 && GuiDraw.hovered(mouseX, mouseY, listX, listY, listW, listH)) {
			int count = StrayConfig.get().commandShortcuts == null ? 0 : StrayConfig.get().commandShortcuts.size();
			float maxScroll = Math.max(0f, count * ROW_H - listH);
			scroll = Mth.clamp(scroll - (float) scrollY * ROW_H * 2.2f, 0f, maxScroll);
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
			add();
			return true;
		}
		if (key == InputConstants.KEY_TAB) {
			field = field == 0 ? 1 : 0;
			cursor = current().length();
			blinkAt = System.currentTimeMillis();
			return true;
		}
		String value = current();
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
		if (key == InputConstants.KEY_V && event.hasControlDown()) {
			String clip = minecraft.keyboardHandler.getClipboard();
			if (clip != null && !clip.isBlank()) {
				String insert = clip.replace("\n", "").replace("\r", "").trim();
				if (field == 0) {
					insert = insert.replace(" ", "");
				}
				int cap = field == 0 ? CommandShortcuts.MAX_ALIAS : CommandShortcuts.MAX_COMMAND;
				String next = value.substring(0, cursor) + insert + value.substring(cursor);
				if (next.length() > cap) {
					insert = insert.substring(0, Math.max(0, cap - value.length()));
					next = value.substring(0, cursor) + insert + value.substring(cursor);
				}
				set(next);
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
		if (field == 0 && Character.isWhitespace(insert.charAt(0))) {
			field = 1;
			cursor = command.length();
			blinkAt = System.currentTimeMillis();
			return true;
		}
		String value = current();
		int cap = field == 0 ? CommandShortcuts.MAX_ALIAS : CommandShortcuts.MAX_COMMAND;
		if (value.length() >= cap) {
			return true;
		}
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
