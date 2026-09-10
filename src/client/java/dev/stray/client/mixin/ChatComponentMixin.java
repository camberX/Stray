package dev.stray.client.mixin;

import dev.stray.client.fairy.FairySoulTracker;
import dev.stray.client.mining.CrystalHollows;
import dev.stray.client.visual.NickHider;
import dev.stray.client.mining.MiningTracker;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {
	@ModifyVariable(
		method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
		at = @At("HEAD"),
		argsOnly = true,
		ordinal = 0
	)
	private Component stray$nick(Component message) {
		Component rewritten = NickHider.rewrite(message);
		MiningTracker.onChat(rewritten);
		FairySoulTracker.onChat(rewritten);
		CrystalHollows.allowChat(rewritten, false);
		return rewritten;
	}
}
