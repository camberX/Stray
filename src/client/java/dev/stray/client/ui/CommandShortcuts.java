package dev.stray.client.ui;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.CommandNode;
import dev.stray.client.config.StrayConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Chat aliases: `/hub extra` can become `/warp garden extra` when pass-args is on. */
public final class CommandShortcuts {
	public static final int MAX = 48;
	public static final int MAX_ALIAS = 32;
	public static final int MAX_COMMAND = 256;

	private static final Set<String> REGISTERED = new HashSet<>();
	private static Field childrenField;
	private static Field literalsField;
	private static boolean sending;

	private CommandShortcuts() {
	}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		REGISTERED.clear();
		apply(dispatcher, false);
	}

	public static void sync() {
		CommandDispatcher<FabricClientCommandSource> dispatcher = ClientCommands.getActiveDispatcher();
		if (dispatcher == null) {
			return;
		}
		apply(dispatcher, true);
	}

	private static void apply(CommandDispatcher<FabricClientCommandSource> dispatcher, boolean refresh) {
		Set<String> desired = desiredAliases();
		for (String name : new ArrayList<>(REGISTERED)) {
			if (desired.contains(name)) {
				continue;
			}
			removeChild(dispatcher.getRoot(), name);
			REGISTERED.remove(name);
		}
		for (String name : desired) {
			if (!REGISTERED.add(name)) {
				continue;
			}
			if (dispatcher.getRoot().getChild(name) != null) {
				REGISTERED.remove(name);
				continue;
			}
			dispatcher.register(
				ClientCommands.literal(name)
					.executes(context -> runAlias(name, ""))
					.then(ClientCommands.argument("args", StringArgumentType.greedyString())
						.executes(context -> runAlias(name, StringArgumentType.getString(context, "args"))))
			);
		}
		if (refresh) {
			try {
				ClientCommands.refreshCommandCompletions();
			} catch (IllegalStateException ignored) {
			}
		}
	}

	private static Set<String> desiredAliases() {
		Set<String> names = new HashSet<>();
		StrayConfig config = StrayConfig.get();
		if (!config.commandShortcutsEnabled || config.commandShortcuts == null) {
			return names;
		}
		for (StrayConfig.CommandShortcut row : config.commandShortcuts) {
			if (row == null) {
				continue;
			}
			String name = registrationName(row.alias);
			if (!name.isEmpty()) {
				names.add(name);
			}
		}
		return names;
	}

	public static boolean handleTyped(String raw) {
		if (sending) {
			return false;
		}
		StrayConfig config = StrayConfig.get();
		if (!config.commandShortcutsEnabled) {
			return false;
		}
		String message = raw == null ? "" : raw.trim();
		if (message.startsWith("/")) {
			message = message.substring(1).trim();
		}
		if (message.isEmpty()) {
			return false;
		}
		int split = -1;
		for (int i = 0; i < message.length(); i++) {
			if (Character.isWhitespace(message.charAt(i))) {
				split = i;
				break;
			}
		}
		String alias = (split < 0 ? message : message.substring(0, split)).toLowerCase(Locale.ROOT);
		String rest = split < 0 ? "" : message.substring(split).trim();
		return runAlias(alias, rest) == Command.SINGLE_SUCCESS;
	}

	public static int open() {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> {
			if (client.screen instanceof CommandShortcutScreen) {
				client.screen.onClose();
				return;
			}
			client.setScreen(new CommandShortcutScreen(client.screen));
		});
		return Command.SINGLE_SUCCESS;
	}

	public static StrayConfig.CommandShortcut find(StrayConfig config, String alias) {
		String key = normalizeAlias(alias).toLowerCase(Locale.ROOT);
		if (key.isEmpty() || config.commandShortcuts == null) {
			return null;
		}
		for (StrayConfig.CommandShortcut row : config.commandShortcuts) {
			if (row != null && key.equals(normalizeAlias(row.alias).toLowerCase(Locale.ROOT))) {
				return row;
			}
		}
		return null;
	}

	public static boolean upsert(String aliasRaw, String commandRaw) {
		String alias = normalizeAlias(aliasRaw);
		String command = normalizeCommand(commandRaw);
		if (alias.isEmpty() || command.isEmpty()) {
			return false;
		}
		StrayConfig config = StrayConfig.get();
		normalize(config);
		String key = alias.toLowerCase(Locale.ROOT);
		for (StrayConfig.CommandShortcut row : config.commandShortcuts) {
			if (key.equals(row.alias.toLowerCase(Locale.ROOT))) {
				row.command = command;
				config.save();
				sync();
				return true;
			}
		}
		if (config.commandShortcuts.size() >= MAX) {
			return false;
		}
		StrayConfig.CommandShortcut row = new StrayConfig.CommandShortcut();
		row.alias = alias;
		row.command = command;
		config.commandShortcuts.add(row);
		config.save();
		sync();
		return true;
	}

	public static boolean remove(int index) {
		StrayConfig config = StrayConfig.get();
		normalize(config);
		if (index < 0 || index >= config.commandShortcuts.size()) {
			return false;
		}
		config.commandShortcuts.remove(index);
		config.save();
		sync();
		return true;
	}

	public static void normalize(StrayConfig config) {
		List<StrayConfig.CommandShortcut> next = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		if (config.commandShortcuts != null) {
			for (StrayConfig.CommandShortcut row : config.commandShortcuts) {
				if (row == null) {
					continue;
				}
				String alias = normalizeAlias(row.alias);
				String command = normalizeCommand(row.command);
				if (alias.isEmpty() || command.isEmpty()) {
					continue;
				}
				String key = alias.toLowerCase(Locale.ROOT);
				if (!seen.add(key)) {
					continue;
				}
				row.alias = alias;
				row.command = command;
				next.add(row);
				if (next.size() >= MAX) {
					break;
				}
			}
		}
		config.commandShortcuts = next;
	}

	public static String normalizeAlias(String raw) {
		String value = stripSlash(raw);
		if (value.isEmpty()) {
			return "";
		}
		int split = -1;
		for (int i = 0; i < value.length(); i++) {
			if (Character.isWhitespace(value.charAt(i))) {
				split = i;
				break;
			}
		}
		if (split >= 0) {
			value = value.substring(0, split);
		}
		if (value.length() > MAX_ALIAS) {
			value = value.substring(0, MAX_ALIAS);
		}
		return value;
	}

	public static String normalizeCommand(String raw) {
		String value = stripSlash(raw);
		if (value.length() > MAX_COMMAND) {
			value = value.substring(0, MAX_COMMAND);
		}
		return value;
	}

	private static int runAlias(String alias, String args) {
		if (sending) {
			return 0;
		}
		StrayConfig config = StrayConfig.get();
		if (!config.commandShortcutsEnabled) {
			return 0;
		}
		StrayConfig.CommandShortcut match = find(config, alias);
		if (match == null) {
			return 0;
		}
		String expanded = normalizeCommand(match.command);
		if (expanded.isEmpty()) {
			return 0;
		}
		if (config.commandShortcutsPassArgs && args != null && !args.isBlank()) {
			expanded = expanded + " " + args.trim();
		}
		return send(expanded) ? Command.SINGLE_SUCCESS : 0;
	}

	private static String registrationName(String alias) {
		String name = normalizeAlias(alias).toLowerCase(Locale.ROOT);
		if (name.isEmpty()) {
			return "";
		}
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '-' || c == '.' || c == '+') {
				continue;
			}
			return "";
		}
		return name;
	}

	@SuppressWarnings("unchecked")
	private static void removeChild(CommandNode<?> root, String name) {
		try {
			if (childrenField == null) {
				childrenField = CommandNode.class.getDeclaredField("children");
				literalsField = CommandNode.class.getDeclaredField("literals");
				childrenField.setAccessible(true);
				literalsField.setAccessible(true);
			}
			((Map<String, ?>) childrenField.get(root)).remove(name);
			((Map<String, ?>) literalsField.get(root)).remove(name);
		} catch (ReflectiveOperationException ignored) {
		}
	}

	private static String stripSlash(String raw) {
		String value = raw == null ? "" : raw.replace('\n', ' ').replace('\r', ' ').trim();
		while (value.startsWith("/")) {
			value = value.substring(1).trim();
		}
		return value;
	}

	private static boolean send(String command) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.player.connection == null) {
			return false;
		}
		sending = true;
		try {
			client.player.connection.sendCommand(command);
			return true;
		} catch (RuntimeException ignored) {
			return false;
		} finally {
			sending = false;
		}
	}
}
