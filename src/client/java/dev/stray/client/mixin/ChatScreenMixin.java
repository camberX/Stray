package dev.stray.client.mixin;

import dev.stray.client.media.MediaChat;
import dev.stray.client.render.MusicHudRenderer;
import dev.stray.client.render.RawmatsHudRenderer;
import dev.stray.client.update.UpdateToast;
import dev.stray.client.ui.ProfileCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ChatScreen.class, priority = 2000)
public class ChatScreenMixin {
	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void stray$musicClick(MouseButtonEvent event, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
		if (UpdateToast.mouseClicked(event) || MusicHudRenderer.mouseClicked(event) || RawmatsHudRenderer.mouseClicked(event)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
	private void stray$musicChat(String message, boolean addToHistory, CallbackInfo ci) {
		if (MediaChat.handleTyped(message) || ProfileCommands.handleTyped(message)) {
			if (addToHistory && message != null && !message.isBlank()) {
				Minecraft.getInstance().gui.getChat().addRecentChat(message);
			}
			ci.cancel();
		}
	}
}
