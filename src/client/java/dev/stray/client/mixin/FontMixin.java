package dev.stray.client.mixin;

import dev.stray.client.visual.NickHider;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Font.class)
public class FontMixin {
	@ModifyVariable(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private String stray$nickWidth(String text) {
		return NickHider.rewrite(text);
	}

	/**
	 * Nametag backgrounds and centering are measured here before the text is
	 * prepared, so measure the same rewritten component.
	 */
	@ModifyVariable(method = "width(Lnet/minecraft/network/chat/FormattedText;)I", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private FormattedText stray$nickWidthText(FormattedText text) {
		if (text instanceof Component component) {
			return NickHider.rewrite(component);
		}
		return text;
	}

	@ModifyVariable(
		method = "prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;",
		at = @At("HEAD"),
		argsOnly = true,
		ordinal = 0
	)
	private String stray$nickPrepare(String text) {
		return NickHider.rewrite(text);
	}
}
