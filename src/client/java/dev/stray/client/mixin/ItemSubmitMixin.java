package dev.stray.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.stray.client.visual.HeldItemShader;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(SubmitNodeStorage.ItemSubmit.class)
public class ItemSubmitMixin {
	@Inject(method = "<init>", at = @At("RETURN"))
	private void stray$markFillItem(
		PoseStack.Pose pose,
		ItemDisplayContext displayContext,
		int lightCoords,
		int overlayCoords,
		int outlineColor,
		int[] tintLayers,
		List<BakedQuad> quads,
		ItemStackRenderState.FoilType foilType,
		CallbackInfo ci
	) {
		HeldItemShader.markFillItem(this);
	}
}
