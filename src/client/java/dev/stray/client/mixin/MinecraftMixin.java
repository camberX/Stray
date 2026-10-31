package dev.stray.client.mixin;

import dev.stray.client.movement.AotvSim;
import dev.stray.client.movement.MovementRings;
import dev.stray.client.render.BlockMarks;
import dev.stray.client.render.MobGlowRenderer;
import dev.stray.client.farming.EmptyVacuum;
import dev.stray.client.ui.LoadoutSwap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {
	/**
	 * Same place vanilla left-click is handled, before this tick's movement
	 * packet is sent.
	 */
	@Inject(method = "handleKeybinds", at = @At("HEAD"))
	private void stray$preKeybinds(CallbackInfo ci) {
		Minecraft client = (Minecraft) (Object) this;
		dev.stray.client.movement.Freecam.preKeybinds(client);
		LoadoutSwap.preKeybinds(client);
		EmptyVacuum.preKeybinds(client);
		MovementRings.preKeybinds(client);
	}

	/** In freecam the (unmoved) player's crosshair target is meaningless: no block outline for it. */
	@Inject(method = "pick", at = @At("RETURN"))
	private void stray$ghostPickEnd(float partialTicks, CallbackInfo ci) {
		if (dev.stray.client.movement.Freecam.active()) {
			Minecraft client = (Minecraft) (Object) this;
			client.hitResult = null;
			client.crosshairPickEntity = null;
		}
	}

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void stray$aotvSim(CallbackInfo ci) {
		if (AotvSim.use((Minecraft) (Object) this)) {
			ci.cancel();
		}
	}

	/** Middle-click (pick block) toggles a Stray block mark when that feature is on. */
	@Inject(method = "pickBlockOrEntity", at = @At("HEAD"), cancellable = true)
	private void stray$blockMark(CallbackInfo ci) {
		if (BlockMarks.onPick((Minecraft) (Object) this)) {
			ci.cancel();
		}
	}

	/**
	 * Push ESP targets into the outline buffer (including holograms). Entities
	 * that already have vanilla GLOWING keep Minecraft's own outline.
	 */
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void stray$espGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (!MobGlowRenderer.glowEnabled() || MobGlowRenderer.hasVanillaGlow(entity)) {
			return;
		}
		if (MobGlowRenderer.shouldForceGlow(entity)) {
			cir.setReturnValue(true);
		}
	}
}
