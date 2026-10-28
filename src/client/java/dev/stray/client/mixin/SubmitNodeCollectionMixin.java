package dev.stray.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.stray.client.visual.HeldItemShader;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollection;
//? if <26.3 {
/*import net.minecraft.client.renderer.feature.ModelFeatureRenderer;*///?}
import net.minecraft.client.renderer.rendertype.RenderType;
//? if >=26.3 {
import net.minecraft.client.renderer.texture.UvMapping;
//?} else {
/*import net.minecraft.client.renderer.texture.TextureAtlasSprite;*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SubmitNodeCollection.class)
public class SubmitNodeCollectionMixin {
	@Unique
	private boolean stray$submittingMask;

	@ModifyVariable(method = "submitModel", at = @At("HEAD"), argsOnly = true)
	private RenderType stray$fillModel(RenderType original) {
		return HeldItemShader.wrapSubmitted(original);
	}

	@Inject(method = "submitModel", at = @At("RETURN"))
	private <S> void stray$maskModel(
		Model<? super S> model,
		S state,
		PoseStack pose,
		RenderType renderType,
		int lightCoords,
		int overlayCoords,
		//? if >=26.3 {
		int tintedColor,
		UvMapping uvMapping,
		//?} else {
		/*int seed,
		TextureAtlasSprite sprite,*///?}
		int outlineColor,
		//? if <26.3 {
		/*ModelFeatureRenderer.CrumblingOverlay crumbling,*///?}
		CallbackInfo ci
	) {
		if (this.stray$submittingMask) {
			return;
		}
		RenderType mask = HeldItemShader.playerFillMask(renderType);
		if (mask == null) {
			return;
		}
		this.stray$submittingMask = true;
		try {
			((SubmitNodeCollection) (Object) this).submitModel(
				model,
				state,
				pose,
				mask,
				lightCoords,
				overlayCoords,
				//? if >=26.3 {
				tintedColor,
				uvMapping,
				outlineColor
				//?} else {
				/*seed,
				sprite,
				outlineColor,
				crumbling*///?}
			);
		} finally {
			this.stray$submittingMask = false;
		}
	}
}
