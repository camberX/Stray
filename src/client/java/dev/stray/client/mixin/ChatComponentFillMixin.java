package dev.stray.client.mixin;

import dev.stray.client.ui.ChatChrome;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla chat line bars are filled from a lambda via these accessors, not
 * from {@code ChatComponent.extractRenderState} itself, so wrapping that
 * method never hid the black bars. Incoming/leaving line motion is applied
 * on {@code handleMessage}.
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

	@ModifyVariable(method = "handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int stray$animY(int y, float opacity, FormattedCharSequence text) {
		return y + ChatChrome.lineShift(text);
	}

	@ModifyVariable(method = "handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float stray$animA(float opacity, int y, FormattedCharSequence text) {
		return opacity * ChatChrome.lineAlpha(text);
	}
}
