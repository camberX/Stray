package dev.stray.client.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * SkyHanni wraps GuiRenderer.addElementToMesh and, for every blit whose color
 * alpha is between 1 and 254, swaps the requested pipeline for its own plain
 * textured one. That drops Stray's shaders (rounded corners, circles, frosted
 * blur, cover art) whenever something fades or is translucent, which turns the
 * click GUI and HUD into squares and flat boxes. Stray's pipelines keep the
 * pipeline they asked for; everything else still goes through SkyHanni.
 */
@Pseudo
@Mixin(targets = "at.hannibal2.skyhanni.mixins.hooks.GuiRendererHook", remap = false)
public class SkyHanniGuiPipelineMixin {
	@Inject(method = "replacePipeline", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
	private void stray$keepStrayPipelines(GuiElementRenderState state, Operation<RenderPipeline> original, CallbackInfoReturnable<RenderPipeline> cir) {
		RenderPipeline pipeline = state.pipeline();
		if (pipeline != null && "stray".equals(pipeline.getLocation().getNamespace())) {
			cir.setReturnValue(pipeline);
		}
	}
}
