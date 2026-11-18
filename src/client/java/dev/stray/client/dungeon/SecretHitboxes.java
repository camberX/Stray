package dev.stray.client.dungeon;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.location.SkyblockLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bigger hitboxes for secrets, like NoammAddons' Secret Hitboxes (Secrets.kt): levers and
 * buttons grow over their wall, wither essence and redstone key skulls and mushrooms become a
 * full block, so they are easier to hit. The outline you see while looking at one is, by
 * mode: the bigger box, the block's normal one, or the normal one only while your crosshair is
 * really on the block (the click still lands anywhere in the bigger box).
 * Full edition only.
 */
public final class SecretHitboxes {
	/** Wither essence and redstone key skulls, by their texture profile id (NoammAddons' DungeonUtils). */
	private static final Set<String> SECRET_SKULLS = Set.of(
		"2865274b-3097-394e-8149-ec629c72d850", "e0f3e929-869e-3dca-9504-54c666ee6f23",
		"fed95410-aba1-39df-9b95-1d4f361eb66e");
	/** The F7 lights device: growing these would make you hit the lever next to the one you aim at. */
	private static final Set<BlockPos> LIGHTS_DEVICE = Set.of(
		new BlockPos(61, 136, 142), new BlockPos(60, 136, 142), new BlockPos(59, 136, 142),
		new BlockPos(62, 135, 142), new BlockPos(61, 135, 142), new BlockPos(59, 135, 142),
		new BlockPos(58, 135, 142), new BlockPos(62, 134, 142), new BlockPos(61, 134, 142),
		new BlockPos(59, 134, 142), new BlockPos(58, 134, 142), new BlockPos(61, 133, 142),
		new BlockPos(60, 133, 142), new BlockPos(59, 133, 142));

	private static final Map<BlockState, VoxelShape> LEVERS = new ConcurrentHashMap<>();
	private static final Map<BlockState, VoxelShape> BUTTONS = new ConcurrentHashMap<>();
	private static volatile float leverSize = -1f;
	private static volatile float buttonSize = -1f;
	/** Set while the game works out the outline of the block you look at. */
	private static boolean outline;

	private SecretHitboxes() {
	}

	public static final String[] MODES = {"Expanded", "Normal outline", "Only on the block"};

	/** Outline extraction starts or ends. Expanded mode lets the outline use the bigger box. */
	public static void outline(boolean on) {
		outline = on && StrayConfig.get().secretHitboxMode != 0;
	}

	/**
	 * "Only on the block": true when the block you target is a bigger secret but the crosshair
	 * misses its real shape, so no outline is drawn.
	 */
	public static boolean hideOutline() {
		if (StrayConfig.get().secretHitboxMode != 2) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		Entity camera = client.getCameraEntity();
		if (!(client.hitResult instanceof BlockHitResult hit) || hit.getType() == HitResult.Type.MISS || client.level == null || camera == null || client.player == null) {
			return false;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = client.level.getBlockState(pos);
		boolean was = outline;
		try {
			outline = false;
			if (shape(state.getBlock(), state, client.level, pos) == null) {
				return false;
			}
			outline = true;
			VoxelShape real = state.getShape(client.level, pos, CollisionContext.of(camera));
			Vec3 eye = camera.getEyePosition(1.0F);
			Vec3 end = eye.add(camera.getViewVector(1.0F).scale(client.player.blockInteractionRange() + 1.0));
			return real.isEmpty() || real.clip(eye, end, pos) == null;
		} finally {
			outline = was;
		}
	}

	/** The shape to use instead of the block's own, or null to keep it. */
	public static VoxelShape shape(Object block, BlockState state, BlockGetter level, BlockPos pos) {
		// Only your own world (the crosshair's pick and the outline), never chunk meshing or a server.
		if (outline || !(level instanceof ClientLevel)) {
			return null;
		}
		StrayConfig config = StrayConfig.get();
		if (!config.secretHitboxes || config.secretHitboxesDungeonsOnly && !SkyblockLocation.inDungeon && !DevMode.on()) {
			return null;
		}
		if (block instanceof LeverBlock) {
			return config.secretHitboxLever && !LIGHTS_DEVICE.contains(pos) ? lever(state, config.secretHitboxLeverSize) : null;
		}
		if (block instanceof ButtonBlock) {
			return config.secretHitboxButton ? button(state, config.secretHitboxButtonSize) : null;
		}
		if (block instanceof SkullBlock) {
			return config.secretHitboxSkull && secretSkull(level, pos) ? Shapes.block() : null;
		}
		if (block instanceof MushroomBlock) {
			return config.secretHitboxMushroom ? Shapes.block() : null;
		}
		return null;
	}

	private static boolean secretSkull(BlockGetter level, BlockPos pos) {
		if (!(level.getBlockEntity(pos) instanceof SkullBlockEntity skull) || skull.getOwnerProfile() == null) {
			return false;
		}
		java.util.UUID id = skull.getOwnerProfile().partialProfile().id();
		return id != null && SECRET_SKULLS.contains(id.toString());
	}

	/** NoammAddons' getLeverShape: a size x size plate as deep as it is wide, against the wall. */
	private static VoxelShape lever(BlockState state, float size) {
		if (size != leverSize) {
			LEVERS.clear();
			leverSize = size;
		}
		return LEVERS.computeIfAbsent(state, s -> {
			double px = size * 16.0;
			return Shapes.rotateAttachFace(Block.boxZ(px, px, 16.0 - px, 16.0))
				.get(s.getValue(FaceAttachedHorizontalDirectionalBlock.FACE))
				.get(s.getValue(FaceAttachedHorizontalDirectionalBlock.FACING));
		});
	}

	/** NoammAddons' getButtonShape: a size x size plate as thick as the button. */
	private static VoxelShape button(BlockState state, float size) {
		if (size != buttonSize) {
			BUTTONS.clear();
			buttonSize = size;
		}
		return BUTTONS.computeIfAbsent(state, s -> {
			boolean powered = s.getValue(ButtonBlock.POWERED);
			return Shapes.rotateAttachFace(Block.boxZ(size * 16.0, 16.0 - (powered ? 1 : 2), 16.0))
				.get(s.getValue(FaceAttachedHorizontalDirectionalBlock.FACE))
				.get(s.getValue(FaceAttachedHorizontalDirectionalBlock.FACING));
		});
	}
}
