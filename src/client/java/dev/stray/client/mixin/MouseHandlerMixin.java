package dev.stray.client.mixin;

import dev.stray.client.menu.NoCursorReset;
import dev.stray.client.movement.MovementRings;
import dev.stray.client.ui.ChatPeek;
import dev.stray.client.ui.LoadoutsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	@Shadow
	private double xpos;
	@Shadow
	private double ypos;

	@Unique
	private double stray$beforeX;
	@Unique
	private double stray$beforeY;

	/**
	 * {@code grabMouse} always calls {@code setScreen(null)}. During a hidden
	 * loadout swap that closes the chest before the slot click is sent.
	 */
	@Inject(method = "grabMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"), cancellable = true)
	private void stray$keepHiddenLoadouts(CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (client.gui.screen() != null && LoadoutsScreen.hideDefaultChest(client.gui.screen())) {
			ci.cancel();
		}
	}

	@Inject(method = "grabMouse", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MouseHandler;xpos:D", ordinal = 0, opcode = Opcodes.PUTFIELD))
	private void stray$rememberCursor(CallbackInfo ci) {
		this.stray$beforeX = this.xpos;
		this.stray$beforeY = this.ypos;
	}

	/**
	 * Vanilla warps to the window center, then asks SDL to leave relative mouse
	 * mode. That pair is a blocking compositor round trip on Linux. Calling it
	 * again from here ran once per {@code getWindow()} in this method, so a
	 * chest refresh (shift-click in a menu that reopens) stalled the frame.
	 * The saved coordinates are substituted into the one warp vanilla already does.
	 */
	@Inject(
		method = "releaseMouse",
		at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/blaze3d/platform/InputConstants;releaseMouse(Lcom/mojang/blaze3d/platform/Window;DD)V"
		)
	)
	private void stray$keepCursor(CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (client.gui.screen() instanceof ContainerScreen && NoCursorReset.shouldHookMouse()) {
			this.xpos = this.stray$beforeX;
			this.ypos = this.stray$beforeY;
		}
	}

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
