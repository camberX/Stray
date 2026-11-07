package dev.stray.client.mixin;

import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LoadingOverlay.class)
public interface LoadingOverlayAccessor {
	/** -1 until the reload is done and the overlay starts fading out. */
	@Accessor("fadeOutStart")
	long stray$fadeOutStart();
}
