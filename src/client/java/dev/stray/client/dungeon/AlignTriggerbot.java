package dev.stray.client.dungeon;

import dev.stray.client.combat.OdinClicks;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.location.SkyblockLocation;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Align triggerbot for the F7 arrow align device (the 5x5 wall of item frames with
 * arrows). While you look at an arrow that is not yet pointing the right way, it
 * right clicks it, one click per delay, until it is; you still aim yourself.
 *
 * <p>Clicks go through the normal use key (the same path as pressing right mouse), so
 * the game sends exactly what a real click sends. Solving follows NoammAddons'
 * ArrowAlign (CC0): read each frame's rotation, find the layout that matches the
 * arrows on the wall, and count the clicks each frame still needs.
 */
public final class AlignTriggerbot {
	private static final BlockPos DEVICE = new BlockPos(-2, 120, 75);
	/** The device's bottom corner: the real one, or a test copy built with /stray aligntest. */
	private static BlockPos CORNER = DEVICE;
	private static AABB GRID = grid(DEVICE);
	private static boolean testMode;
	/** After a click the frame's own rotation lags behind; trust our count this long. */
	private static final long TRUST_MS = 1000L;

	private static final int[] ROTATIONS = new int[25];
	private static final long[] CLICKED_AT = new long[25];
	private static final int[] REMAINING = new int[25];
	private static int[] solution;
	private static long lastClickMs;
	private static long nextGapMs;

	private AlignTriggerbot() {
	}

	public static void init() {
		reset();
		// Manual clicks and our own both pass here, so the count stays right either way.
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() && entity instanceof ItemFrame frame) {
				onClicked(frame);
			}
			return InteractionResult.PASS;
		});
	}

	private static boolean active(Minecraft client) {
		StrayConfig config = StrayConfig.get();
		return config.alignTriggerbot
			&& client.player != null
			&& client.level != null
			&& (testMode || SkyblockLocation.inDungeon && SkyblockLocation.inBoss && SkyblockLocation.dungeonFloor == 7)
			&& client.player.distanceToSqr(Vec3.atLowerCornerOf(CORNER)) <= 200.0;
	}

	public static void tick(Minecraft client) {
		if (testMode && client.getSingleplayerServer() == null) {
			stopTest();
		}
		if (!active(client)) {
			if (solution != null || ROTATIONS[0] != -1) {
				reset();
			}
			return;
		}
		read(client);
		solve();
		if (client.gui.screen() != null || client.player.isSpectator()) {
			return;
		}
		HitResult hit = client.hitResult;
		if (!(hit instanceof EntityHitResult entityHit) || !(entityHit.getEntity() instanceof ItemFrame frame)) {
			return;
		}
		int index = index(frame);
		if (index < 0 || REMAINING[index] <= 0) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastClickMs < nextGapMs) {
			return;
		}
		lastClickMs = now;
		nextGapMs = gap();
		OdinClicks.rightClick();
	}

	/** The delay between clicks, with a little random spread when that is on. */
	private static long gap() {
		StrayConfig config = StrayConfig.get();
		long delay = Math.max(50, config.alignTriggerbotDelay);
		if (!config.alignTriggerbotRandom) {
			return delay;
		}
		long spread = Math.max(10, delay / 4);
		return delay + ThreadLocalRandom.current().nextLong(-spread / 2, spread + 1);
	}

	private static void read(Minecraft client) {
		long now = System.currentTimeMillis();
		List<ItemFrame> frames = client.level.getEntitiesOfClass(ItemFrame.class, GRID, f -> f.getItem().is(Items.ARROW));
		for (ItemFrame frame : frames) {
			int index = index(frame);
			if (index >= 0 && now - CLICKED_AT[index] > TRUST_MS) {
				ROTATIONS[index] = frame.getRotation();
			}
		}
	}

	private static void solve() {
		solution = null;
		java.util.Arrays.fill(REMAINING, 0);
		for (int[] candidate : SOLUTIONS) {
			boolean match = true;
			for (int i = 0; i < 25; i++) {
				int current = ROTATIONS[i];
				int target = candidate[i];
				if ((target == -1 || current == -1) && target != current) {
					match = false;
					break;
				}
			}
			if (!match) {
				continue;
			}
			solution = candidate;
			for (int i = 0; i < 25; i++) {
				if (ROTATIONS[i] != -1) {
					REMAINING[i] = clicks(ROTATIONS[i], candidate[i]);
				}
			}
			return;
		}
	}

	private static void onClicked(ItemFrame frame) {
		if (!frame.getItem().is(Items.ARROW)) {
			return;
		}
		int index = index(frame);
		if (index < 0 || ROTATIONS[index] == -1) {
			return;
		}
		CLICKED_AT[index] = System.currentTimeMillis();
		ROTATIONS[index] = (ROTATIONS[index] + 1) % 8;
		if (solution != null) {
			REMAINING[index] = clicks(ROTATIONS[index], solution[index]);
		}
	}

	/** Frame position to 0..24: up the wall first, then along it. -1 when not on the wall. */
	private static int index(ItemFrame frame) {
		BlockPos pos = frame.blockPosition();
		if (pos.getX() != CORNER.getX()) {
			return -1;
		}
		int dy = pos.getY() - CORNER.getY();
		int dz = pos.getZ() - CORNER.getZ();
		if (dy < 0 || dy > 4 || dz < 0 || dz > 4) {
			return -1;
		}
		return dy + dz * 5;
	}

	private static int clicks(int current, int target) {
		if (target == -1) {
			return 0;
		}
		return (8 - current + target) % 8;
	}

	private static AABB grid(BlockPos corner) {
		return new AABB(corner.getX(), corner.getY(), corner.getZ(), corner.getX() + 1, corner.getY() + 5, corner.getZ() + 5);
	}

	public static boolean testMode() {
		return testMode;
	}

	/** Leaves test mode and points the triggerbot back at the real device. */
	public static void stopTest() {
		testMode = false;
		CORNER = DEVICE;
		GRID = grid(DEVICE);
		reset();
	}

	/**
	 * Singleplayer test: builds a copy of the device in front of you (a random layout,
	 * arrows turned randomly) with ordinary commands, and points the triggerbot at it
	 * with the dungeon checks skipped. Needs cheats on in the world.
	 */
	public static boolean buildTest(Minecraft client) {
		if (client.player == null || client.getSingleplayerServer() == null) {
			return false;
		}
		BlockPos feet = client.player.blockPosition();
		// Same shape as the real device: frames on a wall at x, facing +x, the player on a step 1.5 blocks east, looking west.
		BlockPos corner = new BlockPos(feet.getX() - 2, feet.getY(), feet.getZ() - 2);
		int x = corner.getX();
		int y = corner.getY();
		int z = corner.getZ();
		java.util.List<String> commands = new java.util.ArrayList<>();
		commands.add(String.format("kill @e[type=item_frame,x=%d,y=%d,z=%d,dx=1,dy=5,dz=5]", x, y, z));
		commands.add(String.format("fill %d %d %d %d %d %d stone", x - 1, y - 1, z - 1, x - 1, y + 5, z + 5));
		commands.add(String.format("fill %d %d %d %d %d %d air", x, y - 1, z - 1, x + 5, y + 5, z + 5));
		commands.add(String.format("fill %d %d %d %d %d %d stone", x + 1, y, z - 1, x + 5, y, z + 5));
		ThreadLocalRandom random = ThreadLocalRandom.current();
		int[] layout = SOLUTIONS[random.nextInt(SOLUTIONS.length)];
		for (int i = 0; i < 25; i++) {
			if (layout[i] == -1) {
				continue;
			}
			commands.add(String.format(
				"summon item_frame %d %d %d {Facing:5b,Invulnerable:1b,Item:{id:\"minecraft:arrow\",count:1},ItemRotation:%db}",
				x, y + i % 5, z + i / 5, random.nextInt(8)
			));
		}
		// On a step level with the middle row, so most frames are within survival reach (3 blocks).
		commands.add(String.format(java.util.Locale.ROOT, "tp @s %.1f %d %.1f 90 0", x + 1.5, y + 1, z + 2.5));
		for (String command : commands) {
			client.player.connection.sendCommand(command);
		}
		testMode = true;
		CORNER = corner;
		GRID = grid(corner);
		reset();
		return true;
	}

	private static void reset() {
		java.util.Arrays.fill(ROTATIONS, -1);
		java.util.Arrays.fill(CLICKED_AT, 0L);
		java.util.Arrays.fill(REMAINING, 0);
		solution = null;
	}

	/** Every known arrow layout, from NoammAddons; -1 = no arrow in that frame. */
	private static final int[][] SOLUTIONS = {
		{7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, -1, -1, -1, 7, 1},
		{-1, -1, 7, 7, 5, -1, 7, 1, -1, 5, -1, -1, -1, -1, -1, -1, 7, 5, -1, 1, -1, -1, 7, 7, 1},
		{7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, -1, 7, 5, -1, -1, -1, -1, 5, -1, -1, -1, 3, 3},
		{5, 3, 3, 3, -1, 5, -1, -1, -1, -1, 7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, -1},
		{5, 3, 3, 3, 3, 5, -1, -1, -1, 1, 7, 7, -1, -1, 1, -1, -1, -1, -1, 1, -1, 7, 7, 7, 1},
		{7, 7, 7, 7, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, -1, 7, 7, 7, 1},
		{-1, -1, -1, -1, -1, 1, -1, 1, -1, 1, 1, -1, 1, -1, 1, 1, -1, 1, -1, 1, -1, -1, -1, -1, -1},
		{-1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, 7, 7, 7, 7, 1, -1, -1, -1, -1, -1},
		{-1, -1, -1, -1, -1, -1, 1, -1, 1, -1, 7, 1, 7, 1, 3, 1, -1, 1, -1, 1, -1, -1, -1, -1, -1}
	};
}
