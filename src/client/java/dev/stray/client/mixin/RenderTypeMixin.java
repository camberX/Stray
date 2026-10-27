package dev.stray.client.mixin;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.stray.client.visual.HeldItemShader;
import net.minecraft.client.renderer.DynamicGpuData;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderType.class)
public class RenderTypeMixin {
	@Redirect(
		method = "writeDynamicTransforms",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/DynamicGpuData;writeTransform(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"
		)
	)
	private GpuBufferSlice stray$heldItemTransform(DynamicGpuData uniforms, Matrix4f modelView, Matrix4f texture) {
		RenderPipeline pipeline = ((RenderType) (Object) this).pipeline();
		if (!HeldItemShader.isPipeline(pipeline)) {
			return uniforms.writeTransform(modelView, texture);
		}
		Vector4f color = new Vector4f(
			HeldItemShader.isMaskPipeline(pipeline)
				? HeldItemShader.outlineColorModulator()
				: HeldItemShader.colorModulator(pipeline)
		);
		Vector3f offset = new Vector3f(HeldItemShader.modelOffset(pipeline));
		return uniforms.writeTransform(modelView, color, offset, texture);
	}
}
