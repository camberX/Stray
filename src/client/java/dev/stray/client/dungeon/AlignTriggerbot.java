package dev.stray.client.dungeon;

import dev.stray.client.combat.OdinClicks;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.location.SkyblockLocation;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
	/** The real device: frames on x = -2, facing east, bottom corner at y 120, z 75. */
	private static final BlockPos DEVICE = new BlockPos(-2, 120, 75);
	private static final AABB DEVICE_BOX = new AABB(-2, 120, 75, -1, 125, 80);

	/**
	 * Where the wall is. "u" runs along the wall (to your left as you face it), "v" is the
	 * height. On the real device that is the fixed spot above; in test mode it is found
	 * from the arrow frames around you, so a copy can stand anywhere and face any way.
	 */
	private static Direction facing = Direction.EAST;
	private static int plane = DEVICE.getX();
	private static int cornerU = DEVICE.getZ();
	private static int cornerV = DEVICE.getY();
	private static boolean gridKnown = true;
	private static long nextSearchMs;
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
			&& (testMode
				|| SkyblockLocation.inDungeon && SkyblockLocation.inBoss && SkyblockLocation.dungeonFloor == 7
					&& client.player.distanceToSqr(Vec3.atLowerCornerOf(DEVICE)) <= 200.0);
	}

	public static void tick(Minecraft client) {
		if (testMode && client.level == null) {
			stopTest();
		}
		if (!active(client)) {
			if (solution != null || ROTATIONS[0] != -1) {
				reset();
			}
			return;
		}
		if (testMode && !find(client)) {
			return;
		}
		read(client);
		solve();
		if (testMode && solution == null) {
			gridKnown = false;
		}
	}

	/**
	 * Clicks, right before the game handles keys in a tick: the crosshair target was just
	 * picked for this tick, so the click lands on exactly the frame we checked (doing it
	 * at the end of the previous tick let a moving crosshair carry the click onto the
	 * wall behind). Only an item frame with an arrow on the device is ever clicked.
	 */
	public static void beforeKeybinds(Minecraft client) {
		if (!active(client) || testMode && !gridKnown) {
			return;
		}
		if (client.gui.screen() != null || client.player.isSpectator()) {
			return;
		}
		HitResult hit = client.hitResult;
		if (!(hit instanceof EntityHitResult entityHit) || !(entityHit.getEntity() instanceof ItemFrame frame)
			|| !frame.getItem().is(Items.ARROW)) {
			return;
		}
		int index = index(frame);
		if (index < 0) {
			return;
		}
		// Sneaking leaves the last click to you (stops one short of the right way).
		int want = REMAINING[index] - (client.player.isShiftKeyDown() ? 1 : 0);
		if (want <= 0) {
			return;
		}
		long now = System.currentTimeMillis();
		long elapsed = now - lastClickMs;
		if (elapsed < nextGapMs) {
			return;
		}
		// This runs once per tick (50 ms). With a shorter delay, the clicks that fell due
		// since the last one go in together; the game uses each queued click in order,
		// the same as fast manual clicking. Never more than the frame still needs.
		int due = elapsed < 200L ? (int) Math.max(1L, elapsed / Math.max(1L, nextGapMs)) : 1;
		due = Math.min(due, want);
		lastClickMs = now;
		nextGapMs = gap();
		for (int i = 0; i < due; i++) {
			OdinClicks.rightClick();
		}
	}

	/** The delay between clicks, with a little random spread when that is on. */
	private static long gap() {
		StrayConfig config = StrayConfig.get();
		long delay = Math.max(10, config.alignTriggerbotDelay);
		if (!config.alignTriggerbotRandom) {
			return delay;
		}
		long spread = Math.max(2, delay / 4);
		return delay + ThreadLocalRandom.current().nextLong(-spread / 2, spread + 1);
	}

	private static void read(Minecraft client) {
		long now = System.currentTimeMillis();
		List<ItemFrame> frames = client.level.getEntitiesOfClass(ItemFrame.class, testMode ? client.player.getBoundingBox().inflate(SEARCH) : DEVICE_BOX, f -> f.getItem().is(Items.ARROW));
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
		int[] candidate = matching(ROTATIONS);
		if (candidate == null) {
			return;
		}
		solution = candidate;
		for (int i = 0; i < 25; i++) {
			if (ROTATIONS[i] != -1) {
				REMAINING[i] = clicks(ROTATIONS[i], candidate[i]);
			}
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
		if (!gridKnown || frame.getDirection() != facing) {
			return -1;
		}
		BlockPos pos = frame.blockPosition();
		if (planeOf(pos, facing) != plane) {
			return -1;
		}
		int dy = pos.getY() - cornerV;
		int dz = alongOf(pos, facing) - cornerU;
		if (dy < 0 || dy > 4 || dz < 0 || dz > 4) {
			return -1;
		}
		return dy + dz * 5;
	}

	/** Position across the wall (which slice of the world the frames hang in). */
	private static int planeOf(BlockPos pos, Direction face) {
		return face.getAxis() == Direction.Axis.X ? pos.getX() : pos.getZ();
	}

	/** Position along the wall, growing to the left of someone facing it (south on the real device). */
	private static int alongOf(BlockPos pos, Direction face) {
		Direction along = face.getClockWise();
		return along.getStepX() * pos.getX() + along.getStepZ() * pos.getZ();
	}

	private static final double SEARCH = 10.0;

	/**
	 * Test mode: finds the arrow wall near you. Takes the frames that hang the same way on
	 * the same wall and tries every way a 5x5 grid can sit over them until one of the
	 * known layouts fits (layouts can leave whole rows empty, so the bounds alone are not
	 * enough). Looked for again at most every half second while nothing fits.
	 */
	private static boolean find(Minecraft client) {
		if (gridKnown) {
			return true;
		}
		long now = System.currentTimeMillis();
		if (now < nextSearchMs) {
			return false;
		}
		nextSearchMs = now + 500L;
		List<ItemFrame> frames = client.level.getEntitiesOfClass(ItemFrame.class, client.player.getBoundingBox().inflate(SEARCH), f -> f.getItem().is(Items.ARROW) && f.getDirection().getAxis().isHorizontal());
		if (frames.isEmpty()) {
			return false;
		}
		// The wall with the most arrows on it.
		java.util.Map<String, java.util.List<ItemFrame>> walls = new java.util.HashMap<>();
		for (ItemFrame frame : frames) {
			walls.computeIfAbsent(frame.getDirection() + ":" + planeOf(frame.blockPosition(), frame.getDirection()), k -> new java.util.ArrayList<>()).add(frame);
		}
		java.util.List<ItemFrame> wall = java.util.Collections.max(walls.values(), java.util.Comparator.comparingInt(java.util.List::size));
		Direction face = wall.get(0).getDirection();
		int minU = Integer.MAX_VALUE, maxU = Integer.MIN_VALUE, minV = Integer.MAX_VALUE, maxV = Integer.MIN_VALUE;
		for (ItemFrame frame : wall) {
			int u = alongOf(frame.blockPosition(), face);
			int v = frame.blockPosition().getY();
			minU = Math.min(minU, u);
			maxU = Math.max(maxU, u);
			minV = Math.min(minV, v);
			maxV = Math.max(maxV, v);
		}
		if (maxU - minU > 4 || maxV - minV > 4) {
			return false;
		}
		int[] rotations = new int[25];
		for (int u0 = maxU - 4; u0 <= minU; u0++) {
			for (int v0 = maxV - 4; v0 <= minV; v0++) {
				java.util.Arrays.fill(rotations, -1);
				for (ItemFrame frame : wall) {
					rotations[(frame.blockPosition().getY() - v0) + (alongOf(frame.blockPosition(), face) - u0) * 5] = frame.getRotation();
				}
				if (matching(rotations) != null) {
					facing = face;
					plane = planeOf(wall.get(0).blockPosition(), face);
					cornerU = u0;
					cornerV = v0;
					gridKnown = true;
					reset();
					return true;
				}
			}
		}
		return false;
	}

	/** The known layout whose arrows sit exactly where these do, or null. */
	private static int[] matching(int[] rotations) {
		for (int[] candidate : SOLUTIONS) {
			boolean match = true;
			for (int i = 0; i < 25 && match; i++) {
				match = (candidate[i] == -1) == (rotations[i] == -1);
			}
			if (match) {
				return candidate;
			}
		}
		return null;
	}

	private static int clicks(int current, int target) {
		if (target == -1) {
			return 0;
		}
		return (8 - current + target) % 8;
	}

	public static boolean testMode() {
		return testMode;
	}

	/** Test mode: skips the dungeon checks and solves whatever arrow wall is near you. */
	public static void startTest() {
		testMode = true;
		gridKnown = false;
		nextSearchMs = 0L;
		reset();
	}

	/** Leaves test mode and points the triggerbot back at the real device. */
	public static void stopTest() {
		testMode = false;
		facing = Direction.EAST;
		plane = DEVICE.getX();
		cornerU = DEVICE.getZ();
		cornerV = DEVICE.getY();
		gridKnown = true;
		reset();
	}

	/**
	 * Builds a copy of the device in front of you (a random layout, arrows turned
	 * randomly) with ordinary commands, so it needs cheats in singleplayer or op on a
	 * server. Then starts test mode, which finds the copy by itself.
	 */
	public static void buildTest(Minecraft client) {
		BlockPos feet = client.player.blockPosition();
		// Same shape as the real device: frames on a wall at x, facing +x, you on a step 1.5 blocks east looking west.
		int x = feet.getX() - 2;
		int y = feet.getY();
		int z = feet.getZ() - 2;
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
		commands.add(String.format(java.util.Locale.ROOT, "tp @s %.1f %d %.1f 90 0", x + 1.5, y + 1, z + 2.5));
		for (String command : commands) {
			client.player.connection.sendCommand(command);
		}
		startTest();
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
