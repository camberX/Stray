package dev.stray.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.stray.client.combat.OdinClicks;
import dev.stray.client.config.StrayConfig;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Command keys: press a bound key in game and its command runs, the same as typing it in chat
 * (Stray's own client commands work too). Only fires on the press, not while held, and never
 * while a screen or chat is open.
 */
public final class CommandKeys {
	public static final int MAX = 32;
	public static final int MAX_COMMAND = 256;

	/** Keys that were down last tick, so one press runs a command once. */
	private static final Set<String> HELD = new HashSet<>();

	private CommandKeys() {
	}

	public static void tick(Minecraft client, boolean ignore) {
		StrayConfig config = StrayConfig.get();
		if (!config.commandKeysEnabled || config.commandKeys == null || config.commandKeys.isEmpty()) {
			HELD.clear();
			return;
		}
		Set<String> down = new HashSet<>();
		List<String> run = new ArrayList<>();
		for (StrayConfig.CommandKey row : config.commandKeys) {
			if (row == null || row.key == null || row.command == null || row.command.isBlank()) {
				continue;
			}
			InputConstants.Key key = OdinClicks.parseKey(row.key);
			if (!OdinClicks.bound(key) || !OdinClicks.isPressed(key)) {
				continue;
			}
			down.add(row.key);
			if (!HELD.contains(row.key) && !ignore && client.gui.screen() == null && client.player != null) {
				run.add(row.command);
			}
		}
		HELD.clear();
		HELD.addAll(down);
		for (String command : run) {
			send(client, command);
		}
	}

	/** Treat everything currently held as already handled (e.g. right after a bind was set). */
	public static void syncEdge() {
		HELD.clear();
		StrayConfig config = StrayConfig.get();
		if (config.commandKeys == null) {
			return;
		}
		for (StrayConfig.CommandKey row : config.commandKeys) {
			if (row != null && row.key != null && OdinClicks.isPressed(OdinClicks.parseKey(row.key))) {
				HELD.add(row.key);
			}
		}
	}

	private static void send(Minecraft client, String raw) {
		String command = normalizeCommand(raw);
		if (command.isEmpty() || client.player == null) {
			return;
		}
		client.player.connection.sendCommand(command);
	}

	public static String normalizeCommand(String raw) {
		String value = raw == null ? "" : raw.trim();
		while (value.startsWith("/")) {
			value = value.substring(1).trim();
		}
		return value.length() > MAX_COMMAND ? value.substring(0, MAX_COMMAND) : value;
	}

	/** Adds a bind, or changes the command of the bind already on that key. */
	public static boolean upsert(String key, String commandRaw, int editing) {
		String command = normalizeCommand(commandRaw);
		if (key == null || !OdinClicks.bound(OdinClicks.parseKey(key)) || command.isEmpty()) {
			return false;
		}
		StrayConfig config = StrayConfig.get();
		normalize(config);
		if (editing >= 0 && editing < config.commandKeys.size()) {
			StrayConfig.CommandKey row = config.commandKeys.get(editing);
			row.key = key;
			row.command = command;
		} else {
			if (config.commandKeys.size() >= MAX) {
				return false;
			}
			StrayConfig.CommandKey row = new StrayConfig.CommandKey();
			row.key = key;
			row.command = command;
			config.commandKeys.add(row);
		}
		config.save();
		syncEdge();
		return true;
	}

	public static void remove(int index) {
		StrayConfig config = StrayConfig.get();
		normalize(config);
		if (index >= 0 && index < config.commandKeys.size()) {
			config.commandKeys.remove(index);
			config.save();
		}
	}

	public static void normalize(StrayConfig config) {
		List<StrayConfig.CommandKey> next = new ArrayList<>();
		if (config.commandKeys != null) {
			for (StrayConfig.CommandKey row : config.commandKeys) {
				if (row == null || row.key == null) {
					continue;
				}
				row.command = normalizeCommand(row.command);
				if (row.command.isEmpty() || !OdinClicks.bound(OdinClicks.parseKey(row.key))) {
					continue;
				}
				next.add(row);
				if (next.size() >= MAX) {
					break;
				}
			}
		}
		config.commandKeys = next;
	}
}
