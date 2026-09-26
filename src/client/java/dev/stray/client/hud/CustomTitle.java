package dev.stray.client.hud;

import dev.stray.client.config.StrayConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Locale;

/**
 * Shows a configured title when a configured snippet appears in chat.
 * Title text accepts {@code &} and {@code §} formatting codes, plus {@code &#RRGGBB}.
 */
public final class CustomTitle {
	private CustomTitle() {
	}

	public static void onChat(Component message) {
		if (message == null) {
			return;
		}
		StrayConfig config = StrayConfig.get();
		if (!config.customTitleEnabled) {
			return;
		}
		String trigger = config.customTitleTrigger == null ? "" : config.customTitleTrigger.trim();
		String title = config.customTitleText == null ? "" : config.customTitleText;
		if (trigger.isEmpty() || title.isBlank()) {
			return;
		}
		String hay = message.getString();
		if (hay == null || hay.isBlank()) {
			return;
		}
		hay = ChatFormatting.stripFormatting(hay);
		String needle = ChatFormatting.stripFormatting(trigger.replace('&', '\u00A7'));
		if (needle.isEmpty()) {
			return;
		}
		if (!hay.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT))) {
			return;
		}
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
