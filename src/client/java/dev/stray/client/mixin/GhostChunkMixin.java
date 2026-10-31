package dev.stray.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.stray.client.editor.GhostBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** World editor: during the client tick, edited positions read as the server's real block. */
@Mixin(LevelChunk.class)
public class GhostChunkMixin {
	@ModifyReturnValue(method = "getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("RETURN"))
	private BlockState stray$serverBlock(BlockState original, BlockPos pos) {
		return GhostBlocks.physical((LevelChunk) (Object) this, pos, original);
	}

	@ModifyReturnValue(method = "getFluidState(III)Lnet/minecraft/world/level/material/FluidState;", at = @At("RETURN"))
	private FluidState stray$serverFluid(FluidState original, int x, int y, int z) {
		return GhostBlocks.physicalFluid((LevelChunk) (Object) this, x, y, z, original);
	}
}
