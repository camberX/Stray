package dev.voidmark.client.mixin;

import dev.voidmark.client.visual.HeldItemShader;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Vector3fc;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(RenderType.class)
public class RenderTypeMixin {
	@ModifyArg(
		method = "draw",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/DynamicUniforms;writeTransform(Lorg/joml/Matrix4fc;Lorg/joml/Vector4fc;Lorg/joml/Vector3fc;Lorg/joml/Matrix4fc;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"
		),
		index = 1
	)
	private Vector4fc voidmark$heldItemColor(Vector4fc color) {
		var pipeline = ((RenderType) (Object) this).pipeline();
		if (HeldItemShader.isMaskPipeline(pipeline)) {
			return HeldItemShader.outlineColorModulator();
		}
		if (HeldItemShader.isFillPipeline(pipeline)) {
			return HeldItemShader.colorModulator();
		}
		return color;
	}

	@ModifyArg(
		method = "draw",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/DynamicUniforms;writeTransform(Lorg/joml/Matrix4fc;Lorg/joml/Vector4fc;Lorg/joml/Vector3fc;Lorg/joml/Matrix4fc;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"
		),
		index = 2
	)
	private Vector3fc voidmark$heldItemOffset(Vector3fc offset) {
		if (!HeldItemShader.isPipeline(((RenderType) (Object) this).pipeline())) {
			return offset;
		}
		return HeldItemShader.modelOffset();
	}
}
