package dev.stray.client.mixin;

import dev.stray.client.combat.AutoExperiments;
import dev.stray.client.combat.Triggerbot;
import dev.stray.client.render.MobGlowRenderer;
import dev.stray.client.ui.LoadoutsScreen;
import dev.stray.client.ui.StrayTitleScreen;
import dev.stray.client.ui.WardrobeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen stray$titleScreen(Screen screen) {
		if (screen instanceof TitleScreen) {
			return new StrayTitleScreen();
		}
		return WardrobeScreen.wrap(LoadoutsScreen.wrap(screen));
	}

	@Inject(method = "setScreen", at = @At("HEAD"))
	private void stray$autoExperimentsOpen(Screen screen, CallbackInfo ci) {
		AutoExperiments.onOpen(screen);
	}

	/**
	 * Same place vanilla left-click is handled, before this tick's movement
	 * packet is sent.
	 */
	@Inject(method = "handleKeybinds", at = @At("HEAD"))
	private void stray$triggerbot(CallbackInfo ci) {
		Triggerbot.tick((Minecraft) (Object) this);
	}

	/**
	 * Push ESP targets into the outline buffer (including holograms). Entities
	 * that already have vanilla GLOWING keep Minecraft's own outline.
	 */
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void stray$espGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (MobGlowRenderer.hasVanillaGlow(entity)) {
			return;
		}
		if (MobGlowRenderer.shouldForceGlow(entity)) {
			cir.setReturnValue(true);
		}
	}
}
