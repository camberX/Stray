package dev.stray.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.stray.client.chat.StashChat;
import dev.stray.client.fairy.FairySoulTracker;
import dev.stray.client.mining.CrystalHollows;
import dev.stray.client.mining.MiningTracker;
import dev.stray.client.ui.ChatChrome;
import dev.stray.client.ui.ChatPeek;
import dev.stray.client.visual.NickHider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {
	@WrapMethod(
		method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V"
	)
	private void stray$nick(
		Component message,
		MessageSignature signature,
		GuiMessageSource source,
		GuiMessageTag tag,
		Operation<Void> original
	) {
		Component rewritten = NickHider.rewrite(message);
		MiningTracker.onChat(rewritten);
		FairySoulTracker.onChat(rewritten);
		CrystalHollows.allowChat(rewritten, false);
		Component stash = StashChat.filter(rewritten);
		if (stash != null) {
			original.call(stash, signature, source, tag);
		}
	}

	@Inject(method = "getHeight()I", at = @At("HEAD"), cancellable = true)
	private void stray$peekHeight(CallbackInfoReturnable<Integer> cir) {
		if (!ChatPeek.holding()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.options == null) {
			return;
		}
		cir.setReturnValue(ChatComponent.getHeight(client.options.chatHeightFocused().get()));
	}

	@Inject(
		method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
		at = @At("HEAD")
	)
	private void stray$pane(
		GuiGraphicsExtractor graphics,
		Font font,
		int ticks,
		int mouseX,
		int mouseY,
		ChatComponent.DisplayMode mode,
		boolean click,
		CallbackInfo ci
	) {
		ChatChrome.beginFills();
		ChatChrome.extract(graphics, (ChatComponent) (Object) this, ticks, mode);
	}

	@Inject(
		method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
		at = @At("RETURN")
	)
	private void stray$paneDone(CallbackInfo ci) {
		ChatChrome.endFills();
	}

	@WrapOperation(
		method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;fill(IIIII)V"
		)
	)
	private void stray$skipVanillaBars(
		ChatComponent.ChatGraphicsAccess access,
		int x0,
		int y0,
		int x1,
		int y1,
		int color,
		Operation<Void> original
	) {
		if (!ChatChrome.skipFill()) {
			original.call(access, x0, y0, x1, y1, color);
		}
	}
}
