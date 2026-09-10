package dev.stray.client.mixin;

import dev.stray.client.render.VanillaHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {
	@Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
	private void stray$skipScoreboard(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
		if (VanillaHud.customScoreboard()) {
			ci.cancel();
		}
	}

	@Inject(method = "extractBossOverlay", at = @At("HEAD"), cancellable = true)
	private void stray$skipBoss(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
		if (VanillaHud.customBossBar()) {
			ci.cancel();
		}
	}

	@Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
	private void stray$skipEffects(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
		if (VanillaHud.customEffects()) {
			ci.cancel();
		}
	}

	@Inject(method = "extractSelectedItemName", at = @At("HEAD"), cancellable = true)
	private void stray$skipHeldItem(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		if (VanillaHud.customHeldItem()) {
			ci.cancel();
		}
	}
}
