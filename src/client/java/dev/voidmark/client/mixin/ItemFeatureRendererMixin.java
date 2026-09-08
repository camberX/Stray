package dev.voidmark.client.mixin;

import com.mojang.blaze3d.vertex.QuadInstance;
import dev.voidmark.client.visual.HeldItemShader;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemFeatureRenderer.class)
public class ItemFeatureRendererMixin {
	@Shadow
	@Final
	private QuadInstance quadInstance;

	@Unique
	private SubmitNodeStorage.ItemSubmit voidmark$itemSubmit;

	@Inject(method = "renderItem", at = @At("HEAD"))
	private void voidmark$captureItem(
		MultiBufferSource.BufferSource bufferSource,
		OutlineBufferSource outlineBufferSource,
		SubmitNodeStorage.ItemSubmit submit,
		CallbackInfo ci
	) {
		this.voidmark$itemSubmit = submit;
	}

	@ModifyArg(
		method = "renderItem",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;getBuffer(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"
		),
		index = 0
	)
	private RenderType voidmark$heldItemType(RenderType original) {
		SubmitNodeStorage.ItemSubmit submit = this.voidmark$itemSubmit;
		if (submit == null || !HeldItemShader.applies(submit.displayContext())) {
			return original;
		}
		return HeldItemShader.wrap(original, submit.quads());
	}

	@Inject(method = "renderItem", at = @At("RETURN"))
	private void voidmark$heldItemMask(
		MultiBufferSource.BufferSource bufferSource,
		OutlineBufferSource outlineBufferSource,
		SubmitNodeStorage.ItemSubmit submit,
		CallbackInfo ci
	) {
		if (HeldItemShader.applies(submit.displayContext())) {
			this.quadInstance.setLightCoords(submit.lightCoords());
			this.quadInstance.setOverlayCoords(submit.overlayCoords());
			HeldItemShader.drawViewMask(outlineBufferSource, submit.pose(), submit.quads(), this.quadInstance);
		}
		this.voidmark$itemSubmit = null;
	}
}
