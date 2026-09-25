package dev.stray.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.stray.client.account.AccountStore;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.render.TitleBackdrop;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Title-screen account list: Microsoft device code, session token, switch.
 */
public class AccountScreen extends Screen {
	private static final float MENU_W = 384;
	private static final float MENU_H = 300;
	private static final float ROW_H = 28;
	private static final float BTN_H = 22;
	private static final float PAD = 16;
	private static final int VISIBLE = 5;
	private static final float STAR = 22;
	private static final int OK = 0xFF3DDC84;
	private static final int FAIL = 0xFFEF5350;

	private final Screen parent;
	private final List<Hit> hits = new ArrayList<>();
	private float scroll;
	private UUID selectedId;
	private boolean tokenFocus;
	private int tokenCursor;
	private String token = "";
	private volatile String status = "";

	public AccountScreen(Screen parent) {
		super(Component.literal("Accounts"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		AccountStore.rememberLauncher(minecraft);
		if (selectedId == null) {
			for (AccountStore.Entry entry : AccountStore.accounts()) {
				if (entry.active()) {
					selectedId = entry.uuid;
					break;
				}
			}
		}
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		TitleBackdrop.draw(graphics, width, height);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		hits.clear();
		Theme.refresh();
		Font font = minecraft.font;
		float x = (width - MENU_W) * 0.5f;
		float y = (height - MENU_H) * 0.42f;
		if (ControlChrome.on()) {
			ControlChrome.glass(graphics, x, y, MENU_W, MENU_H, ControlChrome.WINDOW_R, ControlChrome.windowFill());
		} else {
			ClickLook.outside(graphics, x, y, MENU_W, MENU_H, Theme.WINDOW_RADIUS, Theme.WINDOW, Theme.LINE);
		}
		GuiDraw.title(graphics, font, "ACCOUNTS", x + PAD, y + 12, ControlChrome.on() ? ControlChrome.text() : Theme.TEXT);
		String current = AccountStore.currentName();
		if (!current.isBlank()) {
			GuiDraw.small(graphics, font, current, x + MENU_W - PAD - GuiDraw.smallWidth(font, current), y + 16, Theme.ACCENT);
		}
		String note = status;
		String url = AccountStore.deviceUrl();
		if (url != null && !url.isBlank() && note.isBlank()) {
			note = "Open microsoft.com/link";
		}
		if (!note.isBlank()) {
			GuiDraw.small(graphics, font, note, x + PAD, y + 28, Theme.WARN);
		}

		List<AccountStore.Entry> accounts = AccountStore.accounts();
		float listX = x + PAD;
		float listY = y + 44;
		float listW = MENU_W - PAD * 2;
		float listH = ROW_H * VISIBLE;
		int maxScroll = Math.max(0, accounts.size() - VISIBLE);
		scroll = Mth.clamp(scroll, 0, maxScroll);
		int first = (int) scroll;
		if (GuiDraw.scissor(graphics, listX, listY, listW, listH)) {
			for (int i = 0; i < VISIBLE + 1; i++) {
				int index = first + i;
				if (index >= accounts.size()) {
					break;
				}
				float rowY = listY + (index - scroll) * ROW_H;
				row(graphics, font, mouseX, mouseY, listX, rowY, listW, accounts.get(index), index);
			}
			GuiDraw.disableScissor(graphics);
		}
		if (accounts.isEmpty()) {
			GuiDraw.small(graphics, font, "No saved accounts yet.", listX, listY + 8, Theme.MUTED);
		}

		float actionsY = listY + listH + 12;
		float third = (listW - 16) * (1f / 3f);
		button(graphics, font, mouseX, mouseY, listX, actionsY, third, "Microsoft", !AccountStore.busy(), this::microsoft);
		button(graphics, font, mouseX, mouseY, listX + third + 8, actionsY, third, "Prism", !AccountStore.busy(), this::prism);
		button(graphics, font, mouseX, mouseY, listX + (third + 8) * 2, actionsY, third, "Launcher", !AccountStore.busy(), this::launcher);

		float fieldY = actionsY + BTN_H + 8;
		field(graphics, font, mouseX, mouseY, listX, fieldY, listW - 72, "Session token");
		button(graphics, font, mouseX, mouseY, listX + listW - 64, fieldY, 64, "Add", !AccountStore.busy() && !token.isBlank(), this::addToken);

		float rowY = fieldY + BTN_H + 8;
		float half = (listW - 8) * 0.5f;
		button(graphics, font, mouseX, mouseY, listX, rowY, half, "Switch", canSwitch(), this::switchSelected);
		button(graphics, font, mouseX, mouseY, listX + half + 8, rowY, half, "Remove", selectedValid(), this::removeSelected);
	}

	private void row(
		GuiGraphicsExtractor graphics,
		Font font,
		int mouseX,
		int mouseY,
		float x,
		float y,
		float w,
		AccountStore.Entry entry,
		int index
	) {
		boolean hovered = GuiDraw.hovered(mouseX, mouseY, x, y, w, ROW_H - 2);
		boolean selected = entry.uuid.equals(selectedId);
		boolean loggedIn = entry.active();
		boolean failed = selected && entry.uuid.equals(AccountStore.lastFailedId());
		int outline = Theme.LINE;
		int fill = Theme.CARD;
		if (selected && failed) {
			outline = FAIL;
			fill = Theme.withAlpha(FAIL, 48);
		} else if (selected && loggedIn) {
			outline = OK;
			fill = Theme.withAlpha(OK, 48);
		} else if (selected || hovered) {
			outline = Theme.ACCENT;
			fill = Theme.CARD_HOVER;
		}
		if (ControlChrome.on()) {
			int glass = selected && (failed || loggedIn)
				? fill
				: (selected || hovered ? Theme.CARD_HOVER : ControlChrome.cardFill());
			ControlChrome.glass(graphics, x, y, w, ROW_H - 4, 10f, glass);
			if (selected && (failed || loggedIn)) {
				GuiDraw.roundedOutline(graphics, x, y, w, ROW_H - 4, 10f, outline, 1.4f);
			}
		} else {
			ClickLook.outside(graphics, x, y, w, ROW_H - 4, 6, fill, outline);
		}
		int text = ControlChrome.on() ? ControlChrome.cardText() : Theme.TEXT;
		float starCx = x + 12;
		float starCy = y + (ROW_H - 4) * 0.5f;
		favoriteMark(graphics, starCx, starCy, entry.favorite);
		GuiDraw.menu(graphics, font, entry.name, x + 24, GuiDraw.middle(y, ROW_H - 4), text);
		String kind = loggedIn ? "playing" : entry.kindLabel();
		int kindColor = selected && failed ? FAIL : (loggedIn ? OK : Theme.MUTED);
		GuiDraw.small(graphics, font, kind, x + w - 10 - GuiDraw.smallWidth(font, kind), y + 8, kindColor);
		hits.add(new Hit(x, y, w, ROW_H - 2, () -> select(index), () -> {
			select(index);
			if (canSwitch()) {
				switchSelected();
			}
		}));
		hits.add(new Hit(x, y, STAR, ROW_H - 2, () -> toggleFavorite(index)));
	}

	private static void favoriteMark(GuiGraphicsExtractor graphics, float cx, float cy, boolean on) {
		int color = on ? Theme.ACCENT : Theme.MUTED;
		float longArm = on ? 5.1f : 4.3f;
		float shortArm = on ? 3.3f : 2.7f;
		float thick = on ? 1.55f : 1.2f;
		GuiDraw.stroke(graphics, cx, cy - longArm, cx, cy + longArm, thick, color);
		GuiDraw.stroke(graphics, cx - shortArm, cy, cx + shortArm, cy, thick, color);
		float diag = shortArm * 0.72f;
		GuiDraw.stroke(graphics, cx - diag, cy - diag, cx + diag, cy + diag, thick * 0.85f, color);
		GuiDraw.stroke(graphics, cx - diag, cy + diag, cx + diag, cy - diag, thick * 0.85f, color);
		if (on) {
			GuiDraw.circle(graphics, cx, cy, 1.55f, color);
		}
	}

	private void field(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float x, float y, float w, String hint) {
		boolean hovered = GuiDraw.hovered(mouseX, mouseY, x, y, w, BTN_H);
		int outline = tokenFocus || hovered ? Theme.ACCENT : Theme.LINE;
		if (ControlChrome.on()) {
			ControlChrome.glass(graphics, x, y, w, BTN_H, 10f, ControlChrome.cardFill());
		} else {
			ClickLook.outside(graphics, x, y, w, BTN_H, 6, Theme.CARD, outline);
		}
		String shown = token.isBlank() ? hint : mask(token);
		int color = token.isBlank() ? Theme.MUTED : (ControlChrome.on() ? ControlChrome.cardText() : Theme.TEXT);
		GuiDraw.small(graphics, font, shown, x + 8, y + 7, color);
		hits.add(new Hit(x, y, w, BTN_H, () -> tokenFocus = true));
	}

	private void button(
		GuiGraphicsExtractor graphics,
		Font font,
		int mouseX,
		int mouseY,
		float x,
		float y,
		float w,
		String label,
		boolean enabled,
		Runnable action
	) {
		boolean hovered = enabled && GuiDraw.hovered(mouseX, mouseY, x, y, w, BTN_H);
		if (ControlChrome.on()) {
			ControlChrome.glass(graphics, x, y, w, BTN_H, 10f, hovered ? Theme.CARD_HOVER : ControlChrome.cardFill());
		} else {
			ClickLook.outside(graphics, x, y, w, BTN_H, 6, Theme.CARD, hovered ? Theme.ACCENT : Theme.LINE);
		}
		int text = enabled
			? (ControlChrome.on() ? ControlChrome.cardText() : Theme.TEXT)
			: Theme.MUTED;
		float labelW = GuiDraw.smallWidth(font, label);
		GuiDraw.small(graphics, font, label, x + (w - labelW) * 0.5f, y + 7, text);
		if (enabled) {
			hits.add(new Hit(x, y, w, BTN_H, action));
		}
	}

	private int selectedIndex() {
		if (selectedId == null) {
			return -1;
		}
		List<AccountStore.Entry> accounts = AccountStore.accounts();
		for (int i = 0; i < accounts.size(); i++) {
			if (accounts.get(i).uuid.equals(selectedId)) {
				return i;
			}
		}
		return -1;
	}

	private void select(int index) {
		List<AccountStore.Entry> accounts = AccountStore.accounts();
		if (index >= 0 && index < accounts.size()) {
			selectedId = accounts.get(index).uuid;
		}
	}

	private void toggleFavorite(int index) {
		click();
		select(index);
		int moved = AccountStore.toggleFavorite(index);
		select(moved);
	}

	private boolean selectedValid() {
		return selectedIndex() >= 0;
	}

	private boolean canSwitch() {
		return !AccountStore.busy() && selectedValid();
	}

	private void microsoft() {
		click();
		AccountStore.addMicrosoft(this::setStatus);
	}

	private void prism() {
		click();
		AccountStore.importPrism(this::setStatus);
	}

	private void launcher() {
		click();
		AccountStore.restoreLauncher(this::setStatus);
	}

	private void addToken() {
		click();
		AccountStore.addToken(token, this::setStatus);
		token = "";
		tokenCursor = 0;
	}

	private void switchSelected() {
		click();
		AccountStore.switchTo(selectedIndex(), this::setStatus);
	}

	private void removeSelected() {
		click();
		int index = selectedIndex();
		AccountStore.remove(index);
		List<AccountStore.Entry> accounts = AccountStore.accounts();
		if (accounts.isEmpty()) {
			selectedId = null;
		} else {
			select(Math.min(index, accounts.size() - 1));
		}
		setStatus("Removed.");
	}

	private void setStatus(String message) {
		if (minecraft != null) {
			minecraft.execute(() -> status = message == null ? "" : message);
		} else {
			status = message == null ? "" : message;
		}
	}

	private void click() {
		tokenFocus = false;
		AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
	}

	private static String mask(String value) {
		if (value.length() <= 18) {
			return value;
		}
		return value.substring(0, 10) + "…" + value.substring(value.length() - 6);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (event.button() != 0) {
			return super.mouseClicked(event, doubled);
		}
		tokenFocus = false;
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (hit.contains(event.x(), event.y())) {
				if (doubled) {
					if (hit.doubleClick != null) {
						hit.doubleClick.run();
					}
					return true;
				}
				hit.click.run();
				return true;
			}
		}
		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0) {
			scroll = (float) (scroll - scrollY);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (tokenFocus) {
			if (event.key() == InputConstants.KEY_BACKSPACE && tokenCursor > 0) {
				token = token.substring(0, tokenCursor - 1) + token.substring(tokenCursor);
				tokenCursor--;
				return true;
			}
			if (event.key() == InputConstants.KEY_DELETE && tokenCursor < token.length()) {
				token = token.substring(0, tokenCursor) + token.substring(tokenCursor + 1);
				return true;
			}
			if (event.key() == InputConstants.KEY_LEFT) {
				tokenCursor = Math.max(0, tokenCursor - 1);
				return true;
			}
			if (event.key() == InputConstants.KEY_RIGHT) {
				tokenCursor = Math.min(token.length(), tokenCursor + 1);
				return true;
			}
			if (event.key() == InputConstants.KEY_V && event.hasControlDown()) {
				String clip = minecraft.keyboardHandler.getClipboard();
				if (clip != null && !clip.isBlank()) {
					String insert = clip.replace("\n", "").replace("\r", "").trim();
					token = token.substring(0, tokenCursor) + insert + token.substring(tokenCursor);
					tokenCursor += insert.length();
				}
				return true;
			}
			if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
				addToken();
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (tokenFocus && event.isAllowedChatCharacter()) {
			String insert = event.codepointAsString();
			if (!insert.isEmpty() && token.length() < 4096) {
				token = token.substring(0, tokenCursor) + insert + token.substring(tokenCursor);
				tokenCursor += insert.length();
			}
			return true;
		}
		return super.charTyped(event);
	}

	private record Hit(float x, float y, float w, float h, Runnable click, Runnable doubleClick) {
		Hit(float x, float y, float w, float h, Runnable click) {
			this(x, y, w, h, click, null);
		}

		boolean contains(double mx, double my) {
			return GuiDraw.hovered(mx, my, x, y, w, h);
		}
	}
}
