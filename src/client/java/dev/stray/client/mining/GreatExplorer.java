package dev.stray.client.mining;

import com.mojang.blaze3d.platform.InputConstants;
import dev.stray.client.config.StrayConfig;
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
 * open a screen. Left click lets go for a tick, the chest is right-clicked,
 * then left click is held again only if it is still down.
 */
public final class GreatExplorer {
	private static final double RANGE_SQ = 6.0 * 6.0;
	private static final long REMEMBER_MS = 15_000L;

	private enum Phase {
		IDLE,
		PAUSE
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
		if (!StrayConfig.get().greatExplorerEnabled || !SkyblockLocation.inCrystalHollows()) {
			if (phase != Phase.IDLE) {
				resume(client);
			}
			return;
		}
		if (phase == Phase.PAUSE) {
			click(client);
			return;
		}
		watch(client);
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
			// The block can predict a chest menu. Great Explorer does not open one.
			if (client.screen != null) {
				client.setScreen(null);
			}
		}
		resume(client);
	}

	private static void resume(Minecraft client) {
		if (stillHolding(client)) {
			client.options.keyAttack.setDown(true);
		}
		phase = Phase.IDLE;
		aim = null;
	}

	private static void releaseAttack(Minecraft client) {
		client.options.keyAttack.setDown(false);
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
		if (!spawned.containsKey(pos.asLong())) {
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
			if (old || gone || far) {
				it.remove();
				opened.remove(entry.getKey());
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
