package dev.stray.client.editor;

import com.mojang.blaze3d.platform.InputConstants;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.input.MouseButtons;
import dev.stray.client.movement.Freecam;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Vector3fc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * World editor: a Blender-style workspace around a free-flying camera. Everything it does goes
 * through {@link GhostBlocks}, so it only ever exists on your screen.
 *
 * <p>Controls (the cursor stays free, like in Blender's viewport):
 * <ul>
 * <li>hold right mouse in the viewport to look around; WASD / Space / Shift fly at any time;</li>
 * <li>left mouse uses the active tool on the block under the cursor, Ctrl+left does the
 * opposite (remove / erase), middle mouse picks a block into the palette;</li>
 * <li>B Build, V Select, P Paint, G Replace all; 1-9 palette; Ctrl+Z / Ctrl+Y undo / redo.</li>
 * </ul>
 */
public final class WorldEditor {
	public enum Tool {
		BUILD("Build", "B", InputConstants.KEY_B),
		SWAP("Replace", "E", InputConstants.KEY_E),
		SELECT("Select", "V", InputConstants.KEY_V),
		WAND("Magic wand", "M", InputConstants.KEY_M),
		PAINT("Paint", "P", InputConstants.KEY_P),
		REPLACE("Replace all", "G", InputConstants.KEY_G);

		public final String label;
		public final String keyLabel;
		final int key;

		Tool(String label, String keyLabel, int key) {
			this.label = label;
			this.keyLabel = keyLabel;
			this.key = key;
		}
	}

	public static final int SLOTS = 9;
	/** Biggest number of blocks one fill / replace / clear may touch. */
	public static final int MAX_OP = 262_144;
	public static final int MAX_BRUSH = 8;
	private static final double REACH = 192.0;
	private static final int HISTORY = 64;
	private static final String[] DEFAULT_PALETTE = {
		"minecraft:stone", "minecraft:stone_bricks", "minecraft:oak_planks", "minecraft:glass", "minecraft:white_concrete",
		"minecraft:black_concrete", "minecraft:grass_block", "minecraft:sea_lantern", "minecraft:air"
	};

	private static boolean active;
	private static Tool tool = Tool.BUILD;
	private static int slot;
	private static final BlockState[] PALETTE = new BlockState[SLOTS];
	private static boolean paletteLoaded;
	private static BlockPos cornerA;
	private static BlockPos cornerB;
	/** Magic wand selection: exact positions (instead of the box). */
	private static LongOpenHashSet mask;
	private static BlockPos maskMin;
	/** The selected blocks nearest the camera when selected: the ones outlined (all of them would be too slow). */
	private static long[] maskShown = new long[0];
	private static BlockPos maskMax;
	private static BlockHitResult hover;
	private static final Deque<Op> UNDO = new ArrayDeque<>();
	private static final Deque<Op> REDO = new ArrayDeque<>();
	/** The edit a held mouse button is adding to, so a whole stroke undoes at once. */
	private static Op stroke;
	private static int holdTicks;
	private static boolean registered;
	private static String status = "";
	private static long statusUntil;

	/** Right mouse held: the mouse is grabbed and turns the camera, the workspace is hidden from input. */
	private static boolean looking;
	/** Left mouse held in the viewport, and whether Ctrl was down when it was pressed. */
	private static boolean leftHeld;
	private static boolean leftCtrl;
	private static boolean selectDragging;
	/** Cursor position in the viewport as 0..1 of the window, or NaN when it isn't over the viewport. */
	private static double cursorX = Double.NaN;
	private static double cursorY = Double.NaN;

	private WorldEditor() {
	}

	public static void init() {
		if (registered) {
			return;
		}
		registered = true;
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> emitGizmos());
		// Chat lines would draw over the workspace; they come back when chat is opened.
		net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.replaceElement(
			net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.CHAT,
			original -> (graphics, delta) -> {
				if (!active || Minecraft.getInstance().gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen) {
					original.extractRenderState(graphics, delta);
				}
			}
		);
		// The cursor aims in the workspace; the crosshair only matters while looking around.
		net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.replaceElement(
			net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.CROSSHAIR,
			original -> (graphics, delta) -> {
				if (!active || looking) {
					original.extractRenderState(graphics, delta);
				}
			}
		);
	}

	public static boolean active() {
		return active;
	}

	public static boolean looking() {
		return looking;
	}

	public static void toggle() {
		if (active) {
			disable();
		} else {
			enable();
		}
	}

	public static void enable() {
		Minecraft client = Minecraft.getInstance();
		if (active || client.player == null || client.level == null) {
			return;
		}
		loadPalette();
		if (!Freecam.active()) {
			Freecam.enable();
		}
		if (!Freecam.active()) {
			return;
		}
		active = true;
		looking = false;
		leftHeld = false;
		stroke = null;
		openWorkspace();
		say(GhostBlocks.ready() ? "Hold right mouse to look around · WASD to fly" : "Finding out where you are...");
	}

	public static void disable() {
		if (!active) {
			return;
		}
		active = false;
		looking = false;
		leftHeld = false;
		finishStroke();
		hover = null;
		GhostBlocks.saveNow();
		Minecraft client = Minecraft.getInstance();
		if (client.gui.screen() instanceof EditorScreen) {
			client.gui.setScreen(null);
		}
		if (Freecam.active()) {
			Freecam.disable();
		}
	}

	private static void openWorkspace() {
		Minecraft client = Minecraft.getInstance();
		if (!(client.gui.screen() instanceof EditorScreen)) {
			client.gui.setScreen(EditorScreen.get());
		}
	}

	// ------------------------------------------------------------------ input from the workspace

	/** The cursor moved over the workspace: where it is over the viewport (or NaN for "not over it"). */
	public static void cursor(double nx, double ny) {
		cursorX = nx;
		cursorY = ny;
	}

	/** A mouse button went down over the viewport. */
	public static void viewportPress(int which, boolean ctrl, boolean shift) {
		if (!active) {
			return;
		}
		if (which == 1) {
			beginLook();
			return;
		}
		GhostBlocks.visible(() -> {
			updateHover(Minecraft.getInstance(), 1.0f);
			if (which == 2) {
				pick();
				return;
			}
			if (!GhostBlocks.ready()) {
				say("Still finding out where you are...");
				return;
			}
			leftHeld = true;
			leftCtrl = ctrl;
			holdTicks = 0;
			finishStroke();
			switch (tool) {
				case BUILD -> build();
				case SWAP -> swap();
				case PAINT -> paint();
				case REPLACE -> replaceAll();
				case WAND -> wand(ctrl, shift);
				case SELECT -> {
					if (hover == null) {
						return;
					}
					mask = null;
					if (shift && cornerA != null) {
						cornerB = hover.getBlockPos();
					} else {
						cornerA = hover.getBlockPos();
						cornerB = cornerA;
					}
					selectDragging = true;
				}
			}
		});
	}

	public static void viewportRelease() {
		if (!leftHeld) {
			return;
		}
		leftHeld = false;
		finishStroke();
		if (selectDragging) {
			selectDragging = false;
			if (hasSelection()) {
				say("Selected " + selectionText());
			}
		}
	}

	/** Hold right mouse: grab the mouse so it turns the camera, until the button is let go. */
	private static void beginLook() {
		Minecraft client = Minecraft.getInstance();
		viewportRelease();
		looking = true;
		client.mouseHandler.grabMouse();
	}

	/** Every frame while the editor is on. */
	public static void frame(Minecraft client) {
		if (!active) {
			return;
		}
		if (looking && !MouseButtons.down(InputConstants.MOUSE_BUTTON_RIGHT)) {
			looking = false;
			if (client.gui.screen() == null) {
				openWorkspace();
			}
		}
	}

	/** Mouse clicks and hotbar keys vanilla would handle while looking around: kept from it. */
	public static void onInput(Minecraft client, int attacks, int uses, int picks, boolean attackHeld, boolean useHeld, int[] slots) {
		if (!active) {
			return;
		}
		var options = client.options;
		drain(options.keyInventory);
		drain(options.keyDrop);
		drain(options.keySwapOffhand);
		drain(options.keySocialInteractions);
		drain(options.keySaveHotbarActivator);
		drain(options.keyLoadHotbarActivator);
		for (int i = 0; i < slots.length && i < SLOTS; i++) {
			if (slots[i] > 0) {
				slot = i;
			}
		}
	}

	private static int drain(KeyMapping key) {
		int count = 0;
		while (key.consumeClick()) {
			count++;
		}
		key.setDown(false);
		return count;
	}

	/** A key pressed in the workspace (not in a text field). Returns true when the editor used it. */
	public static boolean key(int key, boolean ctrl, boolean shift, boolean alt) {
		Minecraft client = Minecraft.getInstance();
		boolean[] used = {true};
		GhostBlocks.visible(() -> {
			if (ctrl) {
				if (key == InputConstants.KEY_Z) {
					if (shift) {
						redo();
					} else {
						undo();
					}
				} else if (key == InputConstants.KEY_Y) {
					redo();
				} else {
					used[0] = false;
				}
				return;
			}
			for (Tool each : Tool.values()) {
				if (key == each.key) {
					setTool(each);
					return;
				}
			}
			int digit = digit(key);
			if (digit >= 0) {
				slot = digit;
				return;
			}
			if (key == InputConstants.KEY_LBRACKET) {
				brush(-1);
			} else if (key == InputConstants.KEY_RBRACKET) {
				brush(1);
			} else if (key == InputConstants.KEY_H) {
				GhostBlocks.setShown(!StrayConfig.get().editorShowEdits);
				say(StrayConfig.get().editorShowEdits ? "Edits shown" : "Edits hidden");
			} else if (key == InputConstants.KEY_T) {
				client.gui.openChatScreen(ChatComponent.ChatMethod.MESSAGE);
			} else if (key == InputConstants.KEY_SLASH) {
				client.gui.openChatScreen(ChatComponent.ChatMethod.COMMAND);
			} else if (key == InputConstants.KEY_F) {
				fillSelection(palette());
			} else if (key == InputConstants.KEY_R) {
				replaceInSelection();
			} else if (key == InputConstants.KEY_X || key == InputConstants.KEY_DELETE) {
				if (alt) {
					restoreSelection();
				} else if (tool == Tool.SELECT || hasSelection() && tool != Tool.BUILD) {
					fillSelection(Blocks.AIR.defaultBlockState());
				} else if (hover != null && GhostBlocks.ready()) {
					finishStroke();
					edit(hover.getBlockPos(), Blocks.AIR.defaultBlockState(), true);
					finishStroke();
				}
			} else if (key == InputConstants.KEY_C || alt && key == InputConstants.KEY_A) {
				clearSelection();
				say("Deselected");
			} else {
				used[0] = false;
			}
		});
		return used[0];
	}

	private static int digit(int key) {
		int[] keys = {
			InputConstants.KEY_1, InputConstants.KEY_2, InputConstants.KEY_3, InputConstants.KEY_4, InputConstants.KEY_5,
			InputConstants.KEY_6, InputConstants.KEY_7, InputConstants.KEY_8, InputConstants.KEY_9
		};
		for (int i = 0; i < keys.length; i++) {
			if (keys[i] == key) {
				return i;
			}
		}
		return -1;
	}

	/** End of client tick: keeps the workspace open, repeats held clicks. */
	public static void tick(Minecraft client) {
		if (!active) {
			return;
		}
		if (!Freecam.active() || client.player == null || client.level == null) {
			disable();
			return;
		}
		if (!looking && client.gui.screen() == null) {
			// Back from chat or the pause menu.
			openWorkspace();
		}
		if (leftHeld && !MouseButtons.down(InputConstants.MOUSE_BUTTON_LEFT)) {
			viewportRelease();
		}
		if (leftHeld && client.gui.screen() instanceof EditorScreen) {
			holdTicks++;
			if (holdTicks > 5 && holdTicks % 2 == 0) {
				GhostBlocks.visible(() -> {
					updateHover(client, 1.0f);
					if (tool == Tool.BUILD) {
						build();
					} else if (tool == Tool.SWAP) {
						swap();
					} else if (tool == Tool.PAINT) {
						paint();
					}
				});
			}
		}
	}

	/** Mouse wheel over the viewport: move the camera along the view (Ctrl: brush size). */
	public static void viewportScroll(double amount, boolean ctrl) {
		if (ctrl) {
			brush(amount > 0 ? 1 : -1);
			return;
		}
		Vec3 step = Freecam.look().scale(Math.signum(amount) * Math.max(1.0, Freecam.speed() * 3.0));
		Freecam.glide(step);
	}

	/** Scroll while looking around (no workspace): palette slot. */
	public static boolean scroll(double amount) {
		if (!active) {
			return false;
		}
		slot = Math.floorMod(slot + (amount > 0 ? -1 : 1), SLOTS);
		return true;
	}

	/** Removes the replace rule for {@code from} (undoable). */
	public static void removeRule(Block from) {
		List<GhostBlocks.Rule> before = new ArrayList<>(GhostBlocks.rules());
		GhostBlocks.removeRule(from);
		push(Op.rules(before, new ArrayList<>(GhostBlocks.rules())));
	}

	// ------------------------------------------------------------------ tools

	private static void build() {
		if (hover == null) {
			return;
		}
		if (leftCtrl) {
			apply(strokeOp(), hover.getBlockPos(), Blocks.AIR.defaultBlockState());
		} else {
			BlockPos at = hover.getBlockPos().relative(hover.getDirection());
			apply(strokeOp(), at, orient(palette(), hover));
		}
	}

	/** Replace: the hovered block becomes the palette block (Ctrl: shows the real block again). */
	private static void swap() {
		if (hover == null) {
			return;
		}
		BlockPos pos = hover.getBlockPos();
		if (leftCtrl) {
			if (GhostBlocks.placedAt(pos) != null) {
				apply(strokeOp(), pos, null);
			}
			return;
		}
		BlockState shown = GhostBlocks.shown(pos);
		BlockState paint = palette();
		if (shown == paint) {
			return;
		}
		apply(strokeOp(), pos, paint.getBlock() == shown.getBlock() || paint.isAir() ? paint : GhostBlocks.copyProperties(shown, paint));
	}

	private static void paint() {
		if (hover == null) {
			return;
		}
		boolean erase = leftCtrl;
		BlockState paint = palette();
		int r = brushSize();
		BlockPos center = hover.getBlockPos();
		Op op = strokeOp();
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					if (dx * dx + dy * dy + dz * dz > r * r + r) {
						continue;
					}
					BlockPos pos = center.offset(dx, dy, dz);
					BlockState shown = GhostBlocks.shown(pos);
					if (shown.isAir()) {
						continue;
					}
					if (erase) {
						apply(op, pos, Blocks.AIR.defaultBlockState());
					} else if (shown != paint) {
						apply(op, pos, paint.getBlock() == shown.getBlock() ? paint : GhostBlocks.copyProperties(shown, paint));
					}
				}
			}
		}
	}

	private static void replaceAll() {
		if (hover == null) {
			return;
		}
		BlockState looked = GhostBlocks.shown(hover.getBlockPos());
		if (looked.isAir()) {
			return;
		}
		Block from = looked.getBlock();
		// Clicking a block that's already the result of a rule edits that rule instead.
		for (GhostBlocks.Rule rule : GhostBlocks.rules()) {
			if (rule.to.getBlock() == from) {
				from = rule.from;
				break;
			}
		}
		List<GhostBlocks.Rule> before = new ArrayList<>(GhostBlocks.rules());
		if (leftCtrl) {
			if (GhostBlocks.ruleFor(from) == null) {
				say("No replace rule for " + name(from.defaultBlockState()));
				return;
			}
			GhostBlocks.removeRule(from);
			push(Op.rules(before, new ArrayList<>(GhostBlocks.rules())));
			say(name(from.defaultBlockState()) + " is back to normal");
			return;
		}
		BlockState to = palette();
		if (from == to.getBlock()) {
			say("Pick a different block in the palette first");
			return;
		}
		if (!GhostBlocks.addRule(from, to)) {
			say("That's the most replace rules one place can have");
			return;
		}
		push(Op.rules(before, new ArrayList<>(GhostBlocks.rules())));
		say("Every " + name(from.defaultBlockState()) + " is now " + name(to));
	}

	// ------------------------------------------------------------------ selection actions

	public static boolean hasSelection() {
		return mask != null && !mask.isEmpty() || cornerA != null && cornerB != null;
	}

	public static long selectionVolume() {
		if (!hasSelection()) {
			return 0;
		}
		if (mask != null) {
			return mask.size();
		}
		return (long) (Math.abs(cornerA.getX() - cornerB.getX()) + 1)
			* (Math.abs(cornerA.getY() - cornerB.getY()) + 1)
			* (Math.abs(cornerA.getZ() - cornerB.getZ()) + 1);
	}

	public static void clearSelection() {
		cornerA = null;
		cornerB = null;
		mask = null;
	}

	// ------------------------------------------------------------------ magic wand

	/** Selects the clicked block's connected blocks of the same type (Ctrl: every one around). Shift adds. */
	private static void wand(boolean everywhere, boolean add) {
		if (hover == null) {
			return;
		}
		Block target = GhostBlocks.shown(hover.getBlockPos()).getBlock();
		if (target == Blocks.AIR) {
			return;
		}
		LongOpenHashSet found = everywhere ? allOfType(target) : connected(hover.getBlockPos(), target);
		boolean capped = found.size() >= MAX_OP;
		if (add && mask != null) {
			mask.addAll(found);
		} else {
			mask = found;
		}
		cornerA = null;
		cornerB = null;
		maskBounds();
		say((everywhere ? "Selected every " : "Selected ") + found.size() + " " + name(target.defaultBlockState())
			+ (capped ? " (limit reached)" : "") + (add ? " · " + mask.size() + " in total" : ""));
	}

	private static LongOpenHashSet connected(BlockPos start, Block target) {
		LongOpenHashSet seen = new LongOpenHashSet();
		it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue queue = new it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue();
		seen.add(start.asLong());
		queue.enqueue(start.asLong());
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		Direction[] directions = Direction.values();
		while (!queue.isEmpty() && seen.size() < MAX_OP) {
			long pos = queue.dequeueLong();
			for (Direction direction : directions) {
				long next = BlockPos.offset(pos, direction);
				if (seen.contains(next)) {
					continue;
				}
				cursor.set(next);
				if (GhostBlocks.shown(cursor).getBlock() == target) {
					seen.add(next);
					queue.enqueue(next);
					if (seen.size() >= MAX_OP) {
						break;
					}
				}
			}
		}
		return seen;
	}

	private static LongOpenHashSet allOfType(Block target) {
		LongOpenHashSet out = new LongOpenHashSet();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		GhostBlocks.forEachLoaded(chunk -> {
			if (out.size() >= MAX_OP) {
				return;
			}
			var sections = chunk.getSections();
			int bx = chunk.getPos().x() << 4;
			int bz = chunk.getPos().z() << 4;
			for (int i = 0; i < sections.length && out.size() < MAX_OP; i++) {
				var section = sections[i];
				if (section == null || !section.maybeHas(state -> GhostBlocks.ruled(state.getBlock()) == target)) {
					continue;
				}
				int by = chunk.getSectionYFromSectionIndex(i) << 4;
				for (int y = 0; y < 16; y++) {
					for (int z = 0; z < 16; z++) {
						for (int x = 0; x < 16; x++) {
							BlockState real = section.getBlockState(x, y, z);
							if (GhostBlocks.ruled(real.getBlock()) != target) {
								continue;
							}
							cursor.set(bx + x, by + y, bz + z);
							if (GhostBlocks.shown(cursor, real).getBlock() == target && out.size() < MAX_OP) {
								out.add(cursor.asLong());
							}
						}
					}
				}
			}
		});
		// Placed blocks of that type (wherever the world has something else).
		GhostBlocks.forEachPlaced((pos, state) -> {
			if (state.getBlock() == target && out.size() < MAX_OP) {
				out.add(pos);
			}
		});
		return out;
	}

	private static void maskBounds() {
		if (mask == null || mask.isEmpty()) {
			mask = null;
			maskMin = null;
			maskMax = null;
			return;
		}
		int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE;
		int x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
		for (long pos : mask) {
			int x = BlockPos.getX(pos);
			int y = BlockPos.getY(pos);
			int z = BlockPos.getZ(pos);
			x0 = Math.min(x0, x);
			y0 = Math.min(y0, y);
			z0 = Math.min(z0, z);
			x1 = Math.max(x1, x);
			y1 = Math.max(y1, y);
			z1 = Math.max(z1, z);
		}
		maskMin = new BlockPos(x0, y0, z0);
		maskMax = new BlockPos(x1, y1, z1);
		Vec3 eye = Freecam.position(1.0f);
		long[] all = mask.toLongArray();
		if (all.length > 1500) {
			// Partial sort by distance: keep the nearest 1500.
			double[] dist = new double[all.length];
			Integer[] order = new Integer[all.length];
			for (int i = 0; i < all.length; i++) {
				dist[i] = Vec3.atCenterOf(BlockPos.of(all[i])).distanceToSqr(eye);
				order[i] = i;
			}
			java.util.Arrays.sort(order, (a, b) -> Double.compare(dist[a], dist[b]));
			long[] near = new long[1500];
			for (int i = 0; i < near.length; i++) {
				near[i] = all[order[i]];
			}
			all = near;
		}
		maskShown = all;
	}

	public static void fillSelection(BlockState state) {
		if (!checkSelection()) {
			return;
		}
		Op op = new Op();
		forSelection(pos -> apply(op, pos, state));
		push(op);
		say((state.isAir() ? "Cleared " : "Filled ") + op.size() + " blocks");
	}

	/** In the selection, the looked-at block's type (or every solid block) becomes the palette block. */
	public static void replaceInSelection() {
		if (!checkSelection()) {
			return;
		}
		BlockState looked = hover == null ? null : GhostBlocks.shown(hover.getBlockPos());
		Block target = looked == null || looked.isAir() ? null : looked.getBlock();
		BlockState paint = palette();
		Op op = new Op();
		forSelection(pos -> {
			BlockState shown = GhostBlocks.shown(pos);
			if (shown.isAir() || target != null && shown.getBlock() != target || shown == paint) {
				return;
			}
			apply(op, pos, paint.getBlock() == shown.getBlock() ? paint : GhostBlocks.copyProperties(shown, paint));
		});
		push(op);
		say("Replaced " + op.size() + (target == null ? " blocks" : " " + name(target.defaultBlockState())));
	}

	/** Undoes every edit inside the selection: the server's blocks come back. */
	public static void restoreSelection() {
		if (!checkSelection()) {
			return;
		}
		Op op = new Op();
		forSelection(pos -> {
			if (GhostBlocks.placedAt(pos) != null) {
				apply(op, pos, null);
			}
		});
		push(op);
		say("Restored " + op.size() + " blocks");
	}

	private static boolean checkSelection() {
		if (!GhostBlocks.ready()) {
			say("Still finding out where you are...");
			return false;
		}
		if (!hasSelection()) {
			say("Select something first: drag with Select, or click with the Magic wand");
			return false;
		}
		if (selectionVolume() > MAX_OP) {
			say("Selection is too big (" + selectionVolume() + " blocks, max " + MAX_OP + ")");
			return false;
		}
		return true;
	}

	private static void forSelection(java.util.function.Consumer<BlockPos> action) {
		if (mask != null) {
			for (long pos : mask.toLongArray()) {
				action.accept(BlockPos.of(pos));
			}
			return;
		}
		int x0 = Math.min(cornerA.getX(), cornerB.getX());
		int y0 = Math.min(cornerA.getY(), cornerB.getY());
		int z0 = Math.min(cornerA.getZ(), cornerB.getZ());
		int x1 = Math.max(cornerA.getX(), cornerB.getX());
		int y1 = Math.max(cornerA.getY(), cornerB.getY());
		int z1 = Math.max(cornerA.getZ(), cornerB.getZ());
		for (int y = y0; y <= y1; y++) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					action.accept(new BlockPos(x, y, z));
				}
			}
		}
	}

	// ------------------------------------------------------------------ edits and history

	private static void edit(BlockPos pos, BlockState state, boolean fresh) {
		if (fresh) {
			finishStroke();
		}
		apply(strokeOp(), pos, state);
	}

	private static Op strokeOp() {
		if (stroke == null) {
			stroke = new Op();
		}
		return stroke;
	}

	private static void finishStroke() {
		if (stroke != null) {
			push(stroke);
			stroke = null;
		}
	}

	private static void apply(Op op, BlockPos pos, BlockState state) {
		BlockState before = GhostBlocks.placedAt(pos);
		if (before == state) {
			return;
		}
		if (!GhostBlocks.set(pos, state)) {
			say("This place is full: undo or restore some edits first");
			return;
		}
		op.record(pos.asLong(), before, state);
	}

	private static void push(Op op) {
		if (op == null || op.empty()) {
			return;
		}
		UNDO.push(op);
		while (UNDO.size() > HISTORY) {
			UNDO.removeLast();
		}
		REDO.clear();
	}

	public static void undo() {
		finishStroke();
		Op op = UNDO.poll();
		if (op == null) {
			say("Nothing to undo");
			return;
		}
		GhostBlocks.visible(() -> op.revert());
		REDO.push(op);
		say("Undid " + op.describe());
	}

	public static void redo() {
		Op op = REDO.poll();
		if (op == null) {
			say("Nothing to redo");
			return;
		}
		GhostBlocks.visible(() -> op.replay());
		UNDO.push(op);
		say("Redid " + op.describe());
	}

	public static boolean canUndo() {
		return !UNDO.isEmpty() || stroke != null;
	}

	public static boolean canRedo() {
		return !REDO.isEmpty();
	}

	/** History belongs to one place; it's dropped when the edits switch to another. */
	public static void forgetHistory() {
		UNDO.clear();
		REDO.clear();
		stroke = null;
	}

	/** One undo step: block changes, or a change to the replace rules. */
	private static final class Op {
		private final Long2ObjectLinkedOpenHashMap<BlockState[]> blocks = new Long2ObjectLinkedOpenHashMap<>();
		private List<GhostBlocks.Rule> rulesBefore;
		private List<GhostBlocks.Rule> rulesAfter;

		static Op rules(List<GhostBlocks.Rule> before, List<GhostBlocks.Rule> after) {
			Op op = new Op();
			op.rulesBefore = before;
			op.rulesAfter = after;
			return op;
		}

		void record(long pos, BlockState before, BlockState after) {
			BlockState[] entry = blocks.get(pos);
			if (entry == null) {
				blocks.put(pos, new BlockState[] {before, after});
			} else {
				entry[1] = after;
			}
		}

		int size() {
			return blocks.size();
		}

		boolean empty() {
			return blocks.isEmpty() && rulesBefore == null;
		}

		void revert() {
			if (rulesBefore != null) {
				GhostBlocks.replaceRules(rulesBefore);
			}
			BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
			for (var entry : blocks.long2ObjectEntrySet()) {
				GhostBlocks.set(cursor.set(entry.getLongKey()).immutable(), entry.getValue()[0]);
			}
		}

		void replay() {
			if (rulesAfter != null) {
				GhostBlocks.replaceRules(rulesAfter);
			}
			BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
			for (var entry : blocks.long2ObjectEntrySet()) {
				GhostBlocks.set(cursor.set(entry.getLongKey()).immutable(), entry.getValue()[1]);
			}
		}

		String describe() {
			if (rulesBefore != null) {
				return "replace rule change";
			}
			return blocks.size() == 1 ? "1 block" : blocks.size() + " blocks";
		}
	}

	// ------------------------------------------------------------------ palette

	private static void loadPalette() {
		if (paletteLoaded) {
			return;
		}
		paletteLoaded = true;
		List<String> saved = StrayConfig.get().editorPalette;
		for (int i = 0; i < SLOTS; i++) {
			BlockState state = null;
			if (saved != null && i < saved.size()) {
				state = GhostBlocks.parseState(saved.get(i));
			}
			if (state == null) {
				state = GhostBlocks.parseState(DEFAULT_PALETTE[i]);
			}
			PALETTE[i] = state == null ? Blocks.STONE.defaultBlockState() : state;
		}
	}

	private static void savePalette() {
		StrayConfig config = StrayConfig.get();
		List<String> out = new ArrayList<>();
		for (BlockState state : PALETTE) {
			out.add(GhostBlocks.describe(state));
		}
		config.editorPalette = out;
		config.save();
	}

	public static BlockState palette() {
		loadPalette();
		return PALETTE[slot];
	}

	public static BlockState paletteAt(int index) {
		loadPalette();
		return PALETTE[Math.floorMod(index, SLOTS)];
	}

	public static void setPalette(int index, BlockState state) {
		loadPalette();
		if (state == null) {
			return;
		}
		PALETTE[Math.floorMod(index, SLOTS)] = state;
		savePalette();
	}

	public static int slot() {
		return slot;
	}

	public static void setSlot(int index) {
		slot = Math.floorMod(index, SLOTS);
	}

	private static void pick() {
		if (hover == null) {
			return;
		}
		BlockState state = GhostBlocks.shown(hover.getBlockPos());
		if (state.isAir()) {
			return;
		}
		setPalette(slot, state);
		say("Picked " + name(state));
	}

	public static Tool tool() {
		return tool;
	}

	public static void setTool(Tool next) {
		if (next == null || next == tool) {
			return;
		}
		finishStroke();
		tool = next;
		say(next.label);
	}

	public static int brushSize() {
		return Math.max(0, Math.min(MAX_BRUSH, StrayConfig.get().editorBrush));
	}

	public static void brush(int delta) {
		StrayConfig config = StrayConfig.get();
		config.editorBrush = Math.max(0, Math.min(MAX_BRUSH, config.editorBrush + delta));
		config.save();
		say("Brush size " + (config.editorBrush * 2 + 1));
	}

	// ------------------------------------------------------------------ orienting placed blocks

	private static BlockState orient(BlockState state, BlockHitResult hit) {
		BlockState out = state;
		Direction face = hit.getDirection();
		if (out.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
			out = out.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.fromYRot(Freecam.yaw()).getOpposite());
		} else if (out.hasProperty(BlockStateProperties.FACING)) {
			out = out.setValue(BlockStateProperties.FACING, face);
		}
		if (out.hasProperty(BlockStateProperties.AXIS)) {
			out = out.setValue(BlockStateProperties.AXIS, face.getAxis());
		}
		boolean top = face == Direction.DOWN || face != Direction.UP && hit.getLocation().y - hit.getBlockPos().getY() > 0.5;
		if (out.hasProperty(BlockStateProperties.SLAB_TYPE) && out.getValue(BlockStateProperties.SLAB_TYPE) != SlabType.DOUBLE) {
			out = out.setValue(BlockStateProperties.SLAB_TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
		}
		if (out.hasProperty(BlockStateProperties.HALF)) {
			out = out.setValue(BlockStateProperties.HALF, top ? Half.TOP : Half.BOTTOM);
		}
		return out;
	}

	// ------------------------------------------------------------------ aiming and drawing

	/** Ray from the camera through the cursor (or the middle of the screen while looking around). */
	private static void updateHover(Minecraft client, float partial) {
		if (client.level == null) {
			hover = null;
			return;
		}
		Camera camera = client.gameRenderer.mainCamera();
		Vec3 from = Freecam.position(partial);
		Vec3 dir = Freecam.look();
		if (!looking) {
			if (Double.isNaN(cursorX) || !(client.gui.screen() instanceof EditorScreen)) {
				hover = null;
				return;
			}
			Vector3fc f = camera.forwardVector();
			Vector3fc u = camera.upVector();
			Vector3fc l = camera.leftVector();
			double t = Math.tan(Math.toRadians(camera.getFov()) / 2.0);
			var window = client.getWindow();
			double aspect = window.getWidth() / (double) Math.max(1, window.getHeight());
			double sx = (2.0 * cursorX - 1.0) * t * aspect;
			double sy = (1.0 - 2.0 * cursorY) * t;
			dir = new Vec3(
				f.x() - l.x() * sx + u.x() * sy,
				f.y() - l.y() * sx + u.y() * sy,
				f.z() - l.z() * sx + u.z() * sy
			).normalize();
		}
		Vec3 to = from.add(dir.scale(REACH));
		BlockHitResult hit = new GhostView(client.level).clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()));
		hover = hit.getType() == HitResult.Type.BLOCK ? hit.withPosition(hit.getBlockPos().immutable()) : null;
		if (selectDragging && leftHeld && hover != null) {
			cornerB = hover.getBlockPos();
		}
	}

	private static void emitGizmos() {
		if (!active) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return;
		}
		GhostBlocks.visible(() -> updateHover(client, client.getDeltaTracker().getGameTimeDeltaPartialTick(false)));
		if (mask != null && maskMin != null) {
			AABB bounds = new AABB(Vec3.atLowerCornerOf(maskMin), Vec3.atLowerCornerOf(maskMax).add(1, 1, 1)).inflate(0.03);
			GizmoProperties outline = Gizmos.cuboid(bounds, GizmoStyle.stroke(0x80F0A030, 1.4f));
			outline.setAlwaysOnTop();
			for (long pos : maskShown) {
				GizmoProperties cell = Gizmos.cuboid(new AABB(BlockPos.of(pos)).inflate(0.005), GizmoStyle.strokeAndFill(0xC0F0A030, 1.2f, 0x28F0A030));
				cell.setAlwaysOnTop();
			}
		} else if (hasSelection()) {
			AABB box = new AABB(Vec3.atLowerCornerOf(min(cornerA, cornerB)), Vec3.atLowerCornerOf(max(cornerA, cornerB)).add(1, 1, 1)).inflate(0.02);
			GizmoProperties sel = Gizmos.cuboid(box, GizmoStyle.strokeAndFill(0xFFF0A030, 2.4f, 0x18F0A030));
			sel.setAlwaysOnTop();
			cube(cornerA, 0xFFFFFFFF, 0x30FFFFFF);
			cube(cornerB, 0xFFFFD27A, 0x30FFD27A);
		}
		if (hover == null || looking) {
			return;
		}
		BlockPos at = hover.getBlockPos();
		boolean ctrl = EditorScreen.ctrlDown();
		switch (tool) {
			case BUILD -> {
				if (ctrl) {
					cube(at, 0xFFFF5A5A, 0x40FF5A5A);
				} else {
					cube(at, 0x90FFFFFF, 0x0CFFFFFF);
					cube(at.relative(hover.getDirection()), 0xFF6FA8FF, 0x336FA8FF);
				}
			}
			case PAINT -> {
				int r = brushSize();
				int color = ctrl ? 0xFFFF5A5A : 0xFFFFC857;
				AABB box = new AABB(Vec3.atLowerCornerOf(at.offset(-r, -r, -r)), Vec3.atLowerCornerOf(at.offset(r + 1, r + 1, r + 1)));
				GizmoProperties brush = Gizmos.cuboid(box, GizmoStyle.strokeAndFill(color, 1.8f, (color & 0xFFFFFF) | 0x14000000));
				brush.setAlwaysOnTop();
			}
			case REPLACE -> cube(at, ctrl ? 0xFFFF5A5A : 0xFFE070FF, ctrl ? 0x30FF5A5A : 0x30E070FF);
			case SELECT -> cube(at, 0xC0FFFFFF, 0x14FFFFFF);
			case WAND -> cube(at, 0xFFF0A030, 0x30F0A030);
			case SWAP -> cube(at, ctrl ? 0xFFFF5A5A : 0xFF6FA8FF, ctrl ? 0x30FF5A5A : 0x406FA8FF);
		}
	}

	private static void cube(BlockPos pos, int line, int fill) {
		GizmoProperties box = Gizmos.cuboid(new AABB(pos).inflate(0.01), GizmoStyle.strokeAndFill(line, 2.0f, fill));
		box.setAlwaysOnTop();
	}

	private static BlockPos min(BlockPos a, BlockPos b) {
		return new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
	}

	private static BlockPos max(BlockPos a, BlockPos b) {
		return new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
	}

	public static BlockHitResult hover() {
		return hover;
	}

	public static BlockPos cornerA() {
		return cornerA;
	}

	public static BlockPos cornerB() {
		return cornerB;
	}

	// ------------------------------------------------------------------ text

	public static String status() {
		return System.currentTimeMillis() < statusUntil ? status : "";
	}

	public static void say(String text) {
		status = text;
		statusUntil = System.currentTimeMillis() + 2600;
	}

	private static String selectionSize() {
		if (!hasSelection()) {
			return "";
		}
		int dx = Math.abs(cornerA.getX() - cornerB.getX()) + 1;
		int dy = Math.abs(cornerA.getY() - cornerB.getY()) + 1;
		int dz = Math.abs(cornerA.getZ() - cornerB.getZ()) + 1;
		return " · " + dx + "×" + dy + "×" + dz;
	}

	public static String selectionText() {
		if (mask != null) {
			return mask.size() + " blocks";
		}
		return hasSelection() ? selectionSize().substring(3) + " (" + selectionVolume() + ")" : "none";
	}

	private static String text(BlockPos pos) {
		return pos.getX() + " " + pos.getY() + " " + pos.getZ();
	}

	public static String name(BlockState state) {
		if (state == null) {
			return "";
		}
		return state.getBlock().getName().getString();
	}

	/** Lowercase id path, e.g. "oak_planks". */
	public static String id(Block block) {
		return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath().toLowerCase(Locale.ROOT);
	}

	static Component literal(String text) {
		return Component.literal(text);
	}
}
