package dev.stray.client.mixin;

import dev.stray.client.ui.ChatPeek;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void stray$peekScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (this.minecraft.getWindow() == null || window != this.minecraft.getWindow().handle()) {
			return;
		}
		if (ChatPeek.mouseScrolled(yOffset)) {
			ci.cancel();
		}
	}
}
