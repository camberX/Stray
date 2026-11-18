package dev.stray.client.mixin;

import dev.stray.client.dungeon.SecretHitboxes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Secret hitboxes: the shape the crosshair finds on levers, buttons, secret skulls and mushrooms. Full edition only. */
@Mixin({LeverBlock.class, ButtonBlock.class, SkullBlock.class, MushroomBlock.class})
public class SecretHitboxMixin {
	@Inject(method = "getShape", at = @At("HEAD"), cancellable = true, require = 0)
	private void stray$secretHitbox(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
		VoxelShape shape = SecretHitboxes.shape(this, state, level, pos);
		if (shape != null) {
			cir.setReturnValue(shape);
		}
	}
}
