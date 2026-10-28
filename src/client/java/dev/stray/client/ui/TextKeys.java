package dev.stray.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.stray.client.render.GuiDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Editing keys for Stray's hand-drawn text fields. They type at the end and have no caret,
 * so the only selection is the whole text: Ctrl+A selects it, and the next key acts on it
 * (typing or pasting replaces it, Backspace clears it). Ctrl+C / Ctrl+X copy the whole text,
 * Ctrl+V pastes, and Ctrl+Backspace deletes the last word. The shortcuts use vanilla's
 * isSelectAll / isCopy / ... checks, so they follow the keyboard layout (and Cmd on macOS).
 */
public final class TextKeys {
	private static final int SELECTION = 0x55FFFFFF;

	/** Key of the field whose whole text is selected, or null. */
	private static String selected;

	private TextKeys() {
	}

	/**
	 * One text field.
	 *
	 * @param key   stable name, used to remember which field is selected
	 * @param clean turns typed or pasted text into what the field accepts (filtering, case)
	 */
	public record Field(String key, Supplier<String> get, Consumer<String> set, int max, UnaryOperator<String> clean) {
		public Field(String key, Supplier<String> get, Consumer<String> set, int max) {
			this(key, get, set, max, TextKeys::singleLine);
		}

		String value() {
			String value = get.get();
			return value == null ? "" : value;
		}
	}

	/** Handles an editing key for {@code field}. Returns false for keys it does not handle. */
	public static boolean key(KeyEvent event, Field field) {
		if (field == null) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		if (event.isSelectAll()) {
			selected = field.value().isEmpty() ? null : field.key();
			return true;
		}
		if (event.isCopy()) {
			copy(client, field.value());
			return true;
		}
		if (event.isCut()) {
			copy(client, field.value());
			field.set().accept("");
			selected = null;
			return true;
		}
		if (event.isPaste()) {
			String clip = client.keyboardHandler.getClipboard();
			insert(field, clip == null ? "" : clip);
			return true;
		}
		if (event.key() == InputConstants.KEY_BACKSPACE || event.key() == InputConstants.KEY_DELETE) {
			String value = field.value();
			if (isSelected(field.key()) || event.key() == InputConstants.KEY_DELETE && event.hasControlDown()) {
				field.set().accept("");
			} else if (event.hasControlDown() || event.hasAltDown()) {
				field.set().accept(dropWord(value));
			} else if (event.key() == InputConstants.KEY_BACKSPACE && !value.isEmpty()) {
				field.set().accept(value.substring(0, value.offsetByCodePoints(value.length(), -1)));
			}
			selected = null;
			return true;
		}
		if (movesCaret(event.key())) {
			selected = null;
		}
		return false;
	}

	/** Typed characters: replaces the text when it is all selected, otherwise appends. */
	public static boolean type(String typed, Field field) {
		if (field == null) {
			return false;
		}
		insert(field, typed == null ? "" : typed);
		return true;
	}

	/**
	 * For fields that keep their own caret. Handles Ctrl+A, Ctrl+C and Ctrl+X, and Backspace /
	 * Delete on an all-selected text (calling {@code clear}). A paste on a selected text calls
	 * {@code clear} first and returns false so the screen still inserts the clipboard. Arrow,
	 * Home/End, Tab, Enter and Escape drop the selection and return false.
	 */
	public static boolean caretKey(KeyEvent event, String key, String value, Runnable clear) {
		Minecraft client = Minecraft.getInstance();
		String text = value == null ? "" : value;
		if (event.isSelectAll()) {
			selected = text.isEmpty() ? null : key;
			return true;
		}
		if (event.isCopy()) {
			copy(client, text);
			return true;
		}
		if (event.isCut()) {
			copy(client, text);
			clear.run();
			selected = null;
			return true;
		}
		boolean wasSelected = isSelected(key);
		if (wasSelected && (event.key() == InputConstants.KEY_BACKSPACE || event.key() == InputConstants.KEY_DELETE)) {
			clear.run();
			selected = null;
			return true;
		}
		if (wasSelected && event.isPaste()) {
			clear.run();
			selected = null;
			return false;
		}
		if (movesCaret(event.key())) {
			selected = null;
		}
		return false;
	}

	/**
	 * Call before inserting a typed character into a caret field: true when the whole text was
	 * selected, in which case the caller empties the field first.
	 */
	public static boolean takeSelection(String key) {
		if (!isSelected(key)) {
			return false;
		}
		selected = null;
		return true;
	}

	/**
	 * Keys that move or leave the caret end the selection. Letters do not: their key press
	 * arrives before the typed character, which has to replace the selected text.
	 */
	private static boolean movesCaret(int key) {
		return key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT
			|| key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN
			|| key == InputConstants.KEY_HOME || key == InputConstants.KEY_END
			|| key == InputConstants.KEY_TAB || key == InputConstants.KEY_RETURN
			|| key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_ESCAPE;
	}

	public static boolean isSelected(String key) {
		return key != null && key.equals(selected);
	}

	/** Forget the selection, e.g. when focus moves. */
	public static void clear() {
		selected = null;
	}

	/**
	 * Tints a field's box while its whole text is selected. Call it after the box background
	 * and before the text, with the box's bounds.
	 */
	public static void mark(GuiGraphicsExtractor graphics, String key, float x, float y, float w, float h) {
		if (!isSelected(key) || w <= 4f || h <= 4f) {
			return;
		}
		GuiDraw.rounded(graphics, x + 2f, y + 2f, w - 4f, h - 4f, 2f, SELECTION);
	}

	/** The text a field shows: the caret only while focused and nothing is selected. */
	public static String shown(String key, String value, boolean focused) {
		String text = value == null ? "" : value;
		return focused && !isSelected(key) ? text + "|" : text;
	}

	private static void insert(Field field, String raw) {
		String add = field.clean().apply(raw);
		String base = isSelected(field.key()) ? "" : field.value();
		selected = null;
		if (add == null || add.isEmpty()) {
			if (!base.equals(field.value())) {
				field.set().accept(base);
			}
			return;
		}
		String next = base + add;
		if (next.length() > field.max()) {
			next = next.substring(0, Math.max(base.length(), field.max()));
		}
		field.set().accept(next);
	}

	private static void copy(Minecraft client, String value) {
		if (!value.isEmpty()) {
			client.keyboardHandler.setClipboard(value);
		}
	}

	private static String dropWord(String value) {
		int end = value.length();
		while (end > 0 && Character.isWhitespace(value.charAt(end - 1))) {
			end--;
		}
		while (end > 0 && !Character.isWhitespace(value.charAt(end - 1))) {
			end--;
		}
		return value.substring(0, end);
	}

	/** Line breaks and other control characters are dropped; tabs become spaces. */
	public static String singleLine(String text) {
		if (text == null) {
			return "";
		}
		StringBuilder out = new StringBuilder(text.length());
		text.codePoints().forEach(c -> {
			if (c == '\t') {
				out.append(' ');
			} else if (c >= 32 && c != 127 && c != '§') {
				out.appendCodePoint(c);
			}
		});
		return out.toString();
	}
}
