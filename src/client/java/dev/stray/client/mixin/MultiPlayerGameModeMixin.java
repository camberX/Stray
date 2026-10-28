package dev.stray.client.mixin;

import dev.stray.client.fairy.FairySoulTracker;
import dev.stray.client.movement.AotvSim;
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
	private void stray$slayerAttack(Player player, Entity target, CallbackInfo ci) {
		dev.stray.client.slayer.SlayerBosses.onAttack(target);
	}

	@Inject(method = "useItem", at = @At("HEAD"))
	private void stray$slayerDagger(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		dev.stray.client.slayer.SlayerBosses.onUse(player.getItemInHand(hand));
	}

	@Inject(method = "useItemOn", at = @At("HEAD"))
	private void stray$fairySoulClick(
		LocalPlayer player,
		InteractionHand hand,
		BlockHitResult hit,
		CallbackInfoReturnable<InteractionResult> cir
	) {
		if (AotvSim.use(net.minecraft.client.Minecraft.getInstance())) {
			cir.setReturnValue(InteractionResult.SUCCESS);
			return;
		}
		FairySoulTracker.onUseBlock(hit);
	}
}
