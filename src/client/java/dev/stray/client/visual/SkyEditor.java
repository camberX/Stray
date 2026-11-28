package dev.stray.client.visual;

import com.mojang.blaze3d.platform.InputConstants;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.dungeon.DungeonRooms;
import dev.stray.client.editor.WorldEditor;
import dev.stray.client.input.MouseButtons;
import dev.stray.client.movement.Freecam;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * Sky painter for Space Roof ({@code /stray debug dev roof edit}): fly with freecam and aim at
 * a ceiling. The sky layer goes at the height of what you aim at (scroll moves it up or down a
 * block) over the whole floor plan of the room you aim into; its outline shows before you
 * click. Left click places it, right click removes the layer under the crosshair, middle click
 * resets the height shift, Z undoes.
 *
 * <p>C switches to carving: two left clicks on blocks mark the corners of a box where no sky
 * goes (scroll stretches it up or down from the second corner), right click removes the carved
 * box you aim into.
 */
public final class SkyEditor {
	private static final double REACH = 96.0;
	private static final int MAX_SHIFT = 12;
	private static final int UNDO_MAX = 64;

	/** An undo step: a layer that was added, or layers that were removed. */
	private record Step(DungeonRooms.Room room, SpaceRoof.Sheet added, List<SpaceRoof.Sheet> removed, int[] carveAdded, int[] carveRemoved) {
		Step(DungeonRooms.Room room, SpaceRoof.Sheet added, List<SpaceRoof.Sheet> removed) {
			this(room, added, removed, null, null);
		}
	}

	/** Carving mode (C), its first corner and that corner's room, and what's drawn: the room's carved boxes and the one being marked. */
	private static boolean carving;
	private static BlockPos carveStart;
	private static DungeonRooms.Room carveRoom;
	private static List<int[]> shownCarves = List.of();
	private static int[] previewCarve;
	private static boolean carveKeyWas;

	private static boolean active;
	/** Freecam was already on when the painter started (then it stays on afterwards). */
	private static boolean hadFreecam;
	private static ClientLevel level;
	/** Blocks added to the aimed height. */
	private static int shift;
	private static final Deque<Step> UNDO = new ArrayDeque<>();
	private static boolean leftWas;
	private static boolean rightWas;
	private static boolean middleWas;
	private static boolean undoWas;
	private static int hudTicks;

	/** The previewed fill: its start, height and cells, and the edge cells drawn as its outline. */
	private static int previewX = Integer.MIN_VALUE;
	private static int previewZ;
	private static int previewY;
	private static Set<Long> preview = Set.of();
	private static List<Long> previewEdge = List.of();

	private SkyEditor() {
	}

	public static void init() {
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
			if (!active) {
				return;
			}
			GizmoStyle carved = GizmoStyle.strokeAndFill(carving ? 0xFFFF5A6E : 0x80FF5A6E, 1.5f, carving ? 0x24FF5A6E : 0x10FF5A6E);
			for (int[] b : shownCarves) {
				Gizmos.cuboid(new AABB(b[0] - 0.02, b[1] - 0.02, b[2] - 0.02, b[3] + 1.02, b[4] + 1.02, b[5] + 1.02), carved);
			}
			if (previewCarve != null) {
				int[] b = previewCarve;
				Gizmos.cuboid(new AABB(b[0] - 0.03, b[1] - 0.03, b[2] - 0.03, b[3] + 1.03, b[4] + 1.03, b[5] + 1.03),
					GizmoStyle.strokeAndFill(0xFFFF8A9A, 2f, 0x40FF5A6E));
			}
			if (carving || previewEdge.isEmpty()) {
				return;
			}
			GizmoStyle style = GizmoStyle.strokeAndFill(0xFFB9A8FF, 1.5f, 0x30B9A8FF);
			double y = previewY;
			for (long cell : previewEdge) {
				int x = SpaceRoof.x(cell);
				int z = SpaceRoof.z(cell);
				Gizmos.cuboid(new AABB(x, y - 0.03, z, x + 1, y - 0.01, z + 1), style);
			}
		});
	}

	public static boolean active() {
		return active;
	}

	/** {@code /stray debug dev roof edit}. */
	public static String toggle(Minecraft client) {
		if (active) {
			stop(client);
			return "sky painter off.";
		}
		if (client.player == null || client.level == null) {
			return "not in a world.";
		}
		if (WorldEditor.active()) {
			return "close the World editor first.";
		}
		StrayConfig config = StrayConfig.get();
		if (!config.spaceRoof) {
			config.spaceRoof = true;
			config.save();
		}
		hadFreecam = Freecam.active();
		if (!hadFreecam) {
			Freecam.enable();
		}
		if (!Freecam.active()) {
			return "freecam couldn't start here.";
		}
		active = true;
		level = client.level;
		shift = 0;
		carving = false;
		carveStart = null;
		previewCarve = null;
		shownCarves = List.of();
		UNDO.clear();
		clearPreview();
		hudTicks = 0;
		return "sky painter on. Aim at a ceiling: left click places the sky there, right click removes it, scroll moves the height, Z undo, C carves out boxes with no sky. Run it again to stop.";
	}

	private static void stop(Minecraft client) {
		active = false;
		clearPreview();
		carveStart = null;
		previewCarve = null;
		shownCarves = List.of();
		if (!hadFreecam && Freecam.active()) {
			Freecam.disable();
		}
		if (client.gui != null) {
			client.gui.hud.setOverlayMessage(Component.empty(), false);
		}
	}

	private static void clearPreview() {
		previewX = Integer.MIN_VALUE;
		preview = Set.of();
		previewEdge = List.of();
	}

	/** Scroll while painting: the layer's height. */
	public static boolean scroll(double amount) {
		if (!active || amount == 0) {
			return false;
		}
		shift = Math.clamp(shift + (amount > 0 ? 1 : -1), -MAX_SHIFT, MAX_SHIFT);
		hudTicks = 0;
		return true;
	}

	/** Freecam's key pass (tick start): preview, place, remove and undo from the real mouse and keys. */
	public static void onInput(Minecraft client, int picks) {
		if (!active) {
			return;
		}
		if (client.level != level || client.player == null || !Freecam.active()) {
			stop(client);
			return;
		}
		boolean left = MouseButtons.down(InputConstants.MOUSE_BUTTON_LEFT);
		boolean right = MouseButtons.down(InputConstants.MOUSE_BUTTON_RIGHT);
		boolean middle = MouseButtons.down(InputConstants.MOUSE_BUTTON_MIDDLE) || picks > 0;
		boolean undo = InputConstants.isKeyDown(InputConstants.KEY_Z);
		boolean carveKey = InputConstants.isKeyDown(InputConstants.KEY_C);
		boolean carvePress = carveKey && !carveKeyWas;
		carveKeyWas = carveKey;
		boolean leftPress = left && !leftWas;
		boolean rightPress = right && !rightWas;
		boolean middlePress = middle && !middleWas;
		boolean undoPress = undo && !undoWas;
		leftWas = left;
		rightWas = right;
		middleWas = middle;
		undoWas = undo;
		if (client.gui.screen() != null) {
			return;
		}
		if (carvePress) {
			carving = !carving;
			carveStart = null;
			shift = 0;
			hudTicks = 0;
		}
		BlockHitResult hit = target(client);
		DungeonRooms.Room room = null;
		int x = 0;
		int z = 0;
		int y = 0;
		if (hit != null) {
			BlockPos pos = hit.getBlockPos();
			// Aiming at an underside: the layer goes right under it. Anything else: on top of it.
			y = (hit.getDirection() == Direction.DOWN ? pos.getY() : pos.getY() + 1) + shift;
			BlockPos air = hit.getDirection() == Direction.DOWN || hit.getDirection() == Direction.UP ? pos : pos.relative(hit.getDirection());
			x = air.getX();
			z = air.getZ();
			// The boss room part you aim into (on F7 its phase or P3 section, by the block under the layer).
			room = carving ? SpaceRoof.bossRoom(pos.getX(), pos.getY() + shift, pos.getZ()) : SpaceRoof.bossRoom(x, y - 1, z);
			shownCarves = room == null ? List.of() : SpaceRoof.carves(room);
			if (!carving && (x != previewX || z != previewZ || y != previewY)) {
				previewX = x;
				previewZ = z;
				previewY = y;
				preview = room == null ? Set.of() : SpaceRoof.fill(client.level, room, x, z, y);
				previewEdge = edge(preview);
			}
		} else {
			clearPreview();
		}
		if (carving) {
			carve(client, hit, room, leftPress, rightPress, middlePress);
		} else {
			previewCarve = null;
		}
		if (middlePress) {
			shift = 0;
			hudTicks = 0;
		}
		if (carving) {
			if (undoPress) {
				undo();
			}
			return;
		}
		if (room != null && leftPress && !preview.isEmpty()) {
			push(new Step(room, SpaceRoof.add(room, y, preview), List.of()));
		}
		if (room != null && rightPress && hit != null) {
			List<SpaceRoof.Sheet> under = SpaceRoof.sheetsAt(room, x, z, y);
			if (!under.isEmpty()) {
				SpaceRoof.Sheet nearest = under.get(0);
				SpaceRoof.remove(room, nearest);
				push(new Step(room, null, List.of(nearest)));
			}
		}
		if (undoPress) {
			undo();
		}
		if (hudTicks-- <= 0) {
			hudTicks = 10;
			String where = hit == null ? "§7aim at a ceiling" : room == null ? "§cnot in a boss room (/stray debug dev roof boss 7)" : "§f" + room.name();
			client.gui.hud.setOverlayMessage(Component.literal("§bSky painter §7· " + where + " §7· height §f" + y + (shift != 0 ? " §7(" + (shift > 0 ? "+" : "") + shift + ")" : "")
				+ " §7· left place · right remove · scroll height · Z undo · C carve"), false);
		}
	}

	private static void undo() {
		Step last = UNDO.poll();
		if (last == null) {
			return;
		}
		if (last.added != null) {
			SpaceRoof.remove(last.room, last.added);
		}
		if (!last.removed.isEmpty()) {
			SpaceRoof.restore(last.room, last.removed);
		}
		if (last.carveAdded != null) {
			SpaceRoof.removeCarve(last.room, last.carveAdded);
		}
		if (last.carveRemoved != null) {
			SpaceRoof.restoreCarve(last.room, last.carveRemoved);
		}
	}

	/**
	 * Carving: the aimed block is a corner (moved up or down by scroll). First left click fixes
	 * the first corner, the second carves the box; right click removes the carved box aimed into.
	 */
	private static void carve(Minecraft client, BlockHitResult hit, DungeonRooms.Room room, boolean leftPress, boolean rightPress, boolean middlePress) {
		if (middlePress) {
			carveStart = null;
		}
		if (hit == null) {
			previewCarve = carveStart == null ? null : box(carveStart, carveStart);
		} else {
			BlockPos corner = hit.getBlockPos().offset(0, shift, 0);
			previewCarve = box(carveStart == null ? corner : carveStart, corner);
			if (leftPress) {
				if (carveStart == null) {
					if (room != null) {
						carveStart = corner;
						carveRoom = room;
						shift = 0;
					}
				} else {
					int[] added = SpaceRoof.addCarve(carveRoom, carveStart, corner);
					push(new Step(carveRoom, null, List.of(), added, null));
					carveStart = null;
					shift = 0;
				}
			}
			if (rightPress && room != null && carveStart == null) {
				BlockPos pos = hit.getBlockPos();
				int[] found = SpaceRoof.carveAt(room, pos.getX(), pos.getY(), pos.getZ());
				if (found == null) {
					BlockPos air = pos.relative(hit.getDirection());
					found = SpaceRoof.carveAt(room, air.getX(), air.getY(), air.getZ());
				}
				if (found != null) {
					SpaceRoof.removeCarve(room, found);
					push(new Step(room, null, List.of(), null, found));
				}
			}
		}
		if (hudTicks-- <= 0) {
			hudTicks = 10;
			String where = room == null ? "§cnot in a boss room" : "§f" + room.name();
			String step = carveStart == null ? "left first corner" : "left second corner · middle cancel";
			client.gui.hud.setOverlayMessage(Component.literal("§cCarving §7· " + where + " §7· " + step + " · right remove box · scroll up/down"
				+ (shift != 0 ? " §f(" + (shift > 0 ? "+" : "") + shift + ")" : "") + " §7· Z undo · C back to sky"), false);
		}
	}

	private static int[] box(BlockPos a, BlockPos b) {
		return new int[] {Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
			Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())};
	}

	private static void push(Step step) {
		UNDO.push(step);
		while (UNDO.size() > UNDO_MAX) {
			UNDO.removeLast();
		}
	}

	/** The fill's border cells (a neighbour outside it), as its outline. */
	private static List<Long> edge(Set<Long> cells) {
		List<Long> out = new ArrayList<>();
		for (long cell : cells) {
			int x = SpaceRoof.x(cell);
			int z = SpaceRoof.z(cell);
			if (!cells.contains(SpaceRoof.key(x + 1, z)) || !cells.contains(SpaceRoof.key(x - 1, z))
				|| !cells.contains(SpaceRoof.key(x, z + 1)) || !cells.contains(SpaceRoof.key(x, z - 1))) {
				out.add(cell);
			}
		}
		return out;
	}

	/** The block under the crosshair, from the freecam; passes through barriers and other invisible blocks. */
	private static BlockHitResult target(Minecraft client) {
		Vec3 look = Freecam.look();
		Vec3 from = Freecam.position(1.0f);
		Vec3 to = Freecam.position(1.0f).add(look.scale(REACH));
		for (int tries = 0; tries < 64; tries++) {
			BlockHitResult hit = client.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()));
			if (hit.getType() != HitResult.Type.BLOCK) {
				return null;
			}
			if (!SpaceRoof.invisible(client.level.getBlockState(hit.getBlockPos()))) {
				return hit;
			}
			// Carry on from just past the invisible block.
			from = hit.getLocation().add(look.scale(0.02));
			while (BlockPos.containing(from).equals(hit.getBlockPos())) {
				from = from.add(look.scale(0.05));
			}
		}
		return null;
	}
}
