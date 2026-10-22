package dev.stray.client.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.stray.client.ui.ContainerChrome;
import dev.stray.client.visual.NickHider;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
public class GuiGraphicsExtractorMixin {
	@ModifyVariable(
		method = "text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V",
		at = @At("HEAD"),
		argsOnly = true,
		ordinal = 0
	)
	private String stray$nickString(String text) {
		return NickHider.rewrite(text);
	}

	@ModifyVariable(
		method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V",
		at = @At("HEAD"),
		argsOnly = true,
		ordinal = 0
	)
	private Component stray$nickComponent(Component text) {
		return NickHider.rewrite(text);
	}

	@ModifyVariable(
		method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V",
		at = @At("HEAD"),
		argsOnly = true,
		ordinal = 2
	)
	private int stray$containerLabel(int color) {
		return ContainerChrome.labelColor(color);
	}

	@ModifyVariable(
		method = "text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V",
		at = @At("HEAD"),
		argsOnly = true,
		ordinal = 2
	)
	private int stray$containerLabelString(int color) {
		return ContainerChrome.labelColor(color);
	}

	@Inject(
		method = "innerBlit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIFFFFI)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void stray$skipVanillaContainer(
		RenderPipeline pipeline,
		Identifier texture,
		int x0,
		int y0,
		int x1,
		int y1,
		float u0,
		float u1,
		float v0,
		float v1,
		int color,
		CallbackInfo ci
	) {
		if (ContainerChrome.hideVanilla()) {
			ci.cancel();
		}
	}
}
