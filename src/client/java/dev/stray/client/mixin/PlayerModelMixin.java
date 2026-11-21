package dev.stray.client.mixin;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fake sword block: your own player's arm in the block pose (third person, visual only). */
@Mixin(PlayerModel.class)
public class PlayerModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void stray$fakeSwordBlock(AvatarRenderState state, CallbackInfo ci) {
		PlayerModel model = (PlayerModel) (Object) this;
		dev.stray.client.visual.FakeSwordBlock.applyArm(state.id, model.rightArm, model.leftArm);
	}
}
