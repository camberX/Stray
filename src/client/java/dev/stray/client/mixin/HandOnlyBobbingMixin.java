package dev.stray.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.stray.client.config.StrayConfig;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.OptionsRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Hand-only bobbing: view bobbing moves the hand but never the camera. The world view skips
 * the bob; the hand always bobs (even with vanilla bobbing off). Rendering only.
 */
@Mixin(GameRenderer.class)
public class HandOnlyBobbingMixin {
	@WrapOperation(method = "renderLevel", require = 0, at = @At(value = "FIELD",
		target = "Lnet/minecraft/client/renderer/state/OptionsRenderState;bobView:Z"))
	private boolean stray$noWorldBob(OptionsRenderState options, Operation<Boolean> original) {
		return !StrayConfig.get().handOnlyBobbing && original.call(options);
	}

	@WrapOperation(method = "renderItemInHand", require = 0, at = @At(value = "FIELD",
		target = "Lnet/minecraft/client/renderer/state/OptionsRenderState;bobView:Z"))
	private boolean stray$handBob(OptionsRenderState options, Operation<Boolean> original) {
		return StrayConfig.get().handOnlyBobbing || original.call(options);
	}
}
