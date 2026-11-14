package dev.stray.client.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fast Leap, like quoi: a left click while holding a Spirit Leap never reaches the game (no
 * swing, no block breaking). The leap itself happens at the next tick start.
 */
@Mixin(MouseHandler.class)
public class FastLeapClickMixin {
	@Inject(method = "onButton", at = @At("HEAD"), cancellable = true, require = 0)
	private void stray$fastLeapClick(long handle, MouseButtonInfo info, int action, CallbackInfo ci) {
		if (action == 1 && dev.stray.client.dungeon.autoleap.AutoLeap.takeLeftClick(info.button())) {
			ci.cancel();
		}
	}
}
