package dev.stray.client.mixin;

import dev.stray.client.farming.AutoDna;
import dev.stray.client.farming.GardenPlots;
import dev.stray.client.item.StoragePreview;
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
		if (AutoDna.shouldBlock(slotId)) {
			ci.cancel();
		}
	}

	@Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
	private void stray$containerOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
		AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
		GardenPlots.extract(screen, graphics, mouseX, mouseY);
		if (StoragePreview.extract(screen, graphics, mouseX, mouseY, hoveredSlot)) {
			ci.cancel();
		}
	}
}
