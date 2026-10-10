package dev.stray.client.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;
import dev.stray.client.visual.HeldItemShader;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderTypeFeatureRenderer.class)
public class RenderTypeFeatureRendererMixin {
	@Redirect(
		method = "executeGroup",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/rendertype/PreparedRenderType;drawFromBuffer(Lnet/minecraft/client/renderer/StagedVertexBuffer$ExecuteInfo;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"
		)
	)
	private void stray$offscreen(
		PreparedRenderType type,
		StagedVertexBuffer.ExecuteInfo info,
		RenderPass pass
	) {
		if (HeldItemShader.captureOffscreen(type, info)) {
			return;
		}
		type.drawFromBuffer(info, pass);
	}
}
