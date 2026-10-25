package dev.stray.client.mixin;

import dev.stray.client.visual.NickSteal;
import dev.stray.client.visual.ShopCape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public class AbstractClientPlayerMixin {
	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void stray$customCape(CallbackInfoReturnable<PlayerSkin> cir) {
		AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
		Minecraft client = Minecraft.getInstance();
		boolean self = client.player != null && player.getUUID().equals(client.player.getUUID());
		if (NickSteal.active() && self) {
			cir.setReturnValue(NickSteal.skin(cir.getReturnValue()));
		} else {
			cir.setReturnValue(ShopCape.patch(player.getUUID(), cir.getReturnValue()));
		}
		if (self && dev.stray.client.visual.CustomSkin.active()) {
			cir.setReturnValue(dev.stray.client.visual.CustomSkin.patch(cir.getReturnValue()));
		}
	}
}
