package dev.stray.client.dungeon;

import dev.stray.client.combat.OdinClicks;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.location.SkyblockLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Lever triggerbot: when your crosshair lands on a lever, right clicks it once, after
 * a short delay. Look away and back to flip it again, so it never toggles a lever back
 * and forth by itself.
 *
 * <p>The click is the normal use key, pressed where the game handles keys at the start
 * of a tick (right after it picks what the crosshair is on), so the game sends exactly
 * what a real right click on that lever sends, before the tick's movement packet.
 */
public final class LeverTriggerbot {
	private static BlockPos hovered;
	private static long hoverSince;
	private static long waitMs;
	private static BlockPos clicked;

	private LeverTriggerbot() {
	}

	public static void beforeKeybinds(Minecraft client) {
		StrayConfig config = StrayConfig.get();
		if (!config.leverTriggerbot || client.player == null || client.level == null || client.gui.screen() != null
			|| client.player.isSpectator() || config.leverTriggerbotDungeonsOnly && !SkyblockLocation.inDungeon) {
			hovered = null;
			clicked = null;
			return;
		}
		HitResult hit = client.hitResult;
		if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK
			|| !(client.level.getBlockState(blockHit.getBlockPos()).getBlock() instanceof LeverBlock)) {
			hovered = null;
			clicked = null;
			return;
		}
		// Sneaking with something in hand uses the item instead of the lever (vanilla),
		// e.g. an etherwarp: never click then.
		if (client.player.isSecondaryUseActive()
			&& (!client.player.getMainHandItem().isEmpty() || !client.player.getOffhandItem().isEmpty())) {
			return;
		}
		BlockPos pos = blockHit.getBlockPos();
		long now = System.currentTimeMillis();
		if (!pos.equals(hovered)) {
			hovered = pos.immutable();
			hoverSince = now;
			clicked = null;
			waitMs = delay(config);
		}
		if (pos.equals(clicked) || now - hoverSince < waitMs) {
			return;
		}
		clicked = hovered;
		OdinClicks.rightClick();
	}

	private static long delay(StrayConfig config) {
		long base = Math.max(0, config.leverTriggerbotDelay);
		long spread = Math.max(5L, base / 4);
		return base + ThreadLocalRandom.current().nextLong(0, spread + 1);
	}
}
