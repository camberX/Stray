package dev.stray.client.mixin;

import dev.stray.client.editor.GhostBlocks;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** World editor: edits go back into every chunk the server sends. */
@Mixin(ClientChunkCache.class)
public class GhostChunkCacheMixin {
	@Inject(method = "replaceWithPacketData", at = @At("RETURN"))
	private void stray$ghostChunk(CallbackInfoReturnable<LevelChunk> cir) {
		GhostBlocks.onChunkLoaded(cir.getReturnValue());
	}

	@Inject(method = "drop", at = @At("HEAD"))
	private void stray$ghostDrop(ChunkPos pos, CallbackInfo ci) {
		GhostBlocks.onChunkDropped(pos);
	}
}
