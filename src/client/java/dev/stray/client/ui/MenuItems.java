package dev.stray.client.ui;

import dev.stray.client.combat.OdinClicks;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.config.UnloadState;
import dev.stray.client.render.MobCatalog;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The rows a click GUI category shows: modules, the mob list, key binds, or search results. */
final class MenuItems {
	private MenuItems() {
	}

	/** What a category shows (or the search results), used by the Shadow menu. */
	static List<Item> items(StrayScreen screen, String category) {
		String query = ClickGui.filterText().trim().toLowerCase(Locale.ROOT);
		List<Item> out = new ArrayList<>();
		if (!query.isEmpty()) {
			for (ClickGui.Mod mod : ClickGui.modules()) {
				String name = ClickGui.display(mod.name());
				if (name.toLowerCase(Locale.ROOT).contains(query)) {
					out.add(modItem(mod, label(mod.column())));
				}
			}
			return out;
		}
		if ("Mobs".equals(category)) {
			StrayConfig config = StrayConfig.get();
			for (MobCatalog.Entry entry : MobCatalog.filtered(ClickGui.mobQuery())) {
				String id = entry.id().toString();
				out.add(new Item("mob:" + id, entry.name(), config.isMobGlowSelected(id), null, () -> {
					StrayConfig.get().toggleMobGlow(id);
					UnloadState.markDirty();
				}, null));
			}
			return out;
		}
		if ("Keys".equals(category)) {
			for (int i = 0; i < ClickGui.BIND_LABEL.length; i++) {
				int which = ClickGui.BIND_WHICH[i];
				String chip = screen.clickBindListening(which)
					? "Press a key"
					: OdinClicks.keyLabel(OdinClicks.parseKey(ClickGui.bindKey(which)));
				out.add(new Item("bind:" + which, ClickGui.BIND_LABEL[i], null, chip, () -> screen.clickListenBind(which), null));
			}
			StrayConfig config = StrayConfig.get();
			out.add(new Item("keys:shortcuts", "Shortcuts", config.commandShortcutsEnabled, null, () -> {
				StrayConfig current = StrayConfig.get();
				current.commandShortcutsEnabled = !current.commandShortcutsEnabled;
				CommandShortcuts.sync();
				UnloadState.markDirty();
			}, null));
			if (config.commandShortcutsEnabled) {
				out.add(new Item("keys:args", "Pass arguments", config.commandShortcutsPassArgs, null, () -> {
					StrayConfig current = StrayConfig.get();
					current.commandShortcutsPassArgs = !current.commandShortcutsPassArgs;
					UnloadState.markDirty();
				}, null));
				out.add(new Item("keys:edit", "Edit shortcuts", null, "Open", () ->
					Minecraft.getInstance().gui.setScreen(new CommandShortcutScreen(screen)), null));
			}
			out.add(new Item("keys:commandkeys", "Command keys", config.commandKeysEnabled, null, () -> {
				StrayConfig current = StrayConfig.get();
				current.commandKeysEnabled = !current.commandKeysEnabled;
				UnloadState.markDirty();
			}, null));
			if (config.commandKeysEnabled) {
				int count = config.commandKeys == null ? 0 : config.commandKeys.size();
				out.add(new Item("keys:commandkeys:edit", "Edit command keys", null, count == 0 ? "Open" : count + (count == 1 ? " key" : " keys"), () ->
					Minecraft.getInstance().gui.setScreen(new CommandKeysScreen(screen)), null));
			}
			out.add(new Item("keys:editor", "Open world editor", null, "Open", () -> {
				Minecraft.getInstance().gui.setScreen(null);
				dev.stray.client.EditionGate.openEditor();
			}, null));
			return out;
		}
		for (ClickGui.Mod mod : ClickGui.modules()) {
			if (category.equals(mod.column())) {
				out.add(modItem(mod, null));
			}
		}
		return out;
	}

	private static Item modItem(ClickGui.Mod mod, String chip) {
		boolean settings = ClickGui.moduleHasSettings(mod);
		Boolean on = mod.hold() ? null : mod.on().getAsBoolean();
		Runnable left = mod.hold() ? null : () -> ClickGui.toggle(mod);
		return new Item("mod:" + mod.name(), ClickGui.display(mod.name()), on, chip, left, settings || mod.hold() ? mod : null);
	}

	static String label(String id) {
		return ClickGui.display(id);
	}

	record Item(String key, String label, Boolean on, String chip, Runnable left, ClickGui.Mod mod) {
	}

}
