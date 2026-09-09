package dev.voidmark.client.mixin;

import dev.voidmark.client.render.AspectFov;
import net.minecraft.client.renderer.Projection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Projection.class)
public class ProjectionMixin {
	@ModifyVariable(method = "setupPerspective", at = @At("HEAD"), argsOnly = true, ordinal = 3)
	private float voidmark$aspectWidth(float width) {
		return AspectFov.apply(width);
	}
}
