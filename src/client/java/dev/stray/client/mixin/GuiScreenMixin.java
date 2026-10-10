package dev.stray.client.mixin;

import dev.stray.client.combat.AutoExperiments;
import dev.stray.client.farming.AutoDna;
import dev.stray.client.menu.DisabledPotions;
import dev.stray.client.ui.LoadoutsScreen;
import dev.stray.client.ui.StrayTitleScreen;
import dev.stray.client.ui.WardrobeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiScreenMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen stray$titleScreen(Screen screen) {
		if (screen instanceof TitleScreen) {
			return new StrayTitleScreen();
		}
		return WardrobeScreen.wrap(LoadoutsScreen.wrap(screen));
	}

	@Inject(method = "setScreen", at = @At("RETURN"))
	private void stray$hideDefaultLoadouts(Screen screen, CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (client.gui.screen() != null && LoadoutsScreen.hideDefaultChest(client.gui.screen()) && client.mouseHandler != null) {
			client.mouseHandler.grabMouse();
		}
	}

	@Inject(method = "setScreen", at = @At("HEAD"))
	private void stray$autoExperimentsOpen(Screen screen, CallbackInfo ci) {
		AutoExperiments.onOpen(screen);
		AutoDna.onOpen(screen);
		DisabledPotions.onOpen(screen);
	}
}
