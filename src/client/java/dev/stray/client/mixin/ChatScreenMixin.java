package dev.stray.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.stray.client.media.MediaChat;
import dev.stray.client.render.MusicHudRenderer;
import dev.stray.client.render.RawmatsHudRenderer;
import dev.stray.client.update.UpdateToast;
import dev.stray.client.ui.ChatChrome;
import dev.stray.client.ui.ProfileCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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

	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void stray$input(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		ChatChrome.inputBar(graphics, (ChatScreen) (Object) this);
	}

	@WrapOperation(
		method = "extractRenderState",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"
		)
	)
	private void stray$skipInputBar(
		GuiGraphicsExtractor graphics,
		int x0,
		int y0,
		int x1,
		int y1,
		int color,
		Operation<Void> original
	) {
		if (!ChatChrome.enabled()) {
			original.call(graphics, x0, y0, x1, y1, color);
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
