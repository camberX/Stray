package dev.stray.client.mixin;

import net.minecraft.client.renderer.Projection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Projection.class)
public interface ProjectionAccessor {
	@Accessor("perspectiveFov")
	float stray$fov();

	@Accessor("width")
	float stray$width();

	@Accessor("height")
	float stray$height();
}
