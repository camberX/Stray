package dev.stray.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.stray.client.ui.ChatChrome;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CommandSuggestions.class)
public class CommandSuggestionsMixin {
	@WrapOperation(
		method = "extractUsage",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"
		)
	)
	private void stray$liftUsageFill(
		GuiGraphicsExtractor graphics,
		int x0,
		int y0,
		int x1,
		int y1,
		int color,
		Operation<Void> original
	) {
		int lift = ChatChrome.usageLift();
		original.call(graphics, x0, y0 - lift, x1, y1 - lift, color);
	}

	@WrapOperation(
		method = "extractUsage",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)V"
		)
	)
	private void stray$liftUsageText(
		GuiGraphicsExtractor graphics,
		Font font,
		FormattedCharSequence text,
		int x,
		int y,
		int color,
		Operation<Void> original
	) {
		original.call(graphics, font, text, x, y - ChatChrome.usageLift(), color);
	}
}
