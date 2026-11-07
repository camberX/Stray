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
	@org.spongepowered.asm.mixin.Shadow
	private net.minecraft.core.BlockPos destroyBlockPos;

	@org.spongepowered.asm.mixin.Shadow
	private net.minecraft.world.item.ItemStack destroyingItem;

	/** Keep break progress: a re-sent copy of the same SkyBlock tool does not restart the break. */
	@Inject(method = "sameDestroyTarget", at = @At("RETURN"), cancellable = true)
	private void stray$keepBreak(net.minecraft.core.BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (!dev.stray.Edition.LEGIT) {
			net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
			if (!cir.getReturnValueZ() && client.player != null
				&& dev.stray.client.mining.KeepBreakProgress.keep(pos, destroyBlockPos, destroyingItem, client.player.getMainHandItem())) {
				cir.setReturnValue(true);
			}
		}
	}

	@Inject(method = "startDestroyBlock", at = @At("HEAD"))
	private void stray$keepBreakStart(net.minecraft.core.BlockPos pos, net.minecraft.core.Direction face, CallbackInfoReturnable<Boolean> cir) {
		if (!dev.stray.Edition.LEGIT) {
			dev.stray.client.mining.KeepBreakProgress.onStart();
		}
	}
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
