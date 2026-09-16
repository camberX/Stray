package dev.stray.client.mixin;

import dev.stray.client.movement.MovementRings;
import dev.stray.client.ui.ChatPeek;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	@Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
	private void stray$lockLook(double movementTime, CallbackInfo ci) {
		if (MovementRings.playing()) {
			ci.cancel();
		}
	}

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void stray$peekScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (client.getWindow() == null || window != client.getWindow().handle()) {
			return;
		}
		if (ChatPeek.mouseScrolled(yOffset)) {
			ci.cancel();
		}
	}
}
