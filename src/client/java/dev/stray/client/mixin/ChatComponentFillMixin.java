package dev.stray.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.stray.client.ui.ChatChrome;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla chat line bars are filled from a lambda via these accessors, not
 * from {@code ChatComponent.extractRenderState} itself. Line motion wraps
 * the text submit so mixin argument capture cannot crash class load.
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

	@WrapOperation(
		method = "handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/ActiveTextCollector$Parameters;withOpacity(F)Lnet/minecraft/client/gui/ActiveTextCollector$Parameters;"
		)
	)
	private ActiveTextCollector.Parameters stray$animA(
		ActiveTextCollector.Parameters parameters,
		float opacity,
		Operation<ActiveTextCollector.Parameters> original,
		int y,
		float handleOpacity,
		FormattedCharSequence text
	) {
		if (ChatChrome.enabled()) {
			return original.call(parameters, ChatChrome.lineAlpha(text));
		}
		return original.call(parameters, opacity);
	}

	@WrapOperation(
		method = "handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/ActiveTextCollector;accept(Lnet/minecraft/client/gui/TextAlignment;IILnet/minecraft/client/gui/ActiveTextCollector$Parameters;Lnet/minecraft/util/FormattedCharSequence;)V"
		)
	)
	private void stray$animY(
		ActiveTextCollector collector,
		TextAlignment alignment,
		int x,
		int y,
		ActiveTextCollector.Parameters parameters,
		FormattedCharSequence text,
		Operation<Void> original
	) {
		original.call(
			collector,
			alignment,
			x + ChatChrome.lineX(text),
			y + ChatChrome.lineShift(text),
			parameters,
			text
		);
	}
}
