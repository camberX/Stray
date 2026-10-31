package dev.stray.client.mixin;

import dev.stray.client.editor.GhostBlocks;
import net.minecraft.client.renderer.chunk.SectionCopy;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** World editor (vanilla renderer): edits go into the renderer's copy of the section, never the world. */
@Mixin(SectionCopy.class)
public class GhostSectionCopyMixin {
	@Shadow
	@Final
	@Mutable
	private @Nullable PalettedContainer<BlockState> section;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void stray$ghostCopy(LevelChunk chunk, int sectionIndex, CallbackInfo ci) {
		LevelChunkSection[] sections = chunk.getSections();
		if (sectionIndex < 0 || sectionIndex >= sections.length) {
			return;
		}
		int sx = chunk.getPos().x();
		int sy = chunk.getSectionYFromSectionIndex(sectionIndex);
		int sz = chunk.getPos().z();
		if (!GhostBlocks.touches(chunk.getLevel(), sx, sy, sz)) {
			return;
		}
		if (this.section == null) {
			this.section = sections[sectionIndex].getStates().copy();
		}
		GhostBlocks.decorate(chunk.getLevel(), sx, sy, sz, this.section);
	}
}
