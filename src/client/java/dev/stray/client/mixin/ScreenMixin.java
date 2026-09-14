package dev.stray.client.mixin;

import dev.stray.client.render.GuiDraw;
import dev.stray.client.ui.ContainerChrome;
import dev.stray.client.ui.MenuChrome;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {
	@Shadow public int width;
	@Shadow public int height;

	@Inject(
		method = "extractRenderStateWithTooltipAndSubtitles",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/Screen;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"
		)
	)
	private void stray$hideVanillaContainer(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		ContainerChrome.hideVanilla(ContainerChrome.applies((Screen) (Object) this));
	}

	@Inject(
		method = "extractRenderStateWithTooltipAndSubtitles",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/Screen;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
			shift = At.Shift.AFTER
		)
	)
	private void stray$containerChrome(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		ContainerChrome.hideVanilla(false);
		Screen self = (Screen) (Object) this;
		if (ContainerChrome.applies(self)) {
			ContainerChrome.cover(graphics, self);
		}
	}

	@Inject(method = "extractPanorama", at = @At("HEAD"), cancellable = true)
	private void stray$starfield(GuiGraphicsExtractor graphics, float delta, CallbackInfo ci) {
		Screen self = (Screen) (Object) this;
		if (!MenuChrome.applies(self) || !MenuChrome.outOfWorld()) {
			return;
		}
		MenuChrome.sky(graphics, width, height);
		ci.cancel();
	}

	@Inject(method = "extractBlurredBackground", at = @At("HEAD"), cancellable = true)
	private void stray$sharpStars(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		Screen self = (Screen) (Object) this;
		if (MenuChrome.applies(self) && MenuChrome.outOfWorld()) {
			ci.cancel();
		}
	}

	@Inject(method = "extractMenuBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V", at = @At("HEAD"), cancellable = true)
	private void stray$headerFooter(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		Screen self = (Screen) (Object) this;
		if (!MenuChrome.applies(self)) {
			return;
		}
		if (!MenuChrome.outOfWorld()) {
			GuiDraw.fill(graphics, 0, 0, width, height, 0x55000000);
		}
		ci.cancel();
	}

	@Inject(method = "extractMenuBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIII)V", at = @At("HEAD"), cancellable = true)
	private void stray$noDirt(GuiGraphicsExtractor graphics, int x, int y, int w, int h, CallbackInfo ci) {
		if (MenuChrome.applies((Screen) (Object) this)) {
			ci.cancel();
		}
	}
}
