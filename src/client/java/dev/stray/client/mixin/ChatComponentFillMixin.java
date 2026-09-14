package dev.stray.client.mixin;

import dev.stray.client.ui.ChatChrome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla chat line bars are filled from a lambda via these accessors, not
 * from {@code ChatComponent.extractRenderState} itself, so wrapping that
 * method never hid the black bars.
 */
@Mixin(targets = {
	"net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess",
	"net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess"
})
public class ChatComponentFillMixin {
	@Inject(method = "fill(IIIII)V", at = @At("HEAD"), cancellable = true)
	private void stray$skipVanillaBars(int x0, int y0, int x1, int y1, int color, CallbackInfo ci) {
		if (ChatChrome.skipFill()) {
			ci.cancel();
		}
	}
}
