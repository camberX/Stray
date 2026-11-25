package dev.stray.client.render;

import dev.stray.client.mixin.BakedSheetGlyphAccessor;
import dev.stray.client.mixin.FontGlyphInvoker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.HashMap;
import java.util.Map;

/**
 * Where a font's capital letters really sit, read from the glyphs the game baked. Stray's fonts
 * have their own sizes and shifts, so the vanilla rule (text is 9 tall) puts them off centre;
 * this centres the capitals exactly in a box.
 */
public final class TextMetrics {
	/** Middle of a glyph's ink below the text's y, per style and character. */
	private static final Map<Key, Float> MIDDLE = new HashMap<>();

	private record Key(Style style, int codepoint) {
	}

	private TextMetrics() {
	}

	/**
	 * The y to draw text in {@code sample}'s style at so it sits in the middle of a box. The
	 * sample's first character is the one measured: "H" centres capitals, an icon centres itself.
	 */
	public static float centered(Font font, Component sample, float y, float height) {
		String text = sample.getString();
		int cp = text.isEmpty() ? 'H' : text.codePointAt(0);
		return y + height * 0.5f - middle(font, sample.getStyle(), cp);
	}

	/** Drop the cache: a font or resource pack reload re-bakes every glyph. */
	public static void reset() {
		MIDDLE.clear();
	}

	private static float middle(Font font, Style style, int cp) {
		Key key = new Key(style, cp);
		Float known = MIDDLE.get(key);
		if (known != null) {
			return known;
		}
		float middle = 4.5f;
		BakedGlyph glyph = ((FontGlyphInvoker) font).stray$glyph(cp, style);
		if (glyph instanceof BakedSheetGlyphAccessor sheet) {
			float up = sheet.stray$up();
			float down = sheet.stray$down();
			if (down > up) {
				middle = (up + down) * 0.5f;
			}
		}
		if (MIDDLE.size() > 128) {
			MIDDLE.clear();
		}
		MIDDLE.put(key, middle);
		return middle;
	}
}
