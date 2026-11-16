package dev.stray.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.stray.client.input.MouseButtons;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.GuiFrostBlur;
import dev.stray.client.util.TextFit;
import dev.stray.client.util.Utils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.UnaryOperator;

/**
 * A Stray submenu (auto hotbar, shortcuts, command keys, titles, ...) in the Shadow look of
 * the main menu: one see-through panel with a soft shadow and a hairline, the title in the
 * title font with a small caps line under it and a close button, label-left / control-right
 * rows, pill buttons, and lists of cards.
 *
 * <p>Subclasses draw their body in {@link #body} with the components here, which all take a
 * position and return where the next one goes. The window grows to fit the body. Text
 * inputs ({@link Input}) are edited here: caret, Ctrl+A / C / X / V, Tab to the next one,
 * Enter calls {@link #submit}, Esc closes.
 */
public abstract class SubScreen extends Screen {
	/** Space inside the window, and between the window and the screen edge. */
	protected static final float PAD = 14f;
	/** A label-left / control-right row, a field, a button. */
	protected static final float ROW = 20f;
	protected static final float FIELD = 22f;
	protected static final float BUTTON = 20f;
	/** Space between stacked components. */
	protected static final float GAP = 6f;

	protected static final int TEXT = ShadowLook.TEXT;
	protected static final int MUTED = ShadowLook.MUTED;
	protected static final int FAINT = ShadowLook.FAINT;
	protected static final int DANGER = 0xFFFF6B6B;
	private static final int WHITE = 0xFFEEF1F5;
	private static final int INK_ON_WHITE = 0xFF0B0E13;

	protected static final String ICON_CLOSE = "";
	protected static final String ICON_DELETE = "";
	protected static final String ICON_ADD = "";

	protected final Screen parent;
	private final float preferredW;

	/** This frame's drawing state, so components need no long argument lists. */
	protected GuiGraphicsExtractor graphics;
	protected Font font;
	protected int mouseX;
	protected int mouseY;

	private final List<Hit> hits = new ArrayList<>();
	private final Map<String, Scroll> scrolls = new HashMap<>();
	private final List<Input> inputs = new ArrayList<>();
	private Input focused;
	private long blinkAt = System.currentTimeMillis();
	private float windowX;
	private float windowY;
	private float windowW;
	private float windowH = 120f;
	private float bodyTop;
	private final long openedAt = System.currentTimeMillis();

	protected SubScreen(String title, Screen parent, float width) {
		super(Component.literal(title));
		this.parent = parent;
		this.preferredW = width;
	}

	// ------------------------------------------------------------------ subclass hooks

	/** Draws the window's content from ({@code x}, {@code y}), {@code w} wide; returns the bottom. */
	protected abstract float body(float x, float y, float w);


	/** Enter in a field. */
	protected void submit() {
	}

	/** Saves before the screen closes. */
	protected void save() {
	}

	/** A key before the inputs get it; true when the screen used it (key capture, ...). */
	protected boolean keyFirst(KeyEvent event) {
		return false;
	}

	/** A typed character before it goes into the focused input; true when the screen used it. */
	protected boolean typedFirst(Input input, String typed) {
		return false;
	}

	// ------------------------------------------------------------------ screen

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		save();
		TextKeys.clear();
		if (minecraft != null) {
			minecraft.gui.setScreen(parent);
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		this.graphics = graphics;
		this.font = minecraft.font;
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		hits.clear();

		float t = Utils.easeOutCubic(Mth.clamp((System.currentTimeMillis() - openedAt) / 180f, 0f, 1f));
		windowW = Math.min(preferredW, Math.max(80f, width - PAD * 2f));
		windowX = Math.round((width - windowW) * 0.5f);
		windowY = Math.round(Math.max(PAD, (height - windowH) * 0.5f) + (1f - t) * 10f);

		GuiDraw.fill(graphics, 0, 0, width, height, 0x30000000);
		float r = ShadowLook.WINDOW_R;
		ShadowLook.shadow(graphics, windowX, windowY, windowW, windowH, r, 14, 0x60);
		GuiFrostBlur.blitWindow(graphics, windowX, windowY, windowW, windowH, r);
		GuiDraw.roundedFine(graphics, windowX, windowY, windowW, windowH, r, ShadowLook.FILL);
		GuiDraw.hairline(graphics, windowX, windowY, windowW, windowH, r, ShadowLook.edge());

		float x = windowX + PAD;
		float w = windowW - PAD * 2f;
		header(x, windowY + PAD - 2f, w);
		bodyTop = windowY + PAD + 20f;
		float bottom = body(x, bodyTop, w);
		// The window fits what was drawn (next frame), within the screen.
		windowH = Mth.clamp(bottom - windowY + PAD, 80f, Math.max(80f, height - PAD * 2f));
	}

	private void header(float x, float y, float w) {
		GuiDraw.text(graphics, font, MenuFont.shadowTitle(getTitle().getString()), x, y + 2f, 1f, WHITE, false);
		float size = 20f;
		float cx = x + w - size + 4f;
		float cy = y - 2f;
		boolean hot = hovered(cx, cy, size, size);
		if (hot) {
			GuiDraw.roundedFine(graphics, cx, cy, size, size, size * 0.5f, ShadowLook.CARD_HOVER);
		}
		icon(ICON_CLOSE, cx + (size - 9f) * 0.5f, cy, size, hot ? WHITE : MUTED);
		hit(cx, cy, size, size, this::onClose);
	}

	/**
	 * How tall something starting at {@code y} can be, with {@code after} more below it, before
	 * the window reaches the screen edges. Lists use it to fit small screens and big GUI scales.
	 */
	protected float room(float y, float after) {
		float fullTop = PAD + PAD + 20f;
		float fullBottom = height - PAD - PAD;
		return fullBottom - (fullTop + (y - bodyTop)) - after;
	}

	/** A list height: {@code rows} rows when they fit, never under {@code min} rows. */
	protected float listHeight(float y, float after, float rowH, int rows, int min) {
		float step = rowH + 3f;
		float want = Math.max(min, rows) * step - 3f;
		float least = min * step - 3f;
		return Math.max(least, Math.min(want, room(y, after)));
	}

	// ------------------------------------------------------------------ text

	/** Body text, vertically centred in a row {@code h} high. */
	protected void text(String value, float x, float y, float h, int color) {
		GuiDraw.text(graphics, font, MenuFont.shadow(value), x, Math.round(GuiDraw.middle(y, h)), color, false);
	}

	/**
	 * Coloured text (a title preview, an item name) in the menu font: its colours and bold
	 * kept, its own font swapped for the body font.
	 */
	protected Component styled(Component value) {
		net.minecraft.network.chat.MutableComponent out = Component.empty();
		net.minecraft.network.chat.FontDescription body = MenuFont.shadow("").getStyle().getFont();
		value.visit((style, piece) -> {
			out.append(Component.literal(piece).setStyle(style.withFont(body)));
			return java.util.Optional.empty();
		}, net.minecraft.network.chat.Style.EMPTY);
		return out;
	}

	protected float textWidth(String value) {
		return font.width(MenuFont.shadow(value));
	}

	/** {@code value} cut to {@code max} wide with "..." when needed. */
	protected String fit(String value, float max) {
		if (value == null) {
			return "";
		}
		if (textWidth(value) <= max) {
			return value;
		}
		String cut = value;
		while (cut.length() > 1 && textWidth(cut + "...") > max) {
			cut = cut.substring(0, cut.length() - 1);
		}
		return cut + "...";
	}

	protected void icon(String glyph, float x, float y, float h, int color) {
		GuiDraw.text(graphics, font, Component.literal(glyph).withStyle(MenuFont.ICON), x, Math.round(y + (h - font.lineHeight) * 0.5f), color, false);
	}

	/**
	 * Where small text goes to sit centred in a row {@code h} high. Measured on the font: the
	 * capitals of the small font sit 5.5 below where it draws, the body font's 4.5.
	 */
	protected static float smallMiddle(float y, float h) {
		return y + h * 0.5f - 5.5f;
	}

	/** A muted line of help (or an error in red); returns the next y. */
	protected float hint(String value, float x, float y, float w, boolean error) {
		if (value == null || value.isBlank()) {
			return y;
		}
		GuiDraw.small(graphics, font, TextFit.small(font, value, (int) w), x, y, error ? DANGER : MUTED);
		return y + 12f;
	}

	/** Small caps heading over a group, like the menu's category line; returns the next y. */
	protected float section(String label, float x, float y) {
		GuiDraw.small(graphics, font, label.toUpperCase(java.util.Locale.ROOT), x, y, MUTED);
		return y + 13f;
	}

	/** A thin line between groups; returns the next y. */
	protected float divider(float x, float y, float w) {
		GuiDraw.fill(graphics, x, y + 4f, w, 0.5f, 0x18FFFFFF);
		return y + 9f;
	}

	// ------------------------------------------------------------------ controls

	/** Label left, switch right, the whole row clickable; returns the next y. */
	protected float toggleRow(String label, boolean value, float x, float y, float w, Consumer<Boolean> setter) {
		boolean hot = hovered(x - 4f, y, w + 8f, ROW);
		if (hot) {
			GuiDraw.roundedFine(graphics, x - 4f, y, w + 8f, ROW, ShadowLook.CONTROL_R, 0x08FFFFFF);
		}
		text(label, x, y, ROW, TEXT);
		toggle(x + w - 24f, y + (ROW - 12f) * 0.5f, 24f, 12f, value);
		hit(x - 4f, y, w + 8f, ROW, () -> setter.accept(!value));
		return y + ROW;
	}

	/** The menu's switch: gray off, white with a dark knob on. */
	protected void toggle(float x, float y, float w, float h, boolean on) {
		ShadowMenu.toggle(graphics, x, y, w, h, on ? 1f : 0f, 1f);
	}

	public enum Style { PRIMARY, SECONDARY, DANGER }

	/** A pill button; returns its width. */
	protected float button(String label, float x, float y, Style style, Runnable click) {
		return button(label, x, y, buttonWidth(label), style, click);
	}

	protected float button(String label, float x, float y, float w, Style style, Runnable click) {
		boolean hot = hovered(x, y, w, BUTTON);
		int fill;
		int ink;
		if (click == null) {
			// Disabled: there but quiet.
			fill = 0x0AFFFFFF;
			ink = FAINT;
		} else if (style == Style.PRIMARY) {
			fill = hot ? 0xFFFFFFFF : WHITE;
			ink = INK_ON_WHITE;
		} else {
			fill = hot ? 0x22FFFFFF : 0x12FFFFFF;
			ink = style == Style.DANGER ? DANGER : TEXT;
		}
		GuiDraw.roundedFine(graphics, x, y, w, BUTTON, BUTTON * 0.5f, fill);
		text(label, x + (w - textWidth(label)) * 0.5f, y, BUTTON, ink);
		if (click != null) {
			hit(x, y, w, BUTTON, click);
		}
		return w;
	}

	protected float buttonWidth(String label) {
		return Math.max(56f, textWidth(label) + 26f);
	}

	/**
	 * A row of buttons: {@code left} from the left edge, {@code right} against the right edge
	 * (the last one rightmost). Each entry is label, style, action. Returns the next y.
	 */
	protected float buttons(float x, float y, float w, Object[][] left, Object[][] right) {
		float lx = x;
		for (Object[] b : left) {
			lx += button((String) b[0], lx, y, (Style) b[1], (Runnable) b[2]) + GAP;
		}
		float rx = x + w;
		for (int i = right.length - 1; i >= 0; i--) {
			Object[] b = right[i];
			float bw = buttonWidth((String) b[0]);
			rx -= bw;
			button((String) b[0], rx, y, bw, (Style) b[1], (Runnable) b[2]);
			rx -= GAP;
		}
		return y + BUTTON;
	}

	// ------------------------------------------------------------------ inputs

	/** One text input: its text, caret and limits. Make them in the constructor, in Tab order. */
	protected final class Input {
		final String key;
		final int max;
		final String placeholder;
		/** Turns pasted text into what the field takes. */
		UnaryOperator<String> clean = value -> TextKeys.singleLine(value).trim();
		/** How the text shows (a masked token, ...); null shows it as typed. */
		UnaryOperator<String> display;
		String value = "";
		int cursor;

		Input(String key, int max, String placeholder) {
			this.key = key;
			this.max = max;
			this.placeholder = placeholder;
		}

		public String get() {
			return value;
		}

		public void set(String next) {
			value = next == null ? "" : next.length() > max ? next.substring(0, max) : next;
			cursor = value.length();
		}

		public boolean focused() {
			return focused == this;
		}

		public void focus() {
			focusInput(this);
		}
	}

	protected Input input(String key, int max, String placeholder) {
		Input input = new Input(key, max, placeholder);
		inputs.add(input);
		if (focused == null) {
			focused = input;
		}
		return input;
	}

	protected void focusInput(Input input) {
		if (focused != input) {
			TextKeys.clear();
		}
		focused = input;
		if (input != null) {
			input.cursor = input.value.length();
		}
		blinkAt = System.currentTimeMillis();
	}

	protected Input focusedInput() {
		return focused;
	}

	/**
	 * A text field: label on the left (in a column {@code labelW} wide, so stacked fields line
	 * up), the text after it. Returns the next y.
	 */
	protected float field(Input in, String label, float labelW, float x, float y, float w) {
		boolean focus = focused == in;
		boolean hot = hovered(x, y, w, FIELD);
		box(x, y, w, focus, hot);
		if (focus) {
			TextKeys.mark(graphics, in.key, x, y, w, FIELD);
		}
		float textX = x + 10f;
		if (label != null && !label.isEmpty()) {
			text(label, textX, y, FIELD, MUTED);
			textX += labelW;
		}
		float room = x + w - 10f - textX;
		boolean empty = in.value.isEmpty();
		if (empty && !focus) {
			String hint = fit(in.placeholder, room);
			text(hint, centred(textX, room, hint), y, FIELD, FAINT);
		} else if (in.display != null) {
			String shown = fit(in.display.apply(in.value), room);
			textX = centred(textX, room, shown);
			text(shown, textX, y, FIELD, TEXT);
			if (focus && !TextKeys.isSelected(in.key) && ((System.currentTimeMillis() - blinkAt) / 530) % 2 == 0) {
				GuiDraw.fill(graphics, textX + textWidth(shown), y + 6f, 1f, FIELD - 12f, WHITE);
			}
		} else {
			String shown = clipAroundCaret(in.value, focus ? in.cursor : in.value.length(), room);
			int from = in.value.indexOf(shown);
			textX = centred(textX, room, shown);
			text(shown, textX, y, FIELD, TEXT);
			if (focus && !TextKeys.isSelected(in.key) && ((System.currentTimeMillis() - blinkAt) / 530) % 2 == 0) {
				int caret = Mth.clamp(in.cursor - Math.max(0, from), 0, shown.length());
				float cx = textX + textWidth(shown.substring(0, caret));
				GuiDraw.fill(graphics, cx, y + 6f, 1f, FIELD - 12f, WHITE);
			}
		}
		hit(x, y, w, FIELD, () -> focusInput(in));
		return y + FIELD;
	}

	/** Where {@code value} starts to sit centred in {@code room} from {@code start}; the start when it fills it. */
	private float centred(float start, float room, String value) {
		return start + Math.max(0f, (room - textWidth(value)) * 0.5f);
	}

	/** A field-shaped box with any text (a key capture, a picker); returns the next y. */
	protected float valueBox(String label, float labelW, String value, int color, boolean active, float x, float y, float w, Runnable click) {
		box(x, y, w, active, hovered(x, y, w, FIELD));
		float textX = x + 10f;
		if (label != null && !label.isEmpty()) {
			text(label, textX, y, FIELD, MUTED);
			textX += labelW;
		}
		float room = x + w - 10f - textX;
		String shown = fit(value, room);
		text(shown, centred(textX, room, shown), y, FIELD, color);
		hit(x, y, w, FIELD, click);
		return y + FIELD;
	}

	private void box(float x, float y, float w, boolean active, boolean hot) {
		float r = ShadowLook.CONTROL_R;
		GuiDraw.roundedFine(graphics, x, y, w, FIELD, r, active ? ShadowLook.SELECTED : hot ? ShadowLook.CARD_HOVER : ShadowLook.CARD);
		if (active) {
			GuiDraw.hairline(graphics, x, y, w, FIELD, r, ShadowLook.SELECTED_LINE);
		}
	}

	/** The part of {@code value} that fits {@code max}, keeping the caret in view. */
	private String clipAroundCaret(String value, int caret, float max) {
		if (textWidth(value) <= max) {
			return value;
		}
		int end = Mth.clamp(caret, 0, value.length());
		int start = end;
		while (start > 0 && textWidth(value.substring(start - 1, end)) <= max) {
			start--;
		}
		while (end < value.length() && textWidth(value.substring(start, end + 1)) <= max) {
			end++;
		}
		return value.substring(start, end);
	}

	/** The width of the widest label, plus room, for {@link #field}'s label column. */
	protected float labelColumn(String... labels) {
		float w = 0f;
		for (String label : labels) {
			w = Math.max(w, textWidth(label));
		}
		return w + 10f;
	}

	// ------------------------------------------------------------------ lists

	/** Draws one list row inside {@link #list}: index, its box and whether the mouse is on it. */
	@FunctionalInterface
	protected interface RowDrawer {
		void draw(int index, float x, float y, float w, float h, boolean hot);
	}

	/**
	 * A scrolling list of card rows {@code rowH} high in a {@code h} high area; {@code empty}
	 * shows when there are none. Returns the next y.
	 */
	protected float list(String id, int count, float rowH, float x, float y, float w, float h, String empty, RowDrawer rows) {
		Scroll scroll = scrolls.computeIfAbsent(id, k -> new Scroll());
		float step = rowH + 3f;
		scroll.set(x, y, w, h, Math.max(0f, count * step - 3f - h), step);
		if (count == 0) {
			GuiDraw.roundedFine(graphics, x, y, w, h, ShadowLook.CONTROL_R, 0x08FFFFFF);
			GuiDraw.small(graphics, font, empty, x + (w - GuiDraw.smallWidth(font, empty)) * 0.5f, smallMiddle(y, h), MUTED);
			return y + h;
		}
		boolean clip = GuiDraw.scissor(graphics, x, y, w + 6f, h);
		int mark = hits.size();
		try {
			boolean bar = scroll.max > 0f;
			float rw = bar ? w - 6f : w;
			for (int i = 0; i < count; i++) {
				float ry = y + i * step - scroll.offset;
				if (ry + rowH < y || ry > y + h) {
					continue;
				}
				boolean hot = mouseY >= y && mouseY < y + h && hovered(x, ry, rw, rowH);
				rows.draw(i, x, ry, rw, rowH, hot);
			}
			if (bar) {
				float view = h;
				float content = scroll.max + h;
				float thumb = Math.max(14f, view * view / content);
				float ty = y + (view - thumb) * (scroll.offset / scroll.max);
				GuiDraw.roundedFine(graphics, x + w - 2f, ty, 2f, thumb, 1f, 0x30FFFFFF);
			}
		} finally {
			clipHits(mark, y, h);
			if (clip) {
				GuiDraw.disableScissor(graphics);
			}
		}
		return y + h;
	}

	/** Rows scrolled out of an area must not take clicks; the rest are cut to it. */
	private void clipHits(int mark, float y, float h) {
		for (int i = hits.size() - 1; i >= mark; i--) {
			Hit hit = hits.get(i);
			float top = Math.max(hit.y, y);
			float bottom = Math.min(hit.y + hit.h, y + h);
			if (bottom <= top) {
				hits.remove(i);
			} else {
				hits.set(i, new Hit(hit.x, top, hit.w, bottom - top, hit.click));
			}
		}
	}

	protected interface AreaDrawer {
		/** Draws the content from {@code y} (already scrolled) and returns its bottom. */
		float draw(float x, float y, float w);
	}

	/**
	 * A scrolling area {@code h} high for free-form content (cards of any height). The content
	 * height is measured as it draws, so the scroll range follows it a frame later.
	 */
	protected float scrollArea(String id, float x, float y, float w, float h, AreaDrawer content) {
		Scroll scroll = scrolls.computeIfAbsent(id, k -> new Scroll());
		scroll.set(x, y, w, h, scroll.max, 9f);
		boolean clip = GuiDraw.scissor(graphics, x, y, w + 6f, h);
		int mark = hits.size();
		boolean bar = scroll.max > 0f;
		float cw = bar ? w - 6f : w;
		try {
			float bottom = content.draw(x, y - scroll.offset, cw);
			scroll.max = Math.max(0f, bottom + scroll.offset - y - h);
			scroll.offset = Mth.clamp(scroll.offset, 0f, scroll.max);
			if (bar) {
				float content2 = scroll.max + h;
				float thumb = Math.max(14f, h * h / content2);
				float ty = y + (h - thumb) * (scroll.offset / scroll.max);
				GuiDraw.roundedFine(graphics, x + w - 2f, ty, 2f, thumb, 1f, 0x30FFFFFF);
			}
		} finally {
			clipHits(mark, y, h);
			if (clip) {
				GuiDraw.disableScissor(graphics);
			}
		}
		return y + h;
	}

	/** A pill you pick: white with dark ink when on. Returns its width. */
	protected float chip(String label, float x, float y, boolean on, Runnable click) {
		float h = 18f;
		float w = textWidth(label) + 18f;
		boolean hot = hovered(x, y, w, h);
		GuiDraw.roundedFine(graphics, x, y, w, h, h * 0.5f, on ? WHITE : hot ? 0x22FFFFFF : 0x12FFFFFF);
		text(label, x + 9f, y, h, on ? INK_ON_WHITE : TEXT);
		if (click != null) {
			hit(x, y, w, h, click);
		}
		return w;
	}

	/** Width {@link #chip} takes for {@code label}. */
	protected float chipWidth(String label) {
		return textWidth(label) + 18f;
	}

	private DoubleConsumer sliding;
	private float slideX;
	private float slideW;

	/**
	 * A slider row: label, track and value. {@code t} is 0..1; {@code set} gets 0..1 while you
	 * click or drag. Returns the next y.
	 */
	protected float slider(String label, String value, float t, float x, float y, float w, DoubleConsumer set) {
		float h = 18f;
		float labelW = Math.max(56f, textWidth(label) + 12f);
		float valueW = Math.max(34f, textWidth(value) + 8f);
		float tx = x + labelW;
		float tw = Math.max(20f, w - labelW - valueW);
		text(label, x, y, h, MUTED);
		float k = Mth.clamp(t, 0f, 1f);
		float cy = y + h * 0.5f;
		GuiDraw.roundedFine(graphics, tx, cy - 1.5f, tw, 3f, 1.5f, 0x22FFFFFF);
		GuiDraw.roundedFine(graphics, tx, cy - 1.5f, tw * k, 3f, 1.5f, 0xB0FFFFFF);
		boolean hot = hovered(tx - 5f, y, tw + 10f, h) || sliding == set;
		float knob = hot ? 9f : 8f;
		GuiDraw.roundedFine(graphics, tx + tw * k - knob * 0.5f, cy - knob * 0.5f, knob, knob, knob * 0.5f, WHITE);
		text(value, x + w - textWidth(value), y, h, TEXT);
		if (sliding == set) {
			slideX = tx;
			slideW = tw;
		}
		hit(tx - 5f, y, tw + 10f, h, () -> {
			sliding = set;
			slideX = tx;
			slideW = tw;
			set.accept(Mth.clamp((mouseX - tx) / tw, 0f, 1f));
		});
		return y + h + 2f;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (sliding != null && MouseButtons.left(event)) {
			sliding.accept(Mth.clamp((event.x() - slideX) / Math.max(1f, slideW), 0, 1));
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		sliding = null;
		return super.mouseReleased(event);
	}

	/** A list row's card: faint, lighter under the mouse, outlined when selected. */
	protected void card(float x, float y, float w, float h, boolean hot, boolean selected) {
		ShadowLook.card(graphics, x, y, w, h, ShadowLook.CONTROL_R, hot, selected);
	}

	/** A small switch for a list row, centred at the right of ({@code x}, {@code y}, ...); returns its left edge. */
	protected float rowToggle(float right, float y, float h, boolean on, Runnable click) {
		float w = 20f;
		float th = 10f;
		float x = right - w;
		toggle(x, y + (h - th) * 0.5f, w, th, on);
		hit(x - 4f, y, w + 8f, h, click);
		return x;
	}

	/** A delete button (trash icon) at the right of a row; returns its left edge. */
	protected float rowDelete(float right, float y, float h, Runnable click) {
		float size = 16f;
		float x = right - size;
		float iy = y + (h - size) * 0.5f;
		boolean hot = hovered(x, iy, size, size);
		if (hot) {
			GuiDraw.roundedFine(graphics, x, iy, size, size, size * 0.5f, 0x22FF6B6B);
		}
		icon(ICON_DELETE, x + (size - 9f) * 0.5f, iy, size, hot ? DANGER : FAINT);
		hit(x, iy, size, size, click);
		return x;
	}

	/** A small pill with a short label (a key, a tag); returns its width. */
	protected float tag(String label, float x, float y, float h, int color) {
		float tw = GuiDraw.smallWidth(font, label);
		float w = Math.max(18f, tw + 10f);
		float th = 12f;
		float ty = y + (h - th) * 0.5f;
		GuiDraw.roundedFine(graphics, x, ty, w, th, th * 0.5f, 0x16FFFFFF);
		GuiDraw.small(graphics, font, label, x + (w - tw) * 0.5f, smallMiddle(ty, th), color);
		return w;
	}

	// ------------------------------------------------------------------ item slots

	/** An inventory slot with its item; a {@code mark} (slot number) when it is mapped. */
	protected void slot(ItemStack stack, float x, float y, float size, boolean lit, String mark, Runnable click) {
		boolean hot = hovered(x, y, size, size);
		GuiDraw.roundedFine(graphics, x, y, size, size, ShadowLook.SLOT_R, lit ? 0x26FFFFFF : hot ? ShadowLook.CARD_HOVER : ShadowLook.CARD);
		if (lit) {
			GuiDraw.hairline(graphics, x, y, size, size, ShadowLook.SLOT_R, 0x50FFFFFF);
		}
		if (stack != null && !stack.isEmpty()) {
			graphics.item(stack, Math.round(x + (size - 16f) * 0.5f), Math.round(y + (size - 16f) * 0.5f));
		}
		if (mark != null) {
			// Top left, clear of the stack count.
			float mw = GuiDraw.smallWidth(font, mark);
			float bx = x + 2.5f;
			float by = y + 2f;
			GuiDraw.roundedFine(graphics, bx - 1f, by - 0.5f, mw + 3f, 8f, 2f, 0xE0EEF1F5);
			GuiDraw.small(graphics, font, mark, bx + 0.5f, smallMiddle(by - 0.5f, 8f), INK_ON_WHITE);
		}
		if (click != null) {
			hit(x, y, size, size, click);
		}
	}

	// ------------------------------------------------------------------ hits

	protected void hit(float x, float y, float w, float h, Runnable click) {
		hits.add(new Hit(x, y, w, h, click));
	}

	protected boolean hovered(float x, float y, float w, float h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
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
		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0) {
			for (Scroll scroll : scrolls.values()) {
				if (scroll.contains(mouseX, mouseY)) {
					scroll.offset = Mth.clamp(scroll.offset - (float) scrollY * scroll.step * 2f, 0f, scroll.max);
					return true;
				}
			}
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	/** Scrolls list {@code id} so row {@code index} is in view. */
	protected void reveal(String id, int index) {
		Scroll scroll = scrolls.get(id);
		if (scroll == null) {
			return;
		}
		float top = index * scroll.step;
		float bottom = top + scroll.step - 3f;
		if (top < scroll.offset) {
			scroll.offset = top;
		} else if (bottom > scroll.offset + scroll.h) {
			scroll.offset = Math.min(scroll.max, bottom - scroll.h);
		}
	}

	// ------------------------------------------------------------------ keys

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (keyFirst(event)) {
			return true;
		}
		if (event.isEscape()) {
			onClose();
			return true;
		}
		int key = event.key();
		if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			TextKeys.clear();
			submit();
			return true;
		}
		if (key == InputConstants.KEY_TAB && !inputs.isEmpty()) {
			int at = inputs.indexOf(focused);
			int step = event.hasShiftDown() ? -1 : 1;
			focusInput(inputs.get(Math.floorMod(at + step, inputs.size())));
			return true;
		}
		Input in = focused;
		if (in == null) {
			return super.keyPressed(event);
		}
		if (TextKeys.caretKey(event, in.key, in.value, () -> {
			in.value = "";
			in.cursor = 0;
		})) {
			return true;
		}
		blinkAt = System.currentTimeMillis();
		String value = in.value;
		switch (key) {
			case InputConstants.KEY_LEFT -> in.cursor = Math.max(0, in.cursor - 1);
			case InputConstants.KEY_RIGHT -> in.cursor = Math.min(value.length(), in.cursor + 1);
			case InputConstants.KEY_HOME -> in.cursor = 0;
			case InputConstants.KEY_END -> in.cursor = value.length();
			case InputConstants.KEY_BACKSPACE -> {
				if (in.cursor > 0) {
					in.value = value.substring(0, in.cursor - 1) + value.substring(in.cursor);
					in.cursor--;
					changed(in);
				}
			}
			case InputConstants.KEY_DELETE -> {
				if (in.cursor < value.length()) {
					in.value = value.substring(0, in.cursor) + value.substring(in.cursor + 1);
					changed(in);
				}
			}
			default -> {
				if (event.isPaste()) {
					String clip = minecraft.keyboardHandler.getClipboard();
					if (clip != null && !clip.isBlank()) {
						insert(in, in.clean.apply(clip));
					}
					return true;
				}
				return super.keyPressed(event);
			}
		}
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (!event.isAllowedChatCharacter()) {
			return super.charTyped(event);
		}
		String typed = event.codepointAsString();
		if (typed.isEmpty() || typed.charAt(0) < 32) {
			return true;
		}
		Input in = focused;
		if (typedFirst(in, typed) || in == null) {
			return true;
		}
		if (TextKeys.takeSelection(in.key)) {
			in.value = "";
			in.cursor = 0;
		}
		insert(in, typed);
		return true;
	}

	private void insert(Input in, String add) {
		if (add == null || add.isEmpty()) {
			return;
		}
		int room = in.max - in.value.length();
		if (room <= 0) {
			return;
		}
		if (add.length() > room) {
			add = add.substring(0, room);
		}
		int at = Mth.clamp(in.cursor, 0, in.value.length());
		in.value = in.value.substring(0, at) + add + in.value.substring(at);
		in.cursor = at + add.length();
		blinkAt = System.currentTimeMillis();
		changed(in);
	}

	/** An input's text changed by typing, deleting or pasting. */
	protected void changed(Input input) {
	}

	// ------------------------------------------------------------------ records

	private record Hit(float x, float y, float w, float h, Runnable click) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private static final class Scroll {
		float x;
		float y;
		float w;
		float h;
		float max;
		float step;
		float offset;

		void set(float x, float y, float w, float h, float max, float step) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.max = max;
			this.step = step;
			this.offset = Mth.clamp(offset, 0f, max);
		}

		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w + 6f && my >= y && my < y + h;
		}
	}
}
