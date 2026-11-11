package dev.stray.client.mixin;

import dev.stray.client.dungeon.autoleap.LeapRunner;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Auto Leap's silent menu: when the leap it is waiting for opens, the menu is set up without
 * a screen. Runs on the client thread (after the thread hop), right where vanilla would
 * build the screen. Full edition only.
 */
@Mixin(ClientPacketListener.class)
public class LeapSilentMixin {
	@Inject(method = "handleOpenScreen", cancellable = true, require = 0, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/MenuScreens;create(Lnet/minecraft/world/inventory/MenuType;Lnet/minecraft/client/Minecraft;ILnet/minecraft/network/chat/Component;)V"))
	private void stray$silentLeap(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
		if (LeapRunner.onOpenScreen(packet)) {
			ci.cancel();
		}
	}
}
