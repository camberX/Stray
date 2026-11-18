package dev.stray.client.movement;

import dev.stray.client.config.StrayConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.AABB;

/**
 * Edge jump: while you walk on the ground (not sneaking), you jump on the tick where nothing is
 * left under your hitbox, i.e. at the very edge of the block. It only adds the jump to this tick's
 * input, the same as pressing space. Toggle with {@code /stray edge}. Not in Stray Legit.
 */
public final class EdgeJump {
	private EdgeJump() {
	}

	public static void applyInput(ClientInput input) {
		if (!StrayConfig.get().edgeJump || input == null) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.level == null || client.gui.screen() != null) {
			return;
		}
		Input keys = input.keyPresses;
		boolean moving = keys.forward() || keys.backward() || keys.left() || keys.right();
		if (!moving || keys.jump() || keys.shift() || !player.onGround() || player.isPassenger()
			|| player.getAbilities().flying || player.isInWater() || player.isInLava() || player.onClimbable()) {
			return;
		}
		// Nothing below the hitbox (shrunk a hair so a block touching the side does not count).
		AABB below = player.getBoundingBox().move(0.0, -0.5, 0.0).inflate(-0.001, 0.0, -0.001);
		if (client.level.noCollision(player, below)) {
			input.keyPresses = new Input(keys.forward(), keys.backward(), keys.left(), keys.right(), true, keys.shift(), keys.sprint());
		}
	}
}
