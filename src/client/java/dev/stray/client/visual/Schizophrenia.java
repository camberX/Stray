package dev.stray.client.visual;

import dev.stray.client.config.StrayConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.phys.Vec3;

/**
 * Schizophrenia: every other player near you stares at you. Only how they are drawn on your
 * screen changes (the head's turn in the render state); nothing is sent and the server sees nothing.
 */
public final class Schizophrenia {
	/** How far the head turns from the body before the body follows, like a real player. */
	private static final float NECK = 50f;

	private Schizophrenia() {
	}

	/** Turns this player's drawn head toward your eyes. */
	public static void apply(Avatar entity, AvatarRenderState state, float partial) {
		StrayConfig config = StrayConfig.get();
		Minecraft client = Minecraft.getInstance();
		if (!config.schizophrenia || client.player == null || entity == client.player) {
			return;
		}
		float range = StrayConfig.clamp(config.schizophreniaRange, 4f, 128f);
		Vec3 from = entity.getEyePosition(partial);
		Vec3 to = client.player.getEyePosition(partial);
		Vec3 d = to.subtract(from);
		if (d.lengthSqr() > range * range || d.lengthSqr() < 1.0e-4) {
			return;
		}
		float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
		float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
		float turn = Mth.wrapDegrees(yaw - state.bodyRot);
		if (config.schizophreniaBody && Math.abs(turn) > NECK) {
			// The body turns with it, so the head never twists further than a real neck.
			state.bodyRot = yaw - Math.copySign(NECK, turn);
			turn = Math.copySign(NECK, turn);
		}
		state.yRot = turn;
		state.xRot = pitch;
	}
}
