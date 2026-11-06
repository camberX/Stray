package dev.stray.client.mixin;

import dev.stray.client.combat.AutoBookCombine;
import dev.stray.client.input.MouseButtons;
import dev.stray.client.farming.AutoDna;
import dev.stray.client.menu.SackRecipe;
import dev.stray.client.farming.GardenPlots;
import dev.stray.client.item.StoragePreview;
import dev.stray.client.ui.ChestFillers;
import dev.stray.client.ui.ContainerChrome;
import dev.stray.client.ui.LoadoutsScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {
	@Shadow
	protected Slot hoveredSlot;

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void stray$hideLoadoutSwap(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (dev.stray.client.EditionGate.hideDefaultChest((AbstractContainerScreen<?>) (Object) this)) {
			ci.cancel();
			return;
		}
		AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
		if (dev.stray.client.dungeon.LeapMenu.isLeapMenu(screen)) {
			dev.stray.client.dungeon.LeapMenu.updateLeapMenu();
			dev.stray.client.dungeon.LeapMenuView.render(graphics, screen.width, screen.height);
			ci.cancel();
			return;
		}
		if (!dev.stray.Edition.LEGIT && dev.stray.client.dungeon.terminal.TerminalSession.handles(screen)) {
			dev.stray.client.dungeon.terminal.TerminalSession.render(graphics, screen.width, screen.height, mouseX, mouseY);
			ci.cancel();
		}
	}

	@Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
	private void stray$terminalRelease(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (!dev.stray.Edition.LEGIT && dev.stray.client.dungeon.terminal.TerminalSession.handles((AbstractContainerScreen<?>) (Object) this)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
	private void stray$terminalDrag(MouseButtonEvent event, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
		if (!dev.stray.Edition.LEGIT && dev.stray.client.dungeon.terminal.TerminalSession.handles((AbstractContainerScreen<?>) (Object) this)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(
		method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void stray$blockDnaClose(Slot slot, int slotId, int button, ContainerInput type, CallbackInfo ci) {
		if ((!dev.stray.Edition.LEGIT && AutoDna.shouldBlock(slotId)) || dev.stray.client.slayer.SlayerFx.blockClick((AbstractContainerScreen<?>) (Object) this, slot) || SackRecipe.click((AbstractContainerScreen<?>) (Object) this, slot, button, type)) {
			ci.cancel();
		}
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void stray$bookCombineEnter(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (!dev.stray.Edition.LEGIT && dev.stray.client.dungeon.terminal.TerminalSession.handles((AbstractContainerScreen<?>) (Object) this)
			&& dev.stray.client.dungeon.terminal.TerminalSession.keyPressed(event)) {
			cir.setReturnValue(true);
			return;
		}
		if (dev.stray.client.dungeon.LeapMenu.isLeapMenu((AbstractContainerScreen<?>) (Object) this)
			&& dev.stray.client.dungeon.LeapMenu.keyPressed(event.key())) {
			cir.setReturnValue(true);
			return;
		}
		if (!dev.stray.Edition.LEGIT) {
			if (AutoBookCombine.keyPressed((AbstractContainerScreen<?>) (Object) this, event)) {
				cir.setReturnValue(true);
			}
		}
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void stray$sackRecipeClick(MouseButtonEvent event, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
		if (!dev.stray.Edition.LEGIT && dev.stray.client.dungeon.terminal.TerminalSession.handles((AbstractContainerScreen<?>) (Object) this)) {
			dev.stray.client.dungeon.terminal.TerminalSession.mouseClicked(event.x(), event.y());
			cir.setReturnValue(true);
			return;
		}
		if (dev.stray.client.dungeon.LeapMenu.isLeapMenu((AbstractContainerScreen<?>) (Object) this)
			&& dev.stray.client.dungeon.LeapMenu.mouseClicked(event.button())) {
			cir.setReturnValue(true);
			return;
		}
		if (!MouseButtons.middle(event)) {
			return;
		}
		if (SackRecipe.click((AbstractContainerScreen<?>) (Object) this, hoveredSlot, 2, ContainerInput.CLONE)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "extractSlot", at = @At("HEAD"), cancellable = true)
	private void stray$hideBlackGlass(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		if (ChestFillers.hide(slot)) {
			ci.cancel();
		}
	}

	@Inject(method = "extractSlotHighlightBack", at = @At("HEAD"), cancellable = true)
	private void stray$noHoverFill(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		if (ContainerChrome.applies((AbstractContainerScreen<?>) (Object) this) || ChestFillers.hide(hoveredSlot)) {
			ci.cancel();
		}
	}

	@Inject(method = "extractSlotHighlightFront", at = @At("HEAD"), cancellable = true)
	private void stray$hoverOutline(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
		if (!ContainerChrome.applies(screen)) {
			return;
		}
		ContainerChrome.hover(graphics, hoveredSlot);
		ci.cancel();
	}

	@Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
	private void stray$containerOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
		AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
		GardenPlots.extract(screen, graphics, mouseX, mouseY);
		if (ChestFillers.hide(hoveredSlot) || StoragePreview.hideTooltip(screen, hoveredSlot)) {
			ci.cancel();
		}
	}
}
