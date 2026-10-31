package dev.stray.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.stray.client.editor.GhostBlocks;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * World editor: a section with placed (purely visual) blocks must not count as "only air", or
 * the renderers skip it and never draw them. The real emptiness stays available for the chunk's
 * own bookkeeping (see GhostChunkEmptyMixin).
 */
@Mixin(LevelChunkSection.class)
public abstract class GhostSectionMixin implements GhostBlocks.GhostSection {
	@Shadow
	private short nonEmptyBlockCount;

	@Unique
	private boolean stray$ghost;

	@ModifyReturnValue(method = "hasOnlyAir", at = @At("RETURN"))
	private boolean stray$ghostNotEmpty(boolean original) {
		return original && !stray$ghost;
	}

	@Override
	public boolean stray$ghost() {
		return stray$ghost;
	}

	@Override
	public void stray$setGhost(boolean ghost) {
		stray$ghost = ghost;
	}

	@Override
	public boolean stray$reallyEmpty() {
		return nonEmptyBlockCount == 0;
	}
}
