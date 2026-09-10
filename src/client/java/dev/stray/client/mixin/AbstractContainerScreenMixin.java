package dev.stray.client.mixin;

import dev.stray.client.farming.AutoDna;
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
}
