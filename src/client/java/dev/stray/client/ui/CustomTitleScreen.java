package dev.stray.client.ui;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.hud.CustomTitle;
import dev.stray.client.render.GuiDraw;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

/** Add and edit chat titles. Esc returns to the parent menu. */
public class CustomTitleScreen extends SubScreen {
	private final Input trigger = input("custom-title-0", CustomTitle.MAX_TEXT, "rare drop");
	private final Input title = input("custom-title-1", CustomTitle.MAX_TEXT, "&cRare drop!");
	/** The row being edited, or -1 for a new title. */
	private int editing = -1;

	public CustomTitleScreen(Screen parent) {
		super("Titles", parent, 320);
		CustomTitle.normalize(StrayConfig.get());
	}


	@Override
	protected void save() {
		StrayConfig.get().save();
	}

	@Override
	protected float body(float x, float y, float w) {
		StrayConfig config = StrayConfig.get();
		CustomTitle.normalize(config);
		if (editing >= config.customTitles.size()) {
			editing = -1;
		}

		List<StrayConfig.CustomTitleRule> rows = config.customTitles;
		y = section("Titles", x, y);
		float after = 4f + GAP + FIELD * 2 + GAP + 4f + 14f + 6f + BUTTON;
		y = list("titles", rows.size(), 22f, x, y, w, listHeight(y, after, 22f, Math.min(rows.size(), 6), 2), "No titles yet: add one below", (i, rx, ry, rw, rh, hot) -> {
			StrayConfig.CustomTitleRule row = rows.get(i);
			card(rx, ry, rw, rh, hot, i == editing);
			float right = rowDelete(rx + rw - 4f, ry, rh, () -> {
				if (!CustomTitle.remove(i)) {
					return;
				}
				if (editing == i) {
					clearFields();
				} else if (editing > i) {
					editing--;
				}
			}) - 6f;
			float col = Math.min(rw * 0.42f, textWidth(row.trigger) + 14f);
			text(fit(row.trigger, col - 8f), rx + 8f, ry, rh, MUTED);
			// The title as it will look, colours and all.
			GuiDraw.text(graphics, font, styled(CustomTitle.parse(row.title)), rx + 8f + col, Math.round(GuiDraw.middle(ry, rh)), TEXT, false);
			hit(rx, ry, right - rx, rh, () -> {
				trigger.set(row.trigger);
				title.set(row.title);
				editing = i;
				title.focus();
			});
		});
		y += 4f + GAP;

		float labelW = labelColumn("Chat", "Title");
		y = field(trigger, "Chat", labelW, x, y, w) + GAP;
		y = field(title, "Title", labelW, x, y, w) + 4f;
		y += 8f;
		return buttons(x, y, w,
			new Object[][] {{editing >= 0 ? "New" : "Clear", Style.SECONDARY, (Runnable) this::clearFields}},
			new Object[][] {{"Done", Style.SECONDARY, (Runnable) this::onClose}, {editing >= 0 ? "Save" : "Add", Style.PRIMARY, (Runnable) this::submit}});
	}

	@Override
	protected void submit() {
		if (CustomTitle.upsert(trigger.get(), title.get(), editing)) {
			clearFields();
		}
	}

	private void clearFields() {
		trigger.set("");
		title.set("");
		editing = -1;
		trigger.focus();
	}
}
