package dev.stray.client.mixin;

import dev.stray.client.farming.AutoDna;
import dev.stray.client.farming.GardenPlots;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {
	@Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
	private void stray$blockDnaClose(Slot slot, int slotId, int button, ContainerInput type, CallbackInfo ci) {
		if (AutoDna.shouldBlock(slotId)) {
			ci.cancel();
		}
	}

	@Inject(method = "extractTooltip", at = @At("HEAD"))
	private void stray$gardenPlots(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
		GardenPlots.extract((AbstractContainerScreen<?>) (Object) this, graphics, mouseX, mouseY);
	}
}
