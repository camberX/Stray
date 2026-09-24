package dev.stray.client.farming;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.item.ItemIds;
import dev.stray.client.render.HudLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * Top-down block window while a farming tool is held. Columns start at the
 * player's feet and look down, so blocks above the player are left out.
 * The height slider is how far the window looks, in blocks from the player.
 */
public final class TopDownView {
	public static final float MIN_HEIGHT = 4f;
	public static final float MAX_HEIGHT = 24f;
	public static final float WINDOW = 132f;

	private static int sampleTick = Integer.MIN_VALUE;
	private static int sampleRadius;
	private static int[] sample = new int[0];

	private TopDownView() {
	}

	public static boolean showing() {
		if (!StrayConfig.get().topDownView) {
			return false;
		}
		if (HudLayout.editorOpen()) {
			return true;
		}
		Minecraft client = Minecraft.getInstance();
		return client.player != null && farmingTool(client.player.getMainHandItem());
	}

	public static float height() {
		return StrayConfig.clamp(StrayConfig.get().topDownHeight, MIN_HEIGHT, MAX_HEIGHT);
	}

	public static int radius() {
		return Math.round(height());
	}

	public static int[] colors() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			return new int[0];
		}
		int radius = radius();
		int tick = client.player.tickCount;
		if (tick == sampleTick && radius == sampleRadius && sample.length == span(radius) * span(radius)) {
			return sample;
		}
		sampleTick = tick;
		sampleRadius = radius;
		sample = sample(client.level, client.player.position(), client.player.getBlockY(), radius);
		return sample;
	}

	public static boolean farmingTool(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		if (stack.getItem() instanceof HoeItem) {
			return true;
		}
		String id = ItemIds.skyblockId(stack);
		if (id == null || id.isBlank()) {
			return false;
		}
		String key = id.toUpperCase(Locale.ROOT);
		return key.contains("HOE")
			|| key.contains("DICER")
			|| key.contains("CHOPPER")
			|| key.contains("FUNGI_CUTTER")
			|| key.contains("CACTUS_KNIFE");
	}

	private static int span(int radius) {
		return radius * 2 + 1;
	}

	private static int[] sample(Level level, Vec3 pos, int feet, int radius) {
		int span = span(radius);
		int[] colors = new int[span * span];
		int originX = BlockPos.containing(pos.x, feet, pos.z).getX();
		int originZ = BlockPos.containing(pos.x, feet, pos.z).getZ();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int index = 0;
		for (int dz = -radius; dz <= radius; dz++) {
			for (int dx = -radius; dx <= radius; dx++) {
				colors[index++] = column(level, cursor, originX + dx, feet, originZ + dz);
			}
		}
		return colors;
	}

	private static int column(Level level, BlockPos.MutableBlockPos cursor, int x, int feet, int z) {
		int bottom = Math.max(level.getMinY(), feet - 12);
		for (int y = feet; y >= bottom; y--) {
			cursor.set(x, y, z);
			BlockState state = level.getBlockState(cursor);
			if (state.isAir()) {
				continue;
			}
			return color(state);
		}
		return 0x1A1E18;
	}

	private static int color(BlockState state) {
		String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		if (name.contains("wheat") || name.contains("hay")) {
			return 0xE2C14A;
		}
		if (name.contains("carrot")) {
			return 0xE07A2F;
		}
		if (name.contains("potato")) {
			return 0xC4A15A;
		}
		if (name.contains("melon")) {
			return 0x5E9A3A;
		}
		if (name.contains("pumpkin")) {
			return 0xE08A2A;
		}
		if (name.contains("cactus") || name.contains("sugar_cane") || name.contains("nether_wart")) {
			return 0x3E8F45;
		}
		if (name.contains("cocoa")) {
			return 0x8A5A32;
		}
		if (name.contains("mushroom")) {
			return 0xC45A4A;
		}
		if (name.contains("farmland") || name.contains("dirt") || name.contains("podzol")) {
			return 0x6B4A2A;
		}
		if (name.contains("grass") || name.contains("moss") || name.contains("leaves")) {
			return 0x4C8A3A;
		}
		if (name.contains("water")) {
			return 0x3A6EA5;
		}
		if (name.contains("sand")) {
			return 0xD6C48A;
		}
		if (name.contains("log") || name.contains("wood")) {
			return 0x6E5030;
		}
		int hash = name.hashCode();
		int red = 70 + (hash & 55);
		int green = 78 + ((hash >> 5) & 55);
		int blue = 58 + ((hash >> 10) & 40);
		return (red << 16) | (green << 8) | blue;
	}
}
