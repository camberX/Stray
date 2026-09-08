package dev.voidmark.client.mixin;

import dev.voidmark.client.visual.HeldItemShader;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(RenderPipelines.class)
public class RenderPipelinesMixin {
	@Inject(method = "getStaticPipelines", at = @At("HEAD"))
	private static void voidmark$registerHeldItemPipeline(CallbackInfoReturnable<List<com.mojang.blaze3d.pipeline.RenderPipeline>> cir) {
		HeldItemShader.ensureRegistered();
	}
}
