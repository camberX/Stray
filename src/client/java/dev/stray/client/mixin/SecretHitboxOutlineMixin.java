package dev.stray.client.mixin;

import dev.stray.client.dungeon.SecretHitboxes;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hitboxes: which outline the block you look at gets (see SecretHitboxes modes). Full edition only. */
@Mixin(LevelExtractor.class)
public class SecretHitboxOutlineMixin {
	@Inject(method = "extractBlockOutline", at = @At("HEAD"), require = 0)
	private void stray$outlineStart(CallbackInfo ci) {
		SecretHitboxes.outline(true);
	}

	@Inject(method = "extractBlockOutline", at = @At("RETURN"), require = 0)
	private void stray$outlineEnd(Camera camera, LevelRenderState state, CallbackInfo ci) {
		if (state.blockOutlineRenderState != null && SecretHitboxes.hideOutline()) {
			state.blockOutlineRenderState = null;
		}
		SecretHitboxes.outline(false);
	}
}
