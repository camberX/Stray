package dev.stray.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.stray.client.item.ItemIds;
import dev.stray.client.render.BlockMarks;
import dev.stray.client.render.GuiDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Name + item icon for one block mark. Enter saves, Esc cancels, Tab swaps fields. */
public class BlockMarkEditScreen extends Screen {
	private static final float MENU_W = 268;
	private static final float MENU_H = 176;
	private static final float SLOT = 40;
	private static final float FIELD_H = 18;
	private static final int SUGGESTIONS = 4;

	private final BlockPos pos;
	private String name;
	private String icon;
	private int field;
	private int cursor;
	private boolean placed;
	private float windowX;
	private float windowY;
	private float windowW = MENU_W;
	private float windowH = MENU_H;
	private final List<Hit> hits = new ArrayList<>();
	private ItemIds.Preview preview = ItemIds.Preview.empty();
	private List<String> suggestions = List.of();
	private long blinkAt = System.currentTimeMillis();

	public BlockMarkEditScreen(BlockPos pos, String name, String icon) {
		super(Component.literal("Mark"));
		this.pos = pos;
		this.name = name == null ? "" : name;
		this.icon = icon == null ? "" : icon;
		this.cursor = this.name.length();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		hits.clear();
		Font font = minecraft.font;
		refresh();
		layout();

		GuiDraw.fill(graphics, 0, 0, width, height, 0x14000000);
		GuiDraw.panel(graphics, windowX, windowY, windowW, windowH, Theme.WINDOW_RADIUS, Theme.WINDOW, Theme.LINE);
		GuiDraw.title(graphics, font, "MARK", windowX + 12, windowY + 8, Theme.TEXT);
		String coords = pos.getX() + " " + pos.getY() + " " + pos.getZ();
		GuiDraw.small(graphics, font, coords, windowX + 12 + GuiDraw.titleWidth(font, "MARK") + 4, windowY + 10, Theme.ACCENT);

		float slotX = windowX + 12;
		float slotY = windowY + 30;
		GuiDraw.panel(graphics, slotX, slotY, SLOT, SLOT, 8, Theme.TRACK, Theme.LINE);
		ItemStack stack = preview.stack();
		if (!stack.isEmpty()) {
			float scale = 2f;
			float size = 16f * scale;
			graphics.pose().pushMatrix();
			graphics.pose().translate(slotX + (SLOT - size) * 0.5f, slotY + (SLOT - size) * 0.5f);
			graphics.pose().scale(scale, scale);
			graphics.item(stack, 0, 0);
			graphics.pose().popMatrix();
		} else {
			String mark = preview.kind() == ItemIds.Kind.UNKNOWN ? "?" : "—";
			GuiDraw.menu(graphics, font, mark, slotX + (SLOT - GuiDraw.menuWidth(font, mark)) * 0.5f, GuiDraw.middle(slotY, SLOT), Theme.OFF);
		}

		float fieldX = slotX + SLOT + 10;
		float fieldW = windowX + windowW - 12 - fieldX;
		drawField(graphics, font, mouseX, mouseY, 0, "Name", name, fieldX, slotY, fieldW);
		drawField(graphics, font, mouseX, mouseY, 1, "Icon", icon, fieldX, slotY + FIELD_H + 6, fieldW);

		float listY = slotY + SLOT + 10;
		float listBottom = windowY + windowH - 30;
		if (field == 1) {
			for (String id : suggestions) {
				if (listY + 14 > listBottom) {
					break;
				}
				boolean hover = GuiDraw.hovered(mouseX, mouseY, slotX, listY, windowW - 24, 14);
				if (hover) {
					GuiDraw.rounded(graphics, slotX, listY, windowW - 24, 14, 4, 0x14FFFFFF);
				}
				GuiDraw.small(graphics, font, clip(font, id, (int) windowW - 34), slotX + 5, listY + 2, hover ? Theme.TEXT : Theme.MUTED);
				final String pick = id;
				hits.add(new Hit(slotX, listY, windowW - 24, 14, () -> {
					icon = pick;
					cursor = icon.length();
					blinkAt = System.currentTimeMillis();
				}));
				listY += 15;
			}
		}

		float by = windowY + windowH - 26;
		float bw = 64;
		float bh = 18;
		button(graphics, font, mouseX, mouseY, windowX + 12, by, bw, bh, "Save", Theme.ACCENT, this::save);
		button(graphics, font, mouseX, mouseY, windowX + 12 + bw + 6, by, bw, bh, "Cancel", Theme.TEXT, this::onClose);
		button(graphics, font, mouseX, mouseY, windowX + windowW - 12 - bw, by, bw, bh, "Delete", Theme.DANGER, () -> {
			BlockMarks.remove(pos);
			onClose();
		});
		GuiDraw.small(graphics, font, "Enter saves · Tab switches · Esc cancels", windowX + 12, by - 12, Theme.MUTED);
	}

	private void drawField(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, int which, String label, String value, float x, float y, float w) {
		boolean focused = field == which;
		boolean hover = GuiDraw.hovered(mouseX, mouseY, x, y, w, FIELD_H);
		GuiDraw.panel(graphics, x, y, w, FIELD_H, 5, focused || hover ? Theme.CARD_HOVER : Theme.CARD, focused ? Theme.ACCENT : Theme.LINE);
		float labelW = GuiDraw.smallWidth(font, label) + 6;
		GuiDraw.small(graphics, font, label, x + 6, y + 5, Theme.MUTED);
		String shown = value.isEmpty() && !focused ? (which == 0 ? "Unnamed" : "minecraft:diamond or sb:HYPERION") : value;
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

	private void save() {
		BlockMarks.rename(pos, name, icon);
		onClose();
	}

	private String current() {
		return field == 0 ? name : icon;
	}

	private void set(String value) {
		if (field == 0) {
			name = value;
		} else {
			icon = value;
		}
	}

	private void refresh() {
		preview = icon.isBlank() ? ItemIds.Preview.empty() : ItemIds.resolve(icon);
		String trimmed = icon.trim();
		boolean exact = preview.kind() != ItemIds.Kind.UNKNOWN
			&& !trimmed.isEmpty()
			&& (trimmed.equalsIgnoreCase(preview.canonical())
				|| (preview.kind() == ItemIds.Kind.SKYBLOCK && trimmed.equalsIgnoreCase(preview.title())));
		suggestions = exact || trimmed.isEmpty() ? List.of() : ItemIds.suggest(icon, SUGGESTIONS);
	}

	private void layout() {
		float extra = field == 1 && !suggestions.isEmpty() ? suggestions.size() * 15f : 0f;
		windowW = Math.min(MENU_W, Math.max(1, width - 16));
		windowH = Math.min(MENU_H + extra, Math.max(1, height - 16));
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
		if (GuiDraw.smallWidth(font, value) <= maxWidth) {
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
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			onClose();
			return true;
		}
		int key = event.key();
		if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			if (field == 1 && !suggestions.isEmpty() && preview.kind() == ItemIds.Kind.UNKNOWN) {
				icon = suggestions.getFirst();
				cursor = icon.length();
				return true;
			}
			save();
			return true;
		}
		if (key == InputConstants.KEY_TAB) {
			if (field == 1 && !suggestions.isEmpty() && preview.kind() == ItemIds.Kind.UNKNOWN) {
				icon = suggestions.getFirst();
				cursor = icon.length();
				return true;
			}
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
		String value = current();
		if (field == 0 && value.length() >= 32) {
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
