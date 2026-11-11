package dev.stray.client.mixin;

import dev.stray.client.visual.OtherNames;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Chat lines are drawn through these, not GuiGraphics.text. Other players' names are swapped
 * here, at draw time, so the stored chat keeps the real names (Stray's trackers read them) and
 * turning "Hide other names" on or off applies to the whole history right away.
 */
@Mixin(targets = {
	"net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess",
	"net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess",
	"net.minecraft.client.gui.components.ChatComponent$ClickableTextOnlyGraphicsAccess"
})
public class ChatLineNamesMixin {
	@ModifyVariable(method = "handleMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
	private FormattedCharSequence stray$otherNames(FormattedCharSequence message) {
		return OtherNames.rewrite(message);
	}
}
