package dev.stray.client.mixin;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(InventoryScreen.class)
public interface InventoryScreenAccessor {
	@Accessor("xMouse")
	float stray$xMouse();

	@Accessor("yMouse")
	float stray$yMouse();
}
