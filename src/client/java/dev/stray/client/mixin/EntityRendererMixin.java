package dev.stray.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.render.MobGlowRenderer;
import dev.stray.client.render.NametagRenderer;
import dev.stray.client.visual.FillEspMarker;
import dev.stray.client.visual.HeldItemShader;
import dev.stray.client.visual.NickHider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
	/**
	 * Bypass frustum culling for fill ESP and for glow that should show
	 * through walls, so those entities still submit when occluded.
	 */
	@Inject(method = "affectedByCulling", at = @At("HEAD"), cancellable = true)
	private void stray$disableCulling(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (HeldItemShader.shouldFillEntity(entity)) {
			cir.setReturnValue(false);
			return;
		}
		StrayConfig config = StrayConfig.get();
		if (config.mobGlowEnabled && config.mobGlowThroughWalls && MobGlowRenderer.listed(entity)) {
			Minecraft client = Minecraft.getInstance();
			if (client.player != null && entity != client.player) {
				cir.setReturnValue(false);
			}
		}
	}

	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
		at = @At("RETURN")
	)
	private void stray$nickTag(Entity entity, EntityRenderState state, float tickDelta, CallbackInfo ci) {
		if (state instanceof FillEspMarker marker) {
			marker.stray$setFillEsp(HeldItemShader.shouldFillEntity(entity));
		}
		if (StrayConfig.get().mobGlowEnabled) {
			int glow = MobGlowRenderer.outlineColor(entity);
			if (glow != 0 && !MobGlowRenderer.hasVanillaGlow(entity)) {
				state.outlineColor = glow;
			}
		}
		if (NametagRenderer.hidingVanilla(entity)) {
			state.nameTag = null;
			state.scoreText = null;
			return;
		}
		if (state.nameTag != null) {
			state.nameTag = NickHider.rewrite(state.nameTag);
		}
	}

	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Entity;D)Z", at = @At("HEAD"), cancellable = true)
	private void stray$hideName(Entity entity, double dist, CallbackInfoReturnable<Boolean> cir) {
		if (NametagRenderer.hidingVanilla(entity)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(
		method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void stray$hideNameDisplay(
		EntityRenderState state,
		PoseStack pose,
		SubmitNodeCollector collector,
		CameraRenderState camera,
		int yOffset,
		CallbackInfo ci
	) {
		if (NametagRenderer.hidingVanillaState(state)) {
			ci.cancel();
		}
	}
}
