package dev.stray.client.cs2.s2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Text KeyValues (items_game.txt, localization). Objects map to LinkedHashMap; a key that
 * repeats keeps every value in a List so merged sections (several "paint_kits") survive.
 */
public final class Kv1 {
	private final String s;
	private int p;

	private Kv1(String s) {
		this.s = s;
	}

	public static Map<String, Object> parse(String text) {
		Kv1 k = new Kv1(text);
		return k.object(false);
	}

	private void skip() {
		while (p < s.length()) {
			char c = s.charAt(p);
			if (Character.isWhitespace(c) || c == '﻿') {
				p++;
			} else if (c == '/' && p + 1 < s.length() && s.charAt(p + 1) == '/') {
				while (p < s.length() && s.charAt(p) != '\n') {
					p++;
				}
			} else if (c == '[') { // platform conditional like [$WIN32]
				while (p < s.length() && s.charAt(p) != ']') {
					p++;
				}
				p++;
			} else {
				break;
			}
		}
	}

	private String token() {
		skip();
		if (p >= s.length()) {
			return null;
		}
		char c = s.charAt(p);
		if (c == '{' || c == '}') {
			p++;
			return String.valueOf(c);
		}
		StringBuilder b = new StringBuilder();
		if (c == '"') {
			p++;
			while (p < s.length()) {
				c = s.charAt(p++);
				if (c == '\\' && p < s.length()) {
					char n = s.charAt(p++);
					b.append(n == 'n' ? '\n' : n == 't' ? '\t' : n);
				} else if (c == '"') {
					break;
				} else {
					b.append(c);
				}
			}
			return "\u0000" + b;
		}
		while (p < s.length()) {
			c = s.charAt(p);
			if (Character.isWhitespace(c) || c == '{' || c == '}' || c == '"') {
				break;
			}
			b.append(c);
			p++;
		}
		return "\u0000" + b;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> object(boolean braced) {
		Map<String, Object> map = new LinkedHashMap<>();
		while (true) {
			String key = token();
			if (key == null || key.equals("}")) {
				return map;
			}
			if (key.equals("{")) {
				continue;
			}
			key = key.substring(1);
			String val = token();
			if (val == null) {
				return map;
			}
			Object v = val.equals("{") ? object(true) : val.substring(1);
			Object prev = map.get(key);
			if (prev == null) {
				map.put(key, v);
			} else if (prev instanceof Map<?, ?> pm && v instanceof Map<?, ?> vm) {
				((Map<String, Object>) pm).putAll((Map<String, Object>) vm);
			} else {
				List<Object> list;
				if (prev instanceof Multi m) {
					list = m.values;
				} else {
					Multi m = new Multi();
					m.values.add(prev);
					map.put(key, m);
					list = m.values;
				}
				list.add(v);
			}
		}
	}

	/** Repeated scalar keys. */
	public static final class Multi {
		public final List<Object> values = new ArrayList<>();

		@Override
		public String toString() {
			return values.toString();
		}
	}
}
