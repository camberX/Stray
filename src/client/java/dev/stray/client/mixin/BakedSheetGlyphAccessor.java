package dev.stray.client.mixin;

import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A glyph's top and bottom edge below the text's y. Read only. */
@Mixin(BakedSheetGlyph.class)
public interface BakedSheetGlyphAccessor {
	@Accessor("up")
	float stray$up();

	@Accessor("down")
	float stray$down();
}
