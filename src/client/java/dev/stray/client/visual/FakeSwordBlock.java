package dev.stray.client.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.stray.client.config.StrayConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Fake sword block, after ziyno-addons' sword blocking (CC0): while you hold right click with a
 * sword item, the sword is drawn in the old 1.7 blocking pose in first person, and your own
 * player's arm is raised to block in third person. Neither shows mid swing, so a swing plays as
 * normal. Purely visual: nothing is sent and no input, item use or block breaking changes (unlike
 * ziyno-addons, breaking blocks is left alone).
 */
public final class FakeSwordBlock {
	private FakeSwordBlock() {
	}

	/** Right click held with a Minecraft sword item in the main hand, no screen open, not mid swing. */
	public static boolean active() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (!StrayConfig.get().fakeSwordBlock || player == null || client.gui.screen() != null || !client.options.keyUse.isDown()) {
			return false;
		}
		if (!player.getMainHandItem().is(ItemTags.SWORDS)) {
			return false;
		}
		float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		//? if >=26.3 {
		float swing = player.getSwingAnimation(partial);
		//?} else {
		/*float swing = player.getAttackAnim(partial);*///?}
		return !(swing > 0.0f && swing < 0.9f);
	}

	/** First person, after vanilla's arm transform for the main hand. */
	public static void applyFirstPerson(PoseStack pose, HumanoidArm arm) {
		Minecraft client = Minecraft.getInstance();
		if (pose == null || client.player == null || arm != client.player.getMainArm() || !active()) {
			return;
		}
		float k = arm == HumanoidArm.RIGHT ? 1f : -1f;
		pose.translate(k * -0.045f, -0.035f, 0.0f);
		//? if >=26.3 {
		pose.rotate(Axis.YP.rotationDegrees(k * 120.0f));
		pose.rotate(Axis.XP.rotationDegrees(-76.0f));
		pose.rotate(Axis.ZP.rotationDegrees(k * -45.0f));
		//?} else {
		/*pose.mulPose(Axis.YP.rotationDegrees(k * 120.0f));
		pose.mulPose(Axis.XP.rotationDegrees(-76.0f));
		pose.mulPose(Axis.ZP.rotationDegrees(k * -45.0f));*///?}
		pose.scale(1.23f, 1.23f, 1.23f);
	}

	/** Third person, your own player only: the sword arm raised to block. */
	public static void applyArm(int entityId, ModelPart rightArm, ModelPart leftArm) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || entityId != client.player.getId() || !active()) {
			return;
		}
		boolean right = client.player.getMainArm() == HumanoidArm.RIGHT;
		ModelPart arm = right ? rightArm : leftArm;
		arm.xRot = (float) Math.toRadians(-45.0);
		arm.yRot = (float) Math.toRadians(right ? -30.0 : 30.0);
		arm.zRot = 0.0f;
	}
}
