package dev.stray.client.skill;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

public enum SkillKind {
	FARMING("Farming", Items.GOLDEN_HOE, SkillXp.MAIN),
	MINING("Mining", Items.IRON_PICKAXE, SkillXp.MAIN),
	COMBAT("Combat", Items.DIAMOND_SWORD, SkillXp.MAIN),
	FORAGING("Foraging", Items.GOLDEN_AXE, SkillXp.MAIN),
	FISHING("Fishing", Items.FISHING_ROD, SkillXp.MAIN),
	ENCHANTING("Enchanting", Items.ENCHANTED_BOOK, SkillXp.MAIN),
	ALCHEMY("Alchemy", Items.BREWING_STAND, SkillXp.MAIN),
	TAMING("Taming", Items.BONE, SkillXp.MAIN),
	CARPENTRY("Carpentry", Items.CRAFTING_TABLE, SkillXp.MAIN),
	RUNECRAFTING("Runecrafting", Items.MAGMA_CREAM, SkillXp.RUNECRAFTING),
	SOCIAL("Social", Items.EMERALD, SkillXp.SOCIAL),
	CATACOMBS("Catacombs", Items.WITHER_SKELETON_SKULL, SkillXp.DUNGEONEERING);

	public final String label;
	private final Item icon;
	final long[] toReach;

	SkillKind(String label, Item icon, long[] toReach) {
		this.label = label;
		this.icon = icon;
		this.toReach = toReach;
	}

	public ItemStack icon() {
		return new ItemStack(icon);
	}

	public int maxLevel() {
		return toReach.length - 1;
	}

	public static SkillKind parse(String name) {
		if (name == null || name.isEmpty()) {
			return null;
		}
		String key = name.trim().toLowerCase(Locale.ROOT);
		if (key.equals("dungeoneering") || key.equals("dungeon") || key.equals("catacombs")) {
			return CATACOMBS;
		}
		for (SkillKind kind : values()) {
			if (kind.label.equalsIgnoreCase(key)) {
				return kind;
			}
		}
		return null;
	}
}
