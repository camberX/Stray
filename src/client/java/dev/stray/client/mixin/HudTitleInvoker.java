package dev.stray.client.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Draws the game's title/subtitle again, on top of the leap menu. */
@Mixin(Hud.class)
public interface HudTitleInvoker {
	@Invoker("extractTitle")
	void stray$extractTitle(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker);
}
