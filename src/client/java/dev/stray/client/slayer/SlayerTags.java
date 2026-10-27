package dev.stray.client.slayer;

import dev.stray.client.util.Text;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Hypixel's floating name tags (armor stands or text displays) matched to the mob they belong
 * to, rebuilt every couple of ticks. A tag belongs to the mob it rides, else the mob spawned
 * right before it (Hypixel spawns the mob, then its tags), else the closest mob right below it.
 * The mob's own custom name counts as one of its lines too.
 */
public final class SlayerTags {
	/** One floating tag: the entity and its text lines (colour codes removed). */
	public record Tag(Entity entity, List<String> lines, LivingEntity owner) {
		public boolean has(String text) {
			for (String line : lines) {
				if (line.contains(text)) {
					return true;
				}
			}
			return false;
		}

		public String first() {
			return lines.isEmpty() ? "" : lines.get(0);
		}
	}

	private static List<Tag> tags = List.of();
	private static Int2ObjectOpenHashMap<List<String>> byOwner = new Int2ObjectOpenHashMap<>();
	private static int tick;

	private SlayerTags() {
	}

	public static List<Tag> all() {
		return tags;
	}

	/** Every tag line of a mob (its own custom name included), or empty. */
	public static List<String> of(Entity mob) {
		if (mob == null) {
			return List.of();
		}
		List<String> lines = byOwner.get(mob.getId());
		return lines == null ? List.of() : lines;
	}

	public static boolean has(Entity mob, String text) {
		for (String line : of(mob)) {
			if (line.contains(text)) {
				return true;
			}
		}
		return false;
	}

	public static void clear() {
		tags = List.of();
		byOwner = new Int2ObjectOpenHashMap<>();
	}

	public static void tick(Minecraft client) {
		if (client.player == null || client.level == null) {
			clear();
			return;
		}
		if (++tick % 2 != 0) {
			return;
		}
		List<Tag> next = new ArrayList<>();
		Int2ObjectOpenHashMap<List<String>> owners = new Int2ObjectOpenHashMap<>();
		AABB around = client.player.getBoundingBox().inflate(48, 32, 48);
		for (Entity entity : client.level.getEntities(client.player, around, e -> true)) {
			if (isTag(entity)) {
				List<String> lines = text(entity);
				if (lines.isEmpty()) {
					continue;
				}
				LivingEntity owner = ownerOf(client, entity);
				next.add(new Tag(entity, lines, owner));
				if (owner != null) {
					owners.computeIfAbsent(owner.getId(), k -> new ArrayList<>()).addAll(lines);
				}
			} else if (entity instanceof LivingEntity living && entity.hasCustomName()) {
				String own = clean(entity.getCustomName());
				if (!own.isEmpty()) {
					owners.computeIfAbsent(living.getId(), k -> new ArrayList<>()).add(0, own);
				}
			}
		}
		tags = next;
		byOwner = owners;
	}

	public static boolean isTag(Entity entity) {
		return entity instanceof ArmorStand || entity instanceof Display.TextDisplay;
	}

	/** Text lines of a tag entity. */
	public static List<String> text(Entity entity) {
		Component component = null;
		if (entity instanceof Display.TextDisplay display) {
			component = display.getText();
		}
		if ((component == null || component.getString().isBlank()) && entity.hasCustomName()) {
			component = entity.getCustomName();
		}
		if (component == null) {
			return List.of();
		}
		List<String> out = new ArrayList<>();
		for (String line : component.getString().split("\n")) {
			String c = clean(line);
			if (!c.isEmpty()) {
				out.add(c);
			}
		}
		return out;
	}

	public static String clean(Component component) {
		return component == null ? "" : clean(component.getString());
	}

	public static String clean(String raw) {
		return Text.collapse(Text.strip(raw).replace(' ', ' ').replaceAll("[\\p{Cf}\\p{Co}]", "")).trim();
	}

	private static boolean realMob(Entity entity, Entity self) {
		return entity instanceof LivingEntity && !isTag(entity) && entity != self && entity.isAlive();
	}

	private static LivingEntity ownerOf(Minecraft client, Entity tag) {
		Entity vehicle = tag.getVehicle();
		while (vehicle != null) {
			if (realMob(vehicle, client.player)) {
				return (LivingEntity) vehicle;
			}
			vehicle = vehicle.getVehicle();
		}
		// Hypixel spawns the mob right before its tags.
		for (int back = 1; back <= 4; back++) {
			Entity before = client.level.getEntity(tag.getId() - back);
			if (before == null) {
				continue;
			}
			if (realMob(before, client.player)) {
				double dx = before.getX() - tag.getX();
				double dz = before.getZ() - tag.getZ();
				double dy = tag.getY() - before.getY();
				if (dx * dx + dz * dz < 2.25 && dy > -1 && dy < 6) {
					return (LivingEntity) before;
				}
				break;
			}
			if (!isTag(before)) {
				break;
			}
		}
		LivingEntity best = null;
		double bestScore = Double.MAX_VALUE;
		AABB box = new AABB(tag.getX() - 1, tag.getY() - 5, tag.getZ() - 1, tag.getX() + 1, tag.getY() + 0.5, tag.getZ() + 1);
		for (Entity other : client.level.getEntities(tag, box, e -> realMob(e, client.player))) {
			double dx = other.getX() - tag.getX();
			double dz = other.getZ() - tag.getZ();
			double xz = dx * dx + dz * dz;
			double top = other.getY() + other.getBbHeight();
			double dy = Math.abs(tag.getY() - top);
			double score = xz * 4 + dy;
			if (score < bestScore) {
				bestScore = score;
				best = (LivingEntity) other;
			}
		}
		return best;
	}
}
