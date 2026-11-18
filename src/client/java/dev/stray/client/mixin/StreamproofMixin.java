package dev.stray.client.mixin;

import dev.stray.client.render.overlay.window.Streamproof;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the streamproof overlay window right after the game presented its frame. Full edition only. */
@Mixin(Minecraft.class)
public class StreamproofMixin {
	// 26.3 says "endFrame" right after presenting; 26.2 has no such step, so it hooks the frame limiter.
	//? if >=26.3 {
	@Inject(method = {"renderFrame", "runTick"}, at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", args = "ldc=endFrame"), require = 0)
	//?} else {
	/*@Inject(method = {"renderFrame", "runTick"}, at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", args = "ldc=frameLimiter"), require = 0)*///?}
	private void stray$streamproof(boolean advanceGameTime, CallbackInfo ci) {
		Streamproof.frame();
	}
}
