package dev.stray.client.render;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.location.SkyblockLocation;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Starred dungeon mobs, using NoammAddons' entity-id offset and hologram
 * scan. Glow is the same outline buffer as Mob glow.
 */
public final class StarMobEsp {
	private static final String HEART = "❤";
	private static final String STAR = "✯";
	private static final Set<String> MINIBOSSES = Set.of(
		"Shadow Assassin",
		"Lost Adventurer",
		"Diamond Guy",
		"King Midas"
	);
	private static final IntOpenHashSet STARRED = new IntOpenHashSet();
	private static final IntOpenHashSet CHECKED = new IntOpenHashSet();

	private StarMobEsp() {
	}

	public static void reset() {
		STARRED.clear();
		CHECKED.clear();
	}

	public static void onRemoveEntities(IntList ids) {
		if (ids == null) {
			return;
		}
		for (int i = 0; i < ids.size(); i++) {
			int id = ids.getInt(i);
			STARRED.remove(id);
			CHECKED.remove(id);
		}
	}

	public static void onEntityData(ClientboundSetEntityDataPacket packet) {
		if (!tracking() || !SkyblockLocation.inDungeon || SkyblockLocation.inBoss) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return;
		}
		Entity entity = client.level.getEntity(packet.id());
		if (entity == null) {
			return;
		}
		if (entity instanceof ArmorStand) {
			String name = packetName(packet);
			if (entity.getCustomName() != null) {
				String custom = entity.getCustomName().getString();
				if (name.isEmpty() || starredPlate(custom)) {
					name = custom;
				}
			}
			if (starredPlate(name)) {
				bindStand(entity, name);
			}
			return;
		}
		if (entity instanceof Player player && miniboss(player)) {
			STARRED.add(player.getId());
		}
	}

	public static boolean glowing(Entity entity) {
		return StrayConfig.get().starVisuals.glowEnabled && marked(entity);
	}

	public static boolean marked(Entity entity) {
		if (entity == null || !tracking() || !SkyblockLocation.inDungeon || SkyblockLocation.inBoss) {
			return false;
		}
		if (STARRED.contains(entity.getId())) {
			return true;
		}
		return extraColor(entity);
	}

	private static boolean extraColor(Entity entity) {
		StrayConfig config = StrayConfig.get();
		if (entity instanceof Bat bat) {
			return config.starMobBats && !bat.isInvisible() && !bat.isPassenger();
		}
		if (entity instanceof EnderMan enderman) {
			Component name = enderman.getCustomName();
			return config.starMobFels && name != null && "Dinnerbone".equals(name.getString());
		}
		return false;
	}

	public static boolean tracking() {
		StrayConfig config = StrayConfig.get();
		return config.starVisuals.anyEnabled() || config.playerFillStarMobs;
	}

	private static boolean starredPlate(String name) {
		if (name == null || !name.contains(STAR)) {
			return false;
		}
		if (name.endsWith(HEART) || name.endsWith("§c" + HEART)) {
			return true;
		}
		String plain = name.replaceAll("§.", "");
		return plain.contains(HEART) || plain.contains("♥");
	}

	private static void bindStand(Entity stand, String name) {
		if (!CHECKED.add(stand.getId())) {
			return;
		}
		String plain = name.replaceAll("§.", "").toUpperCase();
		int offset = plain.contains("WITHERMANCER") ? 3 : 1;
		int id = stand.getId() - offset;
		Entity mob = stand.level().getEntity(id);
		if (mob != null && !(mob instanceof ArmorStand) && STARRED.add(id)) {
			return;
		}
		AABB box = stand.getBoundingBox().move(0.0, -1.0, 0.0);
		List<Entity> nearby = stand.level().getEntities(
			stand,
			box,
			other -> !(other instanceof ArmorStand) && !(other instanceof ExperienceOrb)
		);
		Minecraft client = Minecraft.getInstance();
		Player self = client.player;
		for (Entity other : nearby) {
			if (STARRED.contains(other.getId()) || !realMob(other, self)) {
				continue;
			}
			STARRED.add(other.getId());
			return;
		}
	}

	private static boolean realMob(Entity entity, Player self) {
		if (entity instanceof Player player) {
			return !player.isInvisible()
				&& player.getUUID().version() == 2
				&& player != self;
		}
		if (entity instanceof WitherBoss || entity instanceof AbstractArrow) {
			return false;
		}
		return true;
	}

	private static boolean miniboss(Player player) {
		Minecraft client = Minecraft.getInstance();
		ClientPacketListener connection = client.getConnection();
		if (connection == null) {
			return false;
		}
		UUID uuid = player.getUUID();
		PlayerInfo info = connection.getPlayerInfo(uuid);
		if (info == null) {
			return false;
		}
		String name = info.getProfile().name();
		return name != null && MINIBOSSES.contains(name);
	}

	private static String packetName(ClientboundSetEntityDataPacket packet) {
		StringBuilder out = new StringBuilder();
		for (SynchedEntityData.DataValue<?> value : packet.packedItems()) {
			append(out, value.value());
		}
		return out.toString();
	}

	private static void append(StringBuilder out, Object value) {
		if (value instanceof Component component) {
			out.append(component.getString());
			return;
		}
		if (value instanceof Optional<?> optional && optional.isPresent()) {
			append(out, optional.get());
		}
	}
}
