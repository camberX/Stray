package dev.stray.client.mixin;

import dev.stray.client.dungeon.autoleap.SwingCheck;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
//? if >=26.3 {
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?} else {
/*import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

/** {@code /stray debug dev swing}: notices the game's own swing running for you. Only looks. Full edition only. */
@Mixin(LivingEntity.class)
public class SwingWatchMixin {
	//? if >=26.3 {
	@Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z", at = @At("HEAD"), require = 0)
	private void stray$watchSwing(InteractionHand hand, SwingAnimation animation, boolean sendToSwingingEntity, CallbackInfoReturnable<Boolean> cir) {
	//?} else {
	/*@Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"), require = 0)
	private void stray$watchSwing(InteractionHand hand, boolean sendToSwingingEntity, CallbackInfo ci) {*///?}
		if ((Object) this instanceof LocalPlayer) {
			SwingCheck.onSwingCall();
		}
	}
}
