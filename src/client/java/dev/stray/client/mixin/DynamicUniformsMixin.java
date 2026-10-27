package dev.stray.client.mixin;

import dev.stray.client.render.StrayUniforms;
import net.minecraft.client.renderer.DynamicUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rotates Stray's uniform ring buffer with vanilla's, once per frame. */
@Mixin(DynamicUniforms.class)
public class DynamicUniformsMixin {
	@Inject(method = "reset", at = @At("RETURN"))
	private void stray$endFrame(CallbackInfo ci) {
		StrayUniforms.endFrame();
	}
}
