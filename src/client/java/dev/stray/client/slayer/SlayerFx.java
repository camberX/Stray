package dev.stray.client.slayer;

import dev.stray.client.config.SlayerSettings;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.location.SkyblockLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Slayer clean-up: hides spawn particles of slain mobs, Voidgloom particles, Inferno clutter
 * (clear view), names of full-health / dead grind mobs, damage splashes around the own boss,
 * irrelevant magma cubes, and blocks clicks on bosses that cannot spawn here.
 */
public final class SlayerFx {
	private record Death(Vec3 at, long ms) {
	}

	private static final Pattern MOB_NAME = Pattern.compile(
		"^\\[Lv\\d+]\\s+\\S*\\s*(?:Zombie Villager|Zombie|Crypt Ghoul|Graveyard Zombie|Dasher Spider|Weaver Spider|Splitter Spider"
			+ "|Voracious Spider|Silverfish|Wolf|Howling Spirit|Pack Spirit|Enderman|Voidling Fanatic|Blaze|Mutated Blaze|Bezal"
			+ "|Smoldering Blaze|Flaming Spider) (\\S+)/(\\S+)❤$");
	private static final Pattern DAMAGE = Pattern.compile("^[✧✯]?[\\d,.]+[kKmM]?[✧✯❤♞☄✷ﬗ⚔]*$");
	private static final List<Death> DEATHS = new ArrayList<>();
	private static boolean nearBlaze;
	private static int tick;

	private SlayerFx() {
	}

	private static SlayerSettings settings() {
		return StrayConfig.get().slayer;
	}

	public static void tick(Minecraft client) {
		if (client.player == null || client.level == null || !SkyblockLocation.inSkyblock) {
			DEATHS.clear();
			nearBlaze = false;
			return;
		}
		tick++;
		long now = System.currentTimeMillis();
		DEATHS.removeIf(d -> now - d.ms > 3000);
		SlayerSettings s = settings();
		if (s.cleanup && (s.hideSpawnWhite || s.hideSpawnPurple || s.hideSpawnSpecific) && SlayerApi.hasQuest() && SlayerApi.inCorrectArea()) {
			for (Entity e : client.level.getEntities(client.player, client.player.getBoundingBox().inflate(24),
				x -> x instanceof LivingEntity l && !(x instanceof ArmorStand) && l.isDeadOrDying())) {
				Vec3 at = e.position();
				boolean known = false;
				for (Death d : DEATHS) {
					if (d.at.distanceToSqr(at) < 0.25) {
						known = true;
						break;
					}
				}
				if (!known) {
					DEATHS.add(new Death(at, now));
				}
			}
		}
		if (tick % 60 == 0) {
			LivingEntity boss = SlayerApi.boss();
			nearBlaze = boss != null && SlayerApi.type() == SlayerType.INFERNO && boss.distanceTo(client.player) < 10;
		}
	}

	private static boolean clearView() {
		SlayerSettings s = settings();
		return s.inferno && s.clearView && nearBlaze && SlayerApi.boss() != null;
	}

	/** True to drop a particle before it is created. */
	public static boolean hideParticle(ParticleOptions options, double x, double y, double z) {
		if (!SkyblockLocation.inSkyblock) {
			return false;
		}
		SlayerSettings s = settings();
		if (clearView()) {
			return true;
		}
		var type = options.getType();
		if (s.voidgloom && s.voidHideParticles && "The End".equalsIgnoreCase(SkyblockLocation.area)
			&& (type == ParticleTypes.LARGE_SMOKE || type == ParticleTypes.FLAME || type == ParticleTypes.WITCH)) {
			Minecraft client = Minecraft.getInstance();
			if (client.level != null && !client.level.getEntitiesOfClass(EnderMan.class, new AABB(x - 3, y - 3, z - 3, x + 3, y + 3, z + 3)).isEmpty()) {
				return true;
			}
		}
		if (s.cleanup && !DEATHS.isEmpty()) {
			boolean wanted = type == ParticleTypes.ENCHANT && s.hideSpawnWhite
				|| type == ParticleTypes.WITCH && s.hideSpawnPurple
				|| type == ParticleTypes.ENTITY_EFFECT && s.hideSpawnSpecific;
			if (wanted) {
				for (Death d : DEATHS) {
					if (d.at.distanceToSqr(x, y, z) < 25) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** True to skip rendering an entity. */
	public static boolean hide(Entity entity) {
		if (!SkyblockLocation.inSkyblock || entity == null) {
			return false;
		}
		SlayerSettings s = settings();
		EntityType<?> type = entity.getType();
		if ((type == EntityType.FIREBALL || type == EntityType.SMALL_FIREBALL) && clearView()) {
			return true;
		}
		if (!s.cleanup) {
			return false;
		}
		if (entity instanceof ArmorStand stand && stand.hasCustomName()) {
			String name = SlayerApi.clean(stand.getCustomName());
			if (s.hideMobNames) {
				Matcher m = MOB_NAME.matcher(name);
				if (m.find() && (m.group(1).equals(m.group(2)) || m.group(1).equals("0"))) {
					return true;
				}
			}
			if (s.hideDamageNear && DAMAGE.matcher(name).matches()) {
				LivingEntity boss = SlayerApi.boss();
				if (boss != null && boss.distanceToSqr(stand) < 64) {
					return true;
				}
			}
			return false;
		}
		if (s.hideIrrelevant && type == EntityType.MAGMA_CUBE && SlayerApi.inCorrectArea()
			&& "Burning Desert".equals(SkyblockLocation.poi)) {
			return true;
		}
		return false;
	}

	/** Blocks clicking a boss in Maddox's menu that does not spawn on this island. */
	public static boolean blockClick(AbstractContainerScreen<?> screen, Slot slot) {
		SlayerSettings s = settings();
		if (!s.cleanup || !s.blockNotSpawnable || slot == null || !SkyblockLocation.inSkyblock) {
			return false;
		}
		if (!screen.getTitle().getString().replaceAll("§.", "").trim().equals("Slayer")) {
			return false;
		}
		ItemStack stack = slot.getItem();
		ItemLore lore = stack.isEmpty() ? null : stack.get(DataComponents.LORE);
		if (lore == null) {
			return false;
		}
		for (Component line : lore.lines()) {
			String text = line.getString().replaceAll("§.", "").trim();
			if (text.equals("Only inside The Rift!") || text.equals("Doesn't exist here!")) {
				return true;
			}
		}
		return false;
	}
}
