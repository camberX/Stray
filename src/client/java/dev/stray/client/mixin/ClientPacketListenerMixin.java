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

	/** M4 Titles (BirdAddon): Hypixel's titles in the Thorn fight, hidden at the packet like BirdAddon does. */
	@Inject(method = "setTitleText", at = @At("HEAD"), cancellable = true)
	private void stray$m4Title(net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket packet, CallbackInfo ci) {
		if (net.minecraft.client.Minecraft.getInstance().isSameThread() && !dev.stray.client.dungeon.m4.M4Titles.allowServer()) {
			ci.cancel();
		}
	}

	@Inject(method = "setSubtitleText", at = @At("HEAD"), cancellable = true)
	private void stray$m4Subtitle(net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket packet, CallbackInfo ci) {
		if (packet != null && net.minecraft.client.Minecraft.getInstance().isSameThread()
			&& !dev.stray.client.dungeon.m4.M4.onSubtitle(dev.stray.client.util.Text.strip(packet.text().getString()))) {
			ci.cancel();
		}
	}

	@Inject(method = "setTitlesAnimation", at = @At("HEAD"), cancellable = true)
	private void stray$m4TitleTimes(net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket packet, CallbackInfo ci) {
		if (net.minecraft.client.Minecraft.getInstance().isSameThread() && !dev.stray.client.dungeon.m4.M4Titles.allowServer()) {
			ci.cancel();
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
