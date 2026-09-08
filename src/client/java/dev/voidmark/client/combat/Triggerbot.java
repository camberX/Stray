package dev.voidmark.client.combat;

import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.location.SkyblockLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Attacks the crosshair entity using vanilla {@code gameMode.attack} plus a
 * swing, from {@code handleKeybinds} so the attack goes out before this tick's
 * movement packet. Fires only when Minecraft already resolved an entity hit
 * that is inside reach. Vanilla worlds wait for weapon charge; Skyblock waits
 * for tab Attack Speed. Humanize adds a short reaction wait, extra delay after
 * hits, and occasional one-tick hesitation.
 */
public final class Triggerbot {
	private static int tick;
	private static int lastHit = Integer.MIN_VALUE;
	private static int extraWait;
	private static int aimId = Integer.MIN_VALUE;
	private static int reactUntil;

	private Triggerbot() {
	}

	public static void reset() {
		tick = 0;
		lastHit = Integer.MIN_VALUE;
		extraWait = 0;
		aimId = Integer.MIN_VALUE;
		reactUntil = 0;
	}

	public static void tick(Minecraft client) {
		tick++;
		AttackSpeed.tick(client);
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!config.triggerbotEnabled) {
			return;
		}
		if (client.player == null || client.level == null || client.gameMode == null) {
			return;
		}
		if (client.screen != null || client.getOverlay() != null || client.isPaused()) {
			clearAim();
			return;
		}
		if (client.options.keyAttack.isDown()) {
			clearAim();
			return;
		}
		tryHit(client);
	}

	private static void tryHit(Minecraft client) {
		LocalPlayer player = client.player;
		MultiPlayerGameMode gameMode = client.gameMode;
		if (player.isSpectator() || gameMode.isSpectator() || player.isHandsBusy() || player.isUsingItem()) {
			clearAim();
			return;
		}
		if (!delayReady(player)) {
			return;
		}
		HitResult hit = client.hitResult;
		if (!(hit instanceof EntityHitResult entityHit) || hit.getType() != HitResult.Type.ENTITY) {
			clearAim();
			return;
		}
		Entity target = entityHit.getEntity();
		float humanize = VoidmarkConfig.clamp(VoidmarkConfig.get().triggerbotHumanize, 0f, 1f);
		if (!isTarget(target, player, VoidmarkConfig.get().triggerbotPlayers)) {
			clearAim();
			return;
		}
		if (!player.isWithinEntityInteractionRange(target, 0.0)) {
			clearAim();
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
			clearAim();
			return;
		}
		if (!reactionReady(target.getId(), humanize)) {
			return;
		}
		if (humanize > 0f && ThreadLocalRandom.current().nextFloat() < 0.04f + 0.08f * humanize) {
			return;
		}
		lastHit = tick;
		extraWait = extraTicks(humanize);
		gameMode.attack(player, target);
		player.swing(InteractionHand.MAIN_HAND);
	}

	private static boolean delayReady(LocalPlayer player) {
		int wait = Math.max(1, AttackSpeed.triggerDelay(player)) + extraWait;
		if (lastHit != Integer.MIN_VALUE && tick - lastHit < wait) {
			return false;
		}
		if (!SkyblockLocation.inSkyblock && !SkyblockLocation.onHypixel
			&& player.getAttackStrengthScale(0.0f) < 1.0f) {
			return false;
		}
		return true;
	}

	private static boolean reactionReady(int entityId, float humanize) {
		if (entityId != aimId) {
			aimId = entityId;
			reactUntil = tick + reactionTicks(humanize);
		}
		return tick >= reactUntil;
	}

	private static int reactionTicks(float humanize) {
		if (humanize <= 0.01f) {
			return 0;
		}
		double mean = 1.1 + 2.4 * humanize;
		double spread = 0.55 + 0.55 * humanize;
		int value = (int) Math.round(mean + ThreadLocalRandom.current().nextGaussian() * spread);
		return Math.max(1, Math.min(7, value));
	}

	private static int extraTicks(float humanize) {
		if (humanize <= 0.01f) {
			return 0;
		}
		int max = Math.max(1, Math.round(2.5f * humanize));
		return ThreadLocalRandom.current().nextInt(max + 1);
	}

	private static void clearAim() {
		aimId = Integer.MIN_VALUE;
		reactUntil = 0;
	}

	private static boolean isTarget(Entity entity, LocalPlayer player, boolean players) {
		if (entity == null || entity == player || entity.isRemoved()) {
			return false;
		}
		if (isArmorStand(entity)) {
			return false;
		}
		if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) {
			return false;
		}
		if (entity instanceof Interaction || entity instanceof Display) {
			return false;
		}
		if (entity instanceof Player) {
			return players && isCombatAlive(entity);
		}
		return entity instanceof LivingEntity && isCombatAlive(entity);
	}

	private static boolean isCombatAlive(Entity entity) {
		if (entity.isRemoved()) {
			return false;
		}
		if (!(entity instanceof LivingEntity living)) {
			return true;
		}
		if (living.deathTime > 0) {
			return false;
		}
		if (SkyblockLocation.inSkyblock || SkyblockLocation.onHypixel) {
			return true;
		}
		return living.isAlive();
	}

	private static boolean isArmorStand(Entity entity) {
		if (entity instanceof ArmorStand || entity instanceof Mannequin) {
			return true;
		}
		EntityType<?> type = entity.getType();
		if (type == EntityType.ARMOR_STAND || type == EntityType.MANNEQUIN) {
			return true;
		}
		Identifier id = EntityType.getKey(type);
		if (id == null) {
			return false;
		}
		String path = id.getPath();
		return path.equals("armor_stand") || path.equals("mannequin");
	}
}
