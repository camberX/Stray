package dev.stray.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.stray.client.combat.OdinClicks;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.input.CommandKeys;
import dev.stray.client.input.MouseButtons;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.List;

/**
 * Command keys: a list of key → command binds. Click the key box and press a key (or mouse
 * button) to set it, type the command, then Add. Clicking a row loads it for editing.
 */
public class CommandKeysScreen extends SubScreen {
	private final Input command = input("command-key-command", CommandKeys.MAX_COMMAND, "warp garden");
	private String key = "";
	/** Waiting for the next key or mouse button to become the bind. */
	private boolean listening;
	/** The key that set a bind also arrives as a typed character right after; drop that one. */
	private boolean swallowChar;
	/** Row being edited, or -1 for a new bind. */
	private int editing = -1;
	private String error = "";

	public CommandKeysScreen(Screen parent) {
		super("Command keys", parent, 320);
		CommandKeys.normalize(StrayConfig.get());
	}

	@Override
	protected String subtitle() {
		return StrayConfig.get().commandKeys.size() + " of " + CommandKeys.MAX + " · key to command";
	}

	@Override
	protected void save() {
		StrayConfig.get().save();
		CommandKeys.syncEdge();
	}

	@Override
	protected float body(float x, float y, float w) {
		StrayConfig config = StrayConfig.get();

		List<StrayConfig.CommandKey> rows = config.commandKeys;
		y = section("Binds", x, y);
		float after = 4f + GAP + FIELD * 2 + GAP + 4f + 14f + 6f + BUTTON;
		y = list("keys", rows.size(), 22f, x, y, w, listHeight(y, after, 22f, Math.min(rows.size(), 6), 2), "No command keys yet: set one below", (i, rx, ry, rw, rh, hot) -> {
			StrayConfig.CommandKey row = rows.get(i);
			card(rx, ry, rw, rh, hot, i == editing);
			float right = rowDelete(rx + rw - 4f, ry, rh, () -> {
				CommandKeys.remove(i);
				if (editing == i) {
					clearFields();
				} else if (editing > i) {
					editing--;
				}
			}) - 6f;
			float tagW = tag(OdinClicks.keyLabel(OdinClicks.parseKey(row.key)), rx + 8f, ry, rh, TEXT);
			float tx = rx + 8f + Math.max(tagW, 34f) + 8f;
			text(fit("/" + row.command, right - tx), tx, ry, rh, MUTED);
			hit(rx, ry, right - rx, rh, () -> {
				editing = i;
				key = row.key;
				command.set(row.command);
				command.focus();
				listening = false;
				error = "";
			});
		});
		y += 4f + GAP;

		float labelW = labelColumn("Key", "Run /");
		String shown;
		int color;
		if (listening) {
			shown = (System.currentTimeMillis() / 400L) % 2L == 0L ? "Press a key..." : "Press a key";
			color = TEXT;
		} else if (key.isEmpty()) {
			shown = "Click to set";
			color = FAINT;
		} else {
			shown = OdinClicks.keyLabel(OdinClicks.parseKey(key));
			color = TEXT;
		}
		y = valueBox("Key", labelW, shown, color, listening, x, y, w, () -> {
			listening = true;
			error = "";
		}) + GAP;
		y = field(command, "Run /", labelW, x, y, w) + 4f;
		String help = !error.isEmpty() ? error
			: listening ? "Press a key or mouse button · Esc clears"
			: "Enter adds · runs when you press the key in game";
		y = hint(help, x, y + 2f, w, !error.isEmpty()) + 6f;
		return buttons(x, y, w,
			new Object[][] {{editing >= 0 ? "New" : "Clear", Style.SECONDARY, (Runnable) this::clearFields}},
			new Object[][] {{"Done", Style.SECONDARY, (Runnable) this::onClose}, {editing >= 0 ? "Save" : "Add", Style.PRIMARY, (Runnable) this::submit}});
	}

	@Override
	protected void submit() {
		if (key.isEmpty()) {
			error = "Set a key first.";
			return;
		}
		if (CommandKeys.normalizeCommand(command.get()).isEmpty()) {
			error = "Type the command to run.";
			return;
		}
		if (editing < 0 && StrayConfig.get().commandKeys.size() >= CommandKeys.MAX) {
			error = "That's the maximum of " + CommandKeys.MAX + ".";
			return;
		}
		if (CommandKeys.upsert(key, command.get(), editing)) {
			clearFields();
		}
	}

	private void clearFields() {
		key = "";
		command.set("");
		editing = -1;
		listening = false;
		error = "";
		command.focus();
	}

	private void setKey(InputConstants.Key bound) {
		key = OdinClicks.keyName(bound);
		listening = false;
		error = "";
		command.focus();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (listening && !MouseButtons.left(event)) {
			setKey(InputConstants.Type.MOUSE.getOrCreate(event.button()));
			return true;
		}
		boolean used = super.mouseClicked(event, doubled);
		if (!used && listening && MouseButtons.left(event)) {
			// A left click elsewhere would bind the left mouse button: a trap. Cancel instead.
			listening = false;
		}
		return used;
	}

	@Override
	protected boolean keyFirst(KeyEvent event) {
		if (listening) {
			int k = event.key();
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
		return false;
	}

	@Override
	protected boolean typedFirst(Input input, String typed) {
		if (swallowChar) {
			swallowChar = false;
			return true;
		}
		if (listening) {
			return true;
		}
		error = "";
		return false;
	}
}
