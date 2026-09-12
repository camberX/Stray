package dev.stray.client.config;

import dev.stray.client.render.MobGlowRenderer;
import dev.stray.client.render.NametagRenderer;
import dev.stray.client.render.StarMobEsp;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

public enum EntityKind {
	PLAYER,
	MOB,
	STAR;

	public static EntityKind of(Entity entity) {
		if (entity == null) {
			return MOB;
		}
		if (StarMobEsp.marked(entity)) {
			return STAR;
		}
		if (entity instanceof Player && NametagRenderer.realAccount(entity)) {
			return PLAYER;
		}
		return MOB;
	}

	public static boolean overlay(Entity entity) {
		if (!(entity instanceof LivingEntity) || entity instanceof ArmorStand) {
			return false;
		}
		if (of(entity) != MOB) {
			return true;
		}
		return MobGlowRenderer.catalogOrNametag(entity);
	}
}
