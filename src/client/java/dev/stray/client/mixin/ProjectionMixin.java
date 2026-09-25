package dev.stray.client.mixin;

import dev.stray.client.render.AspectFov;
import dev.stray.client.render.TopDownCapture;
import net.minecraft.client.renderer.Projection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Projection.class)
public class ProjectionMixin {
	@ModifyVariable(method = "setupPerspective", at = @At("HEAD"), argsOnly = true, ordinal = 3)
	private float stray$aspectWidth(float width) {
		if (TopDownCapture.capturing()) {
			return width;
		}
		return AspectFov.apply(width);
	}
}
