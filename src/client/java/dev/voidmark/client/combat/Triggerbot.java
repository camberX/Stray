package dev.voidmark.client.combat;

import dev.voidmark.client.config.VoidmarkConfig;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Attacks the crosshair entity using vanilla {@code gameMode.attack} plus a
 * swing. No custom packets. Fires only when Minecraft already resolved an
 * entity hit that is inside reach.
 */
public final class Triggerbot {
	private static final Int2IntOpenHashMap LAST = new Int2IntOpenHashMap();
	private static int gameTick;

	private Triggerbot() {
	}

	public static void reset() {
		LAST.clear();
		gameTick = 0;
	}

	public static void tick(Minecraft client) {
		gameTick++;
		if ((gameTick & 31) == 0) {
			prune();
		}
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!config.triggerbotEnabled) {
			return;
		}
		if (client.player == null || client.level == null || client.gameMode == null) {
			return;
		}
		if (client.screen != null || client.getOverlay() != null || client.isPaused()) {
			return;
		}
		if (client.options.keyAttack.isDown()) {
			return;
		}
		tryHit(client);
	}

	private static void tryHit(Minecraft client) {
		LocalPlayer player = client.player;
		MultiPlayerGameMode gameMode = client.gameMode;
		if (player.isSpectator() || gameMode.isSpectator() || player.isHandsBusy() || player.isUsingItem()) {
			return;
		}
		HitResult hit = client.hitResult;
		if (!(hit instanceof EntityHitResult entityHit) || hit.getType() != HitResult.Type.ENTITY) {
			return;
		}
		Entity target = entityHit.getEntity();
		if (!isTarget(target, player, configPlayers())) {
			return;
		}
		if (!player.isWithinEntityInteractionRange(target, 0.0)) {
			return;
		}
		ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (!held.isItemEnabled(client.level.enabledFeatures())) {
			return;
		}
		if (player.cannotAttackWithItem(held, 0)) {
			return;
		}
		if (held.get(DataComponents.PIERCING_WEAPON) != null) {
			return;
		}
		AttackRange range = held.get(DataComponents.ATTACK_RANGE);
		if (range != null && !range.isInRange(player, hit.getLocation())) {
			return;
		}
		if (!entityReady(target.getId())) {
			return;
		}
		LAST.put(target.getId(), gameTick);
		gameMode.attack(player, target);
		player.swing(InteractionHand.MAIN_HAND);
	}

	private static boolean configPlayers() {
		return VoidmarkConfig.get().triggerbotPlayers;
	}

	private static boolean isTarget(Entity entity, LocalPlayer player, boolean players) {
		if (entity == null || entity == player || entity.isRemoved()) {
			return false;
		}
		if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) {
			return false;
		}
		if (entity instanceof ArmorStand || entity instanceof Interaction || entity instanceof Display) {
			return false;
		}
		if (entity instanceof Player) {
			return players;
		}
		return entity instanceof LivingEntity;
	}

	private static boolean entityReady(int entityId) {
		int last = LAST.get(entityId);
		return last == 0 || gameTick - last >= AttackSpeed.meleeDelay();
	}

	private static void prune() {
		if (!LAST.isEmpty()) {
			LAST.int2IntEntrySet().removeIf(entry -> gameTick - entry.getIntValue() > 40);
		}
	}
}
