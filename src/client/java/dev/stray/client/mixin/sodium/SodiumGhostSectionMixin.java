package dev.stray.client.mixin.sodium;

import dev.stray.client.editor.GhostBlocks;
import net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSection;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** World editor (Sodium): edits go into Sodium's clone of the section, never the world. */
@Mixin(value = ClonedChunkSection.class, remap = false)
public class SodiumGhostSectionMixin {
	@Shadow
	@Final
	@Mutable
	private PalettedContainerRO<BlockState> blockData;

	@Inject(method = "<init>", at = @At("RETURN"), require = 0)
	private void stray$ghostClone(Level level, LevelChunk chunk, LevelChunkSection section, SectionPos pos, CallbackInfo ci) {
		if (section == null || !GhostBlocks.touches(level, pos.getX(), pos.getY(), pos.getZ())) {
			return;
		}
		PalettedContainer<BlockState> states;
		if (this.blockData instanceof PalettedContainer<BlockState> clone && this.blockData != section.getStates()) {
			states = clone;
		} else {
			// Shared empty container, or not a plain copy: start from a fresh copy instead.
			states = section.getStates().copy();
			this.blockData = states;
		}
		GhostBlocks.decorate(level, pos.getX(), pos.getY(), pos.getZ(), states);
	}
}
