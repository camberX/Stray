package dev.stray.client.mixin;

import dev.stray.client.visual.NickHider;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Font.class)
public class FontMixin {
	@ModifyVariable(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private String stray$nickWidth(String text) {
		return NickHider.rewrite(text);
	}

	@ModifyVariable(
		method = "drawInBatch(Ljava/lang/String;FFIZLorg/joml/Matrix4fc;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V",
		at = @At("HEAD"),
		argsOnly = true,
		ordinal = 0
	)
	private String stray$nickDrawString(String text) {
		return NickHider.rewrite(text);
	}

	@ModifyVariable(
		method = "drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4fc;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V",
		at = @At("HEAD"),
		argsOnly = true,
		ordinal = 0
	)
	private Component stray$nickDrawComponent(Component text) {
		return NickHider.rewrite(text);
	}
}
