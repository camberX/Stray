package dev.stray.client.dungeon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.stray.client.combat.OdinClicks;
import dev.stray.client.config.IslandSaves;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.mining.SmoothRotate;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.ui.MenuFont;
import dev.stray.client.util.Text;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Etherwarp aim: points you set per dungeon room (saved relative to the room, so they follow
 * its rotation). Sneaking with an Ether Transmission item, the crosshair is pulled toward a
 * point once it is close, and when the crosshair is on the point's block it right clicks once.
 *
 * <p>The click is the normal use key at the start of a tick (see {@link LeverTriggerbot}), so
 * the server gets exactly what a real right click sends. It never clicks while the crosshair is
 * on an entity (the click would go to the entity). Edit mode: middle click a block to add or
 * remove a point; a small HUD says which room you are in.
 */
public final class EtherwarpAim {
	/** Etherwarp reaches 57 blocks, 61 with tuners: look that far. */
	private static final double RANGE = 61.0;
	private static final Path FILE = IslandSaves.DIR.resolve("etherwarp-points.json");
	/** Room name (or "@" + island for points outside known rooms) to points. */
	private static final Map<String, Set<BlockPos>> SAVED = new LinkedHashMap<>();
	private static boolean loaded;

	/** Points of the room you are in, in world coordinates. */
	private static List<BlockPos> live = List.of();
	private static BlockPos clicked;
	private static BlockPos aimed;
	private static long aimedSince;
	private static long waitMs;
	private static long lastFrameNs;
	/** Auto swap: the slot you held before it picked the etherwarp item, or -1. */
	private static int swappedFrom = -1;
	private static long swappedAt;
	private static boolean swapBackNext;
	/** Turn still owed from steps smaller than one mouse count. */
	private static float restYaw;
	private static float restPitch;
	/** Where the points in {@link #live} came from, to start over when that changes. */
	private static String liveKey;
	/** Index of the point to warp to next; past the end once the route is done. */
	private static int next;

	private EtherwarpAim() {
	}

	public static void init() {
		load();
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> emitBoxes());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> save());
	}

	public static boolean active() {
		return StrayConfig.get().etherwarpAim;
	}

	public static void onWorldChange() {
		DungeonRooms.reset();
		live = List.of();
		liveKey = null;
		next = 0;
		clicked = null;
		aimed = null;
	}

	/** Starts the route of the room you are in over from its first point. */
	public static void resetRoute() {
		next = 0;
		clicked = null;
		aimed = null;
	}

	/** The point to warp to next, or null when there is none (no points, or the route is done). */
	public static BlockPos target() {
		return next >= 0 && next < live.size() ? live.get(next) : null;
	}

	/** Once a tick: which room you are in and where its points are. */
	public static void tick(Minecraft client) {
		StrayConfig config = StrayConfig.get();
		if (!config.etherwarpAim || client.player == null || client.level == null) {
			live = List.of();
			return;
		}
		DungeonRooms.update(client);
		DungeonRooms.Room room = DungeonRooms.current();
		String key = key(room);
		if (key == null ? liveKey != null : !key.equals(liveKey)) {
			// A new room: start its route from the first point.
			liveKey = key;
			next = 0;
		}
		Set<BlockPos> stored = key == null ? null : SAVED.get(key);
		if (stored == null || stored.isEmpty()) {
			live = List.of();
			return;
		}
		List<BlockPos> world = new ArrayList<>(stored.size());
		for (BlockPos pos : stored) {
			BlockPos real = room == null ? pos : room.toWorld(pos);
			if (real != null) {
				world.add(real);
			}
		}
		live = world;
		// On (or right over) a point: the one after it is next, also when you skip ahead or go
		// back. Measured from your feet to the top of the block, so landing a hair off its middle
		// or a tick in the air still counts and it never aims down at where you stand.
		Vec3 feet = client.player.position();
		for (int i = 0; i < live.size(); i++) {
			BlockPos pos = live.get(i);
			double dx = feet.x - (pos.getX() + 0.5);
			double dz = feet.z - (pos.getZ() + 0.5);
			double dy = feet.y - (pos.getY() + 1.0);
			if (dx * dx + dz * dz < 1.0 && dy > -0.5 && dy < 1.5) {
				next = i + 1;
			}
		}
	}

	/**
	 * Where a room's points are saved: its name (points relative to the room), or for places
	 * outside known rooms the boss floor or island (points as they are). Null when they can't
	 * be saved yet (a room whose turn isn't known, or between rooms in a run).
	 */
	private static String key(DungeonRooms.Room room) {
		if (room != null) {
			return room.rotated() ? room.name() : null;
		}
		if (dev.stray.client.location.SkyblockLocation.inBoss) {
			return "@boss:f" + dev.stray.client.location.SkyblockLocation.dungeonFloor;
		}
		if (dev.stray.client.location.SkyblockLocation.inDungeon) {
			return null;
		}
		return "@" + IslandSaves.key();
	}

	// ------------------------------------------------------------------ click

	/** At the start of a tick, where the game reads the use key. */
	public static void beforeKeybinds(Minecraft client) {
		LocalPlayer player = client.player;
		StrayConfig config = StrayConfig.get();
		// Swap back the tick after the click (or when you stop aiming after a swap).
		if (swappedFrom >= 0 && player != null) {
			boolean done = swapBackNext || !ready(client) || lookedAtPoint(client, player) == null
				&& System.currentTimeMillis() - swappedAt > 400L;
			if (done) {
				if (config.etherwarpAimSwapBack && swappedFrom < 9) {
					// The same as pressing that hotbar number key.
					player.getInventory().setSelectedSlot(swappedFrom);
				}
				swappedFrom = -1;
				swapBackNext = false;
				return;
			}
		}
		if (!ready(client) || target() == null) {
			aimed = null;
			clicked = null;
			return;
		}
		BlockPos target = lookedAtPoint(client, player);
		if (target == null) {
			aimed = null;
			clicked = null;
			return;
		}
		long now = System.currentTimeMillis();
		if (!target.equals(aimed)) {
			aimed = target;
			aimedSince = now;
			clicked = null;
			waitMs = delay(config);
		}
		if (target.equals(clicked)) {
			return;
		}
		if (!holdingEther(player)) {
			// Auto swap: pick up the etherwarp item now, click on a later tick.
			int slot = etherSlot(player);
			if (!config.etherwarpAimAutoSwap || slot < 0) {
				return;
			}
			if (swappedFrom < 0) {
				swappedFrom = player.getInventory().getSelectedSlot();
			}
			swappedAt = now;
			player.getInventory().setSelectedSlot(slot);
			// At least a tick between the swap and the click, and the delay from here.
			aimedSince = now;
			return;
		}
		if (now - aimedSince < waitMs) {
			return;
		}
		// The use key on an entity uses the entity, not the item: wait until it is out of the way.
		if (client.hitResult != null && client.hitResult.getType() == HitResult.Type.ENTITY) {
			return;
		}
		clicked = target;
		OdinClicks.rightClick();
		if (swappedFrom >= 0) {
			swapBackNext = true;
		}
	}

	/** The hotbar slot of an item with Ether Transmission, or -1. */
	private static int etherSlot(LocalPlayer player) {
		for (int i = 0; i < 9; i++) {
			if (hasEther(player.getInventory().getItem(i))) {
				return i;
			}
		}
		return -1;
	}

	private static long delay(StrayConfig config) {
		long base = Math.max(0, config.etherwarpAimDelay);
		long spread = Math.max(5L, base / 4);
		return base + ThreadLocalRandom.current().nextLong(0, spread + 1);
	}

	private static boolean ready(Minecraft client) {
		LocalPlayer player = client.player;
		return StrayConfig.get().etherwarpAim && player != null && client.level != null && client.gui.screen() == null
			&& !player.isSpectator() && player.isShiftKeyDown()
			&& (holdingEther(player) || StrayConfig.get().etherwarpAimAutoSwap && etherSlot(player) >= 0)
			&& !dev.stray.client.movement.Freecam.active();
	}

	public static boolean holdingEther(LocalPlayer player) {
		return hasEther(player.getMainHandItem());
	}

	private static boolean hasEther(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) {
			return false;
		}
		for (Component line : lore.lines()) {
			if (Text.strip(line.getString()).toLowerCase(Locale.ROOT).contains("ether transmission")) {
				return true;
			}
		}
		return false;
	}

	private static BlockHitResult ray(Minecraft client, LocalPlayer player, Vec3 look) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(look.scale(RANGE));
		return client.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
	}

	private static BlockPos lookedAtPoint(Minecraft client, LocalPlayer player) {
		BlockHitResult hit = ray(client, player, player.getViewVector(1f));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		BlockPos target = target();
		return target != null && hit.getBlockPos().equals(target) ? target : null;
	}

	/** What the feature sees right now, for /stray debug dev etherwarp. */
	public static String debug() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		DungeonRooms.Room room = DungeonRooms.current();
		String where = room == null ? "no room" : room.name() + (room.rotated() ? " " + room.rotation() + "° corner " + room.corner().toShortString() : " (turn unknown)");
		if (player == null) {
			return where;
		}
		BlockPos looked = lookedAtPoint(client, player);
		StringBuilder near = new StringBuilder();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1f);
		for (BlockPos pos : live) {
			Vec3 to = Vec3.atCenterOf(pos).subtract(eye);
			double d = to.length();
			double angle = Math.toDegrees(Math.acos(Mth.clamp(look.dot(to.scale(1.0 / d)), -1.0, 1.0)));
			BlockHitResult hit = ray(client, player, to.scale(1.0 / d));
			near.append(String.format(Locale.ROOT, " [%s %.1f° %.0fm ray %s]", pos.toShortString(), angle, d, hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos().toShortString() : "miss"));
		}
		BlockHitResult cross = ray(client, player, look);
		near.append(" cross ").append(cross.getType() == HitResult.Type.BLOCK ? cross.getBlockPos().toShortString() : "miss");
		return where + " · key " + key(room) + " · " + live.size() + " live · sneak " + player.isShiftKeyDown()
			+ " · ether " + holdingEther(player) + " · on point " + (looked != null) + " · ready " + ready(client) + " · next " + (next + 1) + "/" + live.size() + near;
	}

	// ------------------------------------------------------------------ magnet

	/** Every frame: pull the crosshair toward the nearest open face of a close point. */
	public static void frame(Minecraft client) {
		long now = System.nanoTime();
		float dt = lastFrameNs == 0L ? 0f : Math.min(0.1f, (now - lastFrameNs) / 1.0e9f);
		lastFrameNs = now;
		StrayConfig config = StrayConfig.get();
		if (dt <= 0f || !config.etherwarpAimAssist || !ready(client) || live.isEmpty()) {
			restYaw = 0f;
			restPitch = 0f;
			return;
		}
		LocalPlayer player = client.player;
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1f);
		Vec3 target = bestSpot(client, player, eye, look, config.etherwarpAimRange);
		if (target == null) {
			restYaw = 0f;
			restPitch = 0f;
			return;
		}
		SmoothRotate.Rotation want = SmoothRotate.to(eye, target);
		float yaw = player.getYRot();
		float pitch = player.getXRot();
		// Speed 1-100: the share of the way it moves each second (exponential, so it eases in).
		float k = 1f - (float) Math.exp(-config.etherwarpAimSpeed * 0.25f * dt);
		// Small steps add up until they are a whole mouse step, so a slow pull never stalls.
		restYaw += SmoothRotate.normalizeYaw(want.yaw() - yaw) * k;
		restPitch += (want.pitch() - pitch) * k;
		double gcd = mouseStep(client);
		float stepYaw = (float) (restYaw - restYaw % gcd);
		float stepPitch = (float) (restPitch - restPitch % gcd);
		if (stepYaw == 0f && stepPitch == 0f) {
			return;
		}
		restYaw -= stepYaw;
		restPitch -= stepPitch;
		float nextYaw = yaw + stepYaw;
		float nextPitch = Mth.clamp(pitch + stepPitch, -90f, 90f);
		player.setYRot(nextYaw);
		player.setXRot(nextPitch);
		player.setYHeadRot(nextYaw);
		player.forceSetRotation(nextYaw, false, nextPitch, false);
	}

	/** The angle one mouse count turns, like the game's mouse handling. */
	private static double mouseStep(Minecraft client) {
		double sensitivity = client.options == null ? 0.5 : client.options.sensitivity().get();
		double f = sensitivity * 0.6 + 0.2;
		return Math.max(1.0E-4, f * f * f * 8.0 * 0.15);
	}

	/** Spots tested per frame at most; the closest to the crosshair are tested first. */
	private static final int MAX_RAYS = 160;
	/** How far from a face's middle a spot may be (a hair inside its edge). */
	private static final double EDGE = 0.42;

	/**
	 * The spot on the next point closest to your crosshair that you can actually see: on a face
	 * that looks at you and isn't covered by the block next to it, and with nothing in front of
	 * it. First the place on each face nearest to where you look, then a grid over the face, so
	 * when part of a face is hidden it goes for the free part. Null when none is in range.
	 */
	private static Vec3 bestSpot(Minecraft client, LocalPlayer player, Vec3 eye, Vec3 look, double range) {
		BlockPos target = target();
		if (target == null) {
			return null;
		}
		Vec3 center = Vec3.atCenterOf(target);
		double dist = center.distanceTo(eye);
		if (dist > RANGE || dist < 1.0) {
			return null;
		}
		List<double[]> spots = new ArrayList<>();
		BlockPos.MutableBlockPos beside = new BlockPos.MutableBlockPos();
		for (Direction face : Direction.values()) {
			Vec3 normal = Vec3.atLowerCornerOf(face.getUnitVec3i());
			Vec3 mid = center.add(normal.scale(0.499));
			if (mid.subtract(eye).dot(normal) >= 0) {
				// Faces away from you.
				continue;
			}
			beside.setWithOffset(target, face);
			net.minecraft.world.level.block.state.BlockState there = client.level.getBlockState(beside);
			if (net.minecraft.world.level.block.Block.isFaceFull(there.getCollisionShape(client.level, beside), face.getOpposite())) {
				// Covered by the block next to it.
				continue;
			}
			Direction.Axis[] across = acrossAxes(face.getAxis());
			// Where your view crosses this face's plane, kept on the face.
			double denom = look.dot(normal);
			if (denom < -1.0E-4) {
				double t = mid.subtract(eye).dot(normal) / denom;
				if (t > 0) {
					Vec3 cross = eye.add(look.scale(t)).subtract(mid);
					double u = Mth.clamp(component(cross, across[0]), -EDGE, EDGE);
					double v = Mth.clamp(component(cross, across[1]), -EDGE, EDGE);
					addSpot(spots, eye, look, range, mid.add(axis(across[0], u)).add(axis(across[1], v)));
				}
			}
			// A grid over the face for when that place is hidden.
			for (int a = -3; a <= 3; a++) {
				for (int b = -3; b <= 3; b++) {
					addSpot(spots, eye, look, range, mid.add(axis(across[0], a * EDGE / 3.0)).add(axis(across[1], b * EDGE / 3.0)));
				}
			}
		}
		spots.sort((a, b) -> Double.compare(a[0], b[0]));
		int rays = 0;
		for (double[] spot : spots) {
			if (rays++ >= MAX_RAYS) {
				break;
			}
			Vec3 at = new Vec3(spot[1], spot[2], spot[3]);
			BlockHitResult hit = ray(client, player, at.subtract(eye).normalize());
			if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(target)) {
				return at;
			}
		}
		return null;
	}

	private static void addSpot(List<double[]> spots, Vec3 eye, Vec3 look, double range, Vec3 spot) {
		Vec3 to = spot.subtract(eye).normalize();
		double angle = Math.toDegrees(Math.acos(Mth.clamp(look.dot(to), -1.0, 1.0)));
		if (angle <= range) {
			spots.add(new double[] {angle, spot.x, spot.y, spot.z});
		}
	}

	private static double component(Vec3 vec, Direction.Axis axis) {
		return switch (axis) {
			case X -> vec.x;
			case Y -> vec.y;
			case Z -> vec.z;
		};
	}

	private static Direction.Axis[] acrossAxes(Direction.Axis axis) {
		return switch (axis) {
			case X -> new Direction.Axis[] {Direction.Axis.Y, Direction.Axis.Z};
			case Y -> new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z};
			case Z -> new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Y};
		};
	}

	private static Vec3 axis(Direction.Axis axis, double amount) {
		return switch (axis) {
			case X -> new Vec3(amount, 0, 0);
			case Y -> new Vec3(0, amount, 0);
			case Z -> new Vec3(0, 0, amount);
		};
	}

	// ------------------------------------------------------------------ edit

	/** Middle click in edit mode: add or remove the point you look at. True when used. */
	public static boolean onPick(Minecraft client) {
		StrayConfig config = StrayConfig.get();
		if (!config.etherwarpAim || !config.etherwarpAimEdit || client.player == null || client.level == null || client.gui.screen() != null) {
			return false;
		}
		BlockHitResult hit = ray(client, client.player, client.player.getViewVector(1f));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return false;
		}
		DungeonRooms.Room room = DungeonRooms.current();
		String key = key(room);
		if (key == null) {
			chat(room == null ? "§7Not in a room: stand inside the room the point is for."
				: "§7Can't tell which way this room is turned yet: walk in a bit and try again.");
			return true;
		}
		BlockPos world = hit.getBlockPos().immutable();
		BlockPos stored = room == null ? world : room.toRoom(world);
		Set<BlockPos> points = SAVED.computeIfAbsent(key, k -> new LinkedHashSet<>());
		boolean added = points.add(stored);
		if (!added) {
			points.remove(stored);
		}
		if (points.isEmpty()) {
			SAVED.remove(key);
		}
		save();
		tick(client);
		client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(), added ? 1.4f : 0.8f, 0.6f));
		return true;
	}

	public static int count() {
		Set<BlockPos> points = SAVED.get(key(DungeonRooms.current()));
		return points == null ? 0 : points.size();
	}

	/** Clears the points of the room you are in. */
	public static void clearHere() {
		String key = key(DungeonRooms.current());
		if (key != null && SAVED.remove(key) != null) {
			save();
			live = List.of();
		}
	}

	private static void chat(String message) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			client.player.sendSystemMessage(Component.literal("§bStray §8» " + message));
		}
	}

	// ------------------------------------------------------------------ draw

	private static void emitBoxes() {
		Minecraft client = Minecraft.getInstance();
		StrayConfig config = StrayConfig.get();
		if (!config.etherwarpAim || client.player == null || live.isEmpty()) {
			return;
		}
		if (!showing(client.player)) {
			return;
		}
		BlockPos target = target();
		for (int i = 0; i < live.size(); i++) {
			if (!shown(i, config)) {
				continue;
			}
			BlockPos pos = live.get(i);
			boolean hot = pos.equals(aimed);
			boolean isNext = pos.equals(target);
			int color = (isNext ? config.etherwarpAimNextRgb : config.etherwarpAimRgb) & 0xFFFFFF;
			int fill = Mth.clamp(isNext ? config.etherwarpAimNextAlpha : config.etherwarpAimAlpha, 0, 255);
			// The outline follows the opacity, a little stronger so the edges still read.
			int line = Math.min(255, fill * 2 + 50);
			GizmoProperties box = Gizmos.cuboid(new AABB(pos).inflate(hot || isNext ? 0.03 : 0.005),
				GizmoStyle.strokeAndFill(hot ? 0xFFFFFFFF : line << 24 | color, isNext ? 3f : 2f, fill << 24 | color));
			box.setAlwaysOnTop();
		}
	}

	/** Points show in edit mode and while you could warp: the item in hand, or in the hotbar with auto swap. */
	private static boolean showing(LocalPlayer player) {
		StrayConfig config = StrayConfig.get();
		return config.etherwarpAimEdit || holdingEther(player) || config.etherwarpAimAutoSwap && etherSlot(player) >= 0;
	}

	/** Whether point {@code i} is drawn: all of them, or with "next two" only the next and the one after (all in edit mode). */
	private static boolean shown(int i, StrayConfig config) {
		return config.etherwarpAimEdit || !config.etherwarpAimNextTwo || i == next || i == next + 1;
	}

	/** Edit mode HUD: the room you are in, its turn and how many points it has. */
	public static void extract(GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		StrayConfig config = StrayConfig.get();
		if (!config.etherwarpAim || client.player == null || client.gui.hud.isHidden()) {
			return;
		}
		if (showing(client.player)) {
			numbers(graphics, client, config);
		}
		if (!config.etherwarpAimEdit) {
			return;
		}
		DungeonRooms.Room room = DungeonRooms.current();
		String place = room == null ? "No room · " + IslandSaves.label()
			: room.rotated() ? room.name() + " · " + room.rotation() + "°"
			: room.name() + " · turn unknown";
		int n = count();
		String text = "EDIT · " + place + " · " + n + (n == 1 ? " point" : " points") + (n > 0 ? " · next " + (next < n ? Integer.toString(next + 1) : "done") : "");
		Font font = client.font;
		Component line = MenuFont.shadow(text);
		float w = font.width(line) + 16f;
		float h = 16f;
		float x = (graphics.guiWidth() - w) * 0.5f;
		float y = 6f;
		GuiDraw.roundedFine(graphics, x, y, w, h, h * 0.5f, 0xC00B0E13);
		GuiDraw.roundedFine(graphics, x + 7f, y + 6.5f, 3f, 3f, 1.5f, 0xFF000000 | (config.etherwarpAimRgb & 0xFFFFFF));
		GuiDraw.text(graphics, font, line, x + 13f, Math.round(GuiDraw.middle(y, h)), 0xFFEEF1F5, false);
	}

	/** Each point's place in the route, over its block; the next one in its own colour. */
	private static void numbers(GuiGraphicsExtractor graphics, Minecraft client, StrayConfig config) {
		net.minecraft.client.Camera camera = client.gameRenderer.mainCamera();
		if (!camera.isInitialized() || live.isEmpty()) {
			return;
		}
		Vec3 cam = camera.position();
		org.joml.Vector3fc forward = camera.forwardVector();
		Font font = client.font;
		BlockPos target = target();
		for (int i = 0; i < live.size(); i++) {
			if (!shown(i, config)) {
				continue;
			}
			BlockPos pos = live.get(i);
			Vec3 at = Vec3.atCenterOf(pos).add(0, 0.85, 0);
			Vec3 rel = at.subtract(cam);
			if (rel.x * forward.x() + rel.y * forward.y() + rel.z * forward.z() <= 0.1) {
				continue;
			}
			Vec3 ndc = client.gameRenderer.projectPointToScreen(at);
			if (Math.abs(ndc.x) > 1.1 || Math.abs(ndc.y) > 1.1) {
				continue;
			}
			float x = (float) ((ndc.x * 0.5 + 0.5) * graphics.guiWidth());
			float y = (float) ((-ndc.y * 0.5 + 0.5) * graphics.guiHeight());
			boolean isNext = pos.equals(target);
			Component label = MenuFont.shadow(Integer.toString(i + 1));
			float w = Math.max(14f, font.width(label) + 8f);
			float h = 14f;
			int color = 0xFF000000 | ((isNext ? config.etherwarpAimNextRgb : config.etherwarpAimRgb) & 0xFFFFFF);
			GuiDraw.roundedFine(graphics, x - w * 0.5f, y - h, w, h, h * 0.5f, isNext ? color : 0xC00B0E13);
			GuiDraw.text(graphics, font, label, x - font.width(label) * 0.5f, Math.round(GuiDraw.middle(y - h, h)), isNext ? 0xFF0B0E13 : color, false);
		}
	}

	// ------------------------------------------------------------------ storage

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		JsonObject rooms = IslandSaves.readIslands(FILE);
		for (String key : rooms.keySet()) {
			JsonElement element = rooms.get(key);
			if (!element.isJsonArray()) {
				continue;
			}
			Set<BlockPos> points = new LinkedHashSet<>();
			for (JsonElement point : element.getAsJsonArray()) {
				if (point.isJsonArray() && point.getAsJsonArray().size() == 3) {
					JsonArray xyz = point.getAsJsonArray();
					points.add(new BlockPos(xyz.get(0).getAsInt(), xyz.get(1).getAsInt(), xyz.get(2).getAsInt()));
				}
			}
			if (!points.isEmpty()) {
				SAVED.put(key, points);
			}
		}
	}

	private static void save() {
		JsonObject rooms = new JsonObject();
		for (Map.Entry<String, Set<BlockPos>> entry : SAVED.entrySet()) {
			JsonArray list = new JsonArray();
			for (BlockPos pos : entry.getValue()) {
				JsonArray xyz = new JsonArray();
				xyz.add(pos.getX());
				xyz.add(pos.getY());
				xyz.add(pos.getZ());
				list.add(xyz);
			}
			rooms.add(entry.getKey(), list);
		}
		IslandSaves.writeIslands(FILE, rooms);
	}
}
