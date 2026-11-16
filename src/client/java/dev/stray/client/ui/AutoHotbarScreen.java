package dev.stray.client.ui;

import dev.stray.client.input.MouseButtons;
import dev.stray.client.item.AutoHotbar;
import dev.stray.client.render.GuiDraw;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Auto hotbar setup, after NoammAddons' AutoHotbarScreen: triggers (a name and the exact chat
 * message) and, for the selected trigger, which item goes to which hotbar slot.
 */
public class AutoHotbarScreen extends SubScreen {
	private static final int MAX_NAME = 32;
	private static final int MAX_MESSAGE = 256;
	private static final float CELL = 20f;
	private static final float CELL_GAP = 2f;
	/** Triggers on the left and the items on the right when the screen is wide enough. */
	private static final float WIDE = 500f;

	private final Input name = input("auto-hotbar-0", MAX_NAME, "Mask swap");
	private final Input message = input("auto-hotbar-1", MAX_MESSAGE, "[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!");
	/** The trigger the fields edit and the grid maps; null for a new one. */
	private String selected;
	private ItemStack picked = ItemStack.EMPTY;
	private ItemStack hovered = ItemStack.EMPTY;

	public AutoHotbarScreen(Screen parent) {
		super("Auto hotbar", parent, 500);
		List<String> names = new ArrayList<>(AutoHotbar.setup().triggers.keySet());
		if (!names.isEmpty()) {
			select(names.getFirst());
		}
	}

	@Override
	protected String subtitle() {
		int count = AutoHotbar.setup().triggers.size();
		return count + (count == 1 ? " trigger" : " triggers") + " · items to slots on a chat message";
	}

	@Override
	protected void save() {
		AutoHotbar.save();
	}

	@Override
	protected float body(float x, float y, float w) {
		hovered = ItemStack.EMPTY;
		float gridW = CELL * 9 + CELL_GAP * 8;
		float bottom;
		if (w >= WIDE - PAD * 2) {
			float gap = 18f;
			float leftW = w - gridW - gap;
			float left = triggers(x, y, leftW);
			GuiDraw.fill(graphics, x + leftW + gap * 0.5f, y, 0.5f, Math.max(left, y + 160f) - y, 0x18FFFFFF);
			float right = items(x + leftW + gap, y, gridW);
			bottom = Math.max(left, right);
		} else {
			float top = triggers(x, y, w);
			bottom = items(x + (w - gridW) * 0.5f, divider(x, top + 2f, w), gridW);
		}
		if (!picked.isEmpty()) {
			graphics.item(picked, mouseX - 8, mouseY - 8);
		}
		return bottom;
	}

	private float triggers(float x, float y, float w) {
		AutoHotbar.Setup setup = AutoHotbar.setup();
		List<Map.Entry<String, String>> rows = new ArrayList<>(setup.triggers.entrySet());
		y = section("Triggers", x, y);
		float after = 4f + GAP + FIELD * 2 + GAP + 4f + 14f + 6f + BUTTON;
		y = list("triggers", rows.size(), 26f, x, y, w, listHeight(y, after, 26f, Math.min(rows.size(), 5), 2), "No triggers yet: add one below", (i, rx, ry, rw, rh, hot) -> {
			String key = rows.get(i).getKey();
			boolean on = !setup.disabled.contains(key);
			card(rx, ry, rw, rh, hot, key.equals(selected));
			float right = rowDelete(rx + rw - 4f, ry, rh, () -> delete(key)) - 6f;
			right = rowToggle(right, ry, rh, on, () -> {
				if (!setup.disabled.remove(key)) {
					setup.disabled.add(key);
				}
				AutoHotbar.save();
			}) - 8f;
			int count = setup.rules.getOrDefault(key, List.of()).size();
			String items = count + (count == 1 ? " item" : " items");
			// Name on top, the message under it.
			GuiDraw.text(graphics, font, MenuFont.shadow(fit(key, right - rx - 8f - textWidth(items) - 8f)), rx + 8f, ry + 4f, on ? TEXT : MUTED, false);
			GuiDraw.small(graphics, font, items, right - GuiDraw.smallWidth(font, items), ry + 3f, FAINT);
			GuiDraw.small(graphics, font, dev.stray.client.util.TextFit.small(font, rows.get(i).getValue(), (int) (right - rx - 8f)), rx + 8f, ry + 15f, FAINT);
			hit(rx, ry, right - rx, rh, () -> select(key));
		});
		y += 4f + GAP;
		float labelW = labelColumn("Name", "Message");
		y = field(name, "Name", labelW, x, y, w) + GAP;
		y = field(message, "Message", labelW, x, y, w) + 4f;
		y = hint("Enter saves · the message must match the chat line exactly", x, y + 2f, w, false) + 6f;
		return buttons(x, y, w,
			new Object[][] {{"New", Style.SECONDARY, (Runnable) this::newTrigger}},
			new Object[][] {{"Done", Style.SECONDARY, (Runnable) this::onClose}, {selected == null ? "Add" : "Save", Style.PRIMARY, (Runnable) this::submit}});
	}

	private float items(float x, float y, float w) {
		AutoHotbar.Setup setup = AutoHotbar.setup();
		y = section(selected == null ? "Items" : "Items · " + selected, x, y);
		if (selected == null) {
			return hint(setup.triggers.isEmpty() ? "Add a trigger to start." : "Pick a trigger to map its items.", x, y + 2f, w, false);
		}
		if (minecraft.player == null) {
			return y;
		}
		Inventory inventory = minecraft.player.getInventory();
		List<AutoHotbar.Rule> rules = setup.rules.getOrDefault(selected, List.of());
		float step = CELL + CELL_GAP;
		// Main inventory (slots 9-35).
		for (int i = 0; i < 27; i++) {
			float cx = x + (i % 9) * step;
			float cy = y + (i / 9) * step;
			ItemStack stack = inventory.getItem(i + 9);
			AutoHotbar.Rule rule = ruleFor(rules, stack);
			slot(stack, cx, cy, CELL, rule != null, rule == null ? null : Integer.toString(rule.hotbarSlot + 1), stack.isEmpty() ? null : () -> picked = stack.copy());
			noteHover(stack, cx, cy);
		}
		// The hotbar, a little apart.
		float hy = y + 3 * step + 6f;
		for (int i = 0; i < 9; i++) {
			float cx = x + i * step;
			ItemStack stack = inventory.getItem(i);
			int slot = i;
			boolean target = false;
			for (AutoHotbar.Rule rule : rules) {
				target |= rule.hotbarSlot == i;
			}
			slot(stack, cx, hy, CELL, target, target ? Integer.toString(i + 1) : null, () -> mapTo(slot));
			noteHover(stack, cx, hy);
		}
		y = hy + CELL + 8f;
		String help = !picked.isEmpty() ? "Now click the hotbar slot for " + picked.getHoverName().getString()
			: !hovered.isEmpty() ? hovered.getHoverName().getString()
			: "Click an item, then a hotbar slot. A hotbar slot alone clears it.";
		return hint(help, x, y, w, false);
	}

	private void noteHover(ItemStack stack, float x, float y) {
		if (!stack.isEmpty() && hovered(x, y, CELL, CELL)) {
			hovered = stack;
		}
	}

	private static AutoHotbar.Rule ruleFor(List<AutoHotbar.Rule> rules, ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		String id = AutoHotbar.idOf(stack);
		for (AutoHotbar.Rule rule : rules) {
			if (rule.id.equals(id)) {
				return rule;
			}
		}
		return null;
	}

	private void mapTo(int slot) {
		if (selected == null) {
			return;
		}
		List<AutoHotbar.Rule> rules = AutoHotbar.setup().rules.computeIfAbsent(selected, key -> new ArrayList<>());
		if (picked.isEmpty()) {
			rules.removeIf(rule -> rule.hotbarSlot == slot);
		} else {
			String id = AutoHotbar.idOf(picked);
			rules.removeIf(rule -> rule.hotbarSlot == slot || rule.id.equals(id));
			rules.add(new AutoHotbar.Rule(id, slot));
			picked = ItemStack.EMPTY;
		}
		AutoHotbar.save();
	}

	// ---------------------------------------------------------------- triggers

	private void select(String key) {
		selected = key;
		name.set(key);
		message.set(AutoHotbar.setup().triggers.getOrDefault(key, ""));
		message.focus();
		picked = ItemStack.EMPTY;
	}

	private void newTrigger() {
		selected = null;
		name.set("");
		message.set("");
		name.focus();
		picked = ItemStack.EMPTY;
	}

	@Override
	protected void submit() {
		String newName = name.get().trim();
		String newMessage = message.get().trim();
		if (newName.isEmpty() || newMessage.isEmpty()) {
			return;
		}
		AutoHotbar.Setup setup = AutoHotbar.setup();
		if (selected != null && !selected.equals(newName)) {
			if (setup.triggers.containsKey(newName)) {
				return;
			}
			List<AutoHotbar.Rule> rules = setup.rules.remove(selected);
			boolean off = setup.disabled.remove(selected);
			setup.triggers.remove(selected);
			if (rules != null) {
				setup.rules.put(newName, rules);
			}
			if (off) {
				setup.disabled.add(newName);
			}
		}
		setup.triggers.put(newName, newMessage);
		AutoHotbar.save();
		select(newName);
	}

	private void delete(String key) {
		AutoHotbar.Setup setup = AutoHotbar.setup();
		setup.triggers.remove(key);
		setup.rules.remove(key);
		setup.disabled.remove(key);
		AutoHotbar.save();
		if (key.equals(selected)) {
			newTrigger();
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (!MouseButtons.left(event)) {
			picked = ItemStack.EMPTY;
			return super.mouseClicked(event, doubled);
		}
		boolean used = super.mouseClicked(event, doubled);
		if (!used) {
			picked = ItemStack.EMPTY;
		}
		return used;
	}
}
