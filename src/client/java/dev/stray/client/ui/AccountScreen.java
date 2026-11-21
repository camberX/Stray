package dev.stray.client.ui;

import dev.stray.client.account.AccountStore;
import dev.stray.client.account.NameChanger;
import dev.stray.client.input.MouseButtons;
import dev.stray.client.render.TitleBackdrop;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Title-screen account list: Microsoft device code, session token, switch. */
public class AccountScreen extends SubScreen {
	private static final float ROW_H = 26f;
	private static final int OK = 0xFF3DDC84;
	private static final String STAR_ON = "";
	private static final String STAR_OFF = "";

	private final Input token = input("account-token", 4096, "Paste a session token");
	private final Input newName = input("account-new-name", 16, "New name");
	private volatile String nameStatus = "";
	/** The name the Change button was pressed for once; a second press within 10 s renames. */
	private String armedName;
	private long armedAt;
	private UUID selectedId;
	/** The row under the mouse this frame, for double click to switch. */
	private int hoveredRow = -1;
	private volatile String status = "";

	public AccountScreen(Screen parent) {
		super("Accounts", parent, 360);
		token.display = AccountScreen::mask;
		token.clean = value -> value.replace("\n", "").replace("\r", "").trim();
		newName.clean = value -> value.replace(" ", "").trim();
		// Nothing focused at first: the list is what you came for.
		focusInput(null);
	}

	@Override
	protected void init() {
		super.init();
		AccountStore.rememberLauncher(minecraft);
		if (nameStatus.isBlank()) {
			NameChanger.eligibility(this::setNameStatus);
		}
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
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		if (minecraft.level == null) {
			TitleBackdrop.draw(graphics, width, height);
		}
	}


	@Override
	protected float body(float x, float y, float w) {
		List<AccountStore.Entry> accounts = AccountStore.accounts();
		hoveredRow = -1;
		y = section("Saved", x, y);
		float after = 4f + GAP + 14f + BUTTON + GAP + FIELD + 4f + 14f + 6f + BUTTON + 14f + FIELD + 4f + 14f + 6f;
		y = list("accounts", accounts.size(), ROW_H, x, y, w, listHeight(y, after, ROW_H, Math.min(accounts.size(), 6), 2), "No saved accounts yet", (i, rx, ry, rw, rh, hot) -> {
			AccountStore.Entry entry = accounts.get(i);
			boolean selected = entry.uuid.equals(selectedId);
			boolean playing = entry.active();
			boolean failed = selected && entry.uuid.equals(AccountStore.lastFailedId());
			card(rx, ry, rw, rh, hot, selected);
			// The star pins an account to the top.
			float sx = rx + 4f;
			boolean starHot = hovered(sx, ry, 18f, rh);
			if (hot && !starHot) {
				hoveredRow = i;
			}
			icon(entry.favorite ? STAR_ON : STAR_OFF, sx + 4.5f, ry, rh, entry.favorite ? TEXT : starHot ? MUTED : FAINT);
			String kind = failed ? "failed" : playing ? "playing" : entry.kindLabel();
			int kindColor = failed ? DANGER : playing ? OK : MUTED;
			float tagX = rx + rw - 8f - textWidth(kind) - 12f;
			tag(kind, tagX, ry, rh, kindColor);
			text(fit(entry.name, tagX - rx - 32f), rx + 26f, ry, rh, playing || selected ? TEXT : MUTED);
			hit(rx, ry, rw, rh, () -> select(i));
			hit(sx, ry, 18f, rh, () -> toggleFavorite(i));
		});
		y += 4f + GAP;

		boolean idle = !AccountStore.busy();
		y = section("Add", x, y);
		float third = (w - GAP * 2) / 3f;
		button("Microsoft", x, y, third, Style.SECONDARY, idle ? this::microsoft : null);
		button("Prism", x + third + GAP, y, third, Style.SECONDARY, idle ? this::prism : null);
		button("Launcher", x + (third + GAP) * 2, y, third, Style.SECONDARY, idle ? this::launcher : null);
		y += BUTTON + GAP;
		float addW = buttonWidth("Add");
		field(token, null, 0f, x, y, w - addW - GAP);
		button("Add", x + w - addW, y + (FIELD - BUTTON) * 0.5f, addW, Style.SECONDARY, idle && !token.get().isBlank() ? this::addToken : null);
		y += FIELD + 4f;

		String note = status;
		String url = AccountStore.deviceUrl();
		if (note.isBlank() && url != null && !url.isBlank()) {
			note = "Open microsoft.com/link and enter the code";
		}
		String lower = note.toLowerCase(Locale.ROOT);
		boolean error = lower.contains("fail") || lower.contains("error") || lower.contains("couldn");
		y = hint(note, x, y + 2f, w, error) + 8f;

		// Username: renames the account you are playing on, like minecraft.net's profile page.
		y = section("Username of " + AccountStore.currentName(), x, y);
		boolean free = !NameChanger.busy();
		float checkW = buttonWidth("Check");
		boolean armed = armedName != null && armedName.equals(newName.get()) && System.currentTimeMillis() - armedAt < 10_000L;
		String changeLabel = armed ? "Confirm" : "Change";
		float changeW = buttonWidth("Confirm");
		field(newName, null, 0f, x, y, w - checkW - changeW - GAP * 2);
		boolean typed = !newName.get().isBlank();
		button("Check", x + w - checkW - changeW - GAP, y + (FIELD - BUTTON) * 0.5f, checkW, Style.SECONDARY, free && typed ? this::checkName : null);
		button(changeLabel, x + w - changeW, y + (FIELD - BUTTON) * 0.5f, changeW, armed ? Style.DANGER : Style.SECONDARY, free && typed ? this::changeName : null);
		y += FIELD + 4f;
		String nl = nameStatus.toLowerCase(Locale.ROOT);
		boolean nameError = nl.contains("taken") || nl.contains("can't") || nl.contains("couldn") || nl.contains("rejected")
			|| nl.contains("expired") || nl.contains("too many") || nl.contains("isn't") || nl.contains("doesn't");
		y = hint(nameStatus, x, y + 2f, w, nameError) + 8f;
		boolean picked = selectedIndex() >= 0;
		return buttons(x, y, w,
			new Object[][] {{"Remove", Style.DANGER, picked ? (Runnable) this::removeSelected : null}},
			new Object[][] {{"Done", Style.SECONDARY, (Runnable) this::onClose}, {"Switch", Style.PRIMARY, picked && idle ? (Runnable) this::switchSelected : null}});
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
		select(AccountStore.toggleFavorite(index));
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
		if (AccountStore.busy() || token.get().isBlank()) {
			return;
		}
		click();
		AccountStore.addToken(token.get(), this::setStatus);
		token.set("");
	}

	private void switchSelected() {
		if (AccountStore.busy() || selectedIndex() < 0) {
			return;
		}
		click();
		AccountStore.switchTo(selectedIndex(), this::setStatus);
	}

	private void removeSelected() {
		int index = selectedIndex();
		if (index < 0) {
			return;
		}
		click();
		AccountStore.remove(index);
		List<AccountStore.Entry> accounts = AccountStore.accounts();
		if (accounts.isEmpty()) {
			selectedId = null;
		} else {
			select(Math.min(index, accounts.size() - 1));
		}
		setStatus("Removed.");
	}

	@Override
	protected void submit() {
		if (token.focused() && !token.get().isBlank()) {
			addToken();
		} else if (newName.focused() && !newName.get().isBlank()) {
			checkName();
		}
	}

	private void checkName() {
		click();
		armedName = null;
		NameChanger.check(newName.get().trim(), this::setNameStatus);
	}

	/** First press asks to confirm (a change locks the name for 30 days), the second renames. */
	private void changeName() {
		click();
		String name = newName.get().trim();
		String bad = NameChanger.invalid(name);
		if (bad != null) {
			setNameStatus(bad);
			return;
		}
		boolean armed = name.equals(armedName) && System.currentTimeMillis() - armedAt < 10_000L;
		if (!armed) {
			armedName = name;
			armedAt = System.currentTimeMillis();
			setNameStatus("Press Confirm to rename to " + name + " (then no other change for 30 days).");
			return;
		}
		armedName = null;
		NameChanger.change(name, this::setNameStatus, now -> newName.set(""));
	}

	private void setNameStatus(String message) {
		if (minecraft != null) {
			minecraft.execute(() -> nameStatus = message == null ? "" : message);
		} else {
			nameStatus = message == null ? "" : message;
		}
	}

	private void setStatus(String message) {
		if (minecraft != null) {
			minecraft.execute(() -> status = message == null ? "" : message);
		} else {
			status = message == null ? "" : message;
		}
	}

	private void click() {
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
		if (doubled && MouseButtons.left(event) && hoveredRow >= 0) {
			select(hoveredRow);
			switchSelected();
			return true;
		}
		return super.mouseClicked(event, doubled);
	}
}
