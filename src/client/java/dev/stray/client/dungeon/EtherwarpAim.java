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
		clicked = null;
		aimed = null;
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
		Set<BlockPos> stored = SAVED.get(key(room));
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
		if (!ready(client) || live.isEmpty()) {
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
			waitMs = delay(StrayConfig.get());
		}
		if (target.equals(clicked) || now - aimedSince < waitMs) {
			return;
		}
		// The use key on an entity uses the entity, not the item: wait until it is out of the way.
		if (client.hitResult != null && client.hitResult.getType() == HitResult.Type.ENTITY) {
			return;
		}
		clicked = target;
		OdinClicks.rightClick();
	}

	private static long delay(StrayConfig config) {
		long base = Math.max(0, config.etherwarpAimDelay);
		long spread = Math.max(5L, base / 4);
		return base + ThreadLocalRandom.current().nextLong(0, spread + 1);
	}

	private static boolean ready(Minecraft client) {
		LocalPlayer player = client.player;
		return StrayConfig.get().etherwarpAim && player != null && client.level != null && client.gui.screen() == null
			&& !player.isSpectator() && player.isShiftKeyDown() && holdingEther(player)
			&& !dev.stray.client.movement.Freecam.active();
	}

	public static boolean holdingEther(LocalPlayer player) {
		ItemStack stack = player.getMainHandItem();
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
		BlockPos pos = hit.getBlockPos();
		return live.contains(pos) ? pos.immutable() : null;
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
			+ " · ether " + holdingEther(player) + " · on point " + (looked != null) + " · ready " + ready(client) + near;
	}

	// ------------------------------------------------------------------ magnet

	/** Every frame: pull the crosshair toward a close point. */
	public static void frame(Minecraft client) {
		long now = System.nanoTime();
		float dt = lastFrameNs == 0L ? 0f : Math.min(0.1f, (now - lastFrameNs) / 1.0e9f);
		lastFrameNs = now;
		StrayConfig config = StrayConfig.get();
		if (dt <= 0f || !config.etherwarpAimAssist || !ready(client) || live.isEmpty()) {
			return;
		}
		LocalPlayer player = client.player;
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1f);
		BlockPos best = null;
		double bestAngle = config.etherwarpAimRange;
		for (BlockPos pos : live) {
			Vec3 center = Vec3.atCenterOf(pos);
			Vec3 to = center.subtract(eye);
			double dist = to.length();
			if (dist > RANGE || dist < 1.0) {
				continue;
			}
			double angle = Math.toDegrees(Math.acos(Mth.clamp(look.dot(to.scale(1.0 / dist)), -1.0, 1.0)));
			if (angle > bestAngle) {
				continue;
			}
			// Only points you could warp to from here: nothing in between.
			BlockHitResult hit = ray(client, player, to.scale(1.0 / dist));
			if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos)) {
				continue;
			}
			best = pos;
			bestAngle = angle;
		}
		if (best == null) {
			return;
		}
		SmoothRotate.Rotation want = SmoothRotate.to(eye, Vec3.atCenterOf(best));
		float yaw = player.getYRot();
		float pitch = player.getXRot();
		// Speed 1-100: how much of the way it moves each second (exponential, so it eases in).
		float k = 1f - (float) Math.exp(-config.etherwarpAimSpeed * 0.25f * dt);
		float nextYaw = yaw + SmoothRotate.normalizeYaw(want.yaw() - yaw) * k;
		float nextPitch = pitch + (want.pitch() - pitch) * k;
		SmoothRotate.apply(player, nextYaw, nextPitch);
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
		if (!config.etherwarpAimEdit && !holdingEther(client.player)) {
			return;
		}
		int rgb = config.etherwarpAimRgb & 0xFFFFFF;
		for (BlockPos pos : live) {
			boolean hot = pos.equals(aimed);
			GizmoProperties box = Gizmos.cuboid(new AABB(pos).inflate(hot ? 0.03 : 0.005),
				GizmoStyle.strokeAndFill(hot ? 0xFFFFFFFF : 0xE6000000 | rgb, hot ? 3f : 2f, 0x50000000 | rgb));
			box.setAlwaysOnTop();
		}
	}

	/** Edit mode HUD: the room you are in, its turn and how many points it has. */
	public static void extract(GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		StrayConfig config = StrayConfig.get();
		if (!config.etherwarpAim || !config.etherwarpAimEdit || client.player == null || client.gui.hud.isHidden()) {
			return;
		}
		DungeonRooms.Room room = DungeonRooms.current();
		String place = room == null ? "No room · " + IslandSaves.label()
			: room.rotated() ? room.name() + " · " + room.rotation() + "°"
			: room.name() + " · turn unknown";
		int n = count();
		String text = "EDIT · " + place + " · " + n + (n == 1 ? " point" : " points");
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
