package dev.stray.client.mixin;

import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

/** Remembers the glyph atlases of Stray's TTF fonts so {@link GlyphRenderStateMixin} can smooth them. */
@Mixin(FontTexture.class)
public abstract class FontTextureMixin extends AbstractTexture {
	@Inject(method = "<init>", at = @At("RETURN"))
	private void stray$smooth(Supplier<String> label, GlyphRenderTypes renderTypes, boolean colored, CallbackInfo ci) {
		String name = label.get();
		if (name != null && name.startsWith("stray:") && this.textureView != null) {
			dev.stray.client.ui.MenuFont.SMOOTH_ATLASES.add(this.textureView);
		}
	}
}
