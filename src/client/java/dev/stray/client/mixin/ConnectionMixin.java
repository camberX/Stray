package dev.stray.client.mixin;

import dev.stray.client.net.ClientPackets;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ConnectionMixin {
	@Inject(
		method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/network/Connection;genericsFtw(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;)V"
		)
	)
	private void stray$onPacket(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
		ClientPackets.onReceived(packet);
	}

	@Inject(
		method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
		at = @At("HEAD")
	)
	private void stray$printSent(Packet<?> packet, io.netty.channel.ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
		dev.stray.client.net.PacketDebug.onSend(packet);
		if (!dev.stray.Edition.LEGIT && packet instanceof net.minecraft.network.protocol.game.ServerboundContainerClickPacket click) {
			// Only looks: lets the terminal GUI follow clicks other mods send (NoammAddons' auto terminals).
			dev.stray.client.dungeon.terminal.TerminalSession.onClickSent(click.containerId(), click.slotNum(), click.buttonNum(), click.containerInput());
		}
	}
}
