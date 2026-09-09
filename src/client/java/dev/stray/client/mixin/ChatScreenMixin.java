package dev.stray.client.mixin;

import dev.stray.client.media.MediaChat;
import dev.stray.client.render.MusicHudRenderer;
import dev.stray.client.render.RawmatsHudRenderer;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void stray$musicClick(MouseButtonEvent event, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
		if (MusicHudRenderer.mouseClicked(event) || RawmatsHudRenderer.mouseClicked(event)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
	private void stray$musicChat(String message, boolean addToHistory, CallbackInfo ci) {
		if (MediaChat.handleTyped(message)) {
			ci.cancel();
		}
	}
}
