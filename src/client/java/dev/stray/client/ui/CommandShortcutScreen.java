package dev.stray.client.ui;

import dev.stray.client.config.StrayConfig;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

/** Add and edit chat command aliases. Esc returns to the parent menu. */
public class CommandShortcutScreen extends SubScreen {
	private final Input alias = input("cmd-shortcut-0", CommandShortcuts.MAX_ALIAS, "hub");
	private final Input command = input("cmd-shortcut-1", CommandShortcuts.MAX_COMMAND, "warp garden");

	public CommandShortcutScreen(Screen parent) {
		super("Shortcuts", parent, 320);
		alias.clean = value -> TextKeys.singleLine(value).trim().replace(" ", "");
		CommandShortcuts.normalize(StrayConfig.get());
	}

	@Override
	protected String subtitle() {
		return StrayConfig.get().commandShortcuts.size() + " of " + CommandShortcuts.MAX + " · chat aliases";
	}

	@Override
	protected void save() {
		StrayConfig.get().save();
	}

	@Override
	protected float body(float x, float y, float w) {
		StrayConfig config = StrayConfig.get();
		CommandShortcuts.normalize(config);
		y = toggleRow("Pass arguments", config.commandShortcutsPassArgs, x, y, w, v -> {
			config.commandShortcutsPassArgs = v;
			config.save();
		});
		y = hint(config.commandShortcutsPassArgs ? "/alias extra keeps \"extra\" after the command" : "/alias extra drops \"extra\"", x, y + 2f, w, false);
		y = divider(x, y, w);

		List<StrayConfig.CommandShortcut> rows = config.commandShortcuts;
		y = section("Shortcuts", x, y);
		// Fields, hint and buttons below the list.
		float after = 4f + GAP + FIELD * 2 + GAP + 4f + 14f + 6f + BUTTON;
		float aliasCol = 0f;
		for (StrayConfig.CommandShortcut row : rows) {
			aliasCol = Math.max(aliasCol, textWidth("/" + row.alias));
		}
		float col = Math.min(aliasCol + 12f, w * 0.38f);
		y = list("shortcuts", rows.size(), 22f, x, y, w, listHeight(y, after, 22f, Math.min(rows.size(), 6), 2), "No shortcuts yet: add one below", (i, rx, ry, rw, rh, hot) -> {
			StrayConfig.CommandShortcut row = rows.get(i);
			boolean editing = row.alias.equals(alias.get()) && !alias.get().isEmpty();
			card(rx, ry, rw, rh, hot, editing);
			float right = rowDelete(rx + rw - 4f, ry, rh, () -> CommandShortcuts.remove(i)) - 6f;
			text(fit("/" + row.alias, col - 6f), rx + 8f, ry, rh, TEXT);
			float tx = rx + 8f + col;
			text(fit("/" + row.command, right - tx), tx, ry, rh, MUTED);
			hit(rx, ry, right - rx, rh, () -> {
				alias.set(row.alias);
				command.set(row.command);
				command.focus();
			});
		});
		y += 4f + GAP;

		float labelW = labelColumn("Alias", "Run");
		y = field(alias, "Alias", labelW, x, y, w) + GAP;
		y = field(command, "Run", labelW, x, y, w) + 4f;
		y = hint("Enter adds · Tab switches fields · Tab in chat completes", x, y + 2f, w, false) + 6f;
		return buttons(x, y, w,
			new Object[][] {{"Clear", Style.SECONDARY, (Runnable) this::clearFields}},
			new Object[][] {{"Done", Style.SECONDARY, (Runnable) this::onClose}, {"Add", Style.PRIMARY, (Runnable) this::submit}});
	}

	@Override
	protected void submit() {
		if (CommandShortcuts.upsert(alias.get(), command.get())) {
			clearFields();
		}
	}

	private void clearFields() {
		alias.set("");
		command.set("");
		alias.focus();
	}

	@Override
	protected boolean typedFirst(Input input, String typed) {
		// A space after the alias moves on to the command, like typing it in chat.
		if (input == alias && Character.isWhitespace(typed.charAt(0))) {
			command.focus();
			return true;
		}
		return false;
	}
}
