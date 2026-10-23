package dev.stray.client.mixin.cs2;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.stray.client.cs2.Cs2Knives;
import dev.stray.client.cs2.render.KnifeRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** CS2 knives: the local player's held sword becomes the CS2 knife in third person and mirrors. */
@Mixin(ItemInHandLayer.class)
public abstract class Cs2ThirdPersonMixin<S extends ArmedEntityRenderState, M extends EntityModel<S> & ArmedModel<S>> extends RenderLayer<S, M> {
	protected Cs2ThirdPersonMixin() {
		super(null);
	}

	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true, require = 0)
	private void stray$cs2Knife(
		S state,
		ItemStackRenderState itemState,
		ItemStack stack,
		HumanoidArm arm,
		PoseStack pose,
		SubmitNodeCollector collector,
		int light,
		CallbackInfo ci
	) {
		if (!(state instanceof AvatarRenderState avatar) || arm != state.mainArm) {
			return;
		}
		var player = Minecraft.getInstance().player;
		if (player == null || avatar.id != player.getId() || !Cs2Knives.applies(stack)) {
			return;
		}
		Cs2Knives.Knife knife = Cs2Knives.current();
		if (knife == null) {
			return;
		}
		pose.pushPose();
		getParentModel().translateToHand(state, arm, pose);
		pose.mulPose(Axis.XP.rotationDegrees(-90f));
		pose.mulPose(Axis.YP.rotationDegrees(180f));
		boolean left = arm == HumanoidArm.LEFT;
		pose.translate((left ? -1f : 1f) / 16f, 2f / 16f, -10f / 16f);
		if (left) {
			pose.scale(-1f, 1f, 1f);
		}
		Matrix4f toItem = KnifeRender.thirdPerson(knife.id, arm);
		KnifeRender.submit(pose, collector, light, knife, toItem, knife.restSkin());
		pose.popPose();
		ci.cancel();
	}
}
