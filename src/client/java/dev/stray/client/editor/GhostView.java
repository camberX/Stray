package dev.stray.client.editor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

/** The world as the editor draws it (edits included), for aiming at what you actually see. */
final class GhostView implements BlockGetter {
	private final Level level;

	GhostView(Level level) {
		this.level = level;
	}

	@Override
	public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
		return level.getBlockEntity(pos);
	}

	@Override
	public BlockState getBlockState(BlockPos pos) {
		return GhostBlocks.shown(pos, level.getBlockState(pos));
	}

	@Override
	public FluidState getFluidState(BlockPos pos) {
		return getBlockState(pos).getFluidState();
	}

	@Override
	public int getHeight() {
		return level.getHeight();
	}

	@Override
	public int getMinY() {
		return level.getMinY();
	}
}
