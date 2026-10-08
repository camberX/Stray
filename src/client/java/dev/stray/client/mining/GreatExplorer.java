package dev.stray.client.mining;

import com.mojang.blaze3d.platform.InputConstants;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.debug.StrayDebug;
import dev.stray.client.farming.FarmKeys;
import dev.stray.client.location.SkyblockLocation;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Great Explorer chests spawn next to you while mining hardstone. They do not
 * open a screen. Left click lets go, the chest is right-clicked, and left
 * click stays off while the crosshair is within half a block of that
 * chest. It is held again on stone only if it is still physically down.
 */
public final class GreatExplorer {
	private static final double RANGE_SQ = 6.0 * 6.0;
	private static final long REMEMBER_MS = 15_000L;
	/** Left click is blocked this far outside the chest block. */
	private static final double CHEST_PAD = 0.5;
	private static final double CHEST_PAD_SQ = CHEST_PAD * CHEST_PAD;

	private enum Phase {
		IDLE,
		PAUSE,
		GUARD,
		SCREEN
	}

	private static final Map<Long, Long> spawned = new HashMap<>();
	private static final Set<Long> opened = new HashSet<>();
	private static Phase phase = Phase.IDLE;
	private static BlockPos aim;

	private GreatExplorer() {
	}

	public static void reset() {
		spawned.clear();
		opened.clear();
		phase = Phase.IDLE;
		aim = null;
	}

	public static void onPacket(Packet<?> packet) {
		if (!StrayConfig.get().greatExplorerEnabled) {
			return;
		}
		if (!(packet instanceof ClientboundBlockUpdatePacket update)) {
			return;
		}
		BlockPos pos = update.getPos().immutable();
		BlockState state = update.getBlockState();
		Minecraft.getInstance().execute(() -> onBlock(pos, state));
	}

	/**
	 * Runs at the start of {@code handleKeybinds}, before this tick's attack.
	 */
	public static void preKeybinds(Minecraft client) {
		if (client.player == null || client.level == null || client.gameMode == null) {
			reset();
			return;
		}
		if (!active()) {
			if (phase != Phase.IDLE) {
				resume(client);
			}
			return;
		}
		if (phase == Phase.SCREEN && client.screen != null) {
			releaseAttack(client);
			return;
		}
		if (phase == Phase.PAUSE) {
			click(client);
			return;
		}
		if (nearChest(client)) {
			releaseAttack(client);
			phase = Phase.GUARD;
			BlockPos chest = lookedAt(client);
			if (chest != null && !opened.contains(chest.asLong())) {
				watch(client);
			}
			return;
		}
		if (phase == Phase.GUARD || phase == Phase.SCREEN) {
			resume(client);
		}
		watch(client);
	}

	/** {@code /stray debug explorer} right-clicks any chest, anywhere. */
	private static boolean active() {
		if (!StrayConfig.get().greatExplorerEnabled) {
			return false;
		}
		return debug() || SkyblockLocation.inCrystalHollows();
	}

	private static boolean debug() {
		return StrayDebug.enabled("explorer");
	}

	private static void onBlock(BlockPos pos, BlockState state) {
		if (!isChest(state)) {
			spawned.remove(pos.asLong());
			opened.remove(pos.asLong());
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || !SkyblockLocation.inCrystalHollows()) {
			return;
		}
		if (!mining(client)) {
			return;
		}
		if (client.player.distanceToSqr(Vec3.atCenterOf(pos)) > RANGE_SQ) {
			return;
		}
		spawned.put(pos.asLong(), System.currentTimeMillis());
	}

	private static void watch(Minecraft client) {
		prune(client);
		BlockPos chest = lookedAt(client);
		if (chest == null || opened.contains(chest.asLong())) {
			return;
		}
		if (!mining(client)) {
			return;
		}
		aim = chest;
		phase = Phase.PAUSE;
		releaseAttack(client);
	}

	private static void click(Minecraft client) {
		releaseAttack(client);
		BlockPos chest = aim;
		BlockHitResult hit = lookedAtHit(client);
		boolean same = chest != null && hit != null && hit.getBlockPos().equals(chest);
		if (same && isChest(client.level.getBlockState(chest))) {
			client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
			client.player.swing(InteractionHand.MAIN_HAND);
			opened.add(chest.asLong());
		}
		// A chest screen keeps attack released until it closes. Great Explorer
		// does not open one, so attack stays released within half a block of
		// the chest and is held again on stone outside that.
		if (client.screen != null) {
			phase = Phase.SCREEN;
			return;
		}
		if (nearChest(client)) {
			phase = Phase.GUARD;
			return;
		}
		resume(client);
	}

	/**
	 * True when the crosshair is on a chest this feature handles, or within
	 * half a block of one. Stone past that can still be mined.
	 */
	private static boolean nearChest(Minecraft client) {
		BlockHitResult hit = lookedAtHit(client);
		if (hit == null || client.level == null) {
			return false;
		}
		Vec3 point = hit.getLocation();
		BlockPos origin = hit.getBlockPos();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos pos = origin.offset(dx, dy, dz);
					if (!guards(client, pos)) {
						continue;
					}
					if (withinHalfBlock(point, pos)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static boolean guards(Minecraft client, BlockPos pos) {
		if (!isChest(client.level.getBlockState(pos))) {
			return false;
		}
		if (debug()) {
			return true;
		}
		long key = pos.asLong();
		return opened.contains(key) || spawned.containsKey(key);
	}

	private static boolean withinHalfBlock(Vec3 point, BlockPos pos) {
		double minX = pos.getX();
		double minY = pos.getY();
		double minZ = pos.getZ();
		double x = Math.max(minX, Math.min(point.x, minX + 1.0));
		double y = Math.max(minY, Math.min(point.y, minY + 1.0));
		double z = Math.max(minZ, Math.min(point.z, minZ + 1.0));
		double dx = point.x - x;
		double dy = point.y - y;
		double dz = point.z - z;
		return dx * dx + dy * dy + dz * dz <= CHEST_PAD_SQ;
	}

	private static void resume(Minecraft client) {
		if (stillHolding(client)) {
			client.options.keyAttack.setDown(true);
		}
		phase = Phase.IDLE;
		aim = null;
	}

	private static void releaseAttack(Minecraft client) {
		KeyMapping attack = client.options.keyAttack;
		while (attack.consumeClick()) {
		}
		attack.setDown(false);
		if (client.gameMode != null && client.gameMode.isDestroying()) {
			client.gameMode.stopDestroyBlock();
		}
	}

	private static BlockPos lookedAt(Minecraft client) {
		BlockHitResult hit = lookedAtHit(client);
		if (hit == null) {
			return null;
		}
		BlockPos pos = hit.getBlockPos();
		if (!debug() && !spawned.containsKey(pos.asLong())) {
			return null;
		}
		if (!isChest(client.level.getBlockState(pos))) {
			spawned.remove(pos.asLong());
			opened.remove(pos.asLong());
			return null;
		}
		return pos;
	}

	private static BlockHitResult lookedAtHit(Minecraft client) {
		HitResult hit = client.hitResult;
		if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		return block;
	}

	private static void prune(Minecraft client) {
		long now = System.currentTimeMillis();
		Iterator<Map.Entry<Long, Long>> it = spawned.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Long, Long> entry = it.next();
			BlockPos pos = BlockPos.of(entry.getKey());
			boolean old = now - entry.getValue() > REMEMBER_MS;
			boolean gone = client.level.hasChunkAt(pos) && !isChest(client.level.getBlockState(pos));
			boolean far = client.player.distanceToSqr(Vec3.atCenterOf(pos)) > RANGE_SQ * 4.0;
			if (gone || far) {
				it.remove();
				opened.remove(entry.getKey());
			} else if (old) {
				it.remove();
			}
		}
	}

	private static boolean mining(Minecraft client) {
		return FarmKeys.breaking() || client.options.keyAttack.isDown() || stillHolding(client);
	}

	private static boolean stillHolding(Minecraft client) {
		return physical(client, client.options.keyAttack);
	}

	private static boolean physical(Minecraft client, KeyMapping mapping) {
		if (client.getWindow() == null || mapping == null) {
			return false;
		}
		InputConstants.Key key = InputConstants.getKey(mapping.saveString());
		if (key == null || key.equals(InputConstants.UNKNOWN)) {
			return false;
		}
		return switch (key.getType()) {
			case KEYSYM -> InputConstants.isKeyDown(client.getWindow(), key.getValue());
			case MOUSE -> GLFW.glfwGetMouseButton(client.getWindow().handle(), key.getValue()) == GLFW.GLFW_PRESS;
			default -> false;
		};
	}

	private static boolean isChest(BlockState state) {
		return state != null && (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST));
	}
}
