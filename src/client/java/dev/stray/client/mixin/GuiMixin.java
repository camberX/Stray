package dev.stray.client.mixin;

import dev.stray.client.render.VanillaHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Gui.class)
public class GuiMixin {
	@Inject(method = "extractItemHotbar", at = @At("HEAD"), cancellable = true)
	private void stray$skipHotbar(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
		if (VanillaHud.customHotbar()) {
			ci.cancel();
		}
	}

	@Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
	private void stray$skipScoreboard(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
		if (VanillaHud.customScoreboard()) {
			ci.cancel();
		}
	}

	@Inject(method = "extractPlayerHealth", at = @At("HEAD"), cancellable = true)
	private void stray$skipHealth(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		if (VanillaHud.customHealth()) {
			ci.cancel();
		}
	}

	@Inject(method = "extractFood", at = @At("HEAD"), cancellable = true)
	private void stray$skipFood(GuiGraphicsExtractor graphics, Player player, int x, int y, CallbackInfo ci) {
		if (VanillaHud.customHunger()) {
			ci.cancel();
		}
	}

	@Inject(method = "extractArmor", at = @At("HEAD"), cancellable = true)
	private static void stray$skipArmor(
		GuiGraphicsExtractor graphics,
		Player player,
		int y,
		int heartRows,
		int height,
		int x,
		CallbackInfo ci
	) {
		if (VanillaHud.customArmor()) {
			ci.cancel();
		}
	}

	@Inject(method = "extractAirBubbles", at = @At("HEAD"), cancellable = true)
	private void stray$skipAir(GuiGraphicsExtractor graphics, Player player, int x, int y, int line, CallbackInfo ci) {
		if (VanillaHud.customAir()) {
			ci.cancel();
		}
	}

	@Inject(method = "extractVehicleHealth", at = @At("HEAD"), cancellable = true)
	private void stray$skipMount(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		if (VanillaHud.customMount()) {
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

	@Inject(method = "nextContextualInfoState", at = @At("RETURN"), cancellable = true)
	private void stray$skipExperience(CallbackInfoReturnable<Object> cir) {
		if (!VanillaHud.customExperience()) {
			return;
		}
		Object value = cir.getReturnValue();
		if (value instanceof Enum<?> info && "EXPERIENCE".equals(info.name())) {
			@SuppressWarnings({"unchecked", "rawtypes"})
			Enum<?> empty = Enum.valueOf((Class) info.getClass(), "EMPTY");
			cir.setReturnValue(empty);
		}
	}
}
