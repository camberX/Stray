package dev.stray.client.mixin;

import dev.stray.client.fairy.FairySoulTracker;
import dev.stray.client.item.SackLive;
import dev.stray.client.mining.MetalDetector;
import dev.stray.client.skill.SkillProgressTracker;
import dev.stray.client.ui.CommandShortcuts;
import dev.stray.client.ui.ProfileCommands;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientPacketListener.class, priority = 2000)
public class ClientPacketListenerMixin {
	@Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
	private void stray$stealProfileCommand(String command, CallbackInfo ci) {
		if (ProfileCommands.handleTyped(command) || CommandShortcuts.handleTyped(command)) {
			ci.cancel();
		}
	}

	@Inject(method = "handleSystemChat", at = @At("HEAD"))
	private void stray$fairySoulChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
		if (packet != null) {
			FairySoulTracker.onChat(packet.content());
			SackLive.onChat(packet.content());
			if (packet.overlay()) {
				MetalDetector.onActionBar(packet.content());
				SkillProgressTracker.onActionBar(packet.content());
			}
		}
	}

	/** Keep break progress: hold back held-item updates while a block is being broken. */
	@Inject(method = "handleContainerSetSlot", at = @At("HEAD"), cancellable = true)
	private void stray$keepBreakSlot(net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
		if (!dev.stray.Edition.LEGIT) {
			// The handler first re-queues itself onto the render thread; decide there.
			if (packet == null || packet.getContainerId() != 0 || !net.minecraft.client.Minecraft.getInstance().isSameThread()) {
				return;
			}
			int slot = packet.getSlot();
			if (slot < 36 || slot > 44) {
				return;
			}
			ClientPacketListener self = (ClientPacketListener) (Object) this;
			if (dev.stray.client.mining.KeepBreakProgress.hold(slot - 36, packet.getItem(), () -> self.handleContainerSetSlot(packet))) {
				ci.cancel();
			}
		}
	}

	@Inject(method = "handleSetPlayerInventory", at = @At("HEAD"), cancellable = true)
	private void stray$keepBreakInventory(net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket packet, CallbackInfo ci) {
		if (!dev.stray.Edition.LEGIT) {
			if (packet == null || !net.minecraft.client.Minecraft.getInstance().isSameThread()) {
				return;
			}
			ClientPacketListener self = (ClientPacketListener) (Object) this;
			if (dev.stray.client.mining.KeepBreakProgress.hold(packet.slot(), packet.contents(), () -> self.handleSetPlayerInventory(packet))) {
				ci.cancel();
			}
		}
	}

	@Inject(method = "handleContainerContent", at = @At("HEAD"))
	private void stray$keepBreakContent(net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
		if (!dev.stray.Edition.LEGIT) {
			if (net.minecraft.client.Minecraft.getInstance().isSameThread()) {
				dev.stray.client.mining.KeepBreakProgress.clear();
			}
		}
	}

	@Inject(method = "setActionBarText", at = @At("HEAD"))
	private void stray$metalDetectorBar(ClientboundSetActionBarTextPacket packet, CallbackInfo ci) {
		if (packet != null) {
			MetalDetector.onActionBar(packet.text());
			SkillProgressTracker.onActionBar(packet.text());
		}
	}
}
