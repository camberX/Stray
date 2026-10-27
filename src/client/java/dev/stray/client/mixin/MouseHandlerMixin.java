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
import org.spongepowered.asm.mixin.injection.ModifyArg;
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
	 * Vanilla warps the cursor to the window center when it releases the mouse.
	 * When a container opens straight from another one, keep the cursor where it
	 * was instead: swap the warp target and the stored position. The arguments are
	 * already on the stack at the call, so they are replaced with ModifyArg.
	 */
	@ModifyArg(method = "releaseMouse", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/InputConstants;releaseMouse(Lcom/mojang/blaze3d/platform/Window;DD)V"), index = 1)
	private double stray$keepCursorX(double x) {
		return stray$keep() ? this.stray$beforeX : x;
	}

	@ModifyArg(method = "releaseMouse", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/InputConstants;releaseMouse(Lcom/mojang/blaze3d/platform/Window;DD)V"), index = 2)
	private double stray$keepCursorY(double y) {
		return stray$keep() ? this.stray$beforeY : y;
	}

	@Inject(method = "releaseMouse", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/InputConstants;releaseMouse(Lcom/mojang/blaze3d/platform/Window;DD)V", shift = At.Shift.AFTER))
	private void stray$keepCursor(CallbackInfo ci) {
		if (stray$keep()) {
			this.xpos = this.stray$beforeX;
			this.ypos = this.stray$beforeY;
		}
	}

	@Unique
	private static boolean stray$keep() {
		Minecraft client = Minecraft.getInstance();
		return client.gui.screen() instanceof ContainerScreen && NoCursorReset.shouldHookMouse();
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
