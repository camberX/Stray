package dev.stray.client.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.stray.client.visual.HeldItemShader;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderType.class)
public class RenderTypeMixin {
	@Redirect(
		method = "writeDynamicTransforms",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/DynamicUniforms;writeTransform(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"
		)
	)
	private GpuBufferSlice stray$heldItemTransform(DynamicUniforms uniforms, Matrix4f modelView, Matrix4f texture) {
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

	/** CS2 knives: remember the prepared knife batch so the knife's GPU draw can be attached to it. */
	@Inject(method = "prepare", at = @At("RETURN"))
	private void stray$knifePrepared(CallbackInfoReturnable<net.minecraft.client.renderer.rendertype.PreparedRenderType> cir) {
		dev.stray.client.cs2.render.KnifeGpu.prepared((RenderType) (Object) this, cir.getReturnValue());
	}
}
