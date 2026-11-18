package dev.stray.client.mixin;

import dev.stray.client.dungeon.SecretHitboxes;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Secret hitboxes: the outline of the block you look at keeps the block's normal shape. Full edition only. */
@Mixin(LevelExtractor.class)
public class SecretHitboxOutlineMixin {
	@Inject(method = "extractBlockOutline", at = @At("HEAD"), require = 0)
	private void stray$outlineStart(CallbackInfo ci) {
		SecretHitboxes.outline(true);
	}

	@Inject(method = "extractBlockOutline", at = @At("RETURN"), require = 0)
	private void stray$outlineEnd(CallbackInfo ci) {
		SecretHitboxes.outline(false);
	}
}
