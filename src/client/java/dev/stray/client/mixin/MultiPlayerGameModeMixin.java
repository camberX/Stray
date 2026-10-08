package dev.stray.client.mixin;

import dev.stray.client.combat.Hitsound;
import dev.stray.client.fairy.FairySoulTracker;
import dev.stray.client.mining.PinglessBreak;
import dev.stray.client.movement.AotvSim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
	@Shadow
	private int destroyDelay;

	@Shadow
	private boolean isDestroying;

	@Inject(method = "continueDestroyBlock", at = @At("HEAD"))
	private void stray$pinglessBreak(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
		if (PinglessBreak.active()) {
			this.destroyDelay = 0;
		}
	}

	@Inject(method = "startDestroyBlock", at = @At("RETURN"))
	private void stray$pinglessInstamine(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() || !PinglessBreak.predict(pos)) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return;
		}
		BlockState previous = client.level.getBlockState(pos);
		if (((MultiPlayerGameMode) (Object) this).destroyBlock(pos)) {
			this.isDestroying = false;
			PinglessBreak.remember(pos, previous);
		}
	}

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
		if (AotvSim.use(net.minecraft.client.Minecraft.getInstance())) {
			cir.setReturnValue(InteractionResult.SUCCESS);
			return;
		}
		FairySoulTracker.onUseBlock(hit);
	}
}
