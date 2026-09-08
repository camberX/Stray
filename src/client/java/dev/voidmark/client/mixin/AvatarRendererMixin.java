package dev.voidmark.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.voidmark.client.render.NametagRenderer;
import dev.voidmark.client.visual.HeldItemShader;
import dev.voidmark.client.visual.ShopCape;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("RETURN")
	)
	private void voidmark$showCustomCape(Avatar entity, AvatarRenderState state, float tickDelta, CallbackInfo ci) {
		if (entity == null || state.skin == null) {
			return;
		}
		state.skin = ShopCape.patch(entity.getUUID(), state.skin);
		if (ShopCape.showing(entity.getUUID())) {
			state.showCape = true;
		}
	}

	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Avatar;D)Z", at = @At("HEAD"), cancellable = true)
	private void voidmark$hideNametag(Avatar entity, double dist, CallbackInfoReturnable<Boolean> cir) {
		if (NametagRenderer.hidingVanilla(entity)) {
			cir.setReturnValue(false);
		}
	}

	@ModifyArg(
		method = "renderHand",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModelPart(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IILnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"
		),
		index = 2
	)
	private RenderType voidmark$ghostHand(
		RenderType original,
		PoseStack pose,
		SubmitNodeCollector collector,
		int light,
		Identifier texture,
		ModelPart part,
		boolean sleeve
	) {
		return HeldItemShader.wrapArm(original, texture);
	}

	@Inject(method = "renderHand", at = @At("RETURN"))
	private void voidmark$ghostHandMask(
		PoseStack pose,
		SubmitNodeCollector collector,
		int light,
		Identifier texture,
		ModelPart part,
		boolean sleeve,
		CallbackInfo ci
	) {
		HeldItemShader.submitArmMask(collector, pose, light, texture, part);
	}
}
