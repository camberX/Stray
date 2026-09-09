package dev.stray.client.mixin;

import dev.stray.client.visual.HeldItemShader;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SubmitNodeCollection.class)
public class SubmitNodeCollectionMixin {
	@ModifyVariable(method = "submitModel", at = @At("HEAD"), argsOnly = true)
	private RenderType stray$fillModel(RenderType original) {
		return HeldItemShader.wrapSubmitted(original);
	}

	@ModifyVariable(method = "submitModelPart", at = @At("HEAD"), argsOnly = true)
	private RenderType stray$fillModelPart(RenderType original) {
		return HeldItemShader.wrapSubmitted(original);
	}
}
