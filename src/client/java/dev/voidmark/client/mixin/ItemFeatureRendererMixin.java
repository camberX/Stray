package dev.voidmark.client.mixin;

import dev.voidmark.client.visual.HeldItemShader;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ItemFeatureRenderer.class)
public class ItemFeatureRendererMixin {
	@ModifyArg(
		method = "renderItem",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;getBuffer(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"
		),
		index = 0
	)
	private RenderType voidmark$heldItemType(
		RenderType original,
		MultiBufferSource.BufferSource bufferSource,
		OutlineBufferSource outlineBufferSource,
		SubmitNodeStorage.ItemSubmit submit
	) {
		if (!HeldItemShader.applies(submit.displayContext())) {
			return original;
		}
		return HeldItemShader.wrap(original, submit.quads());
	}
}
