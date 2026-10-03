package dev.stray.client.mixin.sodium;

import dev.stray.client.mining.FocusMode;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockRenderer.class, remap = false)
public class SodiumBlockRendererMixin {
	@Unique
	private BlockState stray$state;

	@Inject(method = "renderModel", at = @At("HEAD"), remap = false)
	private void stray$capture(BlockStateModel model, BlockState state, BlockPos pos, BlockPos origin, CallbackInfo ci) {
		this.stray$state = state;
	}

	@Inject(method = "bufferQuad", at = @At("HEAD"), remap = false)
	private void stray$focus(MutableQuadViewImpl quad, float[] brightness, Material material, CallbackInfo ci) {
		BlockState state = this.stray$state;
		if (!FocusMode.visual() || state == null) {
			return;
		}
		int marker = FocusMode.marker(state.getBlock());
		for (int vertex = 0; vertex < 4; vertex++) {
			int alpha = (quad.baseColor(vertex) >>> 24) & 0xFF;
			if (alpha == 0) {
				alpha = 0xFF;
			}
			quad.setColor(vertex, (alpha << 24) | marker);
		}
	}
}
