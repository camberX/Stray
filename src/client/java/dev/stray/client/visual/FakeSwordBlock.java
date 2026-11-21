package dev.stray.client.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.stray.client.config.StrayConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.Locale;

/**
 * Fake sword block: while you hold right click with a sword, your sword is drawn in the old 1.8
 * blocking pose (first person) and your arm in the block pose (third person, your own player).
 * Purely visual: nothing is sent and no input or item use changes, so right click does exactly
 * what it did before (SkyBlock sword abilities included). Swings still show while blocking.
 */
public final class FakeSwordBlock {
	private FakeSwordBlock() {
	}

	/** Your main hand item, while right click is held with a sword and no screen is open. */
	public static boolean active(ItemStack stack) {
		Minecraft client = Minecraft.getInstance();
		return StrayConfig.get().fakeSwordBlock
			&& client.player != null
			&& client.gui.screen() == null
			&& client.options.keyUse.isDown()
			&& sword(stack);
	}

	/** Minecraft swords, and SkyBlock swords (rarity line "... SWORD", e.g. Hyperion). */
	public static boolean sword(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		if (stack.is(ItemTags.SWORDS)) {
			return true;
		}
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) {
			return false;
		}
		List<Component> lines = lore.lines();
		for (int i = lines.size() - 1; i >= Math.max(0, lines.size() - 3); i--) {
			String line = dev.stray.client.util.Text.strip(lines.get(i).getString()).toUpperCase(Locale.ROOT);
			if (line.endsWith(" SWORD") || line.contains(" SWORD ")) {
				return true;
			}
		}
		return false;
	}

	/** First person: turns the sword into the 1.8 block pose, after vanilla's own (swing included). */
	public static void applyFirstPerson(PoseStack pose, InteractionHand hand, ItemStack stack) {
		if (pose == null || hand != InteractionHand.MAIN_HAND || !active(stack)) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		float k = client.player != null && client.player.getMainArm() == HumanoidArm.LEFT ? -1f : 1f;
		pose.translate(k * -0.14142136F, 0.08F, 0.14142136F);
		//? if >=26.3 {
		pose.rotate(Axis.XP.rotationDegrees(-102.25F));
		pose.rotate(Axis.YP.rotationDegrees(k * 13.365F));
		pose.rotate(Axis.ZP.rotationDegrees(k * 78.05F));
		//?} else {
		/*pose.mulPose(Axis.XP.rotationDegrees(-102.25F));
		pose.mulPose(Axis.YP.rotationDegrees(k * 13.365F));
		pose.mulPose(Axis.ZP.rotationDegrees(k * 78.05F));*///?}
	}
}
