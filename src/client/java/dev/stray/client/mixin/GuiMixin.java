package dev.stray.client.mixin;

import dev.stray.client.render.VanillaHud;
import dev.stray.client.skill.SkillProgressTracker;
import dev.stray.client.ui.ChatPeek;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {
	@Inject(method = "setOverlayMessage", at = @At("TAIL"))
	private void stray$skillOverlay(Component message, boolean animateColor, CallbackInfo ci) {
		SkillProgressTracker.onActionBar(message);
	}

	@ModifyArg(
		method = "extractChat",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/ChatComponent;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V"
		),
		index = 5
	)
	private ChatComponent.DisplayMode stray$peekChat(ChatComponent.DisplayMode mode) {
		return ChatPeek.holding() ? ChatComponent.DisplayMode.FOREGROUND : mode;
	}

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
