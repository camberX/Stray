package dev.stray.client.mixin;

import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.stray.client.visual.HeldItemShader;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemFeatureRenderer.class)
public abstract class ItemFeatureRendererMixin {
	@Shadow
	@Final
	private QuadInstance quadInstance;

	@Shadow
	protected abstract VertexConsumer getVertexBuilder(RenderType renderType);

	@Unique
	private ItemFeatureRenderer.Submit stray$itemSubmit;

	@Inject(method = "prepareMainSubmit", at = @At("HEAD"))
	private void stray$captureItem(ItemFeatureRenderer.Submit submit, CallbackInfo ci) {
		this.stray$itemSubmit = submit;
	}

	@Redirect(
		method = "prepareMainSubmit",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/feature/ItemFeatureRenderer;getVertexBuilder(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"
		)
	)
	private VertexConsumer stray$heldItemType(ItemFeatureRenderer instance, RenderType original) {
		ItemFeatureRenderer.Submit submit = this.stray$itemSubmit;
		RenderType type = original;
		if (submit != null && (HeldItemShader.active() || HeldItemShader.playerFill())) {
			if (HeldItemShader.isFillItem(submit)) {
				type = HeldItemShader.wrapPlayerItem(original, submit.quads());
			} else if (HeldItemShader.appliesFill(submit.displayContext())) {
				type = HeldItemShader.wrap(original, submit.quads());
			}
		}
		return this.getVertexBuilder(type);
	}

	@Inject(method = "prepareMainSubmit", at = @At("RETURN"))
	private void stray$heldItemMask(ItemFeatureRenderer.Submit submit, CallbackInfo ci) {
		if (HeldItemShader.masking()
			&& (HeldItemShader.appliesOutline(submit.displayContext()) || HeldItemShader.isFillItem(submit))) {
			this.quadInstance.setLightCoords(submit.lightCoords());
			this.quadInstance.setOverlayCoords(submit.overlayCoords());
			for (BakedQuad quad : submit.quads()) {
				Identifier atlas = TextureAtlas.LOCATION_ITEMS;
				if (quad != null && quad.materialInfo().sprite() != null) {
					atlas = quad.materialInfo().sprite().atlasLocation();
				}
				this.getVertexBuilder(HeldItemShader.maskRenderType(atlas)).putBakedQuad(submit.pose(), quad, this.quadInstance);
			}
		}
		this.stray$itemSubmit = null;
	}
}
