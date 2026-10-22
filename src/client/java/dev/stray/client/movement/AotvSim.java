package dev.stray.client.movement;

import dev.stray.client.config.StrayConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.UUID;

/**
 * Singleplayer Aspect of the Void stand-in. A diamond shovel right-click
 * transmits 8 blocks and lands on the block center. Sneak right-click
 * etherwarps onto the aimed block, up to 72 blocks, if the player fits.
 */
public final class AotvSim {
	private static final double TRANSMIT = 8.0;
	private static final double ETHER = 72.0;
	private static final double STEP = 0.25;
	private static int cooldown;

	private AotvSim() {
	}

	public static void tick() {
		if (cooldown > 0) {
			cooldown--;
		}
	}

	public static boolean use(Minecraft client) {
		if (!ready(client)) {
			return false;
		}
		if (cooldown > 0) {
			return true;
		}
		LocalPlayer player = client.player;
		if (player.isShiftKeyDown()) {
			if (etherwarp(client, player)) {
				cooldown = 4;
				player.swing(InteractionHand.MAIN_HAND);
			}
			return true;
		}
		if (transmit(client, player)) {
			cooldown = 4;
			player.swing(InteractionHand.MAIN_HAND);
			return true;
		}
		return false;
	}

	private static boolean ready(Minecraft client) {
		if (client == null || !client.isLocalServer() || client.getSingleplayerServer() == null) {
			return false;
		}
		if (!StrayConfig.get().aotvSimEnabled) {
			return false;
		}
		if (client.player == null || client.level == null || client.screen != null) {
			return false;
		}
		return client.player.getMainHandItem().getItem() == Items.DIAMOND_SHOVEL;
	}

	private static boolean transmit(Minecraft client, LocalPlayer player) {
		Vec3 look = player.getLookAngle();
		Vec3 eye = player.getEyePosition();
		Vec3 last = null;
		for (double dist = STEP; dist <= TRANSMIT + 1.0E-4; dist += STEP) {
			Vec3 point = eye.add(look.scale(dist));
			Vec3 feet = standAt(client.level, player, point);
			if (feet == null || !fits(client.level, player, feet)) {
				break;
			}
			last = feet;
		}
		if (last == null) {
			return false;
		}
		teleport(client, player, last);
		client.level.playLocalSound(last.x, last.y, last.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.15f, false);
		return true;
	}

	private static boolean etherwarp(Minecraft client, LocalPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 end = eye.add(look.scale(ETHER));
		BlockHitResult hit = client.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (hit.getType() == HitResult.Type.MISS) {
			fail(client, player);
			return false;
		}
		BlockPos pos = hit.getBlockPos();
		if (eye.distanceTo(Vec3.atCenterOf(pos)) > ETHER) {
			fail(client, player);
			return false;
		}
		double top = topY(client.level, pos);
		if (Double.isNaN(top)) {
			fail(client, player);
			return false;
		}
		Vec3 feet = new Vec3(pos.getX() + 0.5, top, pos.getZ() + 0.5);
		if (!fits(client.level, player, feet)) {
			fail(client, player);
			return false;
		}
		teleport(client, player, feet);
		client.level.playLocalSound(feet.x, feet.y, feet.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1f, false);
		return true;
	}

	private static void fail(Minecraft client, LocalPlayer player) {
		cooldown = 4;
		client.level.playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.7f, 0.6f, false);
	}

	private static Vec3 standAt(Level level, Player player, Vec3 point) {
		int x = Mth.floor(point.x);
		int z = Mth.floor(point.z);
		int startY = Mth.floor(point.y);
		for (int y = startY; y >= startY - 3; y--) {
			BlockPos pos = new BlockPos(x, y, z);
			double top = topY(level, pos);
			if (Double.isNaN(top)) {
				continue;
			}
			Vec3 feet = new Vec3(x + 0.5, top, z + 0.5);
			if (fits(level, player, feet)) {
				return feet;
			}
		}
		Vec3 air = new Vec3(x + 0.5, startY, z + 0.5);
		return fits(level, player, air) ? air : null;
	}

	private static double topY(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		VoxelShape shape = state.getCollisionShape(level, pos);
		if (shape.isEmpty()) {
			return Double.NaN;
		}
		return pos.getY() + shape.max(Direction.Axis.Y);
	}

	private static boolean fits(Level level, Player player, Vec3 feet) {
		double r = player.getBbWidth() * 0.5;
		AABB box = new AABB(feet.x - r, feet.y, feet.z - r, feet.x + r, feet.y + player.getBbHeight(), feet.z + r);
		return level.noCollision(player, box);
	}

	private static void teleport(Minecraft client, LocalPlayer player, Vec3 feet) {
		player.teleportTo(feet.x, feet.y, feet.z);
		player.setDeltaMovement(Vec3.ZERO);
		IntegratedServer server = client.getSingleplayerServer();
		if (server == null) {
			return;
		}
		UUID id = player.getUUID();
		double x = feet.x;
		double y = feet.y;
		double z = feet.z;
		server.execute(() -> {
			var sp = server.getPlayerList().getPlayer(id);
			if (sp != null) {
				sp.teleportTo(x, y, z);
				sp.setDeltaMovement(Vec3.ZERO);
			}
		});
	}
}
