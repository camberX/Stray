package dev.stray.client.mixin;

import dev.stray.client.combat.Hitsound;
import dev.stray.client.fairy.FairySoulTracker;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
	@Inject(method = "attack", at = @At("HEAD"))
	private void stray$hitsoundMelee(Player player, Entity target, CallbackInfo ci) {
		Hitsound.onMelee(player, target);
	}

	@Inject(method = "useItemOn", at = @At("HEAD"))
	private void stray$fairySoulClick(
		LocalPlayer player,
		InteractionHand hand,
		BlockHitResult hit,
		CallbackInfoReturnable<InteractionResult> cir
	) {
		FairySoulTracker.onUseBlock(hit);
	}
}
