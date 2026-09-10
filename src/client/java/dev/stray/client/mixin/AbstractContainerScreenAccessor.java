package dev.stray.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("leftPos")
	int stray$leftPos();

	@Accessor("topPos")
	int stray$topPos();

	@Accessor("imageWidth")
	int stray$imageWidth();

	@Accessor("imageHeight")
	int stray$imageHeight();
}
