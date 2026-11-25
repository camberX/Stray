package dev.stray.client.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The glyph a character draws with in a style, to read where its ink sits (text centring). */
@Mixin(Font.class)
public interface FontGlyphInvoker {
	@Invoker("getGlyph")
	BakedGlyph stray$glyph(int codepoint, Style style);
}
