package dev.stray.client.hud;

import com.mojang.brigadier.Command;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.ui.CustomTitleScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Shows a configured title when a configured snippet appears in chat.
 * Title text accepts {@code &} and {@code §} formatting codes, plus {@code &#RRGGBB}.
 */
public final class CustomTitle {
	public static final int MAX = 48;
	public static final int MAX_TEXT = 96;

	private CustomTitle() {
	}

	public static void onChat(Component message) {
		if (message == null) {
			return;
		}
		StrayConfig config = StrayConfig.get();
		if (!config.customTitleEnabled || config.customTitles == null) {
			return;
		}
		String hay = message.getString();
		if (hay == null || hay.isBlank()) {
			return;
		}
		hay = ChatFormatting.stripFormatting(hay).toLowerCase(Locale.ROOT);
		for (StrayConfig.CustomTitleRule rule : config.customTitles) {
			if (rule == null || rule.title == null || rule.title.isBlank()) {
				continue;
			}
			String needle = needle(rule.trigger);
			if (needle.isEmpty() || !hay.contains(needle)) {
				continue;
			}
			show(rule.title);
			return;
		}
	}

	public static int open() {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> {
			if (client.screen instanceof CustomTitleScreen) {
				client.screen.onClose();
				return;
			}
			client.setScreen(new CustomTitleScreen(client.screen));
		});
		return Command.SINGLE_SUCCESS;
	}

	public static boolean upsert(String triggerRaw, String titleRaw, int editingIndex) {
		String trigger = clip(triggerRaw);
		String title = clip(titleRaw);
		if (trigger.isEmpty() || title.isEmpty()) {
			return false;
		}
		StrayConfig config = StrayConfig.get();
		normalize(config);
		String key = key(trigger);
		if (editingIndex >= 0 && editingIndex < config.customTitles.size()) {
			for (int i = 0; i < config.customTitles.size(); i++) {
				if (i != editingIndex && key.equals(key(config.customTitles.get(i).trigger))) {
					config.customTitles.get(i).title = title;
					config.customTitles.remove(editingIndex);
					config.save();
					return true;
				}
			}
			StrayConfig.CustomTitleRule row = config.customTitles.get(editingIndex);
			row.trigger = trigger;
			row.title = title;
			config.save();
			return true;
		}
		for (StrayConfig.CustomTitleRule row : config.customTitles) {
			if (key.equals(key(row.trigger))) {
				row.title = title;
				config.save();
				return true;
			}
		}
		if (config.customTitles.size() >= MAX) {
			return false;
		}
		StrayConfig.CustomTitleRule row = new StrayConfig.CustomTitleRule();
		row.trigger = trigger;
		row.title = title;
		config.customTitles.add(row);
		config.save();
		return true;
	}

	public static boolean remove(int index) {
		StrayConfig config = StrayConfig.get();
		normalize(config);
		if (index < 0 || index >= config.customTitles.size()) {
			return false;
		}
		config.customTitles.remove(index);
		config.save();
		return true;
	}

	public static void normalize(StrayConfig config) {
		java.util.List<StrayConfig.CustomTitleRule> next = new ArrayList<>();
		java.util.Set<String> seen = new java.util.HashSet<>();
		if (config.customTitles != null) {
			for (StrayConfig.CustomTitleRule row : config.customTitles) {
				if (row == null) {
					continue;
				}
				String trigger = clip(row.trigger);
				String title = clip(row.title);
				if (trigger.isEmpty() || title.isEmpty()) {
					continue;
				}
				if (!seen.add(key(trigger))) {
					continue;
				}
				row.trigger = trigger;
				row.title = title;
				next.add(row);
				if (next.size() >= MAX) {
					break;
				}
			}
		}
		config.customTitles = next;
	}

	private static void show(String title) {
		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			return;
		}
		Gui gui = client.gui;
		if (gui == null) {
			return;
		}
		gui.setTimes(10, 70, 20);
		gui.setSubtitle(Component.empty());
		gui.setTitle(parse(title));
	}

	private static String needle(String trigger) {
		if (trigger == null) {
			return "";
		}
		String stripped = ChatFormatting.stripFormatting(trigger.replace('&', '\u00A7'));
		return stripped == null ? "" : stripped.toLowerCase(Locale.ROOT);
	}

	private static String clip(String raw) {
		String value = raw == null ? "" : raw.replace('\n', ' ').replace('\r', ' ').trim();
		if (value.length() > MAX_TEXT) {
			value = value.substring(0, MAX_TEXT);
		}
		return value;
	}

	private static String key(String trigger) {
		return needle(trigger);
	}

	static Component parse(String raw) {
		if (raw == null || raw.isEmpty()) {
			return Component.empty();
		}
		MutableComponent root = Component.empty();
		Style style = Style.EMPTY;
		StringBuilder buffer = new StringBuilder();
		for (int i = 0; i < raw.length(); i++) {
			char current = raw.charAt(i);
			if ((current == '&' || current == '\u00A7') && i + 1 < raw.length()) {
				char next = raw.charAt(i + 1);
				if (next == '#' && i + 7 < raw.length()) {
					Integer rgb = hex(raw.substring(i + 2, i + 8));
					if (rgb != null) {
						flush(root, buffer, style);
						style = style.withColor(rgb);
						i += 7;
						continue;
					}
				}
				ChatFormatting formatting = ChatFormatting.getByCode(Character.toLowerCase(next));
				if (formatting != null) {
					flush(root, buffer, style);
					style = formatting == ChatFormatting.RESET ? Style.EMPTY : style.applyFormat(formatting);
					i++;
					continue;
				}
				if (next == current) {
					buffer.append(current);
					i++;
					continue;
				}
			}
			buffer.append(current);
		}
		flush(root, buffer, style);
		return root;
	}

	private static void flush(MutableComponent root, StringBuilder buffer, Style style) {
		if (buffer.isEmpty()) {
			return;
		}
		root.append(Component.literal(buffer.toString()).withStyle(style));
		buffer.setLength(0);
	}

	private static Integer hex(String text) {
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			boolean digit = c >= '0' && c <= '9';
			boolean lower = c >= 'a' && c <= 'f';
			boolean upper = c >= 'A' && c <= 'F';
			if (!digit && !lower && !upper) {
				return null;
			}
		}
		return Integer.parseInt(text, 16);
	}
}
