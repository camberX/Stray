package dev.stray.client.combat;

import com.mojang.blaze3d.platform.InputConstants;
import dev.stray.client.config.StrayConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * OdinClient AutoClicker.kt, translated without changing CPS, jitter, or
 * terminator / whitelist / dungeon-breaker behavior.
 */
public final class AutoClicker {
	private static double nlc;
	private static double nrc;

	private AutoClicker() {
	}

	public static void reset() {
		nlc = 0;
		nrc = 0;
	}

	public static void tick(Minecraft client) {
		StrayConfig config = StrayConfig.get();
		if (!config.autoClickerEnabled) {
			return;
		}
		if (client.screen != null) {
			return;
		}
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}
		if (player.isUsingItem()) {
			return;
		}
		if (client.gameMode != null && client.gameMode.isDestroying()) {
			return;
		}
		long now = System.currentTimeMillis();

		if (config.autoClickerTerminatorOnly) {
			if (!"TERMINATOR".equals(OdinClicks.itemId(player.getMainHandItem())) || !client.options.keyUse.isDown()) {
				return;
			}
			if (now < nrc) {
				return;
			}
			nrc = now + ((1000 / config.autoClickerCps) + ((Math.random() - .5) * 60.0));
			OdinClicks.leftClick();
			return;
		}

		String h1 = OdinClicks.itemId(player.getMainHandItem());
		if (config.autoClickerBlockBreaker && "DUNGEONBREAKER".equals(h1)) {
			return;
		}

		String h2 = held();
		boolean a = !config.autoClickerWhiteListOnly || config.autoClickerLeftWhitelist.contains(h2);
		boolean b = !config.autoClickerWhiteListOnly || config.autoClickerRightWhitelist.contains(h2);
		if (!a && !b) {
			return;
		}

		Level level = client.level;
		if (level == null) {
			return;
		}
		HitResult hitResult = client.hitResult;
		BlockHitResult hit = hitResult instanceof BlockHitResult block ? block : null;

		boolean lc = a && config.autoClickerEnableLeftClick && OdinClicks.isPressed(leftKey());
		boolean rc = b && config.autoClickerEnableRightClick && OdinClicks.isPressed(rightKey());

		if (hit != null && !level.getBlockState(hit.getBlockPos()).isAir() && lc && config.autoClickerAllowBreaking) {
			OdinClicks.holdAttack();
			return;
		}

		if (lc && now >= nlc) {
			nlc = now + ((1000 / config.autoClickerLeftCps) + ((Math.random() - .5) * 60.0));
			OdinClicks.leftClick();
		}

		if (rc && now >= nrc) {
			nrc = now + ((1000 / config.autoClickerRightCps) + ((Math.random() - .5) * 60.0));
			OdinClicks.rightClick();
		}
	}

	public static String held() {
		return OdinClicks.held();
	}

	public static InputConstants.Key leftKey() {
		return OdinClicks.parseKey(StrayConfig.get().autoClickerLeftKey);
	}

	public static InputConstants.Key rightKey() {
		return OdinClicks.parseKey(StrayConfig.get().autoClickerRightKey);
	}
}
