package dev.stray.client.mixin;

import dev.stray.client.editor.GhostBlocks;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** World editor: server block updates keep edited positions edited (and apply replace rules). */
@Mixin(ClientLevel.class)
public class GhostLevelMixin {
	@Inject(method = "setServerVerifiedBlockState", at = @At("HEAD"), cancellable = true)
	private void stray$ghostServerBlock(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
		ClientLevel level = (ClientLevel) (Object) this;
		BlockState shown = GhostBlocks.fromServer(level, pos, state);
		if (shown == state) {
			return;
		}
		ci.cancel();
		if (shown != null) {
			GhostBlocks.passThrough(() -> level.setServerVerifiedBlockState(pos, shown, flags));
		}
	}

	@Inject(method = "syncBlockState", at = @At("HEAD"), cancellable = true)
	private void stray$ghostSync(BlockPos pos, BlockState state, Vec3 playerPos, CallbackInfo ci) {
		ClientLevel level = (ClientLevel) (Object) this;
		BlockState shown = GhostBlocks.fromServer(level, pos, state);
		if (shown == state) {
			return;
		}
		ci.cancel();
		if (shown != null) {
			GhostBlocks.passThrough(() -> level.syncBlockState(pos, shown, playerPos));
		}
	}
}
