package dev.stray.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 3D player wearing a loadout plus a matching Skyblock pet, when one exists.
 */
public final class LoadoutPreview {
	private static final Map<String, EntityType<?>> PETS = pets();
	private static Entity petEntity;
	private static EntityType<?> petType;

	private LoadoutPreview() {
	}

	public static PlayerPreview.Drawn player(
		GuiGraphicsExtractor graphics,
		float x,
		float y,
		float w,
		float h,
		float yaw,
		float pitch,
		PlayerPreview.View view,
		ItemStack helmet,
		ItemStack chest,
		ItemStack legs,
		ItemStack boots
	) {
		return PlayerPreview.drawEquipped(
			graphics,
			x,
			y,
			w,
			h,
			yaw,
			pitch,
			view,
			new PlayerPreview.Gear(helmet, chest, legs, boots)
		);
	}

	public static PlayerPreview.Drawn pet(
		GuiGraphicsExtractor graphics,
		float x,
		float y,
		float w,
		float h,
		float yaw,
		float pitch,
		PlayerPreview.View view,
		String type
	) {
		Entity entity = petEntity(type);
		if (entity == null || view == null) {
			return null;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			entity.setPos(client.player.getX(), client.player.getY(), client.player.getZ());
		}
		entity.setYRot(180f + yaw);
		entity.setXRot(pitch);
		EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
		EntityRenderState state = dispatcher.extractEntity(entity, 1f);
		return PlayerPreview.drawEntity(graphics, state, x, y, w, h, yaw, pitch, view, 0.7f);
	}

	private static Entity petEntity(String type) {
		EntityType<?> mapped = PETS.get(canon(type));
		if (mapped == null) {
			clear();
			return null;
		}
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		if (level == null) {
			clear();
			return null;
		}
		if (petEntity == null || petType != mapped || petEntity.level() != level) {
			clear();
			try {
				petEntity = mapped.create(level, EntitySpawnReason.LOAD);
			} catch (RuntimeException ignored) {
				petEntity = null;
			}
			petType = mapped;
		}
		return petEntity;
	}

	public static void clear() {
		petEntity = null;
		petType = null;
	}

	private static String canon(String type) {
		if (type == null) {
			return "";
		}
		return type.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace("-", "_");
	}

	private static Map<String, EntityType<?>> pets() {
		Map<String, EntityType<?>> map = new HashMap<>();
		put(map, EntityTypes.ALLAY, "ALLAY", "SPIRIT");
		put(map, EntityTypes.ARMADILLO, "ARMADILLO");
		put(map, EntityTypes.BAT, "BAT");
		put(map, EntityTypes.BEE, "BEE");
		put(map, EntityTypes.BLAZE, "BLAZE", "PHOENIX", "BAL");
		put(map, EntityTypes.CAT, "BLACK_CAT", "CAT");
		put(map, EntityTypes.CAVE_SPIDER, "CAVE_SPIDER", "TARANTULA");
		put(map, EntityTypes.CHICKEN, "CHICKEN");
		put(map, EntityTypes.COW, "COW");
		put(map, EntityTypes.CREEPER, "CREEPER");
		put(map, EntityTypes.DOLPHIN, "DOLPHIN", "BLUE_WHALE");
		put(map, EntityTypes.ENDERMAN, "ENDERMAN");
		put(map, EntityTypes.ENDERMITE, "ENDERMITE", "SCATHA", "MITE");
		put(map, EntityTypes.ENDER_DRAGON, "ENDER_DRAGON", "DRAGON", "GOLDEN_DRAGON");
		put(map, EntityTypes.FOX, "FOX");
		put(map, EntityTypes.FROG, "FROG");
		put(map, EntityTypes.GHAST, "GHAST");
		put(map, EntityTypes.GLOW_SQUID, "GLOW_SQUID", "JELLYFISH");
		put(map, EntityTypes.GOAT, "GOAT", "REINDEER");
		put(map, EntityTypes.GUARDIAN, "GUARDIAN", "FLYING_FISH");
		put(map, EntityTypes.HOGLIN, "HOGLIN", "KUUDRA");
		put(map, EntityTypes.HORSE, "HORSE", "SKELETON_HORSE");
		put(map, EntityTypes.IRON_GOLEM, "GOLEM", "IRON_GOLEM", "MITHRIL_GOLEM");
		put(map, EntityTypes.MAGMA_CUBE, "MAGMA_CUBE");
		put(map, EntityTypes.MOOSHROOM, "MOOSHROOM", "MUSHROOM_COW");
		put(map, EntityTypes.OCELOT, "OCELOT", "TIGER", "LION");
		put(map, EntityTypes.PANDA, "PANDA", "MONKEY");
		put(map, EntityTypes.PARROT, "PARROT", "GRIFFIN");
		put(map, EntityTypes.PHANTOM, "PHANTOM");
		put(map, EntityTypes.PIG, "PIG");
		put(map, EntityTypes.ZOMBIFIED_PIGLIN, "PIGMAN", "ZOMBIE_PIGMAN", "ZOMBIFIED_PIGLIN");
		put(map, EntityTypes.RABBIT, "RABBIT");
		put(map, EntityTypes.RAVAGER, "ELEPHANT", "RAVAGER");
		put(map, EntityTypes.SHEEP, "SHEEP");
		put(map, EntityTypes.SILVERFISH, "SILVERFISH", "ROCK", "SNAIL");
		put(map, EntityTypes.SKELETON, "SKELETON");
		put(map, EntityTypes.SLIME, "SLIME");
		put(map, EntityTypes.SNOW_GOLEM, "SNOWMAN", "SNOW_GOLEM", "BABY_YETI", "YETI");
		put(map, EntityTypes.SPIDER, "SPIDER");
		put(map, EntityTypes.SQUID, "SQUID");
		put(map, EntityTypes.TURTLE, "TURTLE");
		put(map, EntityTypes.VEX, "VEX");
		put(map, EntityTypes.VILLAGER, "JERRY", "VILLAGER");
		put(map, EntityTypes.WITCH, "WITCH");
		put(map, EntityTypes.WITHER_SKELETON, "WITHER_SKELETON");
		put(map, EntityTypes.WOLF, "WOLF", "HOUND");
		put(map, EntityTypes.ZOMBIE, "ZOMBIE", "GHOUL");
		put(map, EntityTypes.CAMEL, "GIRAFFE", "CAMEL");
		put(map, EntityTypes.ELDER_GUARDIAN, "MEGALODON", "ELDER_GUARDIAN");
		return map;
	}

	private static void put(Map<String, EntityType<?>> map, EntityType<?> type, String... keys) {
		for (String key : keys) {
			map.put(key, type);
		}
	}
}
