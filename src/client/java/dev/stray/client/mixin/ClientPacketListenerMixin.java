package dev.stray.client.mixin;

import dev.stray.client.combat.Hitsound;
import dev.stray.client.fairy.FairySoulTracker;
import dev.stray.client.ui.ProfileCommands;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientPacketListener.class, priority = 2000)
public class ClientPacketListenerMixin {
	@Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
	private void stray$stealProfileCommand(String command, CallbackInfo ci) {
		if (ProfileCommands.handleTyped(command)) {
			ci.cancel();
		}
	}

	@Inject(method = "handleSystemChat", at = @At("HEAD"))
	private void stray$fairySoulChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
		if (packet != null) {
			FairySoulTracker.onChat(packet.content());
		}
	}

	@Inject(method = "handleGameEvent", at = @At("HEAD"), cancellable = true)
	private void stray$skipDelayedArrowPing(ClientboundGameEventPacket packet, CallbackInfo ci) {
		if (packet.getEvent() != ClientboundGameEventPacket.PLAY_ARROW_HIT_SOUND) {
			return;
		}
		if (Hitsound.suppressVanillaArrowPing()) {
			ci.cancel();
		}
	}
}
