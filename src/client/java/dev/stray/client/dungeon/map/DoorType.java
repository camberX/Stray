package dev.stray.client.dungeon.map;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public enum DoorType {
	BLOOD,
	WITHER,
	NORMAL,
	ENTRANCE;

	/** The block that stands in the doorway at y 69 for this kind of door. */
	public Block source() {
		return switch (this) {
			case BLOOD -> Blocks.DYED_TERRACOTTA.red();
			case WITHER -> Blocks.COAL_BLOCK;
			case NORMAL -> Blocks.AIR;
			case ENTRANCE -> Blocks.INFESTED_CHISELED_STONE_BRICKS;
		};
	}

	public static DoorType fromBlock(Block block) {
		for (DoorType type : values()) {
			if (type.source() == block) {
				return type;
			}
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
