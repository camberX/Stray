package dev.stray.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.stray.client.editor.GhostBlocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Real block changes keep using the section's real emptiness (lighting and heightmaps depend on it). */
@Mixin(LevelChunk.class)
public class GhostChunkEmptyMixin {
	@WrapOperation(
		method = "setBlockState",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunkSection;hasOnlyAir()Z")
	)
	private boolean stray$realEmpty(LevelChunkSection section, Operation<Boolean> original) {
		if (section instanceof GhostBlocks.GhostSection ghost && ghost.stray$ghost()) {
			return ghost.stray$reallyEmpty();
		}
		return original.call(section);
	}
}
