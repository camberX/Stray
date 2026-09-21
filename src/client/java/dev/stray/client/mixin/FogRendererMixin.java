package dev.stray.client.mixin;

import dev.stray.client.visual.CustomFog;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FogRenderer.class, priority = 990)
public class FogRendererMixin {
	@Inject(method = "setupFog", at = @At("RETURN"))
	private void stray$customFog(Camera camera, int renderDistance, DeltaTracker deltaTracker, float darkenWorldAmount, ClientLevel level, CallbackInfoReturnable<FogData> cir) {
		CustomFog.apply(cir.getReturnValue(), camera, renderDistance);
	}
}
