package dev.stray.client.mixin;

import dev.stray.client.combat.AutoExperiments;
import dev.stray.client.farming.AutoDna;
import dev.stray.client.farming.GardenPlots;
import dev.stray.client.item.StoragePreview;
import dev.stray.client.ui.ChestFillers;
import dev.stray.client.ui.ContainerChrome;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {
	@Shadow
	protected Slot hoveredSlot;

	@Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
	private void stray$blockDnaClose(Slot slot, int slotId, int button, ContainerInput type, CallbackInfo ci) {
		AutoExperiments.onSlotClick((AbstractContainerScreen<?>) (Object) this, slot);
		if (AutoDna.shouldBlock(slotId)) {
			ci.cancel();
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
