package dev.stray.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla samples every glyph with nearest filtering, which suits the pixel font but makes Inter
 * jagged (letters losing strokes) whenever it is drawn at a scale that isn't a whole multiple:
 * menu scale, HUD scale, opening animations. Stray's own fonts sample smoothly instead.
 */
@Mixin(GlyphRenderState.class)
public abstract class GlyphRenderStateMixin {
	@Shadow
	public abstract TextRenderable renderable();

	@Inject(method = "textureSetup", at = @At("HEAD"), cancellable = true)
	private void stray$smoothGlyphs(CallbackInfoReturnable<TextureSetup> cir) {
		if (dev.stray.client.ui.MenuFont.SMOOTH_ATLASES.contains(this.renderable().textureView())) {
			cir.setReturnValue(TextureSetup.singleTextureWithLightmap(this.renderable().textureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)));
		}
	}
}
