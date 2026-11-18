package dev.stray.client.dungeon.map;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;

public enum DoorType {
	BLOOD, WITHER, NORMAL, ENTRANCE;

	public static DoorType fromBlock(Block block) {
		if (block == Blocks.DYED_TERRACOTTA.pick(DyeColor.RED)) {
			return BLOOD;
		}
		if (block == Blocks.COAL_BLOCK) {
			return WITHER;
		}
		if (block == Blocks.INFESTED_CHISELED_STONE_BRICKS) {
			return ENTRANCE;
		}
		return NORMAL;
	}

	public static DoorType fromMapColor(int color) {
		return switch (color) {
			case 18 -> BLOOD;
			case 30 -> ENTRANCE;
			case 119 -> WITHER;
			case 74, 82, 66, 62, 85, 63 -> NORMAL;
			default -> null;
		};
	}
}
