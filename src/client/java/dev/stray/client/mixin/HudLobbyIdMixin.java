package dev.stray.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.stray.client.visual.LobbyIdHider;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Vanilla sidebar: the lobby id after the date is hidden when "Hide lobby ID" is on. */
@Mixin(Hud.class)
public class HudLobbyIdMixin {
	@WrapOperation(method = "*", require = 0, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/scores/PlayerTeam;formatNameForTeam(Lnet/minecraft/world/scores/Team;Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/MutableComponent;"))
	private net.minecraft.network.chat.MutableComponent stray$hideLobbyId(Team team, Component name, Operation<net.minecraft.network.chat.MutableComponent> original) {
		net.minecraft.network.chat.MutableComponent line = original.call(team, name);
		Component stripped = dev.stray.client.visual.OtherNames.rewriteScoreboard(LobbyIdHider.strip(line));
		return stripped == line ? line : stripped.copy();
	}
}
